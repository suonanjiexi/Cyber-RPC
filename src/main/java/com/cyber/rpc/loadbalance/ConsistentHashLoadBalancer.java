package com.cyber.rpc.loadbalance;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一致性哈希负载均衡器
 * 使用一致性哈希算法选择服务实例，相同的服务名和请求参数将始终选择相同的服务实例，
 * 且在服务实例列表变化时能够最小化请求的迁移。
 */
public class ConsistentHashLoadBalancer implements LoadBalancer {
    
    // 每个服务的一致性哈希环
    private final Map<String, ConsistentHashRing> hashRingMap = new ConcurrentHashMap<>();
    
    // 虚拟节点的数量，越多分布越均匀，但会消耗更多内存
    private final int virtualNodes;
    
    // 请求参数提取器，用于获取请求的特定参数作为哈希依据
    private final RequestHashArgProvider hashArgProvider;
    
    /**
     * 创建一致性哈希负载均衡器
     * 
     * @param virtualNodes 每个实际节点对应的虚拟节点数
     * @param hashArgProvider 请求参数提取器
     */
    public ConsistentHashLoadBalancer(int virtualNodes, RequestHashArgProvider hashArgProvider) {
        this.virtualNodes = virtualNodes;
        this.hashArgProvider = hashArgProvider;
    }
    
    /**
     * 使用默认配置创建一致性哈希负载均衡器
     * 默认每个实际节点有160个虚拟节点，使用服务名作为哈希依据
     */
    public ConsistentHashLoadBalancer() {
        this(160, (serviceName, attachment) -> serviceName);
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
        
        // 获取一致性哈希环，如果不存在或需要更新则创建
        ConsistentHashRing hashRing = hashRingMap.compute(serviceName, (key, existingRing) -> {
            // 如果没有哈希环或者服务地址有变化，需要创建新的哈希环
            if (existingRing == null || !existingRing.isValid(addresses)) {
                return new ConsistentHashRing(addresses, virtualNodes);
            }
            return existingRing;
        });
        
        // 获取请求的哈希参数
        Object hashArg = hashArgProvider.getHashArg(serviceName, null);
        
        // 根据哈希参数选择节点
        return hashRing.getServer(hashArg);
    }
    
    /**
     * 根据请求参数和服务名选择服务实例
     * 
     * @param addresses 服务地址列表
     * @param serviceName 服务名称
     * @param attachment 请求附加参数，例如方法名或参数值
     * @return 选择的服务地址
     */
    public InetSocketAddress select(List<InetSocketAddress> addresses, String serviceName, Object attachment) {
        if (addresses == null || addresses.isEmpty()) {
            return null;
        }
        
        // 如果只有一个地址，直接返回
        if (addresses.size() == 1) {
            return addresses.get(0);
        }
        
        // 获取一致性哈希环
        ConsistentHashRing hashRing = hashRingMap.compute(serviceName, (key, existingRing) -> {
            if (existingRing == null || !existingRing.isValid(addresses)) {
                return new ConsistentHashRing(addresses, virtualNodes);
            }
            return existingRing;
        });
        
        // 获取请求的哈希参数
        Object hashArg = hashArgProvider.getHashArg(serviceName, attachment);
        
        // 根据哈希参数选择节点
        return hashRing.getServer(hashArg);
    }
    
    /**
     * 一致性哈希环实现
     */
    private static class ConsistentHashRing {
        // 哈希环，键为哈希值，值为服务地址
        private final SortedMap<Integer, InetSocketAddress> hashRing = new TreeMap<>();
        
        // 当前环中的服务地址列表
        private final List<InetSocketAddress> currentAddresses;
        
        /**
         * 创建一致性哈希环
         * 
         * @param addresses 服务地址列表
         * @param virtualNodes 每个实际节点对应的虚拟节点数
         */
        ConsistentHashRing(List<InetSocketAddress> addresses, int virtualNodes) {
            this.currentAddresses = addresses;
            
            // 为每个实际节点创建虚拟节点
            for (InetSocketAddress address : addresses) {
                for (int i = 0; i < virtualNodes; i++) {
                    String nodeKey = address.toString() + "#" + i;
                    int hash = getHash(nodeKey);
                    hashRing.put(hash, address);
                }
            }
        }
        
        /**
         * 检查哈希环是否仍然有效（服务地址列表是否有变化）
         * 
         * @param addresses 当前服务地址列表
         * @return 如果地址列表相同则返回true，否则返回false
         */
        boolean isValid(List<InetSocketAddress> addresses) {
            if (currentAddresses.size() != addresses.size()) {
                return false;
            }
            
            // 检查每个地址是否相同
            for (InetSocketAddress address : addresses) {
                if (!currentAddresses.contains(address)) {
                    return false;
                }
            }
            
            return true;
        }
        
        /**
         * 根据哈希值获取服务实例
         * 
         * @param hashArg 哈希参数
         * @return 服务地址
         */
        InetSocketAddress getServer(Object hashArg) {
            if (hashRing.isEmpty()) {
                return null;
            }
            
            // 计算哈希值
            int hash = getHash(hashArg.toString());
            
            // 查找第一个大于等于该哈希值的虚拟节点
            SortedMap<Integer, InetSocketAddress> tailMap = hashRing.tailMap(hash);
            
            // 如果没有大于该哈希值的节点，则使用环的第一个节点
            Integer key = tailMap.isEmpty() ? hashRing.firstKey() : tailMap.firstKey();
            
            return hashRing.get(key);
        }
        
        /**
         * 计算哈希值，使用FNV1_32_HASH算法
         * 
         * @param key 键
         * @return 哈希值
         */
        private int getHash(String key) {
            final int p = 16777619;
            int hash = (int) 2166136261L;
            for (int i = 0; i < key.length(); i++) {
                hash = (hash ^ key.charAt(i)) * p;
            }
            hash += hash << 13;
            hash ^= hash >> 7;
            hash += hash << 3;
            hash ^= hash >> 17;
            hash += hash << 5;
            
            // 防止溢出变成负数
            return hash & 0x7FFFFFFF;
        }
    }
    
    /**
     * 请求哈希参数提取器接口
     * 用于从请求中提取用于一致性哈希的参数
     */
    public interface RequestHashArgProvider {
        /**
         * 获取请求的哈希参数
         * 
         * @param serviceName 服务名称
         * @param attachment 请求附加参数，例如方法名或参数值
         * @return 哈希参数
         */
        Object getHashArg(String serviceName, Object attachment);
    }
}
