package com.cyber.rpc.retry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 重试策略工厂
 * 用于创建和管理各种重试策略
 */
public class RetryStrategyFactory {
    
    private static final Logger LOGGER = Logger.getLogger(RetryStrategyFactory.class.getName());
    
    // 单例实例
    private static final RetryStrategyFactory INSTANCE = new RetryStrategyFactory();
    
    // 预定义的重试策略
    private final Map<String, RetryStrategy> predefinedStrategies = new ConcurrentHashMap<>();
    
    /**
     * 私有构造函数
     */
    private RetryStrategyFactory() {
        // 初始化预定义的重试策略
        
        // 无重试
        predefinedStrategies.put("noRetry", new FixedIntervalRetryStrategy(0, 0));
        
        // 快速重试：最多3次，间隔100ms
        predefinedStrategies.put("quickRetry", new FixedIntervalRetryStrategy(3, 100));
        
        // 标准重试：最多3次，初始500ms，指数递增，最大5s
        predefinedStrategies.put("standardRetry", new ExponentialBackoffRetryStrategy(3, 500, 5000));
        
        // 渐进重试：最多5次，初始1s，指数递增，带20%抖动，最大30s
        predefinedStrategies.put("progressiveRetry", new RandomJitterRetryStrategy(5, 1000, 30000, 0.2));
        
        // 高可用重试：最多10次，初始1s，指数递增，带30%抖动，最大60s
        predefinedStrategies.put("highAvailabilityRetry", new RandomJitterRetryStrategy(10, 1000, 60000, 0.3));
        
        LOGGER.info("重试策略工厂初始化完成");
    }
    
    /**
     * 获取工厂实例
     * 
     * @return 重试策略工厂实例
     */
    public static RetryStrategyFactory getInstance() {
        return INSTANCE;
    }
    
    /**
     * 获取预定义的重试策略
     * 
     * @param name 策略名称
     * @return 重试策略实例，如果不存在则返回无重试策略
     */
    public RetryStrategy getStrategy(String name) {
        return predefinedStrategies.getOrDefault(name, predefinedStrategies.get("noRetry"));
    }
    
    /**
     * 注册自定义重试策略
     * 
     * @param name 策略名称
     * @param strategy 重试策略实例
     */
    public void registerStrategy(String name, RetryStrategy strategy) {
        predefinedStrategies.put(name, strategy);
        LOGGER.info("注册重试策略: " + name);
    }
    
    /**
     * 创建固定间隔重试策略
     * 
     * @param maxRetries 最大重试次数
     * @param intervalMs 重试间隔（毫秒）
     * @return 重试策略实例
     */
    public RetryStrategy createFixedIntervalStrategy(int maxRetries, long intervalMs) {
        return new FixedIntervalRetryStrategy(maxRetries, intervalMs);
    }
    
    /**
     * 创建指数退避重试策略
     * 
     * @param maxRetries 最大重试次数
     * @param initialWaitMs 初始等待时间（毫秒）
     * @param maxWaitMs 最大等待时间（毫秒）
     * @return 重试策略实例
     */
    public RetryStrategy createExponentialBackoffStrategy(int maxRetries, long initialWaitMs, long maxWaitMs) {
        return new ExponentialBackoffRetryStrategy(maxRetries, initialWaitMs, maxWaitMs);
    }
    
    /**
     * 创建随机抖动重试策略
     * 
     * @param maxRetries 最大重试次数
     * @param initialWaitMs 初始等待时间（毫秒）
     * @param maxWaitMs 最大等待时间（毫秒）
     * @param jitterFactor 随机抖动因子（0-1）
     * @return 重试策略实例
     */
    public RetryStrategy createRandomJitterStrategy(int maxRetries, long initialWaitMs, long maxWaitMs, double jitterFactor) {
        return new RandomJitterRetryStrategy(maxRetries, initialWaitMs, maxWaitMs, jitterFactor);
    }
}
