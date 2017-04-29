package io.github.jraft;

import grpc.Raft.Log;

class AppliedRes {
    
    enum Error {
        NoSuchKey, MethodNotSupported, UnkownError, InternalError, None
    }
    
    private boolean success_;
    private String result_;
    private Error error_;
    
    
    private AppliedRes(boolean success, String result, Error error) {
        success_ = success;
        result_ = result;
        error_ = error;
    }
    
    static AppliedRes newSuccessRes(String result) {
        return new AppliedRes(true, result, Error.None);
    }
    
    static AppliedRes newSuccessRes() {
        return newSuccessRes("");
    }
    
    static AppliedRes newFailedRes(Error error) {
        return new AppliedRes(false, "", error);
    }
    
    public String getResult() {
        return result_;
    }
    
    public Error getError() {
        return error_;
    }
    
    public boolean isSuccess() {
        return success_;
    }
}

interface IFSM {
    AppliedRes apply(Log log);
}
