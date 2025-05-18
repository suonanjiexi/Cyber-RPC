package com.cyber.rpc.client;

import com.cyber.rpc.loadbalance.LoadBalancerFactory;
import com.cyber.rpc.retry.RetryStrategy;
import com.cyber.rpc.retry.RetryStrategyFactory;
import com.cyber.rpc.serialize.SerializerFactory;
import com.cyber.rpc.serialize.SerializerType;

/**
 * RPC客户端配置类
 * 用于统一管理客户端的配置参数
 */
public class RpcClientConfig {
    
    // 序列化类型
    private SerializerType serializerType = SerializerType.PROTOSTUFF;
    
    // 负载均衡类型
    private LoadBalancerFactory.LoadBalancerType loadBalancerType = LoadBalancerFactory.LoadBalancerType.RANDOM;
    
    // 重试策略名称，对应RetryStrategyFactory中的预定义策略
    private String retryStrategyName = "standardRetry";
    
    // 连接超时时间（毫秒）
    private int connectTimeoutMs = 5000;
    
    // 请求超时时间（毫秒）
    private int requestTimeoutMs = 10000;
    
    // 最大连接数
    private int maxConnections = 10;
    
    // 是否启用熔断器
    private boolean enableCircuitBreaker = true;
    
    // 是否启用限流器
    private boolean enableRateLimiter = false;
    
    // 限流器每秒最大请求数
    private double maxRequestsPerSecond = 100.0;
    
    /**
     * 获取序列化器类型
     */
    public SerializerType getSerializerType() {
        return serializerType;
    }
    
    /**
     * 设置序列化器类型
     */
    public RpcClientConfig setSerializerType(SerializerType serializerType) {
        this.serializerType = serializerType;
        return this;
    }
    
    /**
     * 获取负载均衡器类型
     */
    public LoadBalancerFactory.LoadBalancerType getLoadBalancerType() {
        return loadBalancerType;
    }
    
    /**
     * 设置负载均衡器类型
     */
    public RpcClientConfig setLoadBalancerType(LoadBalancerFactory.LoadBalancerType loadBalancerType) {
        this.loadBalancerType = loadBalancerType;
        return this;
    }
    
    /**
     * 获取重试策略名称
     */
    public String getRetryStrategyName() {
        return retryStrategyName;
    }
    
    /**
     * 设置重试策略名称
     */
    public RpcClientConfig setRetryStrategyName(String retryStrategyName) {
        this.retryStrategyName = retryStrategyName;
        return this;
    }
    
    /**
     * 获取连接超时时间
     */
    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }
    
    /**
     * 设置连接超时时间
     */
    public RpcClientConfig setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
        return this;
    }
    
    /**
     * 获取请求超时时间
     */
    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }
    
    /**
     * 设置请求超时时间
     */
    public RpcClientConfig setRequestTimeoutMs(int requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
        return this;
    }
    
    /**
     * 获取最大连接数
     */
    public int getMaxConnections() {
        return maxConnections;
    }
    
    /**
     * 设置最大连接数
     */
    public RpcClientConfig setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
        return this;
    }
    
    /**
     * 是否启用熔断器
     */
    public boolean isEnableCircuitBreaker() {
        return enableCircuitBreaker;
    }
    
    /**
     * 设置是否启用熔断器
     */
    public RpcClientConfig setEnableCircuitBreaker(boolean enableCircuitBreaker) {
        this.enableCircuitBreaker = enableCircuitBreaker;
        return this;
    }
    
    /**
     * 是否启用限流器
     */
    public boolean isEnableRateLimiter() {
        return enableRateLimiter;
    }
    
    /**
     * 设置是否启用限流器
     */
    public RpcClientConfig setEnableRateLimiter(boolean enableRateLimiter) {
        this.enableRateLimiter = enableRateLimiter;
        return this;
    }
    
    /**
     * 获取限流器每秒最大请求数
     */
    public double getMaxRequestsPerSecond() {
        return maxRequestsPerSecond;
    }
    
    /**
     * 设置限流器每秒最大请求数
     */
    public RpcClientConfig setMaxRequestsPerSecond(double maxRequestsPerSecond) {
        this.maxRequestsPerSecond = maxRequestsPerSecond;
        return this;
    }
    
    /**
     * 创建重试策略
     */
    public RetryStrategy createRetryStrategy() {
        return RetryStrategyFactory.getInstance().getStrategy(retryStrategyName);
    }
}
