package io.github.jraft;

import javax.annotation.Nullable;

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
    
    @Override
    public boolean equals(@Nullable Object object) {
        if (object == null) return false;
        if (!(object instanceof Endpoint)) return false;
        Endpoint ep = (Endpoint) object;
        return id_ == ep.getId() && host_.equals(ep.getHost()) && port_ == ep.getPort();
    }
    
    @Override
    public int hashCode() {
        return (id_ * 31 + port_) * 31 + host_.hashCode();
    }
    
    
}
