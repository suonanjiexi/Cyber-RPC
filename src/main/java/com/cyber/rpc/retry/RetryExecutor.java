package com.cyber.rpc.retry;

import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 重试执行器
 * 负责根据重试策略执行带重试逻辑的任务
 */
public class RetryExecutor {
    
    private static final Logger LOGGER = Logger.getLogger(RetryExecutor.class.getName());
    
    // 重试策略
    private final RetryStrategy retryStrategy;
    
    // 自定义失败判断逻辑，默认为null，表示使用异常判断
    private final Predicate<Object> resultPredicate;
    
    /**
     * 创建重试执行器
     *
     * @param retryStrategy 重试策略
     */
    public RetryExecutor(RetryStrategy retryStrategy) {
        this(retryStrategy, null);
    }
    
    /**
     * 创建重试执行器，可自定义失败判断逻辑
     *
     * @param retryStrategy 重试策略
     * @param resultPredicate 结果判断函数，返回true表示需要重试
     */
    public RetryExecutor(RetryStrategy retryStrategy, Predicate<Object> resultPredicate) {
        this.retryStrategy = retryStrategy;
        this.resultPredicate = resultPredicate;
    }
    
    /**
     * 执行带重试逻辑的任务
     *
     * @param task 要执行的任务
     * @param <T> 任务返回类型
     * @return 任务执行结果
     * @throws Exception 当最终执行失败时抛出
     */
    public <T> T execute(Callable<T> task) throws Exception {
        int retryCount = 0;
        Exception lastException = null;
        
        while (true) {
            try {
                // 执行任务
                T result = task.call();
                
                // 如果有结果判断函数，判断结果是否需要重试
                if (resultPredicate != null && resultPredicate.test(result)) {
                    // 需要重试，判断是否达到重试上限
                    if (retryStrategy.shouldRetry(retryCount, lastException)) {
                        // 等待后重试
                        long waitTime = retryStrategy.getWaitTimeMs(retryCount);
                        LOGGER.info(String.format("结果需要重试，等待 %d ms 后进行第 %d 次重试", waitTime, retryCount + 1));
                        sleep(waitTime);
                        retryCount++;
                        continue;
                    } else {
                        // 达到重试上限，返回最后一次结果
                        LOGGER.warning("达到重试上限，返回最后一次结果");
                        return result;
                    }
                }
                
                // 执行成功
                return result;
                
            } catch (Exception e) {
                lastException = e;
                
                // 判断是否需要重试
                if (retryStrategy.shouldRetry(retryCount, e)) {
                    // 计算等待时间
                    long waitTime = retryStrategy.getWaitTimeMs(retryCount);
                    
                    LOGGER.log(Level.WARNING, 
                        String.format("执行异常 (%s)，等待 %d ms 后进行第 %d 次重试", 
                        e.getClass().getSimpleName(), waitTime, retryCount + 1), e);
                    
                    // 等待后重试
                    sleep(waitTime);
                    retryCount++;
                } else {
                    // 达到重试上限或不可重试的异常，抛出最后一次异常
                    throw lastException;
                }
            }
        }
    }
    
    /**
     * 执行带重试逻辑的无返回值任务
     *
     * @param task 要执行的任务
     * @throws Exception 当最终执行失败时抛出
     */
    public void executeRunnable(Runnable task) throws Exception {
        execute(() -> {
            task.run();
            return null;
        });
    }
    
    /**
     * 休眠指定时间
     *
     * @param milliseconds 毫秒数
     */
    private void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warning("重试等待被中断: " + e.getMessage());
        }
    }
}
