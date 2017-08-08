package io.github.jraft.fsm;

import grpc.Raft.Log;
import io.github.jraft.fsm.AppliedRes;

import java.io.Closeable;

public interface IFSM extends Closeable {
    AppliedRes apply(Log log);
}
