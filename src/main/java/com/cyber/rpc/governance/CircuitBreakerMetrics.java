package com.cyber.rpc.governance;

/**
 * 熔断器统计指标
 * 包含熔断器的各项统计数据
 */
public class CircuitBreakerMetrics {
    
    // 总请求数
    private final long totalRequests;
    // 成功请求数
    private final long successfulRequests;
    // 失败请求数
    private final long failedRequests;
    // 被拒绝的请求数
    private final long rejectedRequests;
    // 失败率
    private final float failureRate;
    // 上次状态转换时间
    private final long lastStateTransitionTime;
    
    public CircuitBreakerMetrics(long totalRequests, long successfulRequests, long failedRequests, 
                               long rejectedRequests, float failureRate, long lastStateTransitionTime) {
        this.totalRequests = totalRequests;
        this.successfulRequests = successfulRequests;
        this.failedRequests = failedRequests;
        this.rejectedRequests = rejectedRequests;
        this.failureRate = failureRate;
        this.lastStateTransitionTime = lastStateTransitionTime;
    }
    
    public long getTotalRequests() {
        return totalRequests;
    }
    
    public long getSuccessfulRequests() {
        return successfulRequests;
    }
    
    public long getFailedRequests() {
        return failedRequests;
    }
    
    public long getRejectedRequests() {
        return rejectedRequests;
    }
    
    public float getFailureRate() {
        return failureRate;
    }
    
    public long getLastStateTransitionTime() {
        return lastStateTransitionTime;
    }
    
    @Override
    public String toString() {
        return "CircuitBreakerMetrics{" +
               "totalRequests=" + totalRequests +
               ", successfulRequests=" + successfulRequests +
               ", failedRequests=" + failedRequests +
               ", rejectedRequests=" + rejectedRequests +
               ", failureRate=" + failureRate +
               ", lastStateTransitionTime=" + lastStateTransitionTime +
               '}';
    }
}
