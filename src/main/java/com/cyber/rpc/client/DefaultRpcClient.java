package com.cyber.rpc.client;

import com.cyber.rpc.governance.CircuitBreaker;
import com.cyber.rpc.governance.GovernanceManager;
import com.cyber.rpc.governance.RateLimiter;
import com.cyber.rpc.governance.SlidingWindowCircuitBreaker;
import com.cyber.rpc.governance.TokenBucketRateLimiter;
import com.cyber.rpc.loadbalance.LoadBalancer;
import com.cyber.rpc.metrics.DefaultRpcMetrics;
import com.cyber.rpc.metrics.MetricsManager;
import com.cyber.rpc.metrics.RpcMetrics;
import com.cyber.rpc.protocol.RpcRequest;
import com.cyber.rpc.protocol.RpcResponse;
import com.cyber.rpc.registry.ServiceRegistry;
import com.cyber.rpc.retry.RetryExecutor;
import com.cyber.rpc.retry.RetryStrategy;
import com.cyber.rpc.serialize.Serializer;
import com.cyber.rpc.serialize.SerializerFactory;
import com.cyber.rpc.transport.NettyTransport;
import com.cyber.rpc.transport.Transport;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 默认RPC客户端实现
 */
public class DefaultRpcClient implements RpcClient {
    
    private static final Logger LOGGER = Logger.getLogger(DefaultRpcClient.class.getName());
    
    // RPC客户端配置
    private final RpcClientConfig config;
    
    // 服务注册中心
    private final ServiceRegistry serviceRegistry;
    
    // 负载均衡器
    private final LoadBalancer loadBalancer;
    
    // 重试策略
    private final RetryStrategy retryStrategy;
    
    // 治理管理器
    private final GovernanceManager governanceManager;
    
    // 线程池
    private final ExecutorService executorService;
    
    // 序列化器
    private final Serializer serializer;
    
    // 传输层
    private final Transport transport;
    
    // 服务对应的代理缓存
    private final Map<Class<?>, Object> serviceProxies = new ConcurrentHashMap<>();
    
    // 指标收集器
    private final RpcMetrics metrics;
    
    /**
     * 创建RPC客户端
     */
    public DefaultRpcClient(RpcClientConfig config, 
                          ServiceRegistry serviceRegistry,
                          LoadBalancer loadBalancer,
                          RetryStrategy retryStrategy,
                          GovernanceManager governanceManager,
                          ExecutorService executorService) {
        this.config = config;
        this.serviceRegistry = serviceRegistry;
        this.loadBalancer = loadBalancer;
        this.retryStrategy = retryStrategy;
        this.executorService = executorService;
        
        // 初始化序列化器
        this.serializer = SerializerFactory.getSerializer(config.getSerializerType());
        
        // 初始化传输层
        this.transport = new NettyTransport(executorService, config.getMaxConnections(), 
                                           config.getConnectTimeoutMs(), config.getRequestTimeoutMs());
        
        // 初始化指标收集
        this.metrics = new DefaultRpcMetrics();
        MetricsManager.getInstance().registerMetrics("rpcClient", metrics);
        
        // 初始化治理管理器
        if (governanceManager == null && (config.isEnableCircuitBreaker() || config.isEnableRateLimiter())) {
            this.governanceManager = initDefaultGovernanceManager();
        } else {
            this.governanceManager = governanceManager;
        }
        
        LOGGER.info("RPC客户端初始化完成");
    }
    
    /**
     * 初始化默认治理管理器
     */
    private GovernanceManager initDefaultGovernanceManager() {
        GovernanceManager manager = new GovernanceManager();
        
        // 如果启用熔断器，注册默认熔断器
        if (config.isEnableCircuitBreaker()) {
            CircuitBreaker circuitBreaker = new SlidingWindowCircuitBreaker(20, 10, 0.5, 5000);
            manager.registerCircuitBreaker("default", circuitBreaker);
        }
        
        // 如果启用限流器，注册默认限流器
        if (config.isEnableRateLimiter()) {
            RateLimiter rateLimiter = new TokenBucketRateLimiter("default", config.getMaxRequestsPerSecond(), config.getMaxRequestsPerSecond() / 10);
            manager.registerRateLimiter("default", rateLimiter);
        }
        
        return manager;
    }
    
    @Override
    public <T> T createService(Class<T> serviceClass, String serviceName) {
        // 检查缓存中是否已存在代理
        @SuppressWarnings("unchecked")
        T cachedProxy = (T) serviceProxies.get(serviceClass);
        if (cachedProxy != null) {
            return cachedProxy;
        }
        
        // 创建服务代理
        @SuppressWarnings("unchecked")
        T proxy = (T) Proxy.newProxyInstance(
            serviceClass.getClassLoader(),
            new Class<?>[]{serviceClass},
            new RpcInvocationHandler(serviceName)
        );
        
        // 缓存代理对象
        serviceProxies.put(serviceClass, proxy);
        
        return proxy;
    }
    
    @Override
    public CompletableFuture<Object> asyncCall(String serviceName, String methodName, Object[] args) {
        // 创建RPC请求
        RpcRequest request = new RpcRequest();
        request.setRequestId(UUID.randomUUID().toString());
        request.setServiceName(serviceName);
        request.setMethodName(methodName);
        request.setParameters(args);
        request.setParameterTypes(getParameterTypes(args));
        
        // 创建结果Future
        CompletableFuture<Object> resultFuture = new CompletableFuture<>();
        
        // 使用重试执行器发送请求
        executorService.submit(() -> {
            try {
                // 创建重试执行器
                RetryExecutor retryExecutor = new RetryExecutor(retryStrategy);
                
                // 使用重试执行器执行请求
                Object result = retryExecutor.execute(() -> {
                    try {
                        long startTime = System.currentTimeMillis();
                        
                        // 检查和应用限流规则
                        if (governanceManager != null && config.isEnableRateLimiter()) {
                            RateLimiter rateLimiter = governanceManager.getRateLimiter("default");
                            if (rateLimiter != null && !rateLimiter.tryAcquire()) {
                                throw new RuntimeException("请求被限流");
                            }
                        }
                        
                        // 检查熔断器状态
                        if (governanceManager != null && config.isEnableCircuitBreaker()) {
                            CircuitBreaker circuitBreaker = governanceManager.getCircuitBreaker("default");
                            if (circuitBreaker != null && !circuitBreaker.allowRequest()) {
                                throw new RuntimeException("熔断器已打开，请求被拒绝");
                            }
                        }
                        
                        // 获取可用服务地址
                        List<InetSocketAddress> addresses = serviceRegistry.lookup(serviceName);
                        if (addresses == null || addresses.isEmpty()) {
                            throw new RuntimeException("没有可用的服务实例: " + serviceName);
                        }
                        
                        // 使用负载均衡选择服务地址
                        InetSocketAddress address = loadBalancer.select(addresses, serviceName);
                        
                        // 序列化请求
                        byte[] requestBytes = serializer.serialize(request);
                        
                        // 发送请求并等待响应
                        byte[] responseBytes = transport.sendRequest(address, requestBytes, config.getRequestTimeoutMs());
                        
                        // 反序列化响应
                        RpcResponse response = serializer.deserialize(responseBytes, RpcResponse.class);
                        
                        // 记录指标
                        long elapsedTime = System.currentTimeMillis() - startTime;
                        metrics.recordRequest(serviceName, methodName, elapsedTime);
                        
                        // 更新熔断器统计
                        if (governanceManager != null && config.isEnableCircuitBreaker()) {
                            CircuitBreaker circuitBreaker = governanceManager.getCircuitBreaker("default");
                            if (circuitBreaker != null) {
                                circuitBreaker.recordSuccess();
                            }
                        }
                        
                        // 处理响应结果
                        if (response.getException() != null) {
                            throw new RuntimeException("调用服务异常: " + response.getException());
                        }
                        
                        return response.getResult();
                    } catch (Exception e) {
                        // 记录错误指标
                        metrics.recordError(serviceName, methodName);
                        
                        // 更新熔断器统计
                        if (governanceManager != null && config.isEnableCircuitBreaker()) {
                            CircuitBreaker circuitBreaker = governanceManager.getCircuitBreaker("default");
                            if (circuitBreaker != null) {
                                circuitBreaker.recordFailure();
                            }
                        }
                        
                        throw e;
                    }
                });
                
                // 设置结果
                resultFuture.complete(result);
            } catch (Exception e) {
                resultFuture.completeExceptionally(e);
            }
        });
        
        return resultFuture;
    }
    
    @Override
    public void close() {
        try {
            transport.close();
            executorService.shutdown();
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING, "关闭RPC客户端时被中断", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "关闭RPC客户端异常", e);
        } finally {
            // 确保线程池被关闭
            if (!executorService.isShutdown()) {
                executorService.shutdownNow();
            }
        }
    }
    
    /**
     * 获取参数类型数组
     */
    private Class<?>[] getParameterTypes(Object[] args) {
        if (args == null) {
            return new Class<?>[0];
        }
        
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i] != null ? args[i].getClass() : null;
        }
        return types;
    }
    
    /**
     * RPC调用处理器
     */
    private class RpcInvocationHandler implements InvocationHandler {
        
        private final String serviceName;
        
        public RpcInvocationHandler(String serviceName) {
            this.serviceName = serviceName;
        }
        
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // 如果是Object类的方法，直接本地调用
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }
            
            // 异步调用
            CompletableFuture<Object> future = asyncCall(serviceName, method.getName(), args);
            
            // 如果返回类型是CompletableFuture，直接返回future
            if (CompletableFuture.class.isAssignableFrom(method.getReturnType())) {
                return future;
            }
            
            // 否则，等待结果并返回
            try {
                return future.get(config.getRequestTimeoutMs(), TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                throw new RuntimeException("调用服务方法失败: " + e.getMessage(), e);
            }
        }
    }
}
