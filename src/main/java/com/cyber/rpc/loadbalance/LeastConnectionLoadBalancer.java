package com.cyber.rpc.loadbalance;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 最少连接负载均衡器
 * 选择当前连接数最少的服务实例，实现更均衡的负载分配
 */
public class LeastConnectionLoadBalancer implements LoadBalancer {
    
    // 每个服务实例的当前连接数
    private final Map<InetSocketAddress, AtomicInteger> connectionCounters = new ConcurrentHashMap<>();
    
    // 连接变化监听器
    private final ConnectionChangeListener connectionChangeListener;
    
    /**
     * 创建最少连接负载均衡器
     * 
     * @param connectionChangeListener 连接变化监听器
     */
    public LeastConnectionLoadBalancer(ConnectionChangeListener connectionChangeListener) {
        this.connectionChangeListener = connectionChangeListener;
    }
    
    /**
     * 创建默认最少连接负载均衡器，使用内部计数
     */
    public LeastConnectionLoadBalancer() {
        this.connectionChangeListener = null;
    }
    
    @Override
    public InetSocketAddress select(List<InetSocketAddress> addresses, String serviceName) {
        if (addresses == null || addresses.isEmpty()) {
            return null;
        }
        
        // 如果只有一个地址，直接返回
        if (addresses.size() == 1) {
            return addresses.get(0);
        }
        
        // 清理已不存在的地址的计数
        cleanupConnectionCounters(addresses);
        
        // 初始化所有地址的计数器
        for (InetSocketAddress address : addresses) {
            connectionCounters.computeIfAbsent(address, k -> new AtomicInteger(0));
        }
        
        // 找到连接数最少的地址
        InetSocketAddress selectedAddress = null;
        int minConnections = Integer.MAX_VALUE;
        
        for (InetSocketAddress address : addresses) {
            // 如果有外部监听器，使用外部连接数
            int connections = connectionChangeListener != null ? 
                    connectionChangeListener.getConnectionCount(address, serviceName) :
                    connectionCounters.get(address).get();
            
            if (connections < minConnections) {
                minConnections = connections;
                selectedAddress = address;
            }
        }
        
        // 增加选中地址的连接计数
        if (selectedAddress != null) {
            connectionCounters.get(selectedAddress).incrementAndGet();
            
            if (connectionChangeListener != null) {
                connectionChangeListener.onConnectionAdded(selectedAddress, serviceName);
            }
        }
        
        // 如果所有地址都无效，则返回第一个地址
        return selectedAddress != null ? selectedAddress : addresses.get(0);
    }
    
    /**
     * 通知负载均衡器连接已关闭
     * 
     * @param address 服务地址
     * @param serviceName 服务名称
     */
    public void connectionClosed(InetSocketAddress address, String serviceName) {
        AtomicInteger counter = connectionCounters.get(address);
        if (counter != null) {
            counter.decrementAndGet();
        }
        
        if (connectionChangeListener != null) {
            connectionChangeListener.onConnectionRemoved(address, serviceName);
        }
    }
    
    /**
     * 清理不再使用的地址的计数器
     */
    private void cleanupConnectionCounters(List<InetSocketAddress> activeAddresses) {
        connectionCounters.keySet().removeIf(address -> !activeAddresses.contains(address));
    }
    
    /**
     * 连接变化监听器接口
     * 用于监听服务实例连接的变化
     */
    public interface ConnectionChangeListener {
        /**
         * 获取服务实例当前的连接数
         * 
         * @param address 服务地址
         * @param serviceName 服务名称
         * @return 当前连接数
         */
        int getConnectionCount(InetSocketAddress address, String serviceName);
        
        /**
         * 当有新连接添加时调用
         * 
         * @param address 服务地址
         * @param serviceName 服务名称
         */
        void onConnectionAdded(InetSocketAddress address, String serviceName);
        
        /**
         * 当有连接移除时调用
         * 
         * @param address 服务地址
         * @param serviceName 服务名称
         */
        void onConnectionRemoved(InetSocketAddress address, String serviceName);
    }
}
