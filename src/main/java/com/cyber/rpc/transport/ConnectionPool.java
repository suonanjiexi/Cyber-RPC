package com.cyber.rpc.transport;

import io.netty.channel.Channel;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

/**
 * 连接池接口
 * 管理和复用与服务器的连接，提高通信效率
 */
public interface ConnectionPool {
    
    /**
     * 获取与指定地址的连接
     * 如果连接不存在或已关闭，则创建新连接
     *
     * @param address 服务器地址
     * @return 连接的CompletableFuture
     */
    CompletableFuture<Channel> getConnection(InetSocketAddress address);
    
    /**
     * 返回连接到池中
     * 这将使连接可以再次被复用，而不是关闭它
     *
     * @param address 服务器地址
     * @param channel 要返回的连接
     */
    void returnConnection(InetSocketAddress address, Channel channel);
    
    /**
     * 检查连接的健康状态
     *
     * @param address 服务器地址
     * @param channel 要检查的连接
     * @return 连接是否健康
     */
    boolean isHealthy(InetSocketAddress address, Channel channel);
    
    /**
     * 关闭与指定地址的所有连接
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
     * @return 连接池状态信息
     */
    PoolStatus getStatus();
    
    /**
     * 连接池状态
     */
    class PoolStatus {
        private int totalConnections;
        private int activeConnections;
        private int idleConnections;
        
        public PoolStatus(int totalConnections, int activeConnections, int idleConnections) {
            this.totalConnections = totalConnections;
            this.activeConnections = activeConnections;
            this.idleConnections = idleConnections;
        }
        
        public int getTotalConnections() {
            return totalConnections;
        }
        
        public int getActiveConnections() {
            return activeConnections;
        }
        
        public int getIdleConnections() {
            return idleConnections;
        }
        
        @Override
        public String toString() {
            return "PoolStatus{" +
                    "totalConnections=" + totalConnections +
                    ", activeConnections=" + activeConnections +
                    ", idleConnections=" + idleConnections +
                    '}';
        }
    }
}
