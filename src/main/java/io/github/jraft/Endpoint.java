package io.github.jraft;


public class Endpoint {
    private int id_;
    private String host_;
    private int port_;
    
    public Endpoint(int id, String host, int port) {
        id_ = id;
        host_ = host;
        port_ = port;
    }
    
    public String getHost() {
        return host_;
    }
    
    public int getPort() {
        return port_;
    }
    
    public int getId() {
        return id_;
    }
}
