package com.cyber.rpc.example;

import com.cyber.rpc.client.RpcClient;
import com.cyber.rpc.client.RpcClientBuilder;
import com.cyber.rpc.loadbalance.ConsistentHashLoadBalancer;
import com.cyber.rpc.loadbalance.LoadBalancerFactory;
import com.cyber.rpc.registry.ServiceRegistry;
import com.cyber.rpc.registry.ZookeeperServiceRegistry;
import com.cyber.rpc.retry.RetryExecutor;
import com.cyber.rpc.retry.RetryStrategy;
import com.cyber.rpc.retry.RetryStrategyFactory;
import com.cyber.rpc.serialize.SerializerType;

import java.util.Scanner;
import java.util.logging.Logger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * RPC客户端示例
 */
public class ClientExample {
    
    private static final Logger LOGGER = Logger.getLogger(ClientExample.class.getName());
    
    public static void main(String[] args) {
        // 创建服务注册中心（可以根据需要选择不同的注册中心实现）
        ServiceRegistry serviceRegistry = createServiceRegistry();
        
        // 创建并配置RPC客户端
        RpcClient client = createRpcClient(serviceRegistry);
        
        try {
            // 创建服务代理
            HelloService helloService = client.createService(HelloService.class, "helloService");
            
            // 测试基本调用
            testBasicCall(helloService);
            
            // 测试重试机制（调用可能超时的方法）
            testRetryWithDelay(helloService);
            
            // 测试异常重试（调用可能抛出异常的方法）
            testRetryWithException(helloService);
            
            // 测试异步调用
            testAsyncCall(client);
            
            // 等待用户输入以退出
            LOGGER.info("按任意键退出...");
            try (Scanner scanner = new Scanner(System.in)) {
                scanner.nextLine();
            }
            
        } finally {
            // 关闭客户端
            client.close();
            LOGGER.info("RPC客户端已关闭");
        }
    }
    
    /**
     * 创建服务注册中心
     */
    private static ServiceRegistry createServiceRegistry() {
        // 这里使用ZooKeeper作为示例，连接字符串应该根据实际环境配置
        return new ZookeeperServiceRegistry("localhost:2181");
    }
    
    /**
     * 创建RPC客户端
     */
    private static RpcClient createRpcClient(ServiceRegistry serviceRegistry) {
        // 这里使用一致性哈希负载均衡作为示例
        ConsistentHashLoadBalancer customLoadBalancer = new ConsistentHashLoadBalancer(
                160, // 虚拟节点数
                (methodName, args) -> {
                    // 如果第一个参数是字符串，用它作为哈希键
                    if (args != null && args.length > 0 && args[0] instanceof String) {
                        return args[0].toString();
                    }
                    return methodName; // 否则使用方法名
                }
        );
        
        return new RpcClientBuilder()
                .serializer(SerializerType.PROTOSTUFF)
                .loadBalancer(LoadBalancerFactory.LoadBalancerType.CONSISTENT_HASH)
                //.loadBalancer(customLoadBalancer) // 使用自定义负载均衡器
                .retryStrategy("progressiveRetry") // 使用预定义的渐进重试策略
                .connectTimeout(3000)
                .requestTimeout(5000)
                .maxConnections(10)
                .enableCircuitBreaker(true)
                .serviceRegistry(serviceRegistry)
                .build();
    }
    
    /**
     * 测试基本调用
     */
    private static void testBasicCall(HelloService helloService) {
        LOGGER.info("测试基本调用...");
        String result = helloService.sayHello("World");
        LOGGER.info("结果: " + result);
    }
    
    /**
     * 测试重试机制（调用可能超时的方法）
     */
    private static void testRetryWithDelay(HelloService helloService) {
        LOGGER.info("测试重试机制（延迟调用）...");
        try {
            // 调用一个延迟3秒的方法，可能会触发重试
            String result = helloService.sayHelloWithDelay("SlowWorld", 3000);
            LOGGER.info("结果: " + result);
        } catch (Exception e) {
            LOGGER.severe("调用失败: " + e.getMessage());
        }
    }
    
    /**
     * 测试异常重试（调用可能抛出异常的方法）
     */
    private static void testRetryWithException(HelloService helloService) {
        LOGGER.info("测试异常重试...");
        
        // 创建自定义重试执行器
        RetryStrategy retryStrategy = RetryStrategyFactory.getInstance().getStrategy("standardRetry");
        RetryExecutor retryExecutor = new RetryExecutor(retryStrategy);
        
        try {
            // 使用重试执行器包装可能失败的调用
            String result = retryExecutor.execute(() -> {
                // 50%概率抛出异常，用于测试重试
                boolean throwException = Math.random() < 0.5;
                LOGGER.info("尝试调用，是否抛出异常: " + throwException);
                return helloService.sayHelloWithException("ExceptionWorld", throwException);
            });
            
            LOGGER.info("最终结果: " + result);
        } catch (Exception e) {
            LOGGER.severe("最终调用失败: " + e.getMessage());
        }
    }
    
    /**
     * 测试异步调用
     */
    private static void testAsyncCall(RpcClient client) {
        LOGGER.info("测试异步调用...");
        
        try {
            // 发起异步调用
            CompletableFuture<Object> future = client.asyncCall(
                    "helloService", 
                    "sayHello",
                    new Object[]{"AsyncWorld"}
            );
            
            // 添加回调
            future.thenAccept(result -> LOGGER.info("异步调用结果: " + result));
            
            // 等待完成
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.severe("异步调用失败: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }
}
