package io.github.jraft.fsm;


import grpc.Raft.ClientReq;
import grpc.Raft.Log;
import grpc.Raft.Op;
import io.github.jraft.fsm.AppliedRes.LogApplyError;
import io.github.jraft.LeveldbLogStore;
import org.apache.commons.io.FileUtils;
import org.junit.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class KvFsmTest {

    private static String LOG_DIR_PATH = "/tmp/test";
    private LeveldbLogStore logStore_;

    @Before
    public void beforeTest() throws IOException {
        deleteFile(LOG_DIR_PATH);
        FileUtils.forceMkdir(new File(LOG_DIR_PATH));
        logStore_ = new LeveldbLogStore(LOG_DIR_PATH);
    }

    private static void deleteFile(String filePath) throws IOException {
        File dir = new File(filePath);
        FileUtils.deleteDirectory(dir);
    }

    @After
    public void afterTest() throws IOException {
        deleteFile(LOG_DIR_PATH);
        logStore_.close();
    }

    @AfterClass
    public static void after() throws IOException {
        deleteFile(LOG_DIR_PATH);
    }

    private ClientReq getClientReq(Op op, String[] args) {
        ClientReq.Builder builder = ClientReq.newBuilder();
        builder.setOp(op).addAllArgs(Arrays.asList(args));
        return builder.build();
    }

    @Test
    public void testApply() throws IOException {


        try(KvFsm kvFsm = new KvFsm(LOG_DIR_PATH)) {

            // Get a key which does not exist
            String testkey = "testkey";
            String testValue = "1";
            {
                ClientReq req = getClientReq(Op.Get, new String[]{testkey});
                Log.Builder builder = Log.newBuilder().setData(req.toByteString());
                AppliedRes res = kvFsm.apply(builder.build());
                Assert.assertFalse(res.isSuccess());
                Assert.assertEquals(LogApplyError.NoSuchKey, res.getError());
            }

            // Set and Get
            {
                ClientReq req = getClientReq(Op.Put, new String[]{testkey, testValue});
                AppliedRes res = kvFsm.apply(Log.newBuilder().setData(req.toByteString()).build());
                Assert.assertTrue(res.isSuccess());
                Assert.assertEquals(LogApplyError.None, res.getError());

                req = getClientReq(Op.Get, new String[]{testkey});
                res = kvFsm.apply(Log.newBuilder().setData(req.toByteString()).build());
                Assert.assertTrue(res.isSuccess());
                Assert.assertEquals(testValue, res.getResult());
                Assert.assertEquals(LogApplyError.None, res.getError());
            }

            // Delete And Get
            {
                ClientReq req = getClientReq(Op.Del, new String[]{testkey});
                AppliedRes res = kvFsm.apply(Log.newBuilder().setData(req.toByteString()).build());
                Assert.assertTrue(res.isSuccess());
                Assert.assertEquals(LogApplyError.None, res.getError());

                req = getClientReq(Op.Get, new String[]{testkey});
                res = kvFsm.apply(Log.newBuilder().setData(req.toByteString()).build());
                Assert.assertFalse(res.isSuccess());
                Assert.assertEquals(LogApplyError.NoSuchKey, res.getError());
            }
        }
    }
}
