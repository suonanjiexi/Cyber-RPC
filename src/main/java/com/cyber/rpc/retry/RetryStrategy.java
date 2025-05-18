package com.cyber.rpc.retry;

/**
 * 重试策略接口
 * 定义了重试行为的基本方法
 */
public interface RetryStrategy {
    
    /**
     * 判断是否可以进行重试
     * 
     * @param retryCount 当前已重试次数
     * @param throwable 上次失败的异常
     * @return 如果可以重试返回true，否则返回false
     */
    boolean shouldRetry(int retryCount, Throwable throwable);
    
    /**
     * 获取下次重试前的等待时间（毫秒）
     * 
     * @param retryCount 当前已重试次数
     * @return 等待时间（毫秒）
     */
    long getWaitTimeMs(int retryCount);
    
    /**
     * 获取最大重试次数
     * 
     * @return 最大重试次数
     */
    int getMaxRetries();
    
    /**
     * 获取重试策略类型
     * 
     * @return 重试策略类型
     */
    RetryStrategyType getType();
    
    /**
     * 重试策略类型
     */
    enum RetryStrategyType {
        /**
         * 固定间隔重试
         */
        FIXED_INTERVAL,
        
        /**
         * 指数退避重试
         */
        EXPONENTIAL_BACKOFF,
        
        /**
         * 随机抖动重试
         */
        RANDOM_JITTER
    }
}
