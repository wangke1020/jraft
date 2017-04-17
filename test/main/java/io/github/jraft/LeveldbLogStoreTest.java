package io.github.jraft;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LeveldbLogStoreTest {
    @Test
    public void test() throws IOException {
        List<Log> logs = new ArrayList<>();
        byte[] data = "test".getBytes();
        int i = 0;
        for(;i<100;++i) {
            logs.add(new Log(i, i/5, data, "peer"));
        }
    
    
        String logFilePath = "/tmp/test.db";
        File file = new File(logFilePath);
        if(file.isFile()) {
            file.delete();
        }
        LeveldbLogStore logStore = new LeveldbLogStore(logFilePath);
        
        logStore.storeLog(logs);
    
        Assert.assertEquals("get first index", 0, logStore.getFirstIndex());
        Assert.assertEquals("get last index", 99, logStore.getLastIndex());
        
        
        logStore.deleteRange(0, 10);
        logStore.deleteRange(90, 99);
    
        Assert.assertEquals("get first index", 11, logStore.getFirstIndex());
        Assert.assertEquals("get last index", 89, logStore.getLastIndex());
        
        
        Log log = logStore.getLog(11);
        Assert.assertEquals(11, log.getIndex());
        Assert.assertEquals(11 / 5, log.getTerm());
        
    }
}
