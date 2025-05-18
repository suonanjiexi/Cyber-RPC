package com.cyber.rpc.server;

import com.cyber.rpc.governance.GovernanceManager;
import com.cyber.rpc.governance.RateLimiter;
import com.cyber.rpc.governance.TokenBucketRateLimiter;
import com.cyber.rpc.metrics.DefaultRpcMetrics;
import com.cyber.rpc.metrics.MetricsManager;
import com.cyber.rpc.metrics.RpcMetrics;
import com.cyber.rpc.registry.ServiceRegistry;
import com.cyber.rpc.serialize.SerializerType;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * RPC服务器构建器
 * 用于创建并配置RPC服务器
 */
public class RpcServerBuilder {

    private final RpcServerConfig config = new RpcServerConfig();
    private ServiceRegistry serviceRegistry;
    private ExecutorService executorService;
    private GovernanceManager governanceManager;
    private RpcMetrics metrics;

    /**
     * 设置服务器端口
     */
    public RpcServerBuilder port(int port) {
        config.setPort(port);
        return this;
    }

    /**
     * 设置序列化类型
     */
    public RpcServerBuilder serializer(SerializerType serializerType) {
        config.setSerializerType(serializerType);
        return this;
    }

    /**
     * 设置工作线程数
     */
    public RpcServerBuilder workerThreads(int workerThreads) {
        config.setWorkerThreads(workerThreads);
        return this;
    }

    /**
     * 设置是否启用限流
     */
    public RpcServerBuilder enableRateLimiter(boolean enable) {
        config.setEnableRateLimiter(enable);
        return this;
    }

    /**
     * 设置全局限流每秒最大请求数
     */
    public RpcServerBuilder maxRequestsPerSecond(double maxRequestsPerSecond) {
        config.setMaxRequestsPerSecond(maxRequestsPerSecond);
        return this;
    }

    /**
     * 设置单服务限流每秒最大请求数
     */
    public RpcServerBuilder serviceMaxRequestsPerSecond(Double serviceMaxRequestsPerSecond) {
        config.setServiceMaxRequestsPerSecond(serviceMaxRequestsPerSecond);
        return this;
    }

    /**
     * 设置是否启用指标收集
     */
    public RpcServerBuilder enableMetrics(boolean enable) {
        config.setEnableMetrics(enable);
        return this;
    }

    /**
     * 设置是否注册到服务中心
     */
    public RpcServerBuilder registerToRegistry(boolean register) {
        config.setRegisterToRegistry(register);
        return this;
    }

    /**
     * 设置服务注册中心
     */
    public RpcServerBuilder serviceRegistry(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
        return this;
    }

    /**
     * 设置治理管理器
     */
    public RpcServerBuilder governanceManager(GovernanceManager governanceManager) {
        this.governanceManager = governanceManager;
        return this;
    }

    /**
     * 设置指标收集器
     */
    public RpcServerBuilder metrics(RpcMetrics metrics) {
        this.metrics = metrics;
        return this;
    }

    /**
     * 设置线程池
     */
    public RpcServerBuilder executorService(ExecutorService executorService) {
        this.executorService = executorService;
        return this;
    }

    /**
     * 构建RPC服务器
     */
    public RpcServer build() {
        // 如果没有设置线程池，则创建默认线程池
        if (executorService == null) {
            executorService = Executors.newFixedThreadPool(config.getWorkerThreads());
        }

        // 如果没有设置指标收集器，并且启用了指标收集
        if (metrics == null && config.isEnableMetrics()) {
            metrics = new DefaultRpcMetrics();
            MetricsManager.getInstance().registerMetrics("rpcServer", metrics);
        }

        // 如果没有设置治理管理器，并且启用了限流
        if (governanceManager == null && config.isEnableRateLimiter()) {
            governanceManager = new GovernanceManager();
            
            // 创建全局限流器
            RateLimiter globalRateLimiter = new TokenBucketRateLimiter(
                "global",
                config.getMaxRequestsPerSecond(),
                config.getMaxRequestsPerSecond() / 10
            );
            governanceManager.registerRateLimiter("global", globalRateLimiter);
        }

        // 创建RPC服务器
        return new DefaultRpcServer(
                config,
                serviceRegistry,
                governanceManager,
                metrics,
                executorService
        );
    }
}
