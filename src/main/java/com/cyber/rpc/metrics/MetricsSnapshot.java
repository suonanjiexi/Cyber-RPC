package com.cyber.rpc.metrics;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 指标快照
 * 保存RPC调用相关的所有统计指标
 */
public class MetricsSnapshot {
    
    // 快照创建时间
    private final long timestamp;
    
    // 总请求数
    private final long totalRequests;
    
    // 总错误数
    private final long totalErrors;
    
    // 总延迟（毫秒）
    private final long totalLatency;
    
    // 按服务统计的指标
    private final Map<String, ServiceMetrics> serviceMetrics;
    
    public MetricsSnapshot(long timestamp, long totalRequests, long totalErrors, long totalLatency,
                         Map<String, ServiceMetrics> serviceMetrics) {
        this.timestamp = timestamp;
        this.totalRequests = totalRequests;
        this.totalErrors = totalErrors;
        this.totalLatency = totalLatency;
        this.serviceMetrics = Collections.unmodifiableMap(
                new ConcurrentHashMap<>(serviceMetrics));
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public long getTotalRequests() {
        return totalRequests;
    }
    
    public long getTotalErrors() {
        return totalErrors;
    }
    
    public long getTotalLatency() {
        return totalLatency;
    }
    
    public double getAverageLatency() {
        return totalRequests > 0 ? (double) totalLatency / totalRequests : 0;
    }
    
    public double getErrorRate() {
        return totalRequests > 0 ? (double) totalErrors / totalRequests : 0;
    }
    
    public Map<String, ServiceMetrics> getServiceMetrics() {
        return serviceMetrics;
    }
    
    /**
     * 服务指标
     * 包含单个服务的调用统计
     */
    public static class ServiceMetrics {
        // 服务名称
        private final String serviceName;
        
        // 请求数
        private final long requests;
        
        // 错误数
        private final long errors;
        
        // 总延迟（毫秒）
        private final long totalLatency;
        
        // 最大延迟（毫秒）
        private final long maxLatency;
        
        // 最小延迟（毫秒）
        private final long minLatency;
        
        // 按方法统计的指标
        private final Map<String, MethodMetrics> methodMetrics;
        
        public ServiceMetrics(String serviceName, long requests, long errors,
                            long totalLatency, long maxLatency, long minLatency,
                            Map<String, MethodMetrics> methodMetrics) {
            this.serviceName = serviceName;
            this.requests = requests;
            this.errors = errors;
            this.totalLatency = totalLatency;
            this.maxLatency = maxLatency;
            this.minLatency = minLatency;
            this.methodMetrics = Collections.unmodifiableMap(
                    new ConcurrentHashMap<>(methodMetrics));
        }
        
        public String getServiceName() {
            return serviceName;
        }
        
        public long getRequests() {
            return requests;
        }
        
        public long getErrors() {
            return errors;
        }
        
        public long getTotalLatency() {
            return totalLatency;
        }
        
        public long getMaxLatency() {
            return maxLatency;
        }
        
        public long getMinLatency() {
            return minLatency;
        }
        
        public double getAverageLatency() {
            return requests > 0 ? (double) totalLatency / requests : 0;
        }
        
        public double getErrorRate() {
            return requests > 0 ? (double) errors / requests : 0;
        }
        
        public Map<String, MethodMetrics> getMethodMetrics() {
            return methodMetrics;
        }
    }
    
    /**
     * 方法指标
     * 包含单个方法的调用统计
     */
    public static class MethodMetrics {
        // 方法名称
        private final String methodName;
        
        // 请求数
        private final long requests;
        
        // 错误数
        private final long errors;
        
        // 总延迟（毫秒）
        private final long totalLatency;
        
        // 最大延迟（毫秒）
        private final long maxLatency;
        
        // 最小延迟（毫秒）
        private final long minLatency;
        
        // 最近错误
        private final String lastError;
        
        public MethodMetrics(String methodName, long requests, long errors,
                           long totalLatency, long maxLatency, long minLatency,
                           String lastError) {
            this.methodName = methodName;
            this.requests = requests;
            this.errors = errors;
            this.totalLatency = totalLatency;
            this.maxLatency = maxLatency;
            this.minLatency = minLatency;
            this.lastError = lastError;
        }
        
        public String getMethodName() {
            return methodName;
        }
        
        public long getRequests() {
            return requests;
        }
        
        public long getErrors() {
            return errors;
        }
        
        public long getTotalLatency() {
            return totalLatency;
        }
        
        public long getMaxLatency() {
            return maxLatency;
        }
        
        public long getMinLatency() {
            return minLatency;
        }
        
        public String getLastError() {
            return lastError;
        }
        
        public double getAverageLatency() {
            return requests > 0 ? (double) totalLatency / requests : 0;
        }
        
        public double getErrorRate() {
            return requests > 0 ? (double) errors / requests : 0;
        }
    }
}
