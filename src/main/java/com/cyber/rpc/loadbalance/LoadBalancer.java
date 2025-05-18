package com.cyber.rpc.loadbalance;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * 负载均衡接口，用于在多个服务提供者中选择一个
 */
public interface LoadBalancer {
    
    /**
     * 从服务地址列表中选择一个地址
     * 
     * @param addresses 服务地址列表
     * @param serviceName 服务名称，可用于特定服务的负载均衡策略
     * @return 选择的服务地址
     */
    InetSocketAddress select(List<InetSocketAddress> addresses, String serviceName);
}