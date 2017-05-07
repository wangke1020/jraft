package io.github.jraft;


import grpc.Raft;
import grpc.Raft.ClientResp;
import grpc.Raft.Log;
import grpc.RaftCommServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.commons.io.FileUtils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.*;

class MockFSM implements IFSM {
    
    public MockFSM(String fliePath) {
        
    }
    
    private List<Log> logs_ = new ArrayList<>();
    
    @Override
    public AppliedRes apply(Log log) {
        System.out.println("===============>MockFSM: add log to list");
        logs_.add(log);
        return AppliedRes.newSuccessRes();
    }
    
    public int getLogNum() {
        return logs_.size();
    }
}


public class RaftTest {
    
    private String tmpFolder_ = "/tmp/raft-test/";
    private File dir_;

    @BeforeClass
    public static void beforeClass() {
        Configurator.setRootLevel(Level.DEBUG);
    }

    @Before
    public void beforeTest() throws IOException {
        dir_ = new File(tmpFolder_);
        FileUtils.deleteDirectory(dir_);
        if(!dir_.isDirectory())
            dir_.mkdir();
    }

    @After
    public void afterTest() throws IOException {
        FileUtils.deleteDirectory(dir_);
    }
    
    
    public class TestClient implements Closeable {
        private final ManagedChannel channel;
        private final RaftCommServiceGrpc.RaftCommServiceBlockingStub blockingStub_;

        /**
         * Construct client connecting to HelloWorld server at {@code host:port}.
         */
        public TestClient(String host, int port) {
            this(ManagedChannelBuilder.forAddress(host, port)
                    // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
                    // needing certificates.
                    .usePlaintext(true));
        }

        public TestClient(ManagedChannelBuilder<?> channelBuilder) {
            channel = channelBuilder.build();
            blockingStub_ = RaftCommServiceGrpc.newBlockingStub(channel);
        }

        public void shutdown() throws InterruptedException {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
        
        public ClientResp put(String key, String value) {
            Raft.ClientReq req = Raft.ClientReq.newBuilder()
                    .setOp(Raft.Op.Put).addArgs(key).addArgs(value)
                    .build();
            
            return blockingStub_.clientOperate(req);
        }
        
        public ClientResp get(String key) {
            Raft.ClientReq req = Raft.ClientReq.newBuilder().setOp(Raft.Op.Get).addArgs(key).build();
            
            
            return blockingStub_.clientOperate(req);
            
        }
        
        public ClientResp del(String key) {
            Raft.ClientReq req = Raft.ClientReq.newBuilder().setOp(Raft.Op.Del).addArgs(key).build();
            
            return blockingStub_.clientOperate(req);
            
        }
        
        @Override
        public void close() throws IOException {
            try {
                shutdown();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    
    @Test
    public void testSingleNodeWithMockFsm() throws IOException, InterruptedException, ReflectiveOperationException {
        LocalCluster cluster = new LocalCluster(1, MockFSM.class, tmpFolder_);
        Node n = cluster.get(0);
        Config conf = n.getConf();
        MockFSM fsm = (MockFSM)n.getFsm();
        Thread.sleep((conf.getFollowerTimeoutSec() + conf.getCandidateTimeoutSec()) * 1000);

        Assert.assertTrue(n.isLeader());
        
        try (TestClient testClient = new TestClient(n.getEndpoint().getHost(), n.getConf().getLocalServerPort())) {
            ClientResp resp = testClient.put("test", "test");
            
            Assert.assertTrue(resp.getSuccess());
            
            Assert.assertEquals("1 msg in fsm should be applied", 1, fsm.getLogNum());
        }
    }
    
    @Test
    public void testSingleNodeWithKvFsm() throws IOException, InterruptedException, ReflectiveOperationException {
        LocalCluster cluster = new LocalCluster(1, KvFsm.class, tmpFolder_);
        Node n = cluster.get(0);
        Config conf = n.getConf();
        Thread.sleep((conf.getFollowerTimeoutSec() + conf.getCandidateTimeoutSec()) * 1000);
        
        Assert.assertTrue(n.isLeader());
        
        try (TestClient testClient = new TestClient(n.getEndpoint().getHost(), n.getConf().getLocalServerPort())) {
            String testKey = "test_key";
            String testValue = "test_value";
            
            // test Put Op
            System.out.println("test Put Op ============>");
            ClientResp putResp = testClient.put(testKey, testValue);
            Assert.assertTrue(putResp.getSuccess());
            
            // test Get Op
            System.out.println("test Get Op ============>");
            ClientResp getResp = testClient.get(testKey);
            Assert.assertTrue(getResp.getSuccess());
            List<String> results = getResp.getResultList();
            Assert.assertEquals(results.size(), 1);
            Assert.assertEquals(results.get(0), testValue);
            
            // test Del Op
            System.out.println("test Del Op =============>");
            ClientResp delResp = testClient.del(testKey);
            Assert.assertTrue(delResp.getSuccess());
        }
    }
    
    @Test
    public void testThreeNodesWithMockFsm() throws InterruptedException, ReflectiveOperationException, IOException {
        LocalCluster cluster = new LocalCluster(3, MockFSM.class, tmpFolder_);
        Node n0 = cluster.get(0);
        Node n1 = cluster.get(1);
        Node n2 = cluster.get(2);
        Config conf = n0.getConf();
        Thread.sleep((conf.getFollowerTimeoutSec() + conf.getCandidateTimeoutSec()) * 1000);
        
        Integer leaderId = n0.getLeaderId();
        Assert.assertNotNull("leader id should not be null", leaderId);
        Assert.assertEquals(leaderId, n1.getLeaderId());
        Assert.assertEquals(leaderId, n2.getLeaderId());
        
        Node leader = cluster.get(leaderId);
        Assert.assertTrue(leader.isLeader());
        
        try (TestClient testClient = new TestClient(leader.getEndpoint().getHost(), leader.getConf().getLocalServerPort())) {
            ClientResp resp = testClient.put("test", "test");
            Assert.assertTrue(resp.getSuccess());
    
            for (Node n : cluster.getNodeList()) {
                MockFSM fsm = (MockFSM) n.getFsm();
                Assert.assertEquals("1 msg in fsm should be applied in node: " + n.getId(), 1, fsm.getLogNum());
            }
    
            System.out.println("put again");
            // put again
            resp = testClient.put("test2", "test");
            Assert.assertTrue(resp.getSuccess());
    
            for (Node n : cluster.getNodeList()) {
                MockFSM fsm = (MockFSM) n.getFsm();
                Assert.assertEquals("2 msg in fsm should be applied in node: " + n.getId(), 2, fsm.getLogNum());
            }
    
        }
    }
    
}
