package io.github.jraft;


import grpc.Raft.Log;

import java.util.List;

public interface LogStore {
    long getFirstIndex();
    long getLastIndex();
    Log getLog(long index);
    
    // return logs [start, end]
    List<Log> getLogs(long start, long end);
    void storeLog(List<Log> logs);
    void deleteRange(long min, long max);
    
}
