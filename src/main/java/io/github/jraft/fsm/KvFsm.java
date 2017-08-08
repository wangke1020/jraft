package io.github.jraft.fsm;

import com.google.common.base.Preconditions;
import com.google.protobuf.InvalidProtocolBufferException;
import grpc.Raft;
import io.github.jraft.fsm.AppliedRes.LogApplyError;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;

import static org.fusesource.leveldbjni.JniDBFactory.asString;
import static org.fusesource.leveldbjni.JniDBFactory.bytes;
import static org.fusesource.leveldbjni.JniDBFactory.factory;

public class KvFsm implements IFSM, Closeable {
    
    private static String FILE_NAME = "kv.db";
    private DB db_;
    
    
    public KvFsm(String dirPath) throws IOException {
        Options options = new Options();
        options.createIfMissing(true);
        String filePath = dirPath.endsWith("/") ? dirPath + FILE_NAME : dirPath + "/" + FILE_NAME;
        db_ = factory.open(new File(filePath), options);
    }
    
    private void put(String key, String value) {
        db_.put(bytes(key), bytes(value));
    }
    
    @Nullable
    private String get(String key) {
        byte[] value = db_.get(bytes(key));
        if (value != null) return asString(value);
        return null;
    }
    
    private void del(String key) {
        db_.delete(bytes(key));
    }
    
    @Override
    public AppliedRes apply(Raft.Log log) {
        try {
            Raft.ClientReq req = Raft.ClientReq.parseFrom(log.getData());
            switch (req.getOp()) {
                case Get: {
                    Preconditions.checkArgument(req.getArgsCount() == 1);
                    String key = req.getArgs(0);
                    String value = get(key);
                    if (value == null) return AppliedRes.newFailedRes(LogApplyError.NoSuchKey);
                    return AppliedRes.newSuccessRes(value);
                }
                
                case Put: {
                    Preconditions.checkArgument(req.getArgsCount() == 2);
                    String key = req.getArgs(0);
                    String value = req.getArgs(1);
                    
                    put(key, value);
                    
                    return AppliedRes.newSuccessRes();
                }
                case Del: {
                    Preconditions.checkArgument(req.getArgsCount() == 1);
                    String key = req.getArgs(0);
                    del(key);
                    return AppliedRes.newSuccessRes();
                }
                default:
                    return AppliedRes.newFailedRes(LogApplyError.MethodNotSupported);
            }
            
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
            return AppliedRes.newFailedRes(LogApplyError.InternalError);
        }
    }
    
    @Override
    public void close() throws IOException {
        if (db_ != null) {
            
            db_.close();
        }
    }
}
