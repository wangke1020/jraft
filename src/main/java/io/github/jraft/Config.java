package io.github.jraft;

public class Config {
    public static int timeoutMinSec = 5;
    public static int timeoutMaxSec = 15;
    public static int CandidateTimeoutSec = 5;

    public static int leaderHbIntervalSec = 3;
    
    public static String persistenceFilePath = "/opt/test/raft.db";
}
