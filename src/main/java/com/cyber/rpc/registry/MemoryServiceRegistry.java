package com.cyber.rpc.registry;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 基于内存的服务注册中心实现
 */
public class MemoryServiceRegistry implements ServiceRegistry {
    
    private final Map<String, List<InetSocketAddress>> serviceMap = new ConcurrentHashMap<>();
    
    @Override
    public void register(String serviceName, InetSocketAddress address) {
        serviceMap.computeIfAbsent(serviceName, k -> new CopyOnWriteArrayList<>())
                .add(address);
        System.out.println("服务已注册: " + serviceName + " => " + address);
    }
    
    @Override
    public void unregister(String serviceName, InetSocketAddress address) {
        List<InetSocketAddress> addresses = serviceMap.get(serviceName);
        if (addresses != null) {
            addresses.remove(address);
            if (addresses.isEmpty()) {
                serviceMap.remove(serviceName);
            }
            System.out.println("服务已注销: " + serviceName + " => " + address);
        }
    }
    
    @Override
    public List<InetSocketAddress> lookup(String serviceName) {
        List<InetSocketAddress> addresses = serviceMap.get(serviceName);
        if (addresses == null || addresses.isEmpty()) {
            throw new RuntimeException("未找到服务: " + serviceName);
        }
        return addresses;
    }
    
    @Override
    public void close() {
        serviceMap.clear();
        System.out.println("内存服务注册中心已关闭");
    }
}