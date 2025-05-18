package com.cyber.rpc.common;

import java.io.Serializable;

/**
 * RPC请求对象，包含调用的服务信息
 */
public class RpcRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * 请求ID，用于唯一标识一次请求
     */
    private String requestId;
    
    /**
     * 接口名称
     */
    private String interfaceName;
    
    /**
     * 方法名称
     */
    private String methodName;
    
    /**
     * 参数类型数组
     */
    private Class<?>[] parameterTypes;
    
    /**
     * 参数值数组
     */
    private Object[] parameters;
    
    /**
     * 版本号
     */
    private String version;
    
    /**
     * 分组
     */
    private String group;
    
    public RpcRequest() {
    }
    
    public String getRequestId() {
        return requestId;
    }
    
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
    
    public String getInterfaceName() {
        return interfaceName;
    }
    
    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }
    
    public String getMethodName() {
        return methodName;
    }
    
    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }
    
    public Class<?>[] getParameterTypes() {
        return parameterTypes;
    }
    
    public void setParameterTypes(Class<?>[] parameterTypes) {
        this.parameterTypes = parameterTypes;
    }
    
    public Object[] getParameters() {
        return parameters;
    }
    
    public void setParameters(Object[] parameters) {
        this.parameters = parameters;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public String getGroup() {
        return group;
    }
    
    public void setGroup(String group) {
        this.group = group;
    }
    
    /**
     * 获取服务名称，接口名+版本号+分组
     */
    public String getServiceName() {
        StringBuilder sb = new StringBuilder(interfaceName);
        if (version != null && !version.isEmpty()) {
            sb.append(":").append(version);
        }
        if (group != null && !group.isEmpty()) {
            sb.append(":").append(group);
        }
        return sb.toString();
    }
}