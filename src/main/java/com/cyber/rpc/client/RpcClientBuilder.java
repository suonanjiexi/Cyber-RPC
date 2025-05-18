package com.cyber.rpc.client;

import com.cyber.rpc.governance.GovernanceManager;
import com.cyber.rpc.loadbalance.LoadBalancer;
import com.cyber.rpc.loadbalance.LoadBalancerFactory;
import com.cyber.rpc.registry.ServiceRegistry;
import com.cyber.rpc.retry.RetryStrategy;
import com.cyber.rpc.retry.RetryStrategyFactory;
import com.cyber.rpc.serialize.SerializerType;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * RPC客户端构建器
 * 用于创建并配置RPC客户端
 */
public class RpcClientBuilder {

    private final RpcClientConfig config = new RpcClientConfig();
    private ServiceRegistry serviceRegistry;
    private ExecutorService executorService;
    private LoadBalancer customLoadBalancer;
    private RetryStrategy customRetryStrategy;
    private GovernanceManager governanceManager;

    /**
     * 设置序列化类型
     */
    public RpcClientBuilder serializer(SerializerType serializerType) {
        config.setSerializerType(serializerType);
        return this;
    }

    /**
     * 设置负载均衡类型
     */
    public RpcClientBuilder loadBalancer(LoadBalancerFactory.LoadBalancerType loadBalancerType) {
        config.setLoadBalancerType(loadBalancerType);
        return this;
    }

    /**
     * 设置自定义负载均衡器
     */
    public RpcClientBuilder loadBalancer(LoadBalancer loadBalancer) {
        this.customLoadBalancer = loadBalancer;
        return this;
    }

    /**
     * 设置重试策略名称
     */
    public RpcClientBuilder retryStrategy(String retryStrategyName) {
        config.setRetryStrategyName(retryStrategyName);
        return this;
    }

    /**
     * 设置自定义重试策略
     */
    public RpcClientBuilder retryStrategy(RetryStrategy retryStrategy) {
        this.customRetryStrategy = retryStrategy;
        return this;
    }

    /**
     * 设置连接超时时间
     */
    public RpcClientBuilder connectTimeout(int timeoutMs) {
        config.setConnectTimeoutMs(timeoutMs);
        return this;
    }

    /**
     * 设置请求超时时间
     */
    public RpcClientBuilder requestTimeout(int timeoutMs) {
        config.setRequestTimeoutMs(timeoutMs);
        return this;
    }

    /**
     * 设置最大连接数
     */
    public RpcClientBuilder maxConnections(int maxConnections) {
        config.setMaxConnections(maxConnections);
        return this;
    }

    /**
     * 设置服务注册中心
     */
    public RpcClientBuilder serviceRegistry(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
        return this;
    }

    /**
     * 设置是否启用熔断器
     */
    public RpcClientBuilder enableCircuitBreaker(boolean enable) {
        config.setEnableCircuitBreaker(enable);
        return this;
    }

    /**
     * 设置是否启用限流器
     */
    public RpcClientBuilder enableRateLimiter(boolean enable) {
        config.setEnableRateLimiter(enable);
        return this;
    }

    /**
     * 设置限流器每秒最大请求数
     */
    public RpcClientBuilder maxRequestsPerSecond(double maxRequestsPerSecond) {
        config.setMaxRequestsPerSecond(maxRequestsPerSecond);
        return this;
    }

    /**
     * 设置治理管理器
     */
    public RpcClientBuilder governanceManager(GovernanceManager governanceManager) {
        this.governanceManager = governanceManager;
        return this;
    }

    /**
     * 设置线程池
     */
    public RpcClientBuilder executorService(ExecutorService executorService) {
        this.executorService = executorService;
        return this;
    }

    /**
     * 构建RPC客户端
     */
    public RpcClient build() {
        // 如果没有设置线程池，则创建默认线程池
        if (executorService == null) {
            executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
        }

        // 获取负载均衡器
        LoadBalancer loadBalancer = customLoadBalancer != null ? 
                customLoadBalancer : 
                LoadBalancerFactory.getInstance().getLoadBalancer(config.getLoadBalancerType());

        // 获取重试策略
        RetryStrategy retryStrategy = customRetryStrategy != null ? 
                customRetryStrategy : 
                config.createRetryStrategy();

        // 创建RPC客户端
        return new DefaultRpcClient(
                config,
                serviceRegistry,
                loadBalancer,
                retryStrategy,
                governanceManager,
                executorService
        );
    }
}
