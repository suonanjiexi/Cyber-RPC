package com.cyber.rpc.example;

import java.util.logging.Logger;

/**
 * 示例服务实现
 */
public class HelloServiceImpl implements HelloService {
    
    private static final Logger LOGGER = Logger.getLogger(HelloServiceImpl.class.getName());
    
    @Override
    public String sayHello(String name) {
        LOGGER.info("接收到sayHello请求，参数: " + name);
        return "Hello, " + name + "!";
    }
    
    @Override
    public String sayHelloWithDelay(String name, long delayMs) {
        LOGGER.info("接收到sayHelloWithDelay请求，参数: " + name + ", 延迟: " + delayMs + "ms");
        
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return "Hello after " + delayMs + "ms, " + name + "!";
    }
    
    @Override
    public String sayHelloWithException(String name, boolean throwException) {
        LOGGER.info("接收到sayHelloWithException请求，参数: " + name + ", 抛出异常: " + throwException);
        
        if (throwException) {
            throw new RuntimeException("测试异常");
        }
        
        return "Hello without exception, " + name + "!";
    }
}
