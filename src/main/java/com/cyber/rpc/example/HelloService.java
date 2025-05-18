package com.cyber.rpc.example;

/**
 * 示例服务接口
 */
public interface HelloService {
    
    /**
     * 简单问候方法
     *
     * @param name 名称
     * @return 问候语
     */
    String sayHello(String name);
    
    /**
     * 带延迟的问候方法，用于测试超时和重试
     *
     * @param name 名称
     * @param delayMs 延迟毫秒数
     * @return 问候语
     */
    String sayHelloWithDelay(String name, long delayMs);
    
    /**
     * 可能抛出异常的问候方法，用于测试异常处理和重试
     *
     * @param name 名称
     * @param throwException 是否抛出异常
     * @return 问候语
     * @throws RuntimeException 如果throwException为true
     */
    String sayHelloWithException(String name, boolean throwException);
}
