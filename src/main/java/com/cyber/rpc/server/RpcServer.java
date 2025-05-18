package com.cyber.rpc.server;

/**
 * RPC服务器接口
 * 定义了RPC服务器的基本操作
 */
public interface RpcServer {
    
    /**
     * 注册服务
     *
     * @param serviceName 服务名称
     * @param serviceImpl 服务实现
     */
    void registerService(String serviceName, Object serviceImpl);
    
    /**
     * 启动服务器
     */
    void start();
    
    /**
     * 停止服务器
     */
    void stop();
    
    /**
     * 获取服务器端口
     * 
     * @return 服务器端口
     */
    int getPort();
}
