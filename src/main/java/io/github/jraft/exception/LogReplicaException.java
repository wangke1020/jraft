package io.github.jraft.exception;


public class LogReplicaException extends Exception {
    public LogReplicaException(String message) {
        super(message);
    }

    public LogReplicaException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
