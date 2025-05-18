package com.cyber.rpc.serialize;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 序列化工厂，用于获取不同类型的序列化器
 */
public class SerializerFactory {
    
    private static final Map<SerializerType, Serializer> SERIALIZER_MAP = new ConcurrentHashMap<>();
    
    static {
        // 注册所有内置序列化器
        register(new JdkSerializer());
        register(new ProtostuffSerializer());
        register(new KryoSerializer());
        register(new FSTSerializer());
    }
    
    /**
     * 注册序列化器
     * 
     * @param serializer 序列化器实例
     */
    public static void register(Serializer serializer) {
        SERIALIZER_MAP.put(serializer.getType(), serializer);
    }
    
    /**
     * 获取序列化器
     * 
     * @param type 序列化类型
     * @return 序列化器实例
     */
    public static Serializer getSerializer(SerializerType type) {
        Serializer serializer = SERIALIZER_MAP.get(type);
        if (serializer == null) {
            throw new RuntimeException("未找到序列化器: " + type);
        }
        return serializer;
    }
    
    /**
     * 获取默认序列化器（JDK）
     * 
     * @return 默认序列化器
     */
    public static Serializer getDefaultSerializer() {
        return getSerializer(SerializerType.JDK);
    }
}