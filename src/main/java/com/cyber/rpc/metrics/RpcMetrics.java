package com.cyber.rpc.metrics;

/**
 * RPC指标接口
 * 提供收集和获取RPC调用相关指标的能力
 */
public interface RpcMetrics {
    
    /**
     * 记录RPC请求
     *
     * @param service 服务名称
     * @param method  方法名
     */
    void recordRequest(String service, String method);
    
    /**
     * 记录RPC请求延迟
     *
     * @param service   服务名称
     * @param method    方法名
     * @param latencyMs 延迟时间（毫秒）
     */
    void recordLatency(String service, String method, long latencyMs);
    
    /**
     * 记录RPC请求错误
     *
     * @param service 服务名称
     * @param method  方法名
     * @param error   错误类型
     */
    void recordError(String service, String method, Throwable error);
    
    /**
     * 获取指标快照
     *
     * @return 指标快照
     */
    MetricsSnapshot getSnapshot();
}
