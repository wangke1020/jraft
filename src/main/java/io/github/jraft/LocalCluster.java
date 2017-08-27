package io.github.jraft;

import com.google.common.base.Preconditions;
import io.github.jraft.exception.LeaderElectionException;
import io.github.jraft.fsm.IFSM;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class LocalCluster implements Closeable {
    private static final Logger logger_ = LogManager.getLogger(LocalCluster.class);
    private HashMap<Integer, Node> nodes_ = new HashMap<>();
    
    public LocalCluster(int n, Class<?> fsmClass, String rootDataDir) throws ReflectiveOperationException, IOException, InterruptedException {
        Preconditions.checkArgument(n > 0);
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

    public LocalCluster(HashMap<Integer, Node> nodes) {
        nodes_.putAll(nodes);
    }

    public LocalCluster(List<Node> nodes) {
        for(int i=0;i<nodes.size();++i) {
            nodes_.put(i, nodes.get(i));
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

    public Node getLeader() throws LeaderElectionException {
        List<Node> leaders = new ArrayList<>();
        for(Node n : nodes_.values()) {
            if(n.isLeader())
                leaders.add(n);
        }

        if(leaders.isEmpty())
            return null;
        if(leaders.size() > 1) {
            String err = "more than one leader elected";
            logger_.error(err);
            throw new LeaderElectionException(err);
        }
        return leaders.get(0);
    }
    
    @Override
    public void close() throws IOException {
        shutdown();
    }
}
