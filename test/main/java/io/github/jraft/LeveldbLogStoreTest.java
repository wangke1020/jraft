package io.github.jraft;

import com.google.protobuf.ByteString;
import grpc.Raft.Log;
import org.junit.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LeveldbLogStoreTest {
    private static String LOG_FILE_PATH = "/tmp/test.db";
    private LeveldbLogStore logStore_;
    
    @Before
    public void beforeTest() throws IOException {
        deleteFile(LOG_FILE_PATH);
        
        logStore_ = new LeveldbLogStore(LOG_FILE_PATH);
    }
    
    private static void deleteFile(String filePath) {
        File file = new File(filePath);
        if (file.isFile()) {
            file.delete();
        }
    }
    
    @After
    public void afterTest() throws IOException {
        logStore_.close();
    }
    
    @AfterClass
    public static void after() {
        deleteFile(LOG_FILE_PATH);
    }
    
    @Test
    public void test() throws IOException {
        List<Log> logs = new ArrayList<>();
        byte[] data = "test".getBytes();
        int i = 0;
        for (; i < 100; ++i) {
            Log log = Log.newBuilder().setIndex(i).setTerm(i / 5).setData(ByteString.copyFrom(data)).setPeer("peer").build();
            logs.add(log);
        }
    
        logStore_.storeLog(logs);
    
        Assert.assertEquals("get first index", 0, logStore_.getFirstIndex());
        Assert.assertEquals("get last index", 99, logStore_.getLastIndex());
    
    
        logStore_.deleteRange(0, 10);
        logStore_.deleteRange(90, 99);
    
        Assert.assertEquals("get first index", 11, logStore_.getFirstIndex());
        Assert.assertEquals("get last index", 89, logStore_.getLastIndex());
    
    
        Log log = logStore_.getLog(11);
        Assert.assertEquals(11, log.getIndex());
        Assert.assertEquals(11 / 5, log.getTerm());
    
    }
    
    @Test
    public void testKeyNotExist() {
        Log log = logStore_.getLog(10);
        Assert.assertNull(log);
    }
}
