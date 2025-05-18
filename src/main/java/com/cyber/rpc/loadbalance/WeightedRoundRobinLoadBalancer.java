package com.cyber.rpc.loadbalance;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 加权轮询负载均衡器
 * 根据服务实例的权重进行轮询选择，权重高的实例被选中的概率更大
 */
public class WeightedRoundRobinLoadBalancer implements LoadBalancer {
    
    // 每个服务的计数器，用于轮询
    private final Map<String, AtomicInteger> counterMap = new ConcurrentHashMap<>();
    
    // 服务实例权重提供者
    private final InstanceWeightProvider weightProvider;
    
    /**
     * 创建加权轮询负载均衡器
     * 
     * @param weightProvider 权重提供者
     */
    public WeightedRoundRobinLoadBalancer(InstanceWeightProvider weightProvider) {
        this.weightProvider = weightProvider;
    }
    
    /**
     * 使用默认权重提供者创建加权轮询负载均衡器
     * 默认情况下所有实例权重相同
     */
    public WeightedRoundRobinLoadBalancer() {
        this((address, serviceName) -> 1);
    }
    
    @Override
    public InetSocketAddress select(List<InetSocketAddress> addresses, String serviceName) {
        if (addresses == null || addresses.isEmpty()) {
            return null;
        }
        
        // 如果只有一个地址，直接返回
        if (addresses.size() == 1) {
            return addresses.get(0);
        }
        
        // 预处理权重信息
        List<WeightedServer> servers = new ArrayList<>(addresses.size());
        int totalWeight = 0;
        
        for (InetSocketAddress address : addresses) {
            int weight = Math.max(1, weightProvider.getWeight(address, serviceName));
            totalWeight += weight;
            servers.add(new WeightedServer(address, weight));
        }
        
        // 获取或创建服务的计数器
        AtomicInteger counter = counterMap.computeIfAbsent(serviceName, k -> new AtomicInteger(0));
        
        // 获取当前计数
        int currentCount = counter.getAndIncrement() % totalWeight;
        
        // 如果计数器超过Integer最大值的一半，则重置
        if (currentCount > Integer.MAX_VALUE / 2) {
            counter.set(0);
            currentCount = 0;
        }
        
        // 根据权重选择服务器
        int weightSum = 0;
        for (WeightedServer server : servers) {
            weightSum += server.weight;
            if (currentCount < weightSum) {
                return server.address;
            }
        }
        
        // 兜底，返回第一个地址
        return addresses.get(0);
    }
    
    /**
     * 带权重的服务器信息
     */
    private static class WeightedServer {
        final InetSocketAddress address;
        final int weight;
        
        WeightedServer(InetSocketAddress address, int weight) {
            this.address = address;
            this.weight = weight;
        }
    }
    
    /**
     * 实例权重提供者接口
     * 用于获取服务实例的权重
     */
    public interface InstanceWeightProvider {
        /**
         * 获取服务实例的权重
         * 
         * @param address 服务地址
         * @param serviceName 服务名称
         * @return 权重值，必须大于0
         */
        int getWeight(InetSocketAddress address, String serviceName);
    }
}
