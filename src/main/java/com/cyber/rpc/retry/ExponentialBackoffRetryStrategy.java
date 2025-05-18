package com.cyber.rpc.retry;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 指数退避重试策略
 * 每次重试的等待时间以指数形式增加，可以有效防止系统雪崩
 */
public class ExponentialBackoffRetryStrategy implements RetryStrategy {
    
    // 最大重试次数
    private final int maxRetries;
    
    // 初始等待时间（毫秒）
    private final long initialWaitMs;
    
    // 最大等待时间（毫秒）
    private final long maxWaitMs;
    
    // 可重试的异常类型
    private final Set<Class<? extends Throwable>> retryableExceptions;
    
    /**
     * 创建指数退避重试策略
     *
     * @param maxRetries 最大重试次数
     * @param initialWaitMs 初始等待时间（毫秒）
     * @param maxWaitMs 最大等待时间（毫秒）
     * @param retryableExceptions 可重试的异常类型
     */
    public ExponentialBackoffRetryStrategy(int maxRetries, long initialWaitMs, long maxWaitMs,
                                         Class<? extends Throwable>... retryableExceptions) {
        this.maxRetries = maxRetries;
        this.initialWaitMs = initialWaitMs;
        this.maxWaitMs = maxWaitMs;
        this.retryableExceptions = new HashSet<>(Arrays.asList(retryableExceptions));
    }
    
    /**
     * 创建指数退避重试策略，默认可重试所有IOException和TimeoutException
     *
     * @param maxRetries 最大重试次数
     * @param initialWaitMs 初始等待时间（毫秒）
     * @param maxWaitMs 最大等待时间（毫秒）
     */
    @SuppressWarnings("unchecked")
    public ExponentialBackoffRetryStrategy(int maxRetries, long initialWaitMs, long maxWaitMs) {
        this.maxRetries = maxRetries;
        this.initialWaitMs = initialWaitMs;
        this.maxWaitMs = maxWaitMs;
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
        long waitTime = initialWaitMs * (1L << retryCount);
        
        // 不超过最大等待时间
        return Math.min(waitTime, maxWaitMs);
    }
    
    @Override
    public int getMaxRetries() {
        return maxRetries;
    }
    
    @Override
    public RetryStrategyType getType() {
        return RetryStrategyType.EXPONENTIAL_BACKOFF;
    }
}
