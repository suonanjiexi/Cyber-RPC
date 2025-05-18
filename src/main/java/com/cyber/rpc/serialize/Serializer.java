package com.cyber.rpc.serialize;

/**
 * 序列化接口，所有序列化实现类都需要实现此接口
 */
public interface Serializer {
    
    /**
     * 序列化方法，将对象转换为字节数组
     * 
     * @param obj 要序列化的对象
     * @return 序列化后的字节数组
     */
    byte[] serialize(Object obj);
    
    /**
     * 反序列化方法，将字节数组转换为对象
     * 
     * @param bytes 序列化后的字节数组
     * @param clazz 目标类
     * @param <T>   目标类型
     * @return 反序列化后的对象
     */
    <T> T deserialize(byte[] bytes, Class<T> clazz);
    
    /**
     * 获取序列化器类型
     * 
     * @return 序列化器类型
     */
    SerializerType getType();
}