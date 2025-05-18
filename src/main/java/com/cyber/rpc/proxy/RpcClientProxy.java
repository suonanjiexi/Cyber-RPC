package com.cyber.rpc.proxy;

import com.cyber.rpc.common.RpcRequest;
import com.cyber.rpc.common.RpcResponse;
import com.cyber.rpc.transport.RpcTransport;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;

/**
 * RPC客户端代理工厂，用于创建远程服务的本地代理
 */
public class RpcClientProxy implements InvocationHandler {
    
    private final RpcTransport rpcTransport;
    private final String version;
    private final String group;
    
    public RpcClientProxy(RpcTransport rpcTransport) {
        this(rpcTransport, "", "");
    }
    
    public RpcClientProxy(RpcTransport rpcTransport, String version, String group) {
        this.rpcTransport = rpcTransport;
        this.version = version;
        this.group = group;
    }
    
    /**
     * 创建代理对象
     * 
     * @param clazz 服务接口类
     * @param <T>   服务接口类型
     * @return 代理对象
     */
    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<T> clazz) {
        return (T) Proxy.newProxyInstance(
                clazz.getClassLoader(),
                new Class<?>[]{clazz},
                this
        );
    }
    
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 对Object类的方法直接调用
        if (Object.class.equals(method.getDeclaringClass())) {
            return method.invoke(this, args);
        }
        
        // 构建RPC请求对象
        RpcRequest request = new RpcRequest();
        request.setRequestId(UUID.randomUUID().toString());
        request.setInterfaceName(method.getDeclaringClass().getName());
        request.setMethodName(method.getName());
        request.setParameterTypes(method.getParameterTypes());
        request.setParameters(args);
        request.setVersion(version);
        request.setGroup(group);
        
        // 发送RPC请求并获取响应
        RpcResponse<?> response = rpcTransport.sendRequest(request);
        
        // 检查响应状态
        if (!response.isSuccess()) {
            throw new RuntimeException("RPC调用失败: " + response.getMessage());
        }
        
        // 返回响应结果
        return response.getData();
    }
}