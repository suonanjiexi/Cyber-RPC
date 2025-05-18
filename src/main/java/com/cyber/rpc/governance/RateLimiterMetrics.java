package com.cyber.rpc.governance;

/**
 * 限流器统计指标
 * 包含限流器的统计数据
 */
public class RateLimiterMetrics {
    
    // 总请求数
    private final long totalRequests;
    
    // 成功通过的请求数
    private final long passedRequests;
    
    // 被限流的请求数
    private final long limitedRequests;
    
    // 当前限流速率（每秒请求数）
    private final double currentRate;
    
    // 当前可用令牌数
    private final double availableTokens;
    
    public RateLimiterMetrics(long totalRequests, long passedRequests,
                            long limitedRequests, double currentRate,
                            double availableTokens) {
        this.totalRequests = totalRequests;
        this.passedRequests = passedRequests;
        this.limitedRequests = limitedRequests;
        this.currentRate = currentRate;
        this.availableTokens = availableTokens;
    }
    
    public long getTotalRequests() {
        return totalRequests;
    }
    
    public long getPassedRequests() {
        return passedRequests;
    }
    
    public long getLimitedRequests() {
        return limitedRequests;
    }
    
    public double getPassRate() {
        return totalRequests == 0 ? 1.0 : (double) passedRequests / totalRequests;
    }
    
    public double getCurrentRate() {
        return currentRate;
    }
    
    public double getAvailableTokens() {
        return availableTokens;
    }
    
    @Override
    public String toString() {
        return "RateLimiterMetrics{" +
                "totalRequests=" + totalRequests +
                ", passedRequests=" + passedRequests +
                ", limitedRequests=" + limitedRequests +
                ", passRate=" + getPassRate() +
                ", currentRate=" + currentRate +
                ", availableTokens=" + availableTokens +
                '}';
    }
}
