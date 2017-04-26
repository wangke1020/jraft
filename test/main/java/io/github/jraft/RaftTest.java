package io.github.jraft;


import grpc.Raft;
import grpc.Raft.Log;
import grpc.RaftCommServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.*;

public class RaftTest {

    private String tmpFolder = "/tmp/raft-test/";
    private File dir_;

    @BeforeClass
    public static void beforeClass() {
        Configurator.setRootLevel(Level.DEBUG);
    }

    @Before
    public void beforeTest() {
        dir_ = new File(tmpFolder);
        if(!dir_.isDirectory())
            dir_.mkdir();
    }

    @After
    public void afterTest() throws IOException {
        FileUtils.deleteDirectory(dir_);
    }


    class MockConfig extends Config{

        MockConfig() {
        }

        @Override
        public String  getPersistenceFilePathPrefix() {
            return tmpFolder + "raft_";
        }

    }

    class MockFSM implements IFSM {
        private List<Log> logs_;
        MockFSM() {
            logs_ = new ArrayList<>();
        }

        @Override
        public boolean apply(Log log) {
            logs_.add(log);
            return true;
        }

        public int getLogNum() {
            return logs_.size();
        }
    }

    public class TestClient {
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

        public Raft.ClientResp putMsg(String msg) {
            Raft.ClientReq req = Raft.ClientReq.newBuilder()
                    .setOp(Raft.Op.Put)
                    .addArgs(msg)
                    .build();

            try {
                return blockingStub_.clientOperate(req);
            } catch (StatusRuntimeException e) {
                  e.printStackTrace();
            }
            return null;
        }
    }


        public List<Node> makeCluster(int n) throws IOException, InterruptedException {
        List<Node> cluster = new ArrayList<Node>();
        int startPort = 5555;

        ArrayList<Endpoint> endpoints = new ArrayList<>();

        for(int i=0; i<n; ++i) {
            endpoints.add(new Endpoint(i, "localhost", startPort+i));
        }

        for(int i=0;i<n;++i) {
            MockConfig mockConf = new MockConfig();
            MockFSM mockFSM = new MockFSM();
            cluster.add(new Node(endpoints.get(i), mockConf, mockFSM, endpoints));
        }

        return cluster;

    }

    @Test
    public void testSingleNode() throws IOException, InterruptedException {
        List<Node> nodes = makeCluster(1);
        Node n = nodes.get(0);
        Config conf = n.getConf();
        MockFSM fsm = (MockFSM)n.getFsm();
        Thread.sleep((conf.getFollowerTimeoutSec() + conf.getCandidateTimeoutSec()) * 1000);

        Assert.assertTrue(n.isLeader());

        TestClient testClient = new TestClient(n.getEndpoint().getHost(), conf.getLocalServerPort());
        Raft.ClientResp resp = testClient.putMsg("test msg");

        Assert.assertTrue(resp.getSuccess());
    }
}
