package io.github.jraft;

import java.util.List;
import org.iq80.leveldb.*;
import static org.fusesource.leveldbjni.JniDBFactory.*;
import java.io.*;

public class LeveldbLogStore implements LogStore, Closeable {
    private String LOG_PREFIX = "log.";
    
    private DB db_;
    
    public LeveldbLogStore(String logFilePath) throws IOException {
    
        DBComparator comparator = new DBComparator(){
            public int compare(byte[] key1, byte[] key2) {
    
                String key1Str = new String(key1);
                String key2Str = new String(key2);
                
                if(key1Str.startsWith(LOG_PREFIX) && key2Str.startsWith(LOG_PREFIX)) {
                    return (int)(getIndexFromKey(key1Str) - getIndexFromKey(key2Str));
                }
                
                return key1Str.compareTo(key2Str);
            }
            public String name() {
                return "simple";
            }
            public byte[] findShortestSeparator(byte[] start, byte[] limit) {
                return start;
            }
            public byte[] findShortSuccessor(byte[] key) {
                return key;
            }
        };
        
        Options options = new Options();
        options.comparator(comparator);
        options.createIfMissing(true);
        db_ = factory.open(new File(logFilePath), options);
    }
    
    @Override
    public void close() throws IOException {
        if(db_ != null) db_.close();
    }
    
    @Override
    public long getFirstIndex() {
        DBIterator iterator = db_.iterator();
        try {
            iterator.seekToFirst();
            String key = asString(iterator.peekNext().getKey());
            return getIndexFromKey(key);
        } finally {
            // Make sure you close the iterator to avoid resource leaks.
            try {
                iterator.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    @Override
    public long getLastIndex() {
        DBIterator iterator = db_.iterator();
        try {
            iterator.seekToLast();
            String key = asString(iterator.peekNext().getKey());
            return getIndexFromKey(key);
        } finally {
            // Make sure you close the iterator to avoid resource leaks.
            try {
                iterator.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
    }
    
    @Override
    public Log getLog(long index) {
        byte[] value = db_.get(getKeyBytes(index));
        return Log.deserialize(value);
    }
    
    @Override
    public void storeLog(List<Log> logs) {
        for(Log log : logs) {
            db_.put(getKeyBytes(log.getIndex()), log.serialize());
        }
    }
    
    @Override
    public void deleteRange(long min, long max) {
        for(long index=min;index<=max;++index) {
            byte[] key = getKeyBytes(index);
            db_.delete(key);
        }
    }
    
    private long getIndexFromKey(String key) {
        String[] arr = key.split("\\.");
        return Long.parseLong(arr[1]);
    }
    
    private byte[] getKeyBytes(long index) {
        return bytes(LOG_PREFIX + Long.valueOf(index).toString());
    }
    

}
