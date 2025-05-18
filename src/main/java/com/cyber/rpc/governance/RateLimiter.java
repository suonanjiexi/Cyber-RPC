package com.cyber.rpc.governance;

/**
 * 限流器接口
 * 用于控制请求速率，防止服务过载
 */
public interface RateLimiter {
    
    /**
     * 尝试获取一个许可
     *
     * @return 如果获取成功返回true，否则返回false
     */
    boolean tryAcquire();
    
    /**
     * 尝试获取指定数量的许可
     *
     * @param permits 许可数量
     * @return 如果获取成功返回true，否则返回false
     */
    boolean tryAcquire(int permits);
    
    /**
     * 设置每秒允许的请求数
     *
     * @param permitsPerSecond 每秒请求数
     */
    void setRate(double permitsPerSecond);
    
    /**
     * 获取限流器的名称
     *
     * @return 限流器名称
     */
    String getName();
    
    /**
     * 获取限流器的统计指标
     *
     * @return 限流器统计指标
     */
    RateLimiterMetrics getMetrics();
}
