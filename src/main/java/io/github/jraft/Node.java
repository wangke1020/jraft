package io.github.jraft;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import grpc.Raft;
import grpc.Raft.*;
import grpc.RaftCommServiceGrpc;
import grpc.RaftCommServiceGrpc.RaftCommServiceFutureStub;
import io.grpc.Channel;
import io.grpc.Server;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import javax.annotation.Nullable;


public class Node extends RaftCommServiceGrpc.RaftCommServiceImplBase {
    
    enum State {
        Follower,
        Candidate,
        Leader,
        Shutdown
    }

    private int id_;
    private final List<Node> cluster_;
    private Integer voteFor_;
    private AtomicLong currentTerm_;

    private AtomicReference<State> state_;
    private String host_;
    private int port_;

    private int grantedVotes_;
    private long commitIndex_;
    private long lastApplied_;
    private LogStore logStore_;

    private long[] nextIndex_;
    private long[] matchIndex_;
    

    private Server gRpcServer_;
    private Lock timeoutLock_;
    private Condition timeoutCond_;
    private Thread loopThread_;
    private boolean isRunning_;

    public Node(int id, String host, int port, List<Node> cluster) throws IOException, InterruptedException {
        id_ = id;
        port_ = port;
        host_ = host;
        cluster_ = cluster;

        state_ = new AtomicReference<>(State.Follower);
        voteFor_ = null;
        currentTerm_ = new AtomicLong(0);
        grantedVotes_ = 0;
        commitIndex_ = 0;
        lastApplied_ = 0;
        logStore_ = new LeveldbLogStore(Config.persistenceFilePathPrefix + id);
    
        timeoutLock_ = new ReentrantLock();
        timeoutCond_ = timeoutLock_.newCondition();
        isRunning_ = true;
    
        startLoopThread();
        startCommServer();
        
    }
    
    public String getHost() {
        return host_;
    }
    
    public int getPort() {
        return port_;
    }
    
    public int getId() {
        return id_;
    }
    
    public State getState() {
        return state_.get();
    }
    
    public void startLoopThread() {
    
        loopThread_ = new Thread(() -> {
            while(isRunning_) {
                switch (state_.get()) {
                    case Follower:
                        runFollower();
                        break;
                    case Candidate:
                        runCandidate();
                        break;
                    case Leader:
                        runLeader();
                        break;
                    default:
                        break;
                }
            }
        });
        loopThread_.start();
    }
    
    private void debug(String str) {
        System.out.println("node " + id_ + ": " + str);
    }
    
    
    private int getFollowerTimeoutMillSec() {
        return (new Random().nextInt(150)
                + Config.FollowerTimeoutSec * 1000 + 1);
    }
    
    private int getCandidateTimeoutMilliSec() {
        return Config.CandidateTimeoutSec * 1000;
    }
    
    private int getQuorumNum() {
        synchronized (cluster_) {
            return cluster_.size() / 2 + 1;
        }
    }
    
    private void runFollower() {
 
        int followerTimeoutMilliSec = getFollowerTimeoutMillSec();
        debug("in follower state, await secs: " + followerTimeoutMilliSec);
        awaitFor(followerTimeoutMilliSec);
    
        debug("follower timeout, become candidate");
        state_.set(State.Candidate);
        
    }
    
    private void awaitFor(int millSecs) {
        try {
            timeoutLock_.lock();
            while (timeoutCond_.await(millSecs, TimeUnit.MILLISECONDS)) {
            }
        }catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            timeoutLock_.unlock();
        }
    }

    private void reinitLeaderStates() {
        nextIndex_ = new long[cluster_.size()];
        for(int i=0;i<cluster_.size();++i)
            nextIndex_[i] = commitIndex_ + 1;

        matchIndex_ = new long[cluster_.size()];
    }
    
    private void startCandidateSate() {
        currentTerm_.incrementAndGet();
        grantedVotes_ = 1;
    }
    
    private void runCandidate() {
        debug("new round for candidate");
        startCandidateSate();
    
        RequestVoteReq req = RequestVoteReq.newBuilder()
                .setTerm(currentTerm_.get())
                .setCandidateId(id_)
                .build();
        
        List<ListenableFuture<RequestVoteResp>> futures = new ArrayList<>();
        for(Node n : cluster_) {
            if(n.getId() != id_) {
                Channel channel = NettyChannelBuilder.forAddress(n.getHost(), n.getPort())
                        .negotiationType(NegotiationType.PLAINTEXT)
                        .build();
                RaftCommServiceFutureStub stub = RaftCommServiceGrpc.newFutureStub(channel);
                futures.add(stub.requestVote(req));
            }
        }
    
        ListenableFuture<List<RequestVoteResp>> successfulReq = Futures.successfulAsList(futures);
        Futures.addCallback(successfulReq, new FutureCallback<List<RequestVoteResp>>() {
            @Override
            public void onSuccess(@Nullable List<RequestVoteResp> result) {
                if(result == null)
                    return;
                debug("receive success vote reply, result length: " + result.size());
                for(RequestVoteResp resp : result) {
                    if(resp == null) {
                        continue;
                    }
                    debug("isVotedGrated: " + resp.getVoteGranted());
                    if(resp.getTerm() > currentTerm_.get()) {
                        state_.set(State.Follower);
                        signalTimeoutCond();
                        return;
                    }
                    
                    if(resp.getVoteGranted()) {
                        debug("quorumNum: " + getQuorumNum());
                        if(++grantedVotes_ >= getQuorumNum()) {
                            becomeLeader();
                            debug("got quorum votes, become leader");
                            return;
                        }
                    }
                }
            }
    
            @Override
            public void onFailure(Throwable t) {
                System.out.println("receive failed msg: " + t);
            }
        });
    
        awaitFor(getCandidateTimeoutMilliSec());
    }

    private void becomeLeader() {
        state_.set(State.Leader);
        reinitLeaderStates();
    }
    
    private void runLeader() {
        AppendEntriesReq req = AppendEntriesReq.newBuilder()
                .setTerm(currentTerm_.get()).build();
        debug("I am leader, send heartbeat");
        for(Node n : cluster_) {
            if (n.getId() != id_) {
                debug("node " + n.id_  + " is in state: " + n.getState());
                Channel channel = NettyChannelBuilder.forAddress(n.getHost(), n.getPort()).
                        negotiationType(NegotiationType.PLAINTEXT).build();
                RaftCommServiceFutureStub stub = RaftCommServiceGrpc.newFutureStub(channel);
                stub.appendEntries(req);
            }
        }
        awaitFor(Config.leaderHbIntervalSec * 1000);
    }
    
    @Override
    public void requestVote(RequestVoteReq req,
                            io.grpc.stub.StreamObserver<RequestVoteResp> responseObserver) {
        
        debug("receive vote request from node: " + req.getCandidateId());
        RequestVoteResp.Builder respBuilder = RequestVoteResp.newBuilder();
//        debug("current state: " + state_.get() +
////                ", current term: " + currentTerm_.get() + ", req term: " + req.getTerm() + ", voteFor: " + voteFor_);

        if(req.getTerm() > currentTerm_.get()) {
            currentTerm_.set(req.getTerm());
            if(!state_.equals(State.Follower)) {
                state_.set(State.Follower);
                signalTimeoutCond();
            }
            respBuilder.setVoteGranted(true);
            voteFor_ = req.getCandidateId();
        }else if(req.getTerm() == currentTerm_.get() &&
                (voteFor_ == null || voteFor_ == req.getCandidateId())) {

            respBuilder.setVoteGranted(true);
            voteFor_ = req.getCandidateId();
        }else {
            respBuilder.setVoteGranted(false);
        }
        
        respBuilder.setTerm(currentTerm_.get());
        
        responseObserver.onNext(respBuilder.build());
        responseObserver.onCompleted();
    }
    
    private void signalTimeoutCond() {
        try {
            timeoutLock_.lock();
            timeoutCond_.signal();
        }catch (Exception e) {
            e.printStackTrace();
        }finally {
            timeoutLock_.unlock();
        }
    }
    
    @Override
    public void appendEntries(AppendEntriesReq req,
                              io.grpc.stub.StreamObserver<AppendEntriesResp> responseObserver) {
        
       AppendEntriesResp.Builder respBuilder = AppendEntriesResp.newBuilder();
       
       // When a leader unavailable, new leader is elected, after old leader come back,
        // it will still think it's leader and send entries.
       if (req.getTerm() < currentTerm_.get()) {
           respBuilder.setSuccess(false);
       }
       else {
           switch (state_.get()) {
               case Follower:
                   signalTimeoutCond();
                   Log log =  logStore_.getLog(req.getPreLogIndex());
                   if(log == null || log.getTerm() != req.getPreLogTerm())
                        respBuilder.setSuccess(false);
                   else{
                       List<Log> logs = req.getEntriesList();
                       logStore_.storeLog(logs);

                       if(req.getLeaderCommit() > commitIndex_) {
                           commitIndex_ = Math.max(req.getLeaderCommit(), logStore_.getLastIndex());
                       }
                       respBuilder.setSuccess(true);
                   }

                   break;
               case Candidate:
                   signalTimeoutCond();
                   state_.set(State.Follower);
                   currentTerm_.set(req.getTerm());
                   respBuilder.setSuccess(true);
                   break;
               case Leader:
                   signalTimeoutCond();
                   state_.set(State.Follower);
                   currentTerm_.set(req.getTerm());
                   respBuilder.setSuccess(true);
                   break;
               default:
                   break;
           }

       }


        responseObserver.onNext(respBuilder.build());
        responseObserver.onCompleted();
    }
    
    private void startCommServer() throws IOException {
    
        gRpcServer_ = NettyServerBuilder.forPort(port_)
                .addService(this)
                .build();
        gRpcServer_.start();
    
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                System.err.println("*** server shut down");
            }
        });
    }
    
    public void shutdown() {
        debug("is shut down");
        state_.set(State.Shutdown);
        isRunning_ = false;
        gRpcServer_.shutdown();
    }
    
    public static void main(String[] args) throws IOException, InterruptedException {
        String host =  "localhost";
        int port = 8300;
        
        ArrayList<Node> nodes = new ArrayList<>();
        
        for(int i=0; i<5; ++i) {
             nodes.add(new Node(i, host, port+i, nodes));
        }
        
        Thread.sleep(30 * 1000);
        
        Node testNode = null;
        for(Node n : nodes) {
            if(n.getState() == State.Leader) {
                n.shutdown();
                testNode = n;
                break;
            }
        }
        
        
        
        Thread.sleep(30 * 1000);
        
        nodes.remove(testNode);
        
        nodes.add(new Node(5, host, port+5, nodes));
        
        
        Thread.sleep(30 * 1000);
        
        
    }
}
