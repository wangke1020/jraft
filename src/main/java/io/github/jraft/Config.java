package io.github.jraft;


import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

public class Config {
    private static int FollowerTimeoutSec = 10;
    private static int CandidateTimeoutSec = 10;

    private static int LeaderHbIntervalSec = 5;
    
    private static int RequestTimeoutSec = 5;
    
    private static String DEFAULT_ROOT_DATA_DIR = "/opt/test/raft/";
    
    private String dataDir_;
    
    public static int LocalServerPort = 8088;
    
    private int id_;
    
    private Endpoint endpoint_;
    
    private int localSrvPort_;

    public static int getFollowerTimeoutSec() {
        return FollowerTimeoutSec;
    }

    public static int getCandidateTimeoutSec() {
        return CandidateTimeoutSec;
    }

    public static int getLeaderHbIntervalSec() {
        return LeaderHbIntervalSec;
    }
    
    public static int getRequestTimeoutSec() {
        return RequestTimeoutSec;
    }
    
    public String getDataDirPath() {
        return dataDir_;
    }
    
    public Config(int id, Endpoint endpoint, int localSrvPort, String rootDataDir) {
        
        id_ = id;
        endpoint_ = endpoint;
        localSrvPort_ = localSrvPort;
        dataDir_ = rootDataDir + Integer.valueOf(id).toString();
        try {
            FileUtils.forceMkdir(new File(dataDir_));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public Config(int id, Endpoint endpoint, int localServerPort) {
        this(id, endpoint, localServerPort, DEFAULT_ROOT_DATA_DIR);
    }
    
    public void setId(int id) {
        id_ = id;
    }
    
    public int getId() {
        return id_;
    }

    public int getLocalServerPort() {
        return localSrvPort_;
    }
    
    public Endpoint getEndpoint() {
        return endpoint_;
    }
}
