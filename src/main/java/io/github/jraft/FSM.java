package io.github.jraft;


import grpc.Raft.Log;

public class FSM {
    boolean apply(Log log) {
        return true;
    }
}
