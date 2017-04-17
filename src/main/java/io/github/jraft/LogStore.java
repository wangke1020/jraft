package io.github.jraft;


import java.util.List;

public interface LogStore {
    long getFirstIndex();
    long getLastIndex();
    Log getLog(long index);
    void storeLog(List<Log> logs);
    void deleteRange(long min, long max);
    
}
