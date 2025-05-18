package com.cyber.rpc.loadbalance;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 负载均衡器工厂
 * 用于创建和管理各种负载均衡策略
 */
public class LoadBalancerFactory {
    
    /**
     * 负载均衡策略类型
     */
    public enum LoadBalancerType {
        /**
         * 随机负载均衡
         */
        RANDOM,
        
        /**
         * 轮询负载均衡
         */
        ROUND_ROBIN,
        
        /**
         * 加权轮询负载均衡
         */
        WEIGHTED_ROUND_ROBIN,
        
        /**
         * 一致性哈希负载均衡
         */
        CONSISTENT_HASH,
        
        /**
         * 最少连接负载均衡
         */
        LEAST_CONNECTION
    }
    
    // 单例实例
    private static final LoadBalancerFactory INSTANCE = new LoadBalancerFactory();
    
    // 负载均衡器缓存，避免重复创建
    private final Map<LoadBalancerType, LoadBalancer> loadBalancerCache = new ConcurrentHashMap<>();
    
    /**
     * 私有构造函数，防止外部创建实例
     */
    private LoadBalancerFactory() {
        // 初始化默认的负载均衡器
        loadBalancerCache.put(LoadBalancerType.RANDOM, new RandomLoadBalancer());
        loadBalancerCache.put(LoadBalancerType.ROUND_ROBIN, new RoundRobinLoadBalancer());
        loadBalancerCache.put(LoadBalancerType.WEIGHTED_ROUND_ROBIN, new WeightedRoundRobinLoadBalancer());
        loadBalancerCache.put(LoadBalancerType.CONSISTENT_HASH, new ConsistentHashLoadBalancer());
        loadBalancerCache.put(LoadBalancerType.LEAST_CONNECTION, new LeastConnectionLoadBalancer());
    }
    
    /**
     * 获取工厂实例
     * 
     * @return 负载均衡器工厂实例
     */
    public static LoadBalancerFactory getInstance() {
        return INSTANCE;
    }
    
    /**
     * 获取负载均衡器
     * 
     * @param type 负载均衡器类型
     * @return 负载均衡器实例
     */
    public LoadBalancer getLoadBalancer(LoadBalancerType type) {
        return loadBalancerCache.getOrDefault(type, loadBalancerCache.get(LoadBalancerType.RANDOM));
    }
    
    /**
     * 注册自定义负载均衡器
     * 
     * @param type 负载均衡器类型
     * @param loadBalancer 负载均衡器实例
     */
    public void registerLoadBalancer(LoadBalancerType type, LoadBalancer loadBalancer) {
        loadBalancerCache.put(type, loadBalancer);
    }
    
    /**
     * 创建加权轮询负载均衡器
     * 
     * @param weightProvider 权重提供者
     * @return 加权轮询负载均衡器
     */
    public LoadBalancer createWeightedRoundRobinLoadBalancer(
            WeightedRoundRobinLoadBalancer.InstanceWeightProvider weightProvider) {
        return new WeightedRoundRobinLoadBalancer(weightProvider);
    }
    
    /**
     * 创建一致性哈希负载均衡器
     * 
     * @param virtualNodes 虚拟节点数
     * @param hashArgProvider 哈希参数提取器
     * @return 一致性哈希负载均衡器
     */
    public LoadBalancer createConsistentHashLoadBalancer(
            int virtualNodes, 
            ConsistentHashLoadBalancer.RequestHashArgProvider hashArgProvider) {
        return new ConsistentHashLoadBalancer(virtualNodes, hashArgProvider);
    }
    
    /**
     * 创建最少连接负载均衡器
     * 
     * @param connectionChangeListener 连接变化监听器
     * @return 最少连接负载均衡器
     */
    public LoadBalancer createLeastConnectionLoadBalancer(
            LeastConnectionLoadBalancer.ConnectionChangeListener connectionChangeListener) {
        return new LeastConnectionLoadBalancer(connectionChangeListener);
    }
}
