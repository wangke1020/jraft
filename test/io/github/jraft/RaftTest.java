package io.github.jraft;


import grpc.Raft;
import grpc.Raft.ClientResp;
import grpc.Raft.Log;
import grpc.RaftCommServiceGrpc;
import io.github.jraft.fsm.AppliedRes;
import io.github.jraft.fsm.IFSM;
import io.github.jraft.fsm.KvFsm;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.apache.commons.io.FileUtils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.*;
import org.junit.rules.ExpectedException;

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

    @Override
    public void close() throws IOException {

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

    private Node waitLeaderElected(LocalCluster cluster) throws Exception {
        return waitLeaderElected(cluster,
                (Config.getFollowerTimeoutSec() + Config.getCandidateTimeoutSec())  * 2 * 1000);
    }

    private Node waitLeaderElected(LocalCluster cluster, int waitmilliSecs) throws Exception {
        long stime = System.currentTimeMillis();
        Node leader = cluster.getLeader();
        while(leader == null) {
            if(System.currentTimeMillis() - stime > waitmilliSecs) {
                throw new Exception("no leader elected");
            }
            Thread.sleep(500);
            leader = cluster.getLeader();
        }
        Assert.assertTrue(leader.isLeader());
        return leader;
    }

    private void applyDummyKv(Node leader) throws IOException {
        try (TestClient testClient = new TestClient(leader.getEndpoint().getHost(), leader.getConf().getLocalServerPort())) {
            ClientResp resp = testClient.put(getRandKey(), "test");
            Assert.assertTrue(resp.getSuccess());
        }
    }

    private String getRandKey() {
        return RandomStringUtils.randomAlphanumeric(8);
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
    public void testSingleNodeWithMockFsm() throws Exception {
        try (LocalCluster cluster = new LocalCluster(1, MockFSM.class, tmpFolder_)) {
            Node n = cluster.get(0);
            MockFSM fsm = (MockFSM) n.getFsm();

            Node leader = waitLeaderElected(cluster);

            try (TestClient testClient = new TestClient(leader.getEndpoint().getHost(), leader.getConf().getLocalServerPort()))
            {
                ClientResp resp = testClient.put("test", "test");

                Assert.assertTrue(resp.getSuccess());

                Assert.assertEquals("1 msg in fsm should be applied", 1, fsm.getLogNum());
            }
        }
    }

    @Test
    public void testSingleNodeWithKvFsm() throws Exception {
        try (LocalCluster cluster = new LocalCluster(1, KvFsm.class, tmpFolder_)) {
            Node n = cluster.get(0);
            waitLeaderElected(cluster);

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
    }

    @Test
    public void testThreeNodesWithMockFsm() throws Exception {
        try (LocalCluster cluster = new LocalCluster(3, MockFSM.class, tmpFolder_)) {

            Node leader =  waitLeaderElected(cluster);

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

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void testClusterShutdown() throws Exception {
        try (LocalCluster cluster = new LocalCluster(1, MockFSM.class, tmpFolder_)) {
            Node n = waitLeaderElected(cluster);
            cluster.shutdown();


            try (TestClient testClient = new TestClient(n.getEndpoint().getHost(), n.getConf().getLocalServerPort())) {
                exception.expect(StatusRuntimeException.class);
                testClient.put("test2", "test");
            }
        }
    }

    @Test
    public void testLeaderFail() throws Exception {
        try (LocalCluster cluster = new LocalCluster(5, MockFSM.class, tmpFolder_)) {

            Node firstLeader = waitLeaderElected(cluster);

            applyDummyKv(firstLeader);

            for (Node n : cluster.getNodeList()) {
                MockFSM fsm = (MockFSM) n.getFsm();
                Assert.assertEquals("1 msg in fsm should be applied in node: " + n.getId(), 1, fsm.getLogNum());
            }


            // get current term
            long term = firstLeader.getCurrentTerm();

            // stop rpc connection of firstLeader
            System.out.println("========> let leader fail");
            firstLeader.fail();

            // new firstLeader should be elected
            Node newLeader = waitLeaderElected(cluster);

            // Ensure the term is increased
            Assert.assertTrue(newLeader.getCurrentTerm() > term);

            // resume old firstLeader
            firstLeader.resume();

            // Apply should work on newer leader
            applyDummyKv(newLeader);

            // Apply should work not work on old firstLeader
            try (TestClient testClient = new TestClient(firstLeader.getEndpoint().getHost(), firstLeader.getConf().getLocalServerPort())) {
                ClientResp  resp = testClient.put(getRandKey(), "test");
                if(resp.getSuccess())
                    Assert.fail("why apply successfully?");
            }catch (StatusRuntimeException e) {
            }

            // Wait for log replication
            Thread.sleep(10000);
            // Check two entries are applied to the FSM
            for (Node n : cluster.getNodeList()) {
                MockFSM fsm = (MockFSM) n.getFsm();
                Assert.assertEquals("2 msg in fsm should be applied in node: " + n.getId(), 2, fsm.getLogNum());
            }
        }
    }

    @Test
    public void test2FollowersTimeoutAtSameTime() throws Exception {
        class MockNode extends Node {

            int i = 0;
            public MockNode(Config conf, IFSM fsm, List<Endpoint> cluster) throws IOException, InterruptedException {
                super(conf, fsm, cluster);
            }

            @Override
            public int getFollowerTimeoutMillSec() {
                // Return mockTimeout as first time
                if(i++ == 0) {
                    System.out.println("==========> return timeout time: " +  Config.getFollowerTimeoutSec() * 1000);
                    return Config.getFollowerTimeoutSec() * 1000;
                }
                return super.getFollowerTimeoutMillSec();
            }
        }

        List<Node> nodes = new ArrayList<>();
        int startPort = 5555;
        ArrayList<Endpoint> endpoints = new ArrayList<>();
        for (int i = 0; i < 3; ++i) {
            endpoints.add(new Endpoint("localhost", startPort + i));
        }

        for (int i = 0; i < 3; ++i) {
            Config conf = new Config(i, endpoints.get(i), Config.LocalServerPort + i, tmpFolder_);

            IFSM fsm = new MockFSM(conf.getDataDirPath());
            if(i<2) nodes.add(new MockNode(conf, fsm, endpoints));
            else    nodes.add(new Node(conf, fsm, endpoints));
        }


        try (LocalCluster cluster = new LocalCluster(nodes)) {
            waitLeaderElected(cluster);
        }
    }
}
