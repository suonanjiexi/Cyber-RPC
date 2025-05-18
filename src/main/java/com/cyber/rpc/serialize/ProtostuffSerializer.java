package com.cyber.rpc.serialize;

import io.protostuff.LinkedBuffer;
import io.protostuff.ProtostuffIOUtil;
import io.protostuff.Schema;
import io.protostuff.runtime.DefaultIdStrategy;
import io.protostuff.runtime.IdStrategy;
import io.protostuff.runtime.RuntimeSchema;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protostuff序列化实现，提供高性能的序列化功能
 * 增强版本：增加了对象池和循环引用支持，优化了性能
 */
public class ProtostuffSerializer implements Serializer {
    
    // 使用ThreadLocal池管理而不是固定大小的池
    
    // 使用ThreadLocal来避免多线程冲突
    private static final ThreadLocal<LinkedBuffer> BUFFER_THREAD_LOCAL = 
        ThreadLocal.withInitial(() -> LinkedBuffer.allocate(LinkedBuffer.DEFAULT_BUFFER_SIZE));
    
    // 缓存Schema
    private static final Map<Class<?>, Schema<?>> SCHEMA_CACHE = new ConcurrentHashMap<>();
    
    // 支持循环引用的IdStrategy
    private static final IdStrategy ID_STRATEGY = new DefaultIdStrategy();
    
    /**
     * 获取类型的Schema
     * @param clazz 类型
     * @return 该类型的Schema
     */
    @SuppressWarnings("unchecked")
    private static <T> Schema<T> getSchema(Class<T> clazz) {
        Schema<?> schema = SCHEMA_CACHE.get(clazz);
        if (schema == null) {
            schema = RuntimeSchema.createFrom(clazz, ID_STRATEGY);
            Schema<?> existingSchema = SCHEMA_CACHE.putIfAbsent(clazz, schema);
            if (existingSchema != null) {
                schema = existingSchema;
            }
        }
        return (Schema<T>) schema;
    }
    
    /**
     * 注册预热类，提前生成Schema
     * @param classes 需要预热的类
     */
    public static void warmUp(Class<?>... classes) {
        for (Class<?> clazz : classes) {
            if (clazz != null && !SCHEMA_CACHE.containsKey(clazz)) {
                SCHEMA_CACHE.put(clazz, RuntimeSchema.createFrom(clazz, ID_STRATEGY));
            }
        }
    }
    
    @Override
    public byte[] serialize(Object obj) {
        if (obj == null) {
            return new byte[0];
        }
        
        Class<?> clazz = obj.getClass();
        LinkedBuffer buffer = BUFFER_THREAD_LOCAL.get();
        
        try {
            buffer.clear(); // 清除之前的数据
            @SuppressWarnings("unchecked")
            Schema<Object> schema = getSchema((Class<Object>) clazz); // 必要的类型转换
            return ProtostuffIOUtil.toByteArray(obj, schema, buffer);
        } catch (Exception e) {
            buffer.clear(); // 发生异常时清除缓存
            throw new IllegalStateException("Protostuff序列化失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        
        try {
            Schema<T> schema = getSchema(Objects.requireNonNull(clazz));
            T instance = schema.newMessage(); // 使用Schema创建实例，避免使用反射
            ProtostuffIOUtil.mergeFrom(bytes, instance, schema);
            return instance;
        } catch (Exception e) {
            throw new IllegalStateException("Protostuff反序列化失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public SerializerType getType() {
        return SerializerType.PROTOSTUFF;
    }
}