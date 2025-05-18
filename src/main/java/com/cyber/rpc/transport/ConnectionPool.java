package com.cyber.rpc.transport;

import io.netty.channel.Channel;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 连接池接口
 * 提供连接获取、返回、监控等功能
 */
public interface ConnectionPool {
    
    /**
     * 获取与指定地址的连接
     * 
     * @param address 服务器地址
     * @return 连接Future
     */
    CompletableFuture<Channel> getConnection(InetSocketAddress address);
    
    /**
     * 将连接返回池中
     * 
     * @param address 服务器地址
     * @param channel 连接
     */
    void returnConnection(InetSocketAddress address, Channel channel);
    
    /**
     * 检查连接是否健康
     * 
     * @param address 服务器地址
     * @param channel 连接
     * @return 是否健康
     */
    boolean isHealthy(InetSocketAddress address, Channel channel);
    
    /**
     * 关闭指定地址的所有连接
     * 
     * @param address 服务器地址
     */
    void closeConnection(InetSocketAddress address);
    
    /**
     * 关闭所有连接
     */
    void closeAll();
    
    /**
     * 获取连接池状态
     * 
     * @return 连接池状态
     */
    PoolStatus getStatus();
    
    /**
     * 获取详细的连接池状态（按地址分组）
     * 
     * @return 连接池状态Map，key为地址，value为该地址的连接池状态
     */
    Map<InetSocketAddress, PoolStatus> getDetailedStatus();
    
    /**
     * 调整连接池大小
     * 
     * @param address 服务器地址
     * @param newSize 新的连接池大小
     */
    void resizePool(InetSocketAddress address, int newSize);
    
    /**
     * 启用自适应连接池大小调整
     * 
     * @param enabled 是否启用
     * @param minSize 最小连接数
     * @param maxSize 最大连接数
     * @param loadThresholdLow 低负载阈值，低于此值时减少连接
     * @param loadThresholdHigh 高负载阈值，高于此值时增加连接
     */
    void enableAdaptivePoolSizing(boolean enabled, int minSize, int maxSize, 
                                 double loadThresholdLow, double loadThresholdHigh);
    
    /**
     * 获取连接状态统计
     * 包括延迟、错误率等指标
     * 
     * @param address 服务器地址
     * @return 连接状态
     */
    ConnectionStats getConnectionStats(InetSocketAddress address);
    
    /**
     * 连接状态统计类
     */
    class ConnectionStats {
        private final double avgResponseTime; // 平均响应时间(ms)
        private final double errorRate;       // 错误率
        private final long requestCount;      // 请求总数
        private final long successCount;      // 成功请求数
        private final long failureCount;      // 失败请求数
        private final long timeoutCount;      // 超时请求数
        private final long lastActiveTime;    // 最后活跃时间
        private final int consecutiveFailures; // 连续失败次数
        
        public ConnectionStats(double avgResponseTime, double errorRate, 
                              long requestCount, long successCount, 
                              long failureCount, long timeoutCount,
                              long lastActiveTime, int consecutiveFailures) {
            this.avgResponseTime = avgResponseTime;
            this.errorRate = errorRate;
            this.requestCount = requestCount;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.timeoutCount = timeoutCount;
            this.lastActiveTime = lastActiveTime;
            this.consecutiveFailures = consecutiveFailures;
        }
        
        // Getters
        public double getAvgResponseTime() {
            return avgResponseTime;
        }
        
        public double getErrorRate() {
            return errorRate;
        }
        
        public long getRequestCount() {
            return requestCount;
        }
        
        public long getSuccessCount() {
            return successCount;
        }
        
        public long getFailureCount() {
            return failureCount;
        }
        
        public long getTimeoutCount() {
            return timeoutCount;
        }
        
        public long getLastActiveTime() {
            return lastActiveTime;
        }
        
        public int getConsecutiveFailures() {
            return consecutiveFailures;
        }
    }
    
    /**
     * 连接池状态记录类
     */
    class PoolStatus {
        private final int totalConnections;
        private final int activeConnections;
        private final int idleConnections;
        private final int pendingRequests;
        private final long creationTime;
        private final long lastResizeTime;
        private final int maxPoolSize;
        private final long totalRequestCount;
        private final double averageRequestTime;
        
        public PoolStatus(int totalConnections, int activeConnections, int idleConnections,
                         int pendingRequests, long creationTime, long lastResizeTime,
                         int maxPoolSize, long totalRequestCount, double averageRequestTime) {
            this.totalConnections = totalConnections;
            this.activeConnections = activeConnections;
            this.idleConnections = idleConnections;
            this.pendingRequests = pendingRequests;
            this.creationTime = creationTime;
            this.lastResizeTime = lastResizeTime;
            this.maxPoolSize = maxPoolSize;
            this.totalRequestCount = totalRequestCount;
            this.averageRequestTime = averageRequestTime;
        }
        
        // 获取活跃连接占比
        public double getActiveRatio() {
            return totalConnections > 0 ? (double) activeConnections / totalConnections : 0;
        }
        
        // Getters
        public int getTotalConnections() {
            return totalConnections;
        }
        
        public int getActiveConnections() {
            return activeConnections;
        }
        
        public int getIdleConnections() {
            return idleConnections;
        }
        
        public int getPendingRequests() {
            return pendingRequests;
        }
        
        public long getCreationTime() {
            return creationTime;
        }
        
        public long getLastResizeTime() {
            return lastResizeTime;
        }
        
        public int getMaxPoolSize() {
            return maxPoolSize;
        }
        
        public long getTotalRequestCount() {
            return totalRequestCount;
        }
        
        public double getAverageRequestTime() {
            return averageRequestTime;
        }
        
        @Override
        public String toString() {
            return "PoolStatus{" +
                    "totalConnections=" + totalConnections +
                    ", activeConnections=" + activeConnections +
                    ", idleConnections=" + idleConnections +
                    ", pendingRequests=" + pendingRequests +
                    ", creationTime=" + creationTime +
                    ", lastResizeTime=" + lastResizeTime +
                    ", maxPoolSize=" + maxPoolSize +
                    ", totalRequestCount=" + totalRequestCount +
                    ", averageRequestTime=" + averageRequestTime +
                    '}';
        }
    }
}
