package com.cyber.rpc.loadbalance;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Random;

/**
 * 随机负载均衡器实现
 */
public class RandomLoadBalancer implements LoadBalancer {
    
    private final Random random = new Random();
    
    @Override
    public InetSocketAddress select(List<InetSocketAddress> addresses, String serviceName) {
        if (addresses == null || addresses.isEmpty()) {
            throw new RuntimeException("服务地址列表为空");
        }
        // 随机选择一个地址
        return addresses.get(random.nextInt(addresses.size()));
    }
}