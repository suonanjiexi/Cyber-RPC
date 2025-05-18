package com.cyber.rpc.example;

import com.cyber.rpc.governance.GovernanceManager;
import com.cyber.rpc.governance.TokenBucketRateLimiter;
import com.cyber.rpc.registry.ServiceRegistry;
import com.cyber.rpc.registry.ZookeeperServiceRegistry;
import com.cyber.rpc.serialize.SerializerType;
import com.cyber.rpc.server.RpcServer;
import com.cyber.rpc.server.RpcServerBuilder;

import java.util.Scanner;
import java.util.logging.Logger;

/**
 * RPC服务器示例
 */
public class ServerExample {
    
    private static final Logger LOGGER = Logger.getLogger(ServerExample.class.getName());
    
    public static void main(String[] args) {
        // 创建服务注册中心（可以根据需要选择不同的注册中心实现）
        ServiceRegistry serviceRegistry = createServiceRegistry();
        
        // 创建服务治理管理器
        GovernanceManager governanceManager = createGovernanceManager();
        
        // 创建并配置RPC服务器
        RpcServer server = new RpcServerBuilder()
                .port(9000)
                .serializer(SerializerType.PROTOSTUFF)
                .workerThreads(16)
                .enableRateLimiter(true)
                .maxRequestsPerSecond(1000.0)
                .enableMetrics(true)
                .serviceRegistry(serviceRegistry)
                .governanceManager(governanceManager)
                .build();
        
        // 注册服务
        server.registerService("helloService", new HelloServiceImpl());
        
        // 启动服务器
        server.start();
        
        LOGGER.info("RPC服务器已启动，按任意键停止服务器...");
        
        // 等待用户输入以停止服务器
        try (Scanner scanner = new Scanner(System.in)) {
            scanner.nextLine();
        }
        
        // 停止服务器
        server.stop();
        LOGGER.info("RPC服务器已停止");
    }
    
    /**
     * 创建服务注册中心
     * 这里使用ZooKeeper注册中心作为示例，实际使用时可以根据需要选择不同的实现
     */
    private static ServiceRegistry createServiceRegistry() {
        // 这里使用ZooKeeper作为示例，连接字符串应该根据实际环境配置
        return new ZookeeperServiceRegistry("localhost:2181");
    }
    
    /**
     * 创建服务治理管理器
     */
    private static GovernanceManager createGovernanceManager() {
        GovernanceManager manager = new GovernanceManager();
        
        // 为HelloService创建一个特定的限流器，每秒允许100个请求
        TokenBucketRateLimiter helloServiceLimiter = new TokenBucketRateLimiter("helloService", 100.0, 10.0);
        manager.registerRateLimiter("helloService", helloServiceLimiter);
        
        return manager;
    }
}
