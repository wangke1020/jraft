package io.github.jraft;

import grpc.Raft.Log;

interface IFSM {
    boolean apply(Log log);
}

public class FSM implements IFSM {

    @Override
    public boolean apply(Log log) {
        return true;
    }
}
