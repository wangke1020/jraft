package io.github.jraft;

public class Config {
    public static int FollowerTimeoutSec = 10;
    public static int CandidateTimeoutSec = 10;

    public static int leaderHbIntervalSec = 5;
    
    public static String persistenceFilePathPrefix = "/opt/test/raft_";
}
