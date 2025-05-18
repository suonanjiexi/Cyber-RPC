package com.cyber.rpc.loadbalance;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轮询负载均衡器
 * 按照顺序依次选择服务实例
 */
public class RoundRobinLoadBalancer implements LoadBalancer {
    
    // 每个服务的计数器，用于轮询
    private final Map<String, AtomicInteger> counterMap = new ConcurrentHashMap<>();
    
    @Override
    public InetSocketAddress select(List<InetSocketAddress> addresses, String serviceName) {
        if (addresses == null || addresses.isEmpty()) {
            return null;
        }
        
        // 如果只有一个地址，直接返回
        if (addresses.size() == 1) {
            return addresses.get(0);
        }
        
        // 获取或创建服务的计数器
        AtomicInteger counter = counterMap.computeIfAbsent(serviceName, k -> new AtomicInteger(0));
        
        // 获取当前计数并递增
        int current = counter.getAndIncrement();
        
        // 如果计数器超过Integer最大值的一半，则重置
        if (current > Integer.MAX_VALUE / 2) {
            counter.set(0);
            current = 0;
        }
        
        // 计算索引并返回对应地址
        int index = Math.abs(current) % addresses.size();
        return addresses.get(index);
    }
}
