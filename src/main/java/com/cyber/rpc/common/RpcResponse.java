package com.cyber.rpc.common;

import java.io.Serializable;

/**
 * RPC响应对象，包含调用结果信息
 */
public class RpcResponse<T> implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * 响应对应的请求ID
     */
    private String requestId;
    
    /**
     * 响应码
     */
    private Integer code;
    
    /**
     * 响应消息
     */
    private String message;
    
    /**
     * 响应数据
     */
    private T data;
    
    /**
     * 响应状态码
     */
    public static final Integer SUCCESS_CODE = 200;
    public static final Integer FAIL_CODE = 500;
    
    /**
     * 成功响应
     */
    public static <T> RpcResponse<T> success(T data, String requestId) {
        RpcResponse<T> response = new RpcResponse<>();
        response.setCode(SUCCESS_CODE);
        response.setMessage("调用成功");
        response.setRequestId(requestId);
        if (data != null) {
            response.setData(data);
        }
        return response;
    }
    
    /**
     * 失败响应
     */
    public static <T> RpcResponse<T> fail(String message, String requestId) {
        RpcResponse<T> response = new RpcResponse<>();
        response.setCode(FAIL_CODE);
        response.setMessage(message);
        response.setRequestId(requestId);
        return response;
    }
    
    public RpcResponse() {
    }
    
    public String getRequestId() {
        return requestId;
    }
    
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
    
    public Integer getCode() {
        return code;
    }
    
    public void setCode(Integer code) {
        this.code = code;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public T getData() {
        return data;
    }
    
    public void setData(T data) {
        this.data = data;
    }
    
    /**
     * 判断响应是否成功
     */
    public boolean isSuccess() {
        return code != null && code.equals(SUCCESS_CODE);
    }
}