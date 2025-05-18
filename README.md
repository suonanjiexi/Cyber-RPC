# Cyber-RPC 框架

## 项目概述

Cyber-RPC 是一个基于 Java 开发的轻量级 RPC（远程过程调用）框架，旨在提供高性能、易扩展的分布式服务调用解决方案。该框架支持服务注册与发现、负载均衡、序列化/反序列化等核心功能。

### 主要特点

- **多注册中心支持**: 支持ZooKeeper、Nacos和内存注册中心
- **多种序列化方式**: 集成JDK、Protostuff、Kryo和FST等高性能序列化器
- **多种负载均衡策略**: 支持随机、轮询、最少连接和一致性哈希等策略
- **服务治理功能**: 内置限流器、熔断器、重试机制等服务治理功能
- **异步调用支持**: 支持基于CompletableFuture的异步调用模式
- **指标监控**: 内置调用统计和性能监控

## 架构设计

### 核心组件

1. **服务提供者 (Provider)**：实现并发布服务的一方
2. **服务消费者 (Consumer)**：调用远程服务的一方
3. **注册中心 (Registry)**：负责服务的注册与发现
4. **序列化/反序列化 (Serialization)**：数据传输的编码与解码
5. **网络传输 (Transport)**：基于 Netty 的网络通信模块
6. **负载均衡 (LoadBalance)**：在多个服务提供者之间进行选择
7. **代理 (Proxy)**：屏蔽远程调用细节，提供透明的接口
8. **治理管理器 (GovernanceManager)**：管理限流器、熔断器等治理组件
9. **指标收集器 (Metrics)**：收集和统计服务调用指标

### 架构图

```
+----------------+    +-----------------+    +----------------+
|                |    |                 |    |                |
|  Consumer      |    |  Registry       |    |  Provider      |
|                |    |                 |    |                |
+-------+--------+    +--------+--------+    +--------+-------+
        |                      |                      |
        |                      |                      |
        |                      |                      |
        |                      |                      |
+-------v------------------------v------------------v-+
|                                                     |
|                Transport (Netty)                    |
|                                                     |
+---------------------+-----------------------------+-+
                      |                             |
              +-------v-------+           +---------v---------+
              |               |           |                   |
              | Serialization |           | Load Balance      |
              |               |           |                   |
              +---------------+           +-------------------+
```

## 开发计划

### 阶段一：基础框架搭建

1. 创建项目结构
2. 定义核心接口和模型
3. 实现基本的网络传输模块
4. 实现简单的序列化/反序列化功能

### 阶段二：核心功能实现

1. 实现服务提供者和消费者
2. 实现动态代理机制
3. 实现简单的服务注册与发现
4. 集成基本的负载均衡策略

### 阶段三：高级特性

1. 增强序列化性能
2. 添加更多负载均衡策略
3. 实现服务熔断和降级
4. 添加监控和统计功能

### 阶段四：优化和测试

1. 性能优化
   - 使用更高效的序列化方式（如Protostuff、Kryo）
   - 实现连接池管理，减少连接创建开销
   - 优化网络传输参数，提高吞吐量
   - 引入压缩算法，减少网络传输数据量
2. 编写单元测试和集成测试
   - 测试各组件功能的正确性
   - 测试系统在高并发下的稳定性
   - 测试不同序列化方式的性能差异
3. 编写示例代码和文档
   - 提供完整的使用示例
   - 编写详细的开发文档

## 技术栈

- **Java 8+**：核心开发语言
- **Netty 4.1.86**：高性能网络通信框架，提供异步事件驱动的网络应用程序框架
- **注册中心**：
  - ZooKeeper 3.8.1：分布式协调服务，用于服务注册与发现
  - Curator 5.4.0：ZooKeeper客户端框架，简化ZooKeeper操作
  - Nacos 2.2.0：阿里巴巴开源的动态服务发现、配置和服务管理平台
- **序列化器**：
  - Protostuff 1.8.0：轻量级、高性能的二进制序列化库
  - Kryo 5.4.0：快速、高效的Java对象图序列化库
  - FST 3.0.3：更高效的Java序列化库，比JDK序列化快很多
- **日志框架**：
  - SLF4J 2.0.7：简单日志门面，用于多种日志框架的集成
  - Logback 1.4.7：作为SLF4J的实现，提供高效的日志记录
- **测试框架**：
  - JUnit 5.9.2：单元测试框架

## 项目当前状态

### 已实现组件

1. **核心接口定义**：已完成所有核心组件的接口设计，包括服务注册、负载均衡、序列化、服务治理等

2. **序列化实现**：提供多种高性能序列化方式
   - JDK原生序列化：简单易用，兼容性好
   - Protostuff序列化：性能极高，支持循环引用，内置对象池优化
   - Kryo序列化：高性能，压缩率高
   - FST序列化：快速序列化库，对JDK类优化更好

3. **服务注册与发现**：支持多种注册中心
   - 内存注册中心：用于单机测试环境
   - ZooKeeper注册中心：具有服务发现、自动容错等特性
   - Nacos注册中心：支持更大规模服务的注册与发现，并支持更多元数据

4. **负载均衡**：实现多种负载均衡策略
   - 随机负载均衡：随机选择服务实例
   - 轮询负载均衡：顺序轮流选择服务实例
   - 加权轮询负载均衡：根据服务权重进行负载分配
   - 最少连接负载均衡：选择当前连接数最少的服务实例
   - 一致性哈希负载均衡：保证相同请求路由到相同服务实例，最大化缓存命中

5. **网络传输**：基于Netty的高性能通信
   - 异步非阻塞网络通信
   - 多路复用连接支持
   - 自动心跳检测
   - 支持请求超时控制

6. **动态代理**：透明的远程调用
   - 基于JDK动态代理的客户端代理实现
   - 支持同步和异步调用方式
   - 异步调用基于CompletableFuture实现

7. **服务治理**：完善的服务治理组件
   - 令牌桶限流器：支持平滑限流和突发流量处理，要求指定限流器名称、每秒请求数和桶容量
   - 滑动窗口熔断器：自动检测服务健康状态，防止服务调用雪崩
   - 重试机制：支持多种重试策略，提高系统异常容错能力

8. **指标计算**：内置统计和监控功能
   - 服务调用次数和成功率统计
   - 响应时间监控
   - 限流器和熔断器的状态监控

### 待改进项

1. **性能优化**：
   - 实现更精细的内存管理和缓存策略
   - 增强序列化器的性能基准测试和对比
   - 引入压缩算法，进一步减少网络传输数据量

2. **连接管理增强**：
   - 实现更智能的连接池管理，支持自适应调整池大小
   - 增强连接活跃度检测与自动重连机制
   - 添加连接状态监控和统计

3. **服务治理增强**：
   - 实现更完善的服务降级机制，支持多种降级策略
   - 扩展限流器实现，增加分布式限流等能力
   - 引入隔离层模式，支持服务集群分组隔离

4. **监控与可视化**：
   - 引入更完善的监控指标体系，涵盖请求、连接、资源等维度
   - 开发简单的Web标准接口，方便对接第三方监控平台
   - 集成Prometheus、Grafana等监控工具

5. **阴影测试和混沌工程**：
   - 增加对生产环境的隐藏测试支持
   - 实现混沌工程管理器，用于编排混沌测试场景
   - 添加服务流量器工具

6. **安全增强**：
   - 添加身份认证和授权机制
   - 支持传输层加密
   - 增强参数校验和防止恶意调用

7. **文档与示例完善**：
   - 开发更多示例代码，展示框架各种功能
   - 完善API文档和使用指南
   - 添加基准测试报告和性能报告

> **注意事项：** 使用 TokenBucketRateLimiter 时必须提供三个参数：(String name, double permitsPerSecond, double capacity)，其中name表示限流器名称，permitsPerSecond表示每秒允许的请求数，capacity表示令牌桶容量。如果只需要指定名称和每秒请求数，可以使用 TokenBucketRateLimiter.create(name, permitsPerSecond) 工厂方法。

## 服务治理功能

### 限流器

Cyber-RPC 框架提供了基于令牌桶算法的限流器，可以平滑地限制请求速率，并允许一定程度的突发流量。

```java
// 创建限流器（需要提供名称、每秒允许的请求数和令牌桶容量）
TokenBucketRateLimiter limiter = new TokenBucketRateLimiter("serviceName", 100.0, 10.0);

// 或者使用工厂方法创建（桶容量默认等于每秒请求数）
TokenBucketRateLimiter limiter = TokenBucketRateLimiter.create("serviceName", 100.0);

// 尝试获取许可
if (limiter.tryAcquire()) {
    // 请求通过，处理业务逻辑
} else {
    // 请求被限流，进行熔断或降级处理
}
```

### 熔断器

Cyber-RPC 框架实现了基于滑动窗口的断路器模式，当服务调用失败率达到阈值时自动熔断，防止级联故障：

```java
// 获取熔断器
CircuitBreaker breaker = governanceManager.getCircuitBreaker("serviceName");

// 尝试执行受保护的操作
if (breaker.allowRequest()) {
    try {
        // 执行远程调用
        Object result = doSomething();
        // 记录成功
        breaker.recordSuccess();
        return result;
    } catch (Exception e) {
        // 记录失败
        breaker.recordFailure(e);
        throw e;
    }
} else {
    // 熔断器已打开，执行降级逻辑
    return fallback();
}
```

## 如何使用

### 服务提供者示例

```java
// 定义服务接口
public interface HelloService {
    String sayHello(String name);
}

// 实现服务接口
public class HelloServiceImpl implements HelloService {
    @Override
    public String sayHello(String name) {
        return "Hello, " + name + "!";
    }
}

// 启动服务提供者
public class HelloServiceProvider {
    public static void main(String[] args) {
        // 创建服务注册中心（可选择ZooKeeper或内存实现）
        ServiceRegistry registry = new ZookeeperServiceRegistry("127.0.0.1:2181");
        // ServiceRegistry registry = new MemoryServiceRegistry();
        
        // 创建RPC服务器
        NettyServer server = new NettyServer("127.0.0.1", 9000, registry);
        
        // 注册服务
        HelloService helloService = new HelloServiceImpl();
        server.registerService(HelloService.class.getName(), helloService);
        
        // 启动服务器
        server.start();
    }
}
```

### 服务消费者示例

```java
// 启动服务消费者
public class HelloServiceConsumer {
    public static void main(String[] args) {
        // 创建服务注册中心（可选择ZooKeeper或内存实现）
        ServiceRegistry registry = new ZookeeperServiceRegistry("127.0.0.1:2181");
        // ServiceRegistry registry = new MemoryServiceRegistry();
        
        // 创建RPC客户端传输层
        RpcTransport transport = new NettyTransport(registry);
        
        // 创建RPC客户端代理
        RpcClientProxy proxy = new RpcClientProxy(transport);
        
        // 获取远程服务代理对象
        HelloService helloService = proxy.getProxy(HelloService.class);
        
        // 调用远程服务
        String result = helloService.sayHello("World");
        System.out.println("调用结果: " + result);
        
        // 关闭传输层
        transport.close();
    }
}
```

## 贡献指南

欢迎贡献代码或提出建议，请遵循以下步骤：

1. Fork 本仓库
2. 创建您的特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交您的更改 (`git commit -m 'Add some amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 打开一个 Pull Request

## 许可证

本项目采用 MIT 许可证 - 详情请参阅 [LICENSE](LICENSE) 文件