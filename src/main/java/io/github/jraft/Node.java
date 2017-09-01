package io.github.jraft;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.*;
import com.google.protobuf.ByteString;
import grpc.Raft.*;
import grpc.RaftCommServiceGrpc;
import grpc.RaftCommServiceGrpc.RaftCommServiceFutureStub;
import io.github.jraft.exception.LogReplicaException;
import io.github.jraft.fsm.AppliedRes;
import io.github.jraft.fsm.IFSM;
import io.grpc.Channel;
import io.grpc.Server;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
    private final AtomicLong currentTerm_;

    private AtomicReference<State> state_;
    private Endpoint endpoint_;

    private int grantedVotes_;
    private long commitIndex_;
    private long lastApplied_;
    private LogStore logStore_;
    private StateTable stateTable_;
    private Integer leaderId_;

    private long[] nextIndex_;
    private long[] matchIndex_;
    

    private Server gCommServer_;
    private Server gRpcServer_;
    private Lock timeoutLock_;
    private Condition timeoutCond_;
    private boolean isRunning_;
    private IFSM fsm_;
    private Config conf_;
    private int id_;
    private ListeningExecutorService executer_ = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
    
    
    public Node(Config conf, IFSM fsm, List<Endpoint> cluster) throws IOException, InterruptedException {
        endpoint_ = conf.getEndpoint();
        conf_ = conf;
        cluster_ = cluster;
        fsm_ = fsm;
        id_ = conf.getId();
        
        
        stateTable_ = new StateTable(conf_);
        state_ = new AtomicReference<>(State.Follower);
        voteFor_ = stateTable_.getVoteFor();
        lastVoteTerm_ = stateTable_.getLastVoteTerm();
        currentTerm_ = new AtomicLong(stateTable_.getCurrentTerm());
    
        // If no 'lastApplied' in state table, lastApplied_ will be set to -1
        lastApplied_ = stateTable_.getLastApplied();
        grantedVotes_ = 0;
        leaderId_ = null;
        
        logStore_ = new LeveldbLogStore(conf.getDataDirPath());
        commitIndex_ = logStore_.getLastIndex();
    
        timeoutLock_ = new ReentrantLock();
        timeoutCond_ = timeoutLock_.newCondition();
        isRunning_ = true;
    
        startLoopThread();
        startCommServer();
        startRpcServer();
        
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
        return id_;
    }
    
    public State getState() {
        return state_.get();
    }

    public Config getConf() {
        return conf_;
    }

    @Nullable
    public Integer getLeaderId() {
        return leaderId_;
    }

    public long getCurrentTerm() {
        return currentTerm_.get();
    }
    
    private void setCurrentTerm(long term) {
        debug("setcurrentTerm: " + term);
        stateTable_.storeCurrentTerm(term);
        currentTerm_.set(term);
    }
    
    private void incrCurrentTerm() {
        setCurrentTerm(currentTerm_.get() + 1);
    }
    
    private void incrLastApplied() {
        stateTable_.storeLastApplied(++lastApplied_);
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
    
    private void debug(String message, Object... params) {
        logger_.debug("[node " + getId() + "]: " + message, params);
    }


    private void debug(String message) {
        logger_.debug("[node " + getId() + "]: " + message);
    }
    
    public boolean isLeader() {
        return getState().equals(State.Leader);
    }
    
    public boolean isFollower() {
        return getState().equals(State.Follower);
    }

    @VisibleForTesting
    public int getFollowerTimeoutMillSec() {
        return randTimeout(conf_.getFollowerTimeoutSec() * 1000);
    }

    public int randTimeout(int min) {
        return new Random().nextInt(min) + min;
    }
    
    private int getCandidateTimeoutMilliSec() {
        return randTimeout(conf_.getCandidateTimeoutSec() * 1000);
    }
    
    private int getQuorumNum() {
        synchronized (cluster_) {
            return cluster_.size() / 2 + 1;
        }
    }
    
    private void runFollower() {
        if(cluster_.size() == 1) {
            becomeLeader();
            return;
        }

        int followerTimeoutMilliSec = getFollowerTimeoutMillSec();
        debug("in follower state, await secs: {}", followerTimeoutMilliSec);
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
            nextIndex_[i] = commitIndex_+1;

        matchIndex_ = new long[cluster_.size()];
    }
    
    private void startCandidateSate() {
        incrCurrentTerm();
        voteFor_ = id_;
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
        if(grantedVotes_ >= getQuorumNum()) {
            becomeLeader();
            return;
        }

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
                debug("receive success vote reply, result length: {}", result.size());
                for(RequestVoteResp resp : result) {
                    if(resp == null) {
                        continue;
                    }
                    debug("isVotedGrated: {}", resp.getVoteGranted());
                    if(resp.getTerm() > currentTerm_.get()) {
                        becomeFollower();
                        signalTimeoutCond();
                        return;
                    }
                    
                    if(resp.getVoteGranted()) {
                        debug("quorumNum: {}", getQuorumNum());
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
    
    private void becomeFollower() {
        leaderId_ = null;
        state_.set(State.Follower);
    }

    private void becomeLeader() {
        leaderId_ = getId();
        state_.set(State.Leader);
        boolean success = dispatchHeartbeat(conf_.getRequestTimeoutSec());
        if (!success) {
            debug("failed to dispatch heartbeat, can not become leader");
            becomeFollower();
        }
        reinitLeaderStates();
    }
    
    private void runLeader() {
        boolean success = dispatchHeartbeat(conf_.getRequestTimeoutSec());
        if (!success) becomeFollower();
        
        awaitFor(conf_.getLeaderHbIntervalSec() * 1000);
    }
    
    private boolean dispatchHeartbeat(int timeoutSecs) {
        AppendEntriesReq req = AppendEntriesReq.newBuilder()
                .setTerm(currentTerm_.get())
                .setLeaderCommit(commitIndex_)
                .build();
        debug("I am leader, send heartbeat");
        List<ListenableFuture<AppendEntriesResp>> futures = new LinkedList<>();
        List<Endpoint> others = getOthers();
        if (others.isEmpty()) return true;

        final int quorumNum = getQuorumNum();
        final CountDownLatch appendEntriesLatch = new CountDownLatch(quorumNum-1);
        final AtomicInteger failureCount = new AtomicInteger(0);

        for (Endpoint ep : others) {
            ListenableFuture<AppendEntriesResp> future = executer_.submit(() -> {
                Channel channel = NettyChannelBuilder.forAddress(ep.getHost(), ep.getPort()).
                        negotiationType(NegotiationType.PLAINTEXT).build();
                RaftCommServiceGrpc.RaftCommServiceBlockingStub stub = RaftCommServiceGrpc.newBlockingStub(channel);
                return stub.appendEntries(req);
            });

            Futures.addCallback(future, new FutureCallback<AppendEntriesResp>() {
                @Override
                public void onSuccess(@Nullable AppendEntriesResp result) {
                    if (result == null) return;
                    if (result.getSuccess()) {
                        appendEntriesLatch.countDown();
                    } else {
                        if (failureCount.incrementAndGet() >= quorumNum) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    if (failureCount.incrementAndGet() >= quorumNum) {
                        Thread.currentThread().interrupt();
                    }
                    debug("failed to appendEntries, reason: {}", t);
                }
            });
        }

        try {
            boolean timeout = !appendEntriesLatch.await(timeoutSecs, TimeUnit.SECONDS);
            if (timeout || appendEntriesLatch.getCount() > 0) {
                debug("append entries timeout, return false");
                return false;
            }
        } catch (InterruptedException e) {
            // too much failures.
            return false;
        }
        debug("succeed to dispatch heartbeat");
        return true;
    }
    
    @Override
    public void requestVote(RequestVoteReq req,
                            io.grpc.stub.StreamObserver<RequestVoteResp> responseObserver) {
        
        RequestVoteResp.Builder respBuilder = RequestVoteResp.newBuilder();
        debug("req term: {}, currentTerm: {}, , request from: {}, voteFor: {}",
        req.getTerm(), currentTerm_.get(), req.getCandidateId(), voteFor_);

        synchronized (currentTerm_) {
            if (req.getTerm() < currentTerm_.get()) {
                respBuilder.setVoteGranted(false);
            } else if (req.getTerm() > currentTerm_.get() ||
                    (voteFor_ == null || voteFor_ == req.getCandidateId()) && (req.getLastLogTerm() == lastVoteTerm_)) {

                if (!isFollower()) {
                    becomeFollower();
                }
                signalTimeoutCond();
                respBuilder.setVoteGranted(true);
                updateVoteFor(req.getCandidateId());
                lastVoteTerm_ = req.getTerm();
                voteFor_ = req.getCandidateId();
                setCurrentTerm(req.getTerm());
            } else {
                respBuilder.setVoteGranted(false);
            }
        }
    
        if (respBuilder.getVoteGranted()) {
            debug("grant vote to node: {}", req.getCandidateId());
        } else {
            debug("deny vote to node: {}", req.getCandidateId());
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

        debug("recv appendEntries");
       AppendEntriesResp.Builder respBuilder = AppendEntriesResp.newBuilder();
       
       // When a leader unavailable, new leader is elected, after old leader come back,
        // it will still think it's leader and send entries.
        long leaderTerm = req.getTerm();
        if (leaderTerm < currentTerm_.get()) {
           respBuilder.setSuccess(false);
       }
       else {
           signalTimeoutCond();
           leaderId_ = req.getLeaderId();
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
                long lastLocalIndex = logStore_.getLastIndex();
                debug("req prelog index {}, logstore lastindex {}",
                        req.getPreLogIndex(), lastLocalIndex);

                Log latestLog = logStore_.getLog(lastLocalIndex);
                if ((latestLog != null && latestLog.getTerm() == req.getPreLogTerm()) &&
                        lastLocalIndex == req.getPreLogIndex() ||
                        (req.getPreLogIndex() == -1 && lastLocalIndex == -1)) {
                    // preLog exists and term of preLog equals with req.getPreLogTerm(),
                    // or no logs on both leader and follower, return success. 
                    List<Log> logs = req.getEntriesList();
                    debug("get logs range from {} to {}", logs.get(0).getIndex(),
                            logs.get(logs.size()-1).getIndex());
                    logStore_.storeLog(logs);
                    respBuilder.setSuccess(true);
                } else {
                    respBuilder.setSuccess(false);
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

    @Override
    public void clientOperate(grpc.Raft.ClientReq request,
                              io.grpc.stub.StreamObserver<grpc.Raft.ClientResp> responseObserver) {
        debug("receive client op request: {}", request.getOp());
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
    
    /**
     * Callable Job for leader to dispatch entries received from client
     * to followers.
     */
    class DispatchEntriesJob implements Callable<Boolean> {
        
        private Endpoint endpoint_;
        
        public DispatchEntriesJob(Endpoint endpoint) {
            endpoint_ = endpoint;
        }
        
        @Override
        public Boolean call() throws LogReplicaException {
            AppendEntriesReq.Builder builder = AppendEntriesReq.newBuilder();
            while(true) {
                int followerId = endpoint_.getId_();
                long lastIndex = logStore_.getLastIndex();
                long preLogIndex = -1;
                long preLogTerm = -1;
                long startLogIndex;


                startLogIndex = nextIndex_[followerId];
                if(startLogIndex > lastIndex) {
                    startLogIndex = lastIndex;
                }

                Log preLog = logStore_.getLog(startLogIndex - 1);
                if(preLog != null) {
                    preLogIndex = preLog.getIndex();
                    preLogTerm = preLog.getTerm();
                }

                List<Log> logs = logStore_.getLogs(startLogIndex, lastIndex);
                debug("dispatch log  to {}, log index from {} to {}, size {}",
                        endpoint_.getId_(), logs.get(0).getIndex(), logs.get(logs.size()-1).getIndex(),
                        logs.size());


                builder.addAllEntries(logs).setLeaderId(getId()).setLeaderCommit(commitIndex_).setPreLogIndex(preLogIndex).setPreLogTerm(preLogTerm).setTerm(currentTerm_.get());

                Channel channel = NettyChannelBuilder.forAddress(endpoint_.getHost(), endpoint_.getPort()).
                        negotiationType(NegotiationType.PLAINTEXT).build();
                RaftCommServiceGrpc.RaftCommServiceBlockingStub stub = RaftCommServiceGrpc.newBlockingStub(channel);
                AppendEntriesResp resp;
                try {
                    resp = stub.appendEntries(builder.build());
                }catch (StatusRuntimeException e) {
                    debug("failed to append entries to " + endpoint_ , e);
                    return false;
                }
                if(resp.getSuccess()) {
                    debug("set node {} 's nextIndex to {}", followerId, lastIndex + 1);
                    nextIndex_[followerId] = lastIndex + 1;
                    matchIndex_[followerId] = lastIndex;
                    break;
                } else {
                    if(--nextIndex_[followerId] < 0)
                        throw new LogReplicaException("why nexIndex < 0? when appending entries to " + endpoint_);
                }
            }
            return true;
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
    
    
        if (request.getOp() == Op.Get) {
            AppliedRes res = applyFsm(log);
            return ClientResp.newBuilder().setSuccess(res.isSuccess()).addResult(res.getResult()).setError(res.getError().toString()).build();
        
        }
        
        ImmutableList<Log> logs = ImmutableList.of(log);
        logStore_.storeLog(logs);

        List<Endpoint> endpoints = getOthers();
        if (!endpoints.isEmpty()) {
            final int quorumNum = getQuorumNum();
            final CountDownLatch countDownLatch = new CountDownLatch(quorumNum-1);
            final AtomicInteger failureCount = new AtomicInteger(0);

            for (Endpoint endpoint : getOthers()) {
                ListenableFuture<Boolean> future = executer_.submit(new DispatchEntriesJob(endpoint));
                Futures.addCallback(future, new FutureCallback<Boolean>() {
                    @Override
                    public void onSuccess(@Nullable Boolean result) {
                        if(result != null && result)
                            countDownLatch.countDown();
                    }
    
                    @Override
                    public void onFailure(Throwable t) {
                        if (failureCount.incrementAndGet() >= quorumNum) Thread.currentThread().interrupt();
                        logger_.warn("", t);
                    }
                });
            }
            try {
                boolean timeout = !countDownLatch.await(10, TimeUnit.SECONDS);
                if (timeout) {
                    becomeFollower();
                    return ClientResp.newBuilder().setSuccess(false).setError("timeout").build();
                }
    
            } catch (InterruptedException e) {
                return ClientResp.newBuilder().setSuccess(false).build();
            }
        }
    
        // commit log
        applyFSM(++commitIndex_);
    
        // Notify followers to commit log
        debug("commit log on leader node, and notify followers via heartbeat");
        boolean success = dispatchHeartbeat(Config.getRequestTimeoutSec());
        if (!success) {
            debug("failed to dispatchHeartbeat, reply failure msg to client");
            becomeFollower();
            ClientResp.newBuilder().setSuccess(false).build();
        }

        return ClientResp.newBuilder().setSuccess(true).build();
    }
    
    private void applyFSM(long index) {
        while (lastApplied_ + 1 <= index) {
            incrLastApplied();
            applyFsm(logStore_.getLog(lastApplied_));
        }
    }
    
    private AppliedRes applyFsm(Log log) {
        return fsm_.apply(log);
    }
    
    private List<Endpoint> getOthers() {
        synchronized (cluster_) {
            List<Endpoint> others = new ArrayList<>();
            others.addAll(cluster_);
            others.remove(getEndpoint());
            return others;
        }
    }

    IFSM getFsm() {
        return fsm_;
    }
    
    private void startCommServer() throws IOException {
    
        gCommServer_ = NettyServerBuilder.forPort(getPort())
                .addService(this)
                .build();
        gCommServer_.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Use stderr here since the logger may have been reset by its JVM shutdown hook.
            debug("*** shutting down gRPC server since JVM is shutting down");
            debug("*** server shut down");
        }));
    }

    private void startRpcServer() throws IOException {
        gRpcServer_ = NettyServerBuilder.forPort(conf_.getLocalServerPort())
                .addService(this)
                .build();
        gRpcServer_.start();
    }

    public void fail() {
        leaderId_ = null;
        state_.set(State.Shutdown);
        isRunning_ = false;
        gRpcServer_.shutdown();
        gCommServer_.shutdown();
    }

    public void resume() throws IOException {
        state_.set(State.Follower);
        startRpcServer();
        startCommServer();
        isRunning_ = true;
    }
    
    public void shutdown() {
        debug("is shut down");
        fail();
        try {
            stateTable_.close();
            logStore_.close();
            fsm_.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    
    }
}
