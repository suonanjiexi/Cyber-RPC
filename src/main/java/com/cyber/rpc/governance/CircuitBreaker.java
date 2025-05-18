package com.cyber.rpc.governance;

/**
 * 熔断器接口
 * 用于防止系统级联失败
 */
public interface CircuitBreaker {
    
    /**
     * 熔断器状态枚举
     */
    enum State {
        /**
         * 关闭状态：允许请求通过
         */
        CLOSED,
        
        /**
         * 开启状态：拒绝所有请求
         */
        OPEN,
        
        /**
         * 半开状态：允许部分请求通过以探测服务是否恢复
         */
        HALF_OPEN
    }
    
    /**
     * 检查当前请求是否被允许通过
     *
     * @return 如果请求被允许通过则返回true，否则返回false
     */
    boolean allowRequest();
    
    /**
     * 记录请求成功
     */
    void recordSuccess();
    
    /**
     * 记录请求失败
     */
    void recordFailure();
    
    /**
     * 获取熔断器当前状态
     *
     * @return 熔断器状态
     */
    State getState();
    
    /**
     * 获取熔断器名称
     *
     * @return 熔断器名称
     */
    String getName();
    
    /**
     * 获取熔断器统计信息
     *
     * @return 熔断器统计信息
     */
    CircuitBreakerMetrics getMetrics();
}
