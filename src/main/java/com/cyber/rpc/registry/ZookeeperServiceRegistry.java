package com.cyber.rpc.registry;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于ZooKeeper的服务注册中心实现
 */
public class ZookeeperServiceRegistry implements ServiceRegistry {
    
    private final CuratorFramework client;
    private static final String ROOT_PATH = "/cyber-rpc";
    
    public ZookeeperServiceRegistry(String zkAddress) {
        // 创建Curator客户端
        client = CuratorFrameworkFactory.builder()
                .connectString(zkAddress)
                .sessionTimeoutMs(5000)
                .connectionTimeoutMs(5000)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .namespace("cyber-rpc")
                .build();
        client.start();
    }
    
    @Override
    public void register(String serviceName, InetSocketAddress address) {
        try {
            // 服务路径
            String servicePath = ROOT_PATH + "/" + serviceName;
            // 地址路径
            String addressPath = servicePath + "/" + address.getHostString() + ":" + address.getPort();
            
            // 创建服务节点（持久节点）
            if (client.checkExists().forPath(servicePath) == null) {
                client.create().creatingParentsIfNeeded()
                        .withMode(CreateMode.PERSISTENT)
                        .forPath(servicePath);
            }
            
            // 创建地址节点（临时节点）
            client.create().withMode(CreateMode.EPHEMERAL).forPath(addressPath);
            
            System.out.println("服务已注册到ZooKeeper: " + serviceName + " => " + address);
        } catch (Exception e) {
            throw new RuntimeException("注册服务到ZooKeeper失败", e);
        }
    }
    
    @Override
    public void unregister(String serviceName, InetSocketAddress address) {
        try {
            String addressPath = ROOT_PATH + "/" + serviceName + "/" + address.getHostString() + ":" + address.getPort();
            client.delete().forPath(addressPath);
            System.out.println("服务已从ZooKeeper注销: " + serviceName + " => " + address);
        } catch (Exception e) {
            throw new RuntimeException("从ZooKeeper注销服务失败", e);
        }
    }
    
    @Override
    public List<InetSocketAddress> lookup(String serviceName) {
        try {
            List<InetSocketAddress> addresses = new ArrayList<>();
            String servicePath = ROOT_PATH + "/" + serviceName;
            
            // 检查服务是否存在
            if (client.checkExists().forPath(servicePath) == null) {
                throw new RuntimeException("服务未找到: " + serviceName);
            }
            
            // 获取所有地址节点
            List<String> addressNodes = client.getChildren().forPath(servicePath);
            if (addressNodes.isEmpty()) {
                throw new RuntimeException("服务没有可用的提供者: " + serviceName);
            }
            
            // 解析地址
            for (String node : addressNodes) {
                String[] parts = node.split(":");
                if (parts.length == 2) {
                    String host = parts[0];
                    int port = Integer.parseInt(parts[1]);
                    addresses.add(new InetSocketAddress(host, port));
                }
            }
            
            return addresses;
        } catch (Exception e) {
            throw new RuntimeException("从ZooKeeper查找服务失败", e);
        }
    }
    
    @Override
    public void close() {
        client.close();
        System.out.println("ZooKeeper服务注册中心已关闭");
    }
}