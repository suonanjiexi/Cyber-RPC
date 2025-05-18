package com.cyber.rpc.transport;

import com.cyber.rpc.common.RpcRequest;
import com.cyber.rpc.common.RpcResponse;

/**
 * RPC传输层接口，定义了RPC通信的基本方法
 */
public interface RpcTransport {
    
    /**
     * 发送RPC请求并获取响应
     * 
     * @param request RPC请求对象
     * @param <T>     响应数据类型
     * @return RPC响应对象
     */
    <T> RpcResponse<T> sendRequest(RpcRequest request);
    
    /**
     * 关闭传输通道
     */
    void close();
}