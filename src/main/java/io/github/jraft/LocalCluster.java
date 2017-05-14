package io.github.jraft;

import com.google.common.base.Preconditions;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class LocalCluster implements Closeable {
    
    private HashMap<Integer, Node> nodes_;
    
    public LocalCluster(int n, Class<?> fsmClass, String rootDataDir) throws ReflectiveOperationException, IOException, InterruptedException {
        Preconditions.checkArgument(n > 0);
        nodes_ = new HashMap<>();
        int startPort = 5555;
        
        ArrayList<Endpoint> endpoints = new ArrayList<>();
        
        for (int i = 0; i < n; ++i) {
            endpoints.add(new Endpoint("localhost", startPort + i));
        }
        
        for (int i = 0; i < n; ++i) {
            Config conf = new Config(i, endpoints.get(i), Config.LocalServerPort + i, rootDataDir);
            
            IFSM fsm = (IFSM) fsmClass.getConstructor(String.class).newInstance(conf.getDataDirPath());
            nodes_.put(i, new Node(conf, fsm, endpoints));
        }
    }
    
    public List<Node> getNodeList() {
        return new ArrayList<>(nodes_.values());
    }
    
    public Node get(int id) {
        return nodes_.get(id);
    }
    
    public int size() {
        return nodes_.size();
    }
    
    public void shutdown() {
        for (Node n : nodes_.values()) {
            n.shutdown();
        }
    }
    
    @Nullable
    public Integer getLeaderId() {
        Node n0 = nodes_.get(0);
        return n0.getLeaderId();
    }
    
    @Override
    public void close() throws IOException {
        shutdown();
    }
}
