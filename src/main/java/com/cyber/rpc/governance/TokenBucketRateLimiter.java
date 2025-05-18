package com.cyber.rpc.governance;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * 基于令牌桶算法的限流器
 * 平滑限制请求速率，允许一定程度的突发流量
 */
public class TokenBucketRateLimiter implements RateLimiter {
    
    private static final Logger LOGGER = Logger.getLogger(TokenBucketRateLimiter.class.getName());
    
    // 限流器名称
    private final String name;
    
    // 令牌桶最大容量
    private final double capacity;
    
    // 当前令牌数量
    private double tokens;
    
    // 上次更新令牌的时间戳（纳秒）
    private long lastRefillTimestampNanos;
    
    // 每纳秒产生的令牌数
    private volatile double refillTokensPerNano;
    
    // 请求统计
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong passedRequests = new AtomicLong(0);
    private final AtomicLong limitedRequests = new AtomicLong(0);
    
    /**
     * 构造一个令牌桶限流器
     *
     * @param name 限流器名称
     * @param permitsPerSecond 每秒允许的请求数
     * @param capacity 令牌桶容量
     */
    public TokenBucketRateLimiter(String name, double permitsPerSecond, double capacity) {
        this.name = name;
        this.capacity = capacity;
        this.tokens = capacity;
        this.lastRefillTimestampNanos = System.nanoTime();
        this.refillTokensPerNano = permitsPerSecond / 1_000_000_000.0;
        
        LOGGER.info("创建令牌桶限流器: " + name + 
                   ", 速率=" + permitsPerSecond + "/秒" + 
                   ", 桶容量=" + capacity);
    }
    
    /**
     * 创建一个默认配置的令牌桶限流器
     *
     * @param name 限流器名称
     * @param permitsPerSecond 每秒允许的请求数
     * @return 限流器实例
     */
    public static TokenBucketRateLimiter create(String name, double permitsPerSecond) {
        return new TokenBucketRateLimiter(name, permitsPerSecond, permitsPerSecond);
    }
    
    @Override
    public synchronized boolean tryAcquire() {
        return tryAcquire(1);
    }
    
    @Override
    public synchronized boolean tryAcquire(int permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("令牌数必须为正数: " + permits);
        }
        
        totalRequests.incrementAndGet();
        
        refillTokens();
        
        if (tokens < permits) {
            // 令牌不足，限流
            limitedRequests.incrementAndGet();
            return false;
        }
        
        // 消耗令牌
        tokens -= permits;
        passedRequests.incrementAndGet();
        return true;
    }
    
    /**
     * 根据时间流逝补充令牌
     */
    private void refillTokens() {
        long currentTimeNanos = System.nanoTime();
        double elapsedNanos = currentTimeNanos - lastRefillTimestampNanos;
        
        // 纳秒可能为负数（系统时钟调整），此时不更新令牌
        if (elapsedNanos <= 0) {
            return;
        }
        
        // 计算需要补充的令牌数量
        double tokensToAdd = elapsedNanos * refillTokensPerNano;
        
        // 更新令牌数，不超过桶容量
        tokens = Math.min(capacity, tokens + tokensToAdd);
        lastRefillTimestampNanos = currentTimeNanos;
    }
    
    @Override
    public synchronized void setRate(double permitsPerSecond) {
        if (permitsPerSecond <= 0) {
            throw new IllegalArgumentException("速率必须为正数: " + permitsPerSecond);
        }
        
        // 先刷新令牌，再更新速率
        refillTokens();
        this.refillTokensPerNano = permitsPerSecond / 1_000_000_000.0;
        
        LOGGER.info("更新限流器速率: " + name + ", 新速率=" + permitsPerSecond + "/秒");
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public RateLimiterMetrics getMetrics() {
        synchronized (this) {
            refillTokens();
            
            return new RateLimiterMetrics(
                    totalRequests.get(),
                    passedRequests.get(),
                    limitedRequests.get(),
                    refillTokensPerNano * 1_000_000_000.0,
                    tokens
            );
        }
    }
}
