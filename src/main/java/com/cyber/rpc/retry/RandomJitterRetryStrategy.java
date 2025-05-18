package com.cyber.rpc.retry;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * 随机抖动重试策略
 * 在指数退避的基础上增加随机抖动，可以更有效地避免重试风暴
 */
public class RandomJitterRetryStrategy implements RetryStrategy {
    
    // 最大重试次数
    private final int maxRetries;
    
    // 初始等待时间（毫秒）
    private final long initialWaitMs;
    
    // 最大等待时间（毫秒）
    private final long maxWaitMs;
    
    // 随机抖动因子（0-1），表示在计算出的等待时间上下浮动的百分比
    private final double jitterFactor;
    
    // 可重试的异常类型
    private final Set<Class<? extends Throwable>> retryableExceptions;
    
    // 随机数生成器
    private final Random random = new Random();
    
    /**
     * 创建随机抖动重试策略
     *
     * @param maxRetries 最大重试次数
     * @param initialWaitMs 初始等待时间（毫秒）
     * @param maxWaitMs 最大等待时间（毫秒）
     * @param jitterFactor 随机抖动因子（0-1）
     * @param retryableExceptions 可重试的异常类型
     */
    public RandomJitterRetryStrategy(int maxRetries, long initialWaitMs, long maxWaitMs, 
                                   double jitterFactor, Class<? extends Throwable>... retryableExceptions) {
        this.maxRetries = maxRetries;
        this.initialWaitMs = initialWaitMs;
        this.maxWaitMs = maxWaitMs;
        this.jitterFactor = Math.max(0, Math.min(1, jitterFactor)); // 确保在0-1范围内
        this.retryableExceptions = new HashSet<>(Arrays.asList(retryableExceptions));
    }
    
    /**
     * 创建随机抖动重试策略，默认可重试所有IOException和TimeoutException
     *
     * @param maxRetries 最大重试次数
     * @param initialWaitMs 初始等待时间（毫秒）
     * @param maxWaitMs 最大等待时间（毫秒）
     * @param jitterFactor 随机抖动因子（0-1）
     */
    @SuppressWarnings("unchecked")
    public RandomJitterRetryStrategy(int maxRetries, long initialWaitMs, long maxWaitMs, double jitterFactor) {
        this.maxRetries = maxRetries;
        this.initialWaitMs = initialWaitMs;
        this.maxWaitMs = maxWaitMs;
        this.jitterFactor = Math.max(0, Math.min(1, jitterFactor)); // 确保在0-1范围内
        this.retryableExceptions = new HashSet<>();
        
        try {
            this.retryableExceptions.add(
                (Class<? extends Throwable>) Class.forName("java.io.IOException"));
            this.retryableExceptions.add(
                (Class<? extends Throwable>) Class.forName("java.util.concurrent.TimeoutException"));
        } catch (ClassNotFoundException e) {
            // 忽略，不应该发生
        }
    }
    
    @Override
    public boolean shouldRetry(int retryCount, Throwable throwable) {
        // 检查重试次数是否超过最大值
        if (retryCount >= maxRetries) {
            return false;
        }
        
        // 如果没有指定可重试异常，则所有异常都可重试
        if (retryableExceptions.isEmpty()) {
            return true;
        }
        
        // 检查异常类型是否可重试
        for (Class<? extends Throwable> exceptionClass : retryableExceptions) {
            if (exceptionClass.isInstance(throwable)) {
                return true;
            }
        }
        
        return false;
    }
    
    @Override
    public long getWaitTimeMs(int retryCount) {
        // 计算指数退避等待时间: initialWaitMs * 2^retryCount
        long baseWaitTime = initialWaitMs * (1L << retryCount);
        
        // 不超过最大等待时间
        baseWaitTime = Math.min(baseWaitTime, maxWaitMs);
        
        // 添加随机抖动
        if (jitterFactor > 0) {
            // 计算抖动范围
            long jitterRange = (long) (baseWaitTime * jitterFactor);
            
            // 在 [-jitterRange/2, jitterRange/2] 范围内生成随机抖动
            long jitter = (long) (jitterRange * (random.nextDouble() - 0.5));
            
            // 应用抖动，确保不为负数
            baseWaitTime = Math.max(1, baseWaitTime + jitter);
        }
        
        return baseWaitTime;
    }
    
    @Override
    public int getMaxRetries() {
        return maxRetries;
    }
    
    @Override
    public RetryStrategyType getType() {
        return RetryStrategyType.RANDOM_JITTER;
    }
}
