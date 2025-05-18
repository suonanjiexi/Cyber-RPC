package com.cyber.rpc.server;

import com.cyber.rpc.serialize.SerializerType;

/**
 * RPC服务器配置类
 */
public class RpcServerConfig {
    
    // 服务器端口
    private int port = 9000;
    
    // 序列化类型
    private SerializerType serializerType = SerializerType.PROTOSTUFF;
    
    // 工作线程数
    private int workerThreads = Runtime.getRuntime().availableProcessors() * 2;
    
    // 是否启用限流
    private boolean enableRateLimiter = false;
    
    // 全局限流每秒最大请求数
    private double maxRequestsPerSecond = 1000.0;
    
    // 单服务限流每秒最大请求数，为null时使用全局配置
    private Double serviceMaxRequestsPerSecond = null;
    
    // 是否启用指标收集
    private boolean enableMetrics = true;
    
    // 是否注册到服务中心
    private boolean registerToRegistry = true;
    
    /**
     * 获取服务器端口
     */
    public int getPort() {
        return port;
    }
    
    /**
     * 设置服务器端口
     */
    public RpcServerConfig setPort(int port) {
        this.port = port;
        return this;
    }
    
    /**
     * 获取序列化类型
     */
    public SerializerType getSerializerType() {
        return serializerType;
    }
    
    /**
     * 设置序列化类型
     */
    public RpcServerConfig setSerializerType(SerializerType serializerType) {
        this.serializerType = serializerType;
        return this;
    }
    
    /**
     * 获取工作线程数
     */
    public int getWorkerThreads() {
        return workerThreads;
    }
    
    /**
     * 设置工作线程数
     */
    public RpcServerConfig setWorkerThreads(int workerThreads) {
        this.workerThreads = workerThreads;
        return this;
    }
    
    /**
     * 是否启用限流
     */
    public boolean isEnableRateLimiter() {
        return enableRateLimiter;
    }
    
    /**
     * 设置是否启用限流
     */
    public RpcServerConfig setEnableRateLimiter(boolean enableRateLimiter) {
        this.enableRateLimiter = enableRateLimiter;
        return this;
    }
    
    /**
     * 获取全局限流每秒最大请求数
     */
    public double getMaxRequestsPerSecond() {
        return maxRequestsPerSecond;
    }
    
    /**
     * 设置全局限流每秒最大请求数
     */
    public RpcServerConfig setMaxRequestsPerSecond(double maxRequestsPerSecond) {
        this.maxRequestsPerSecond = maxRequestsPerSecond;
        return this;
    }
    
    /**
     * 获取单服务限流每秒最大请求数
     */
    public Double getServiceMaxRequestsPerSecond() {
        return serviceMaxRequestsPerSecond;
    }
    
    /**
     * 设置单服务限流每秒最大请求数
     */
    public RpcServerConfig setServiceMaxRequestsPerSecond(Double serviceMaxRequestsPerSecond) {
        this.serviceMaxRequestsPerSecond = serviceMaxRequestsPerSecond;
        return this;
    }
    
    /**
     * 是否启用指标收集
     */
    public boolean isEnableMetrics() {
        return enableMetrics;
    }
    
    /**
     * 设置是否启用指标收集
     */
    public RpcServerConfig setEnableMetrics(boolean enableMetrics) {
        this.enableMetrics = enableMetrics;
        return this;
    }
    
    /**
     * 是否注册到服务中心
     */
    public boolean isRegisterToRegistry() {
        return registerToRegistry;
    }
    
    /**
     * 设置是否注册到服务中心
     */
    public RpcServerConfig setRegisterToRegistry(boolean registerToRegistry) {
        this.registerToRegistry = registerToRegistry;
        return this;
    }
}
