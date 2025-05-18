package com.cyber.rpc.registry;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * 服务注册中心接口，负责服务的注册与发现
 */
public interface ServiceRegistry {
    
    /**
     * 注册服务
     * 
     * @param serviceName 服务名称
     * @param address     服务地址
     */
    void register(String serviceName, InetSocketAddress address);
    
    /**
     * 注销服务
     * 
     * @param serviceName 服务名称
     * @param address     服务地址
     */
    void unregister(String serviceName, InetSocketAddress address);
    
    /**
     * 发现服务
     * 
     * @param serviceName 服务名称
     * @return 服务地址列表
     */
    List<InetSocketAddress> lookup(String serviceName);
    
    /**
     * 关闭注册中心
     */
    void close();
}