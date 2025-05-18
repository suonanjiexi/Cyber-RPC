package com.cyber.rpc.serialize;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.Pool;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Kryo序列化实现，提供高性能的序列化功能
 */
public class KryoSerializer implements Serializer {
    
    // 使用ThreadLocal确保线程安全
    private static final ThreadLocal<Kryo> KRYO_THREAD_LOCAL = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false); // 不要求注册类
        kryo.setReferences(true); // 支持循环引用
        return kryo;
    });
    
    // 使用对象池管理Output和Input对象，减少创建开销
    private static final Pool<Output> OUTPUT_POOL = new Pool<Output>(true, false, 16) {
        @Override
        protected Output create() {
            return new Output(1024, -1);
        }
    };
    
    private static final Pool<Input> INPUT_POOL = new Pool<Input>(true, false, 16) {
        @Override
        protected Input create() {
            return new Input(1024);
        }
    };
    
    @Override
    public byte[] serialize(Object obj) {
        if (obj == null) {
            return null;
        }
        
        Kryo kryo = KRYO_THREAD_LOCAL.get();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Output output = OUTPUT_POOL.obtain();
        output.setOutputStream(byteArrayOutputStream);
        
        try {
            kryo.writeObject(output, obj);
            output.flush();
            return byteArrayOutputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Kryo序列化失败", e);
        } finally {
            output.close();
            OUTPUT_POOL.free(output);
        }
    }
    
    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        
        Kryo kryo = KRYO_THREAD_LOCAL.get();
        Input input = INPUT_POOL.obtain();
        input.setInputStream(new ByteArrayInputStream(bytes));
        
        try {
            return kryo.readObject(input, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Kryo反序列化失败", e);
        } finally {
            INPUT_POOL.free(input);
        }
    }
    
    @Override
    public SerializerType getType() {
        return SerializerType.KRYO;
    }
}