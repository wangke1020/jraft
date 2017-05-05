package io.github.jraft;

import javax.annotation.Nullable;

public class Endpoint {

    private String host_;
    private int port_;
    
    public Endpoint(String host, int port) {
        host_ = host;
        port_ = port;
    }
    
    public String getHost() {
        return host_;
    }
    
    public int getPort() {
        return port_;
    }
    
    
    @Override
    public boolean equals(@Nullable Object object) {
        if (object == null) return false;
        if (!(object instanceof Endpoint)) return false;
        Endpoint ep = (Endpoint) object;
        return host_.equals(ep.getHost()) && port_ == ep.getPort();
    }
    
    @Override
    public int hashCode() {
        return port_ * 31 + host_.hashCode();
    }
    
    
}
