package io.github.jraft.fsm;

public class AppliedRes {

    public enum LogApplyError {
        NoSuchKey, MethodNotSupported, UnkownError, InternalError, None
    }

    private boolean success_;
    private String result_;
    private LogApplyError error_;


    private AppliedRes(boolean success, String result, LogApplyError error) {
        success_ = success;
        result_ = result;
        error_ = error;
    }

    public static AppliedRes newSuccessRes(String result) {
        return new AppliedRes(true, result, LogApplyError.None);
    }

    public static AppliedRes newSuccessRes() {
        return newSuccessRes("");
    }

    public static AppliedRes newFailedRes(LogApplyError error) {
        return new AppliedRes(false, "", error);
    }

    public String getResult() {
        return result_;
    }

    public LogApplyError getError() {
        return error_;
    }

    public boolean isSuccess() {
        return success_;
    }
}
