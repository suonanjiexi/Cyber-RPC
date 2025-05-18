package com.cyber.rpc.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * 默认的RPC指标实现
 * 提供内存级别的指标收集和统计功能
 */
public class DefaultRpcMetrics implements RpcMetrics {
    
    private static final Logger LOGGER = Logger.getLogger(DefaultRpcMetrics.class.getName());
    
    // 全局统计
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    private final AtomicLong totalLatency = new AtomicLong(0);
    
    // 按服务和方法统计的指标
    private final ConcurrentHashMap<String, ServiceStats> serviceStatsMap = new ConcurrentHashMap<>();
    
    @Override
    public void recordRequest(String service, String method) {
        // 增加全局请求计数
        totalRequests.incrementAndGet();
        
        // 增加服务级别的请求计数
        getOrCreateServiceStats(service).totalRequests.incrementAndGet();
        
        // 增加方法级别的请求计数
        getOrCreateMethodStats(service, method).requests.incrementAndGet();
    }
    
    @Override
    public void recordLatency(String service, String method, long latencyMs) {
        // 累加全局延迟
        totalLatency.addAndGet(latencyMs);
        
        // 更新服务级别的延迟统计
        ServiceStats serviceStats = getOrCreateServiceStats(service);
        serviceStats.totalLatency.addAndGet(latencyMs);
        updateMaxMin(serviceStats.maxLatency, serviceStats.minLatency, latencyMs);
        
        // 更新方法级别的延迟统计
        MethodStats methodStats = getOrCreateMethodStats(service, method);
        methodStats.totalLatency.addAndGet(latencyMs);
        updateMaxMin(methodStats.maxLatency, methodStats.minLatency, latencyMs);
        
        // 记录日志（如果延迟较高）
        if (latencyMs > 1000) {
            LOGGER.warning("高延迟RPC调用: " + service + "." + method + " - " + latencyMs + "ms");
        }
    }
    
    @Override
    public void recordError(String service, String method, Throwable error) {
        // 增加全局错误计数
        totalErrors.incrementAndGet();
        
        // 增加服务级别的错误计数
        getOrCreateServiceStats(service).totalErrors.incrementAndGet();
        
        // 增加方法级别的错误计数和记录最近错误
        MethodStats methodStats = getOrCreateMethodStats(service, method);
        methodStats.errors.incrementAndGet();
        methodStats.lastError = error.getClass().getName() + ": " + error.getMessage();
        
        // 记录日志
        LOGGER.warning("RPC调用错误: " + service + "." + method + " - " + 
                      error.getClass().getName() + ": " + error.getMessage());
    }
    
    @Override
    public MetricsSnapshot getSnapshot() {
        Map<String, MetricsSnapshot.ServiceMetrics> serviceMetricsMap = new ConcurrentHashMap<>();
        
        // 为每个服务创建指标
        for (Map.Entry<String, ServiceStats> entry : serviceStatsMap.entrySet()) {
            String serviceName = entry.getKey();
            ServiceStats stats = entry.getValue();
            
            Map<String, MetricsSnapshot.MethodMetrics> methodMetricsMap = new ConcurrentHashMap<>();
            
            // 为每个方法创建指标
            for (Map.Entry<String, MethodStats> methodEntry : stats.methodStatsMap.entrySet()) {
                String methodName = methodEntry.getKey();
                MethodStats methodStats = methodEntry.getValue();
                
                methodMetricsMap.put(methodName, new MetricsSnapshot.MethodMetrics(
                        methodName,
                        methodStats.requests.get(),
                        methodStats.errors.get(),
                        methodStats.totalLatency.get(),
                        methodStats.maxLatency.get(),
                        methodStats.minLatency.get(),
                        methodStats.lastError
                ));
            }
            
            serviceMetricsMap.put(serviceName, new MetricsSnapshot.ServiceMetrics(
                    serviceName,
                    stats.totalRequests.get(),
                    stats.totalErrors.get(),
                    stats.totalLatency.get(),
                    stats.maxLatency.get(),
                    stats.minLatency.get(),
                    methodMetricsMap
            ));
        }
        
        return new MetricsSnapshot(
                System.currentTimeMillis(),
                totalRequests.get(),
                totalErrors.get(),
                totalLatency.get(),
                serviceMetricsMap
        );
    }
    
    /**
     * 更新最大最小值
     */
    private void updateMaxMin(AtomicLong max, AtomicLong min, long value) {
        // 更新最大值
        long currentMax;
        do {
            currentMax = max.get();
            if (value <= currentMax) {
                break;
            }
        } while (!max.compareAndSet(currentMax, value));
        
        // 更新最小值
        long currentMin;
        do {
            currentMin = min.get();
            if (value >= currentMin && currentMin != 0) {
                break;
            }
        } while (!min.compareAndSet(currentMin, value));
    }
    
    /**
     * 获取或创建服务统计对象
     */
    private ServiceStats getOrCreateServiceStats(String service) {
        return serviceStatsMap.computeIfAbsent(service, s -> new ServiceStats());
    }
    
    /**
     * 获取或创建方法统计对象
     */
    private MethodStats getOrCreateMethodStats(String service, String method) {
        ServiceStats serviceStats = getOrCreateServiceStats(service);
        return serviceStats.methodStatsMap.computeIfAbsent(method, m -> new MethodStats());
    }
    
    /**
     * 服务级别统计
     */
    private static class ServiceStats {
        final AtomicLong totalRequests = new AtomicLong(0);
        final AtomicLong totalErrors = new AtomicLong(0);
        final AtomicLong totalLatency = new AtomicLong(0);
        final AtomicLong maxLatency = new AtomicLong(0);
        final AtomicLong minLatency = new AtomicLong(0);
        final ConcurrentHashMap<String, MethodStats> methodStatsMap = new ConcurrentHashMap<>();
    }
    
    /**
     * 方法级别统计
     */
    private static class MethodStats {
        final AtomicLong requests = new AtomicLong(0);
        final AtomicLong errors = new AtomicLong(0);
        final AtomicLong totalLatency = new AtomicLong(0);
        final AtomicLong maxLatency = new AtomicLong(0);
        final AtomicLong minLatency = new AtomicLong(0);
        volatile String lastError = null;
    }
}
