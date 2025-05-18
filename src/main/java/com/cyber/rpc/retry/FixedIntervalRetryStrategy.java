package com.cyber.rpc.retry;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 固定间隔重试策略
 * 每次重试之间的间隔时间固定
 */
public class FixedIntervalRetryStrategy implements RetryStrategy {
    
    // 最大重试次数
    private final int maxRetries;
    
    // 重试间隔时间（毫秒）
    private final long intervalMs;
    
    // 可重试的异常类型
    private final Set<Class<? extends Throwable>> retryableExceptions;
    
    /**
     * 创建固定间隔重试策略
     *
     * @param maxRetries 最大重试次数
     * @param intervalMs 重试间隔（毫秒）
     * @param retryableExceptions 可重试的异常类型
     */
    public FixedIntervalRetryStrategy(int maxRetries, long intervalMs, 
                                    Class<? extends Throwable>... retryableExceptions) {
        this.maxRetries = maxRetries;
        this.intervalMs = intervalMs;
        this.retryableExceptions = new HashSet<>(Arrays.asList(retryableExceptions));
    }
    
    /**
     * 创建固定间隔重试策略，默认可重试所有IOException和TimeoutException
     *
     * @param maxRetries 最大重试次数
     * @param intervalMs 重试间隔（毫秒）
     */
    @SuppressWarnings("unchecked")
    public FixedIntervalRetryStrategy(int maxRetries, long intervalMs) {
        this.maxRetries = maxRetries;
        this.intervalMs = intervalMs;
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
        return intervalMs;
    }
    
    @Override
    public int getMaxRetries() {
        return maxRetries;
    }
    
    @Override
    public RetryStrategyType getType() {
        return RetryStrategyType.FIXED_INTERVAL;
    }
}
