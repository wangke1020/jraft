package io.github.jraft.exception;


public class LeaderElectionException extends Exception {
    public LeaderElectionException(String message) {
        super(message);
    }

    public LeaderElectionException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
