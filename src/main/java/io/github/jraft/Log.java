package io.github.jraft;


import org.apache.commons.lang3.SerializationUtils;

import java.io.*;

public class Log implements Serializable {
    private static final long serialVersionUID = 1234236213L;
    
    private long index_;
    private long term_;
    private byte[] data_;
    private String peer_;
    
    public Log(long index, long term, byte[] data, String peer) {
        index_ = index;
        term_ = term;
        data_ = data;
        peer_ = peer;
    }
    
    
    public byte[] serialize() {
        return SerializationUtils.serialize(this);
    }
    
    public static Log deserialize(byte[] bytes) {
        return SerializationUtils.deserialize(bytes);
    }
    
    public long getIndex() {
        return index_;
    }
    
    public long getTerm() {
        return term_;
    }
    
    public byte[] getData() {
        return data_;
    }
    
    public String getPeer() {
        return peer_;
    }
}


