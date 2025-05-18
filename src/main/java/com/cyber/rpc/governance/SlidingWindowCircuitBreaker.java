package com.cyber.rpc.governance;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * 基于滑动窗口的熔断器实现
 * 
 * 使用计数滑动窗口来跟踪服务的健康状态，
 * 当失败率超过阈值时触发熔断。
 */
public class SlidingWindowCircuitBreaker implements CircuitBreaker {
    
    private static final Logger LOGGER = Logger.getLogger(SlidingWindowCircuitBreaker.class.getName());
    
    // 服务名称
    private final String name;
    
    // 熔断器当前状态
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    
    // 滑动窗口大小（样本数量）
    private final int windowSize;
    
    // 触发熔断的失败阈值百分比
    private final float failureThreshold;
    
    // 半开状态下的允许请求数
    private final int halfOpenAllowedRequests;
    
    // 从开启状态到半开状态的等待时间（毫秒）
    private final long openToHalfOpenWaitTimeMs;
    
    // 状态转换时间戳
    private final AtomicLong stateTransitionTime = new AtomicLong(System.currentTimeMillis());
    
    // 滑动窗口计数器
    private final AtomicInteger requestCounter = new AtomicInteger(0);
    private final AtomicInteger successCounter = new AtomicInteger(0);
    private final AtomicInteger failureCounter = new AtomicInteger(0);
    private final AtomicInteger rejectionCounter = new AtomicInteger(0);
    
    // 半开状态下的请求计数器
    private final AtomicInteger halfOpenRequestCount = new AtomicInteger(0);
    
    /**
     * 构造函数
     *
     * @param name 熔断器名称
     * @param windowSize 滑动窗口大小
     * @param failureThreshold 触发熔断的失败阈值（0.0-1.0）
     * @param halfOpenAllowedRequests 半开状态下允许的请求数
     * @param openToHalfOpenWaitTimeMs 从开启状态到半开状态的等待时间（毫秒）
     */
    public SlidingWindowCircuitBreaker(String name, int windowSize, float failureThreshold, 
                                    int halfOpenAllowedRequests, long openToHalfOpenWaitTimeMs) {
        this.name = name;
        this.windowSize = windowSize;
        this.failureThreshold = failureThreshold;
        this.halfOpenAllowedRequests = halfOpenAllowedRequests;
        this.openToHalfOpenWaitTimeMs = openToHalfOpenWaitTimeMs;
        
        LOGGER.info("创建熔断器: " + name + 
                   ", windowSize=" + windowSize + 
                   ", failureThreshold=" + failureThreshold + 
                   ", halfOpenAllowedRequests=" + halfOpenAllowedRequests + 
                   ", openToHalfOpenWaitTimeMs=" + openToHalfOpenWaitTimeMs);
    }
    
    /**
     * 使用默认配置创建熔断器
     *
     * @param name 熔断器名称
     * @return 熔断器实例
     */
    public static SlidingWindowCircuitBreaker createDefault(String name) {
        return new SlidingWindowCircuitBreaker(
                name, 
                100,                   // 窗口大小为100
                0.5f,                  // 50%失败率触发熔断
                10,                    // 半开状态允许10个请求
                5000                   // 5秒后从开启转为半开
        );
    }
    
    @Override
    public boolean allowRequest() {
        State currentState = state.get();
        
        switch (currentState) {
            case CLOSED:
                // 关闭状态：允许所有请求
                return true;
                
            case OPEN:
                // 开启状态：检查是否可以切换到半开状态，否则拒绝请求
                if (isTimeToTransitionToHalfOpen()) {
                    transitionToHalfOpen();
                    return checkHalfOpenAllowRequest();
                }
                rejectionCounter.incrementAndGet();
                return false;
                
            case HALF_OPEN:
                // 半开状态：限制通过的请求数量
                return checkHalfOpenAllowRequest();
                
            default:
                return false;
        }
    }
    
    /**
     * 检查是否允许半开状态下的请求通过
     */
    private boolean checkHalfOpenAllowRequest() {
        int count = halfOpenRequestCount.incrementAndGet();
        boolean allowed = count <= halfOpenAllowedRequests;
        
        if (!allowed) {
            rejectionCounter.incrementAndGet();
        }
        
        return allowed;
    }
    
    /**
     * 检查是否可以从OPEN状态转换到HALF_OPEN状态
     */
    private boolean isTimeToTransitionToHalfOpen() {
        long currentTime = System.currentTimeMillis();
        long lastTransitionTime = stateTransitionTime.get();
        
        return (currentTime - lastTransitionTime) >= openToHalfOpenWaitTimeMs;
    }
    
    @Override
    public void recordSuccess() {
        requestCounter.incrementAndGet();
        successCounter.incrementAndGet();
        
        if (state.get() == State.HALF_OPEN) {
            // 如果在半开状态下所有允许的请求都成功，则切换到关闭状态
            if (halfOpenRequestCount.get() >= halfOpenAllowedRequests && 
                failureCounter.get() == 0) {
                transitionToClosed();
            }
        }
    }
    
    @Override
    public void recordFailure() {
        requestCounter.incrementAndGet();
        failureCounter.incrementAndGet();
        
        if (state.get() == State.HALF_OPEN) {
            // 半开状态下如果有失败，立即切换回开启状态
            transitionToOpen();
            return;
        }
        
        // 在关闭状态下检查是否需要触发熔断
        if (state.get() == State.CLOSED) {
            checkIfCircuitShouldTrip();
        }
    }
    
    /**
     * 检查是否需要触发熔断
     */
    private void checkIfCircuitShouldTrip() {
        int totalRequests = requestCounter.get();
        
        // 只有当请求数达到窗口大小时才进行熔断检查
        if (totalRequests >= windowSize) {
            float failureRate = (float) failureCounter.get() / totalRequests;
            
            if (failureRate >= failureThreshold) {
                LOGGER.warning("熔断器触发: " + name + 
                              ", 失败率=" + failureRate + 
                              ", 阈值=" + failureThreshold);
                transitionToOpen();
            }
            
            // 重置计数器以实现滑动窗口效果
            if (totalRequests >= windowSize * 2) {
                resetCounters();
            }
        }
    }
    
    /**
     * 重置计数器
     */
    private void resetCounters() {
        // 保留一半的数据，实现滑动效果
        int requests = requestCounter.get() / 2;
        int successes = successCounter.get() / 2;
        int failures = failureCounter.get() / 2;
        
        requestCounter.set(requests);
        successCounter.set(successes);
        failureCounter.set(failures);
        
        LOGGER.fine("重置计数器: " + name + 
                   ", requests=" + requests + 
                   ", successes=" + successes + 
                   ", failures=" + failures);
    }
    
    /**
     * 转换到开启状态
     */
    private void transitionToOpen() {
        if (state.compareAndSet(State.CLOSED, State.OPEN) || 
            state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
            stateTransitionTime.set(System.currentTimeMillis());
            halfOpenRequestCount.set(0);
            LOGGER.info("熔断器状态变更: " + name + " -> OPEN");
        }
    }
    
    /**
     * 转换到半开状态
     */
    private void transitionToHalfOpen() {
        if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
            stateTransitionTime.set(System.currentTimeMillis());
            halfOpenRequestCount.set(0);
            LOGGER.info("熔断器状态变更: " + name + " -> HALF_OPEN");
        }
    }
    
    /**
     * 转换到关闭状态
     */
    private void transitionToClosed() {
        if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
            stateTransitionTime.set(System.currentTimeMillis());
            resetCounters();
            LOGGER.info("熔断器状态变更: " + name + " -> CLOSED");
        }
    }
    
    @Override
    public State getState() {
        // 如果处于开启状态，检查是否应该转为半开
        if (state.get() == State.OPEN && isTimeToTransitionToHalfOpen()) {
            transitionToHalfOpen();
        }
        
        return state.get();
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public CircuitBreakerMetrics getMetrics() {
        int totalRequests = requestCounter.get();
        int successfulRequests = successCounter.get();
        int failedRequests = failureCounter.get();
        int rejectedRequests = rejectionCounter.get();
        
        float failureRate = totalRequests > 0 
                ? (float) failedRequests / totalRequests 
                : 0.0f;
                
        return new CircuitBreakerMetrics(
                totalRequests,
                successfulRequests,
                failedRequests,
                rejectedRequests,
                failureRate,
                stateTransitionTime.get()
        );
    }
}
