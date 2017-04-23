package io.github.jraft;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.*;
import com.google.protobuf.ByteString;
import grpc.Raft.*;
import grpc.RaftCommServiceGrpc;
import grpc.RaftCommServiceGrpc.RaftCommServiceFutureStub;
import io.grpc.Channel;
import io.grpc.Server;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

import javax.annotation.Nullable;


public class Node extends RaftCommServiceGrpc.RaftCommServiceImplBase {
    private static final Logger logger_ = LogManager.getLogger(Node.class);
    enum State {
        Follower,
        Candidate,
        Leader,
        Shutdown
    }
    
    private final List<Endpoint> cluster_;
    private Integer voteFor_;
    private Long lastVoteTerm_;
    private AtomicLong currentTerm_;

    private AtomicReference<State> state_;
    private Endpoint endpoint_;

    private int grantedVotes_;
    private long commitIndex_;
    private long lastApplied_;
    private LogStore logStore_;
    private StateTable stateTable_;

    private long[] nextIndex_;
    private long[] matchIndex_;
    

    private Server gRpcServer_;
    private Lock timeoutLock_;
    private Condition timeoutCond_;
    private boolean isRunning_;
    private FSM fsm_;
    

    public Node(Endpoint endpoint, List<Endpoint> cluster) throws IOException, InterruptedException {
        endpoint_ = endpoint;
        cluster_ = cluster;
    
        stateTable_ = new StateTable(getId());
        state_ = new AtomicReference<>(State.Follower);
        voteFor_ = stateTable_.getVoteFor();
        lastVoteTerm_ = stateTable_.getLastVoteTerm();
        currentTerm_ = new AtomicLong(stateTable_.getCurrentTerm());
        lastApplied_ = stateTable_.getLastApplied();
        grantedVotes_ = 0;

        logStore_ = new LeveldbLogStore(Config.persistenceFilePathPrefix + getId());
        commitIndex_ = logStore_.getLastIndex();

        fsm_ = new FSM();
    
        timeoutLock_ = new ReentrantLock();
        timeoutCond_ = timeoutLock_.newCondition();
        isRunning_ = true;
    
        startLoopThread();
        startCommServer();
        
    }
    
    public Endpoint getEndpoint() {
        return endpoint_;
    }
    
    public String getHost() {
        return endpoint_.getHost();
    }
    
    public int getPort() {
        return endpoint_.getPort();
    }
    
    public int getId() {
        return endpoint_.getId();
    }
    
    public State getState() {
        return state_.get();
    }
    
    private void setCurrentTerm(long term) {
        stateTable_.storeCurrentTerm(term);
        currentTerm_.set(term);
    }
    
    private void incrCurrentTerm() {
        setCurrentTerm(currentTerm_.get() + 1);
    }
    
    private long incrAndGetLastApplied() {
        stateTable_.storeLastApplied(lastApplied_ + 1);
        return ++lastApplied_;
    }
    
    private void updateVoteFor(int voteForCandId) {
        stateTable_.storeVoteFor(voteForCandId);
        stateTable_.storeLastVoteTerm(currentTerm_.get());
        voteFor_ = voteForCandId;
    }
    
    public void startLoopThread() {
    
        Thread loopThread = new Thread(() -> {
            while (isRunning_) {
                switch (getState()) {
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
        loopThread.start();
    }
    
    private void debug(String str) {
        logger_.debug("node " + getId() + ": " + str);
    }
    
    private boolean isLeader() {
        return getState().equals(State.Leader);
    }
    
    private boolean isFollower() {
        return getState().equals(State.Follower);
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
            nextIndex_[i] = 0;

        matchIndex_ = new long[cluster_.size()];
    }
    
    private void startCandidateSate() {
        incrCurrentTerm();
        grantedVotes_ = 1;
    }
    
    private void runCandidate() {
        debug("new round for candidate");
        startCandidateSate();
    
        RequestVoteReq req = RequestVoteReq.newBuilder()
                .setTerm(currentTerm_.get())
                .setCandidateId(getId())
                .build();
        
        List<ListenableFuture<RequestVoteResp>> futures = new ArrayList<>();
        for(Endpoint ep : getOthers()) {
            Channel channel = NettyChannelBuilder.forAddress(ep.getHost(), ep.getPort())
                    .negotiationType(NegotiationType.PLAINTEXT)
                    .build();
            RaftCommServiceFutureStub stub = RaftCommServiceGrpc.newFutureStub(channel);
            futures.add(stub.requestVote(req));
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
        sendHeartbeat();
        awaitFor(Config.leaderHbIntervalSec * 1000);
    }
    
    private List<ListenableFuture<AppendEntriesResp>> sendHeartbeat() {
        AppendEntriesReq req = AppendEntriesReq.newBuilder()
                .setTerm(currentTerm_.get())
                .setLeaderCommit(commitIndex_)
                .build();
        debug("I am leader, send heartbeat");
        List<ListenableFuture<AppendEntriesResp>> futures = new LinkedList<>();
        for(Endpoint ep : getOthers()) {
            Channel channel = NettyChannelBuilder.forAddress(ep.getHost(), ep.getPort()).
                    negotiationType(NegotiationType.PLAINTEXT).build();
            RaftCommServiceFutureStub stub = RaftCommServiceGrpc.newFutureStub(channel);
            futures.add(stub.appendEntries(req));
        }
        
        return futures;
    }
    
    @Override
    public void requestVote(RequestVoteReq req,
                            io.grpc.stub.StreamObserver<RequestVoteResp> responseObserver) {
        
        debug("receive vote request from node: " + req.getCandidateId());
        RequestVoteResp.Builder respBuilder = RequestVoteResp.newBuilder();
//        debug("current state: " + state_.get() +
////                ", current term: " + currentTerm_.get() + ", req term: " + req.getTerm() + ", voteFor: " + voteFor_);

        if(req.getTerm() > currentTerm_.get()) {
            setCurrentTerm(req.getTerm());
            if (!isFollower()) {
                state_.set(State.Follower);
                signalTimeoutCond();
            }
            respBuilder.setVoteGranted(true);
            updateVoteFor(req.getCandidateId());
        } else if ((voteFor_ == null || lastVoteTerm_ == null) || lastVoteTerm_.equals(req.getTerm()) && voteFor_.equals(req.getCandidateId())) {
            respBuilder.setVoteGranted(true);
            updateVoteFor(req.getCandidateId());
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
        long leaderTerm = req.getTerm();
        if (leaderTerm < currentTerm_.get()) {
           respBuilder.setSuccess(false);
       }
       else {
           signalTimeoutCond();
            if(leaderTerm > currentTerm_.get()) {
                setCurrentTerm(leaderTerm);
                if(!isFollower()) {
                    state_.set(State.Follower);
                }
            }
            
            if(req.getEntriesCount() == 0) {
                // Handle heartbeat
                respBuilder.setSuccess(true);
            }
            else {
                // Handle append entries
                Log log = logStore_.getLog(req.getPreLogIndex());
                if (log == null || log.getTerm() != req.getPreLogTerm())
                    respBuilder.setSuccess(false);
                else {
                    List<Log> logs = req.getEntriesList();
                    logStore_.storeLog(logs);
                    
                    respBuilder.setSuccess(true);
                }
            }
            
            if (req.getLeaderCommit() > commitIndex_) {
                commitIndex_ = Math.min(req.getLeaderCommit(), logStore_.getLastIndex());
                applyFSM(commitIndex_);
                
            }
       }

        responseObserver.onNext(respBuilder.build());
        responseObserver.onCompleted();
    }
    
    public void clientOperate(grpc.Raft.ClientReq request,
                              io.grpc.stub.StreamObserver<grpc.Raft.ClientResp> responseObserver) {
        ClientResp resp;
        if(!isLeader()) {
            ClientResp.Builder respBuilder = ClientResp.newBuilder();
            respBuilder.setSuccess(false);
            respBuilder.setError("not leader");
            resp = respBuilder.build();
        } else {
            resp = processClientRpc(request);
        }
        
        responseObserver.onNext(resp);
        responseObserver.onCompleted();
    }
    
    class DispatchEntriesJob implements Callable<Object> {
    
        private Endpoint endpoint_;
        
        public DispatchEntriesJob(Endpoint endpoint) {
            endpoint_ = endpoint;
        }
        @Override
        public Object call() throws Exception {
            AppendEntriesReq.Builder builder = AppendEntriesReq.newBuilder();
            
            while(true) {
                int followerId = endpoint_.getId();
                long preLogIndex = -1;
                long prelogTerm = -1;
                if (nextIndex_[followerId] > 0) {
                    Log preLog = logStore_.getLog(nextIndex_[followerId] - 1);
                    prelogTerm = preLog.getTerm();
                    preLogIndex = preLog.getIndex();
                }
    
                List<Log> logs = logStore_.getLogs(nextIndex_[followerId], commitIndex_);
    
                builder.addAllEntries(logs)
                        .setLeaderId(getId())
                        .setLeaderCommit(commitIndex_)
                        .setPreLogIndex(preLogIndex)
                        .setPreLogTerm(prelogTerm)
                        .setTerm(currentTerm_.get());
    
                Channel channel = NettyChannelBuilder.forAddress(endpoint_.getHost(), endpoint_.getPort()).
                        negotiationType(NegotiationType.PLAINTEXT).build();
                RaftCommServiceGrpc.RaftCommServiceBlockingStub stub = RaftCommServiceGrpc.newBlockingStub(channel);
                AppendEntriesResp resp = stub.appendEntries(builder.build());
                if(resp.getSuccess()) {
                    nextIndex_[followerId] = commitIndex_ + 1;
                    matchIndex_[followerId] = commitIndex_;
                    break;
                }
                else {
                    if(--nextIndex_[followerId] < 0)
                        throw new Exception("why nexIndex < 0? ");
                }
            }
            return new Object();
        }
    }
    
    private ClientResp processClientRpc(ClientReq request) {
        assert isLeader();
        ByteString reqByteString = request.toByteString();
        Log.Builder builder = Log.newBuilder()
                .setData(reqByteString)
                .setIndex(commitIndex_+1)
                .setTerm(currentTerm_.get());
        Log log = builder.build();
        
        ImmutableList<Log> logs = ImmutableList.of(log);
        logStore_.storeLog(logs);
    
    
        ListeningExecutorService service = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
        final CountDownLatch countDownLatch = new CountDownLatch(getQuorumNum());
        
        for(Endpoint endpoint : getOthers()) {
            ListenableFuture<Object> future = service.submit(new DispatchEntriesJob(endpoint));
            Futures.addCallback(future, new FutureCallback<Object>() {
                @Override
                public void onSuccess(@Nullable Object result) {
                    countDownLatch.countDown();
                }
    
                @Override
                public void onFailure(Throwable t) {
                    t.printStackTrace();
                }
            });
        }
        try {
            boolean timeout = !countDownLatch.await(10, TimeUnit.SECONDS);
            if(timeout)
                return ClientResp.newBuilder().setSuccess(false).setError("timeout").build();
            
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // Notify followers to commit log
        List<ListenableFuture<AppendEntriesResp>> futures = sendHeartbeat();
        final CountDownLatch  appendEntriesLatch = new CountDownLatch(getQuorumNum());
        for(ListenableFuture<AppendEntriesResp> future : futures) {
            Futures.addCallback(future, new FutureCallback<AppendEntriesResp>() {
                @Override
                public void onSuccess(@Nullable AppendEntriesResp result) {
                    if(result == null) return;
                    if(result.getSuccess()) {
                        appendEntriesLatch.countDown();
                    }
                }
    
                @Override
                public void onFailure(Throwable t) {
                    debug(t.getMessage());
                }
            });
        }
    
        try {
            boolean timeout = ! appendEntriesLatch.await(10, TimeUnit.SECONDS);
            if(timeout)
                return ClientResp.newBuilder().setSuccess(false).setError("timeout").build();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    
        applyFSM(++commitIndex_);

        return ClientResp.newBuilder().setSuccess(true).build();
    }
    
    private void applyFSM(long index) {
        while (incrAndGetLastApplied() <= index) {
            fsm_.apply(logStore_.getLog(lastApplied_));
        }
    }
    
    private List<Endpoint> getOthers() {
        synchronized (cluster_) {
            List<Endpoint> others = new ArrayList<>();
            others.addAll(cluster_);
            others.remove(getEndpoint());
            return others;
        }
    }
    
    private void startCommServer() throws IOException {
    
        gRpcServer_ = NettyServerBuilder.forPort(getPort())
                .addService(this)
                .build();
        gRpcServer_.start();
    
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Use stderr here since the logger may have been reset by its JVM shutdown hook.
            System.err.println("*** shutting down gRPC server since JVM is shutting down");
            System.err.println("*** server shut down");
        }));
    }
    
    public void shutdown() {
        debug("is shut down");
        state_.set(State.Shutdown);
        isRunning_ = false;
        gRpcServer_.shutdown();
    }
    
    public static void main(String[] args) throws IOException, InterruptedException {
        
        Configurator.setRootLevel(Level.DEBUG);
        
        String host =  "localhost";
        int port = 8300;
        
        ArrayList<Node> nodes = new ArrayList<>();
        ArrayList<Endpoint> endpoints = new ArrayList<>();
        
        for(int i=0; i<5; ++i) {
            endpoints.add(new Endpoint(i, host, port+i));
        }
        
        for(int i=0; i<5; ++i) {
            nodes.add(new Node(endpoints.get(i), endpoints));
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
        
        
    }
}
