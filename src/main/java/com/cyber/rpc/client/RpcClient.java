package com.cyber.rpc.client;

import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;

/**
 * RPC客户端接口
 * 定义RPC客户端的基本操作
 */
public interface RpcClient {

    /**
     * 创建服务代理对象
     *
     * @param serviceClass 服务接口类
     * @param serviceName 服务名称
     * @param <T> 服务接口类型
     * @return 服务代理对象
     */
    <T> T createService(Class<T> serviceClass, String serviceName);

    /**
     * 异步调用服务方法
     *
     * @param serviceName 服务名称
     * @param methodName 方法名称
     * @param args 方法参数
     * @return 包含调用结果的CompletableFuture
     */
    CompletableFuture<Object> asyncCall(String serviceName, String methodName, Object[] args);

    /**
     * 关闭客户端
     */
    void close();
}
