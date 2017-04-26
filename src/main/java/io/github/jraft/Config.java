package io.github.jraft;


public class Config {
    private static int FollowerTimeoutSec = 10;
    private static int CandidateTimeoutSec = 10;

    private static int LeaderHbIntervalSec = 5;

    private static String PersistenceFilePathPrefix = "/opt/test/raft_";

    private int LocalServerPort = 8088;

    public int getFollowerTimeoutSec() {
        return FollowerTimeoutSec;
    }

    public int getCandidateTimeoutSec() {
        return CandidateTimeoutSec;
    }

    public int getLeaderHbIntervalSec() {
        return LeaderHbIntervalSec;
    }

    public String getPersistenceFilePathPrefix() {
        return PersistenceFilePathPrefix;
    }

    public int getLocalServerPort() {
        return LocalServerPort;
    }
}
