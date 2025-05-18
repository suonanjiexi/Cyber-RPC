# CyberRPC - 负载均衡和重试机制

## 1. 负载均衡策略

CyberRPC框架提供了多种负载均衡策略，以便在存在多个服务实例时能够根据不同场景的需求选择最合适的服务节点。

### 1.1 可用的负载均衡策略

| 策略名称 | 实现类 | 特点 | 适用场景 |
|---------|-------|------|---------|
| 随机 | `RandomLoadBalancer` | 随机选择一个服务实例 | 适用于各个服务器性能相近的场景 |
| 轮询 | `RoundRobinLoadBalancer` | 按顺序轮流选择服务实例 | 适用于各个服务器性能相近，希望请求均匀分布的场景 |
| 加权轮询 | `WeightedRoundRobinLoadBalancer` | 根据服务实例的权重分配请求 | 适用于服务器性能不均的场景，可以按照性能配置不同权重 |
| 一致性哈希 | `ConsistentHashLoadBalancer` | 相同参数的请求总是路由到相同的服务实例 | 适用于需要会话亲和性或本地缓存场景 |
| 最少连接 | `LeastConnectionLoadBalancer` | 选择当前连接数最少的服务实例 | 适用于请求处理时间差异较大的场景 |

### 1.2 使用负载均衡器

#### 1.2.1 通过RpcClientBuilder选择负载均衡策略

```java
RpcClient client = new RpcClientBuilder()
    .loadBalancer(LoadBalancerFactory.LoadBalancerType.CONSISTENT_HASH)
    // 其他配置...
    .build();
```

#### 1.2.2 创建自定义负载均衡器

```java
// 创建一个自定义的一致性哈希负载均衡器
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

// 在RpcClientBuilder中使用自定义负载均衡器
RpcClient client = new RpcClientBuilder()
    .loadBalancer(customLoadBalancer)
    // 其他配置...
    .build();
```

#### 1.2.3 通过LoadBalancerFactory获取负载均衡器

```java
LoadBalancer loadBalancer = LoadBalancerFactory.getInstance().getLoadBalancer(
    LoadBalancerFactory.LoadBalancerType.WEIGHTED_ROUND_ROBIN
);
```

## 2. 重试机制

CyberRPC框架提供了灵活的重试策略，可以根据不同的场景配置重试行为。

### 2.1 可用的重试策略

| 策略名称 | 实现类 | 特点 | 适用场景 |
|---------|-------|------|---------|
| 固定间隔 | `FixedIntervalRetryStrategy` | 每次重试之间的时间间隔固定 | 适用于简单场景，重试间隔固定 |
| 指数退避 | `ExponentialBackoffRetryStrategy` | 重试间隔按指数增长 | 适用于需要缓解服务压力的场景 |
| 随机抖动 | `RandomJitterRetryStrategy` | 在指数退避基础上增加随机抖动 | 适用于防止重试风暴的高可用场景 |

### 2.2 预定义的重试策略

RetryStrategyFactory预先定义了以下策略，可以通过名称直接获取：

| 策略名称 | 描述 |
|---------|------|
| `noRetry` | 不进行重试 |
| `quickRetry` | 最多3次，间隔100ms |
| `standardRetry` | 最多3次，初始500ms，指数递增，最大5s |
| `progressiveRetry` | 最多5次，初始1s，指数递增，带20%抖动，最大30s |
| `highAvailabilityRetry` | 最多10次，初始1s，指数递增，带30%抖动，最大60s |

### 2.3 使用重试机制

#### 2.3.1 通过RpcClientBuilder配置重试策略

```java
RpcClient client = new RpcClientBuilder()
    .retryStrategy("progressiveRetry") // 使用预定义的渐进重试策略
    // 其他配置...
    .build();
```

#### 2.3.2 使用RetryExecutor执行自定义重试逻辑

```java
// 获取重试策略
RetryStrategy retryStrategy = RetryStrategyFactory.getInstance().getStrategy("standardRetry");

// 创建重试执行器
RetryExecutor retryExecutor = new RetryExecutor(retryStrategy);

// 使用重试执行器包装可能失败的调用
String result = retryExecutor.execute(() -> {
    // 可能失败的代码
    return helloService.sayHello("World");
});
```

#### 2.3.3 创建自定义重试策略

```java
// 创建一个固定间隔重试策略：最多重试3次，间隔500ms
RetryStrategy fixedStrategy = new FixedIntervalRetryStrategy(3, 500);

// 创建一个指数退避重试策略：最多重试5次，初始等待1s，最大等待30s
RetryStrategy exponentialStrategy = new ExponentialBackoffRetryStrategy(5, 1000, 30000);

// 创建一个随机抖动重试策略：最多重试5次，初始等待1s，最大等待30s，抖动因子0.2
RetryStrategy jitterStrategy = new RandomJitterRetryStrategy(5, 1000, 30000, 0.2);
```

## 3. 最佳实践

### 3.1 负载均衡策略选择

- 对于无状态服务，建议使用**轮询**或**随机**负载均衡策略。
- 对于有状态服务或需要会话亲和性的场景，建议使用**一致性哈希**负载均衡策略。
- 对于性能不均的服务器集群，建议使用**加权轮询**负载均衡策略。
- 对于请求处理时间差异大的场景，建议使用**最少连接**负载均衡策略。

### 3.2 重试策略选择

- 对于简单场景，可以使用**固定间隔**重试策略。
- 对于生产环境，建议使用**随机抖动**重试策略，可以有效防止重试风暴。
- 对于对延迟敏感的场景，可以使用较短的初始等待时间和较少的重试次数。
- 对于高可用性要求的场景，可以使用较多的重试次数和更智能的退避策略。

### 3.3 适用场景

#### 适合使用重试的场景

- 网络抖动导致的临时连接问题
- 服务暂时过载导致的超时
- 分布式系统中的临时错误
- 数据库事务冲突导致的失败

#### 不适合使用重试的场景

- 业务逻辑错误（如参数验证失败）
- 权限验证失败
- 资源永久不可用
- 会导致重复操作且无法保证幂等性的场景

## 4. 示例代码

参考`com.cyber.rpc.example`包中的示例代码，了解如何使用不同的负载均衡和重试策略。
