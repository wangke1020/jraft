package io.github.jraft;


import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;

import static org.fusesource.leveldbjni.JniDBFactory.asString;
import static org.fusesource.leveldbjni.JniDBFactory.bytes;
import static org.fusesource.leveldbjni.JniDBFactory.factory;


public class StateTable implements Closeable {
    
    private String LOG_PREFIX = "state.";
    
    private DB db_;
    private int id_;
    private String filePath_;
    
    private static String TERM_KEY = "term";
    private static String LAST_APPLIED_KEY = "last_applied";
    private static String VOTE_FOR_KEY = "vote_for";
    private static String LAST_VOTE_TERM_KEY = "last_vote_term";
    
    public StateTable(int id) throws IOException {
        id_ = id;
        filePath_ = getFilePath();
        Options options = new Options();
        options.createIfMissing(true);
        db_ = factory.open(new File(filePath_), options);
        
    }
    
    public String getFilePath() {
        return Config.persistenceFilePathPrefix + LOG_PREFIX + id_;
    }
    
    @Nullable
    private String getValue(String key) {
        byte[] value = db_.get(bytes(key));
        if (value == null) return null;
        return asString(value);
    }
    
    private void storeValue(String key, String value) {
        db_.put(bytes(key), bytes(value));
    }
    
    @Nullable
    private Long getLongValue(String key) {
        
        String valueStr = getValue(key);
        if (valueStr == null) return null;
        return Long.parseLong(valueStr);
    }
    
    @Nullable
    private Integer getIntValue(String key) {
        String valueStr = getValue(key);
        if (valueStr == null) return null;
        return Integer.parseInt(valueStr);
    }
    
    private void storeLongValue(String key, Long value) {
        storeValue(key, value.toString());
    }
    
    private void storeIntValue(String key, Integer value) {
        storeValue(key, value.toString());
    }
    
    public long getCurrentTerm() {
        Long term = getLongValue(TERM_KEY);
        if (term == null) return 0;
        return term;
    }
    
    public void storeCurrentTerm(long term) {
        storeLongValue(TERM_KEY, term);
    }
    
    public long getLastApplied() {
        Long index = getLongValue(LAST_APPLIED_KEY);
        if (index == null) return 0;
        return index;
    }
    
    public void storeLastApplied(long term) {
        storeLongValue(LAST_APPLIED_KEY, term);
    }
    
    @Nullable
    public Integer getVoteFor() {
        return getIntValue(VOTE_FOR_KEY);
        
    }
    
    public void storeVoteFor(int id) {
        storeIntValue(VOTE_FOR_KEY, id);
    }
    
    @Nullable
    public Long getLastVoteTerm() {
        return getLongValue(LAST_VOTE_TERM_KEY);
    }
    
    public void storeLastVoteTerm(long term) {
        storeLongValue(LAST_VOTE_TERM_KEY, term);
    }
    
    @Override
    public void close() throws IOException {
        if (db_ != null) {
            db_.close();
        }
    }
}
