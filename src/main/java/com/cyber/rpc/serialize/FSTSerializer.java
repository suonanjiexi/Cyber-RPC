package com.cyber.rpc.serialize;

import org.nustaq.serialization.FSTConfiguration;

import java.util.concurrent.ConcurrentHashMap;

/**
 * FST序列化实现，提供比JDK、Kryo更高性能的序列化
 */
public class FSTSerializer implements Serializer {
    
    // 使用ThreadLocal来避免多线程冲突
    private static final ThreadLocal<FSTConfiguration> FST_CONFIGURATION_THREAD_LOCAL = 
        ThreadLocal.withInitial(() -> {
            FSTConfiguration conf = FSTConfiguration.createDefaultConfiguration();
            conf.setShareReferences(true); // 启用对象引用共享以处理循环引用
            return conf;
        });
    
    // 类注册表，用于提高序列化性能
    private static final ConcurrentHashMap<Class<?>, Boolean> REGISTERED_CLASSES = new ConcurrentHashMap<>();
    
    /**
     * 预先注册类，提高序列化性能
     * @param classes 需要预注册的类
     */
    public static void registerClasses(Class<?>... classes) {
        if (classes == null || classes.length == 0) {
            return;
        }
        
        FSTConfiguration conf = FST_CONFIGURATION_THREAD_LOCAL.get();
        for (Class<?> clazz : classes) {
            if (clazz != null && !REGISTERED_CLASSES.containsKey(clazz)) {
                conf.registerClass(clazz);
                REGISTERED_CLASSES.put(clazz, Boolean.TRUE);
            }
        }
    }
    
    @Override
    public byte[] serialize(Object obj) {
        if (obj == null) {
            return new byte[0];
        }
        
        try {
            // 如果类型未注册，则自动注册
            if (!REGISTERED_CLASSES.containsKey(obj.getClass())) {
                registerClasses(obj.getClass());
            }
            
            FSTConfiguration conf = FST_CONFIGURATION_THREAD_LOCAL.get();
            return conf.asByteArray(obj);
        } catch (Exception e) {
            throw new IllegalStateException("FST序列化失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        if (bytes == null || bytes.length == 0 || clazz == null) {
            return null;
        }
        
        try {
            // 如果类型未注册，则自动注册
            if (!REGISTERED_CLASSES.containsKey(clazz)) {
                registerClasses(clazz);
            }
            
            FSTConfiguration conf = FST_CONFIGURATION_THREAD_LOCAL.get();
            @SuppressWarnings("unchecked")
            T result = (T) conf.asObject(bytes); // 必要的类型转换
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("FST反序列化失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public SerializerType getType() {
        return SerializerType.FST;
    }
}
