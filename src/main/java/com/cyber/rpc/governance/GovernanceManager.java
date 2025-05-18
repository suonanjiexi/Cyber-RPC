package com.cyber.rpc.governance;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 服务治理管理器
 * 统一管理熔断器和限流器
 */
public class GovernanceManager {
    
    private static final Logger LOGGER = Logger.getLogger(GovernanceManager.class.getName());
    
    // 单例实例
    private static final GovernanceManager INSTANCE = new GovernanceManager();
    
    // 熔断器缓存，按服务名管理
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    
    // 限流器缓存，按服务名管理
    private final Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();
    
    /**
     * 私有构造函数，防止外部创建实例
     */
    private GovernanceManager() {
        LOGGER.info("服务治理管理器初始化");
    }
    
    /**
     * 获取单例实例
     * 
     * @return 管理器实例
     */
    public static GovernanceManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * 获取或创建指定服务的熔断器
     * 
     * @param serviceName 服务名称
     * @return 熔断器实例
     */
    public CircuitBreaker getCircuitBreaker(String serviceName) {
        return circuitBreakers.computeIfAbsent(serviceName, 
                name -> SlidingWindowCircuitBreaker.createDefault(name));
    }
    
    /**
     * 注册自定义熔断器
     * 
     * @param serviceName 服务名称
     * @param circuitBreaker 熔断器实例
     */
    public void registerCircuitBreaker(String serviceName, CircuitBreaker circuitBreaker) {
        circuitBreakers.put(serviceName, circuitBreaker);
        LOGGER.info("注册熔断器: " + serviceName + " -> " + circuitBreaker.getClass().getSimpleName());
    }
    
    /**
     * 获取或创建指定服务的限流器
     * 
     * @param serviceName 服务名称
     * @param permitsPerSecond 每秒允许的请求数，仅在新建限流器时使用
     * @return 限流器实例
     */
    public RateLimiter getRateLimiter(String serviceName, double permitsPerSecond) {
        return rateLimiters.computeIfAbsent(serviceName, 
                name -> TokenBucketRateLimiter.create(name, permitsPerSecond));
    }
    
    /**
     * 注册自定义限流器
     * 
     * @param serviceName 服务名称
     * @param rateLimiter 限流器实例
     */
    public void registerRateLimiter(String serviceName, RateLimiter rateLimiter) {
        rateLimiters.put(serviceName, rateLimiter);
        LOGGER.info("注册限流器: " + serviceName + " -> " + rateLimiter.getClass().getSimpleName());
    }
    
    /**
     * 设置服务限流速率
     * 
     * @param serviceName 服务名称
     * @param permitsPerSecond 每秒允许的请求数
     */
    public void setRateLimit(String serviceName, double permitsPerSecond) {
        RateLimiter limiter = getRateLimiter(serviceName, permitsPerSecond);
        limiter.setRate(permitsPerSecond);
    }
    
    /**
     * 检查是否允许调用指定服务
     * 
     * @param serviceName 服务名称
     * @return 是否允许调用
     */
    public boolean allowRequest(String serviceName) {
        // 先检查熔断器
        CircuitBreaker circuitBreaker = getCircuitBreaker(serviceName);
        if (!circuitBreaker.allowRequest()) {
            LOGGER.warning("服务 " + serviceName + " 被熔断拒绝");
            return false;
        }
        
        // 再检查限流器
        RateLimiter rateLimiter = getRateLimiter(serviceName, 1000); // 默认1000 QPS
        boolean allowed = rateLimiter.tryAcquire();
        if (!allowed) {
            LOGGER.warning("服务 " + serviceName + " 被限流拒绝");
        }
        
        return allowed;
    }
    
    /**
     * 记录服务调用成功
     * 
     * @param serviceName 服务名称
     */
    public void recordSuccess(String serviceName) {
        CircuitBreaker circuitBreaker = getCircuitBreaker(serviceName);
        circuitBreaker.recordSuccess();
    }
    
    /**
     * 记录服务调用失败
     * 
     * @param serviceName 服务名称
     */
    public void recordFailure(String serviceName) {
        CircuitBreaker circuitBreaker = getCircuitBreaker(serviceName);
        circuitBreaker.recordFailure();
    }
    
    /**
     * 获取服务治理指标
     * 
     * @param serviceName 服务名称
     * @return 治理指标对象
     */
    public GovernanceMetrics getMetrics(String serviceName) {
        CircuitBreaker circuitBreaker = getCircuitBreaker(serviceName);
        RateLimiter rateLimiter = getRateLimiter(serviceName, 1000); // 默认1000 QPS
        
        return new GovernanceMetrics(
                serviceName,
                circuitBreaker.getState(),
                circuitBreaker.getMetrics(),
                rateLimiter.getMetrics()
        );
    }
    
    /**
     * 复合治理指标
     * 包含熔断和限流的综合指标
     */
    public static class GovernanceMetrics {
        private final String serviceName;
        private final CircuitBreaker.State circuitBreakerState;
        private final CircuitBreakerMetrics circuitBreakerMetrics;
        private final RateLimiterMetrics rateLimiterMetrics;
        
        public GovernanceMetrics(String serviceName, 
                               CircuitBreaker.State circuitBreakerState,
                               CircuitBreakerMetrics circuitBreakerMetrics,
                               RateLimiterMetrics rateLimiterMetrics) {
            this.serviceName = serviceName;
            this.circuitBreakerState = circuitBreakerState;
            this.circuitBreakerMetrics = circuitBreakerMetrics;
            this.rateLimiterMetrics = rateLimiterMetrics;
        }
        
        public String getServiceName() {
            return serviceName;
        }
        
        public CircuitBreaker.State getCircuitBreakerState() {
            return circuitBreakerState;
        }
        
        public CircuitBreakerMetrics getCircuitBreakerMetrics() {
            return circuitBreakerMetrics;
        }
        
        public RateLimiterMetrics getRateLimiterMetrics() {
            return rateLimiterMetrics;
        }
        
        @Override
        public String toString() {
            return "GovernanceMetrics{" +
                   "serviceName='" + serviceName + '\'' +
                   ", circuitBreakerState=" + circuitBreakerState +
                   ", circuitBreakerMetrics=" + circuitBreakerMetrics +
                   ", rateLimiterMetrics=" + rateLimiterMetrics +
                   '}';
        }
    }
}
