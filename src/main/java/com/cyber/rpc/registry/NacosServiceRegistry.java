package com.cyber.rpc.registry;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 基于Nacos的服务注册中心实现
 * 提供服务注册、发现和自动监听功能
 */
public class NacosServiceRegistry implements ServiceRegistry {
    
    private static final Logger LOGGER = Logger.getLogger(NacosServiceRegistry.class.getName());
    
    // Nacos服务命名前缀
    private static final String SERVICE_PREFIX = "cyber-rpc-";
    
    // Nacos命名服务
    private final NamingService namingService;
    
    // 服务实例缓存
    private final Map<String, List<InetSocketAddress>> serviceCache = new ConcurrentHashMap<>();
    
    // 是否启用服务监听
    private final boolean enableWatch;
    
    /**
     * 创建Nacos服务注册中心
     *
     * @param serverAddr Nacos服务器地址，例如 "127.0.0.1:8848"
     * @param enableWatch 是否启用服务列表变更监听
     * @throws RuntimeException 当初始化Nacos客户端失败时抛出
     */
    public NacosServiceRegistry(String serverAddr, boolean enableWatch) {
        try {
            this.namingService = NamingFactory.createNamingService(serverAddr);
            this.enableWatch = enableWatch;
            LOGGER.info("Nacos服务注册中心初始化成功，服务器地址: " + serverAddr);
        } catch (NacosException e) {
            LOGGER.log(Level.SEVERE, "初始化Nacos客户端失败", e);
            throw new RuntimeException("初始化Nacos客户端失败", e);
        }
    }
    
    /**
     * 创建默认Nacos服务注册中心，使用本地Nacos服务器
     *
     * @return Nacos服务注册中心实例
     */
    public static NacosServiceRegistry createDefault() {
        return new NacosServiceRegistry("localhost:8848", true);
    }
    
    @Override
    public void register(String serviceName, InetSocketAddress address) {
        String nacosServiceName = getServiceName(serviceName);
        try {
            // 创建服务实例
            Instance instance = new Instance();
            instance.setIp(address.getHostString());
            instance.setPort(address.getPort());
            instance.setHealthy(true);
            
            // 添加元数据
            Map<String, String> metadata = new ConcurrentHashMap<>();
            metadata.put("serviceVersion", "1.0");
            metadata.put("serviceGroup", "DEFAULT_GROUP");
            instance.setMetadata(metadata);
            
            // 注册实例到Nacos
            namingService.registerInstance(nacosServiceName, instance);
            LOGGER.info("已注册服务到Nacos: " + serviceName + "@" + address);
        } catch (NacosException e) {
            LOGGER.log(Level.SEVERE, "注册服务到Nacos失败: " + serviceName, e);
            throw new RuntimeException("注册服务到Nacos失败", e);
        }
    }
    
    @Override
    public void unregister(String serviceName, InetSocketAddress address) {
        String nacosServiceName = getServiceName(serviceName);
        try {
            namingService.deregisterInstance(
                    nacosServiceName, 
                    address.getHostString(), 
                    address.getPort());
            LOGGER.info("已从Nacos注销服务: " + serviceName + "@" + address);
        } catch (NacosException e) {
            LOGGER.log(Level.SEVERE, "从Nacos注销服务失败: " + serviceName, e);
            throw new RuntimeException("从Nacos注销服务失败", e);
        }
    }
    
    @Override
    public List<InetSocketAddress> lookup(String serviceName) {
        String nacosServiceName = getServiceName(serviceName);
        
        // 优先从缓存获取
        if (serviceCache.containsKey(nacosServiceName)) {
            return serviceCache.get(nacosServiceName);
        }
        
        // 缓存未命中，从Nacos查询
        try {
            List<Instance> instances = namingService.getAllInstances(nacosServiceName);
            List<InetSocketAddress> addresses = convertToAddresses(instances);
            
            // 更新缓存
            serviceCache.put(nacosServiceName, addresses);
            
            // 启用监听
            if (enableWatch && !addresses.isEmpty()) {
                subscribeService(nacosServiceName);
            }
            
            return addresses;
        } catch (NacosException e) {
            LOGGER.log(Level.SEVERE, "从Nacos查询服务失败: " + serviceName, e);
            throw new RuntimeException("从Nacos查询服务失败", e);
        }
    }
    
    /**
     * 转换Nacos实例列表为地址列表
     */
    private List<InetSocketAddress> convertToAddresses(List<Instance> instances) {
        List<InetSocketAddress> addresses = new ArrayList<>(instances.size());
        for (Instance instance : instances) {
            // 只添加健康的实例
            if (instance.isHealthy()) {
                InetSocketAddress address = new InetSocketAddress(
                        instance.getIp(), instance.getPort());
                addresses.add(address);
            }
        }
        return addresses;
    }
    
    /**
     * 订阅服务变更
     */
    private void subscribeService(String nacosServiceName) {
        try {
            namingService.subscribe(nacosServiceName, event -> {
                if (event instanceof NamingEvent) {
                    NamingEvent namingEvent = (NamingEvent) event;
                    List<Instance> instances = namingEvent.getInstances();
                    List<InetSocketAddress> addresses = convertToAddresses(instances);
                    
                    // 更新缓存
                    serviceCache.put(nacosServiceName, addresses);
                    LOGGER.info("服务列表更新: " + nacosServiceName + 
                               ", 实例数: " + addresses.size());
                }
            });
            LOGGER.info("已订阅服务变更: " + nacosServiceName);
        } catch (NacosException e) {
            LOGGER.log(Level.WARNING, "订阅服务变更失败: " + nacosServiceName, e);
        }
    }
    
    /**
     * 获取在Nacos中的服务名
     */
    private String getServiceName(String serviceName) {
        return SERVICE_PREFIX + serviceName;
    }
    
    @Override
    public void close() {
        try {
            // 关闭Nacos客户端
            namingService.shutDown();
            LOGGER.info("Nacos服务注册中心已关闭");
        } catch (NacosException e) {
            LOGGER.log(Level.WARNING, "关闭Nacos客户端失败", e);
        }
    }
}
