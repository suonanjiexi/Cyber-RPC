package com.cyber.rpc.serialize;

/**
 * 序列化类型枚举
 */
public enum SerializerType {
    
    /**
     * JDK原生序列化
     */
    JDK((byte) 0),
    
    /**
     * JSON序列化
     */
    JSON((byte) 1),
    
    /**
     * Hessian序列化
     */
    HESSIAN((byte) 2),
    
    /**
     * Protostuff序列化
     */
    PROTOSTUFF((byte) 3),
    
    /**
     * Kryo序列化
     */
    KRYO((byte) 4),
    
    /**
     * FST序列化，高性能序列化库
     */
    FST((byte) 5);
    
    private final byte code;
    
    SerializerType(byte code) {
        this.code = code;
    }
    
    public byte getCode() {
        return code;
    }
    
    /**
     * 根据类型编码获取序列化类型
     * 
     * @param code 类型编码
     * @return 序列化类型
     */
    public static SerializerType valueOf(byte code) {
        for (SerializerType type : SerializerType.values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown serializer type code: " + code);
    }
}