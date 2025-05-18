package com.cyber.rpc.transport;

import com.cyber.rpc.common.RpcRequest;
import com.cyber.rpc.common.RpcResponse;
import com.cyber.rpc.loadbalance.LoadBalancer;
import com.cyber.rpc.loadbalance.RandomLoadBalancer;
import com.cyber.rpc.registry.ServiceRegistry;
import com.cyber.rpc.serialize.Serializer;
import com.cyber.rpc.serialize.SerializerFactory;
import com.cyber.rpc.serialize.SerializerType;
import io.netty.channel.*;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * 基于Netty的RPC传输实现
 * 增强版本：使用连接池管理连接，提高性能和稳定性
 */
public class NettyTransport implements RpcTransport {
    
    private static final Logger LOGGER = Logger.getLogger(NettyTransport.class.getName());
    
    // 请求超时时间（毫秒）
    private static final int DEFAULT_REQUEST_TIMEOUT_MS = 5000;
    
    private final ServiceRegistry serviceRegistry;
    private final LoadBalancer loadBalancer;
    private final ConnectionPool connectionPool;
    private final SerializerType serializerType;
    private final int requestTimeoutMs;
    
    // 待处理的请求映射
    private final ConcurrentHashMap<String, CompletableFuture<RpcResponse<?>>> pendingRequests = new ConcurrentHashMap<>();
    
    /**
     * 使用默认配置创建NettyTransport
     * 
     * @param serviceRegistry 服务注册中心
     */
    public NettyTransport(ServiceRegistry serviceRegistry) {
        this(serviceRegistry, new RandomLoadBalancer(), new NettyConnectionPool(), SerializerType.PROTOSTUFF, DEFAULT_REQUEST_TIMEOUT_MS);
    }
    
    /**
     * 使用自定义负载均衡器创建NettyTransport
     * 
     * @param serviceRegistry 服务注册中心
     * @param loadBalancer 负载均衡器
     */
    public NettyTransport(ServiceRegistry serviceRegistry, LoadBalancer loadBalancer) {
        this(serviceRegistry, loadBalancer, new NettyConnectionPool(), SerializerType.PROTOSTUFF, DEFAULT_REQUEST_TIMEOUT_MS);
    }
    
    /**
     * 完全自定义的NettyTransport构造函数
     * 
     * @param serviceRegistry 服务注册中心
     * @param loadBalancer 负载均衡器
     * @param connectionPool 连接池
     * @param serializerType 序列化器类型
     * @param requestTimeoutMs 请求超时时间
     */
    public NettyTransport(ServiceRegistry serviceRegistry, LoadBalancer loadBalancer, 
                         ConnectionPool connectionPool, SerializerType serializerType, int requestTimeoutMs) {
        this.serviceRegistry = serviceRegistry;
        this.loadBalancer = loadBalancer;
        this.connectionPool = connectionPool;
        this.serializerType = serializerType;
        this.requestTimeoutMs = requestTimeoutMs;
        
        // 注册连接关闭监听器
        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
    }
    
    @Override
    public <T> RpcResponse<T> sendRequest(RpcRequest request) {
        // 创建一个CompletableFuture来接收响应
        CompletableFuture<RpcResponse<?>> responseFuture = new CompletableFuture<>();
        pendingRequests.put(request.getRequestId(), responseFuture);
        
        try {
            // 获取服务地址
            List<InetSocketAddress> addresses = serviceRegistry.lookup(request.getInterfaceName());
            if (addresses == null || addresses.isEmpty()) {
                throw new IllegalStateException("无可用的服务提供者: " + request.getInterfaceName());
            }
            
            // 使用负载均衡器选择一个地址
            InetSocketAddress address = loadBalancer.select(addresses, request.getInterfaceName());
            LOGGER.info("选择服务地址: " + address + " 用于请求: " + request.getRequestId());
            
            // 从连接池获取连接
            CompletableFuture<Channel> channelFuture = connectionPool.getConnection(address);
            
            // 通过获取到的通道发送请求
            channelFuture.thenAccept(channel -> {
                if (channel == null || !channel.isActive()) {
                    responseFuture.completeExceptionally(new IllegalStateException("获取到的连接不可用"));
                    return;
                }
                
                // 确保通道有正确的处理器
                if (!hasRpcHandlers(channel)) {
                    setupChannelHandlers(channel);
                }
                
                // 发送请求
                channel.writeAndFlush(request).addListener((ChannelFutureListener) writeFuture -> {
                    if (writeFuture.isSuccess()) {
                        LOGGER.info("请求发送成功: " + request.getRequestId());
                    } else {
                        // 如果写入失败，则完成future并带有异常
                        pendingRequests.remove(request.getRequestId());
                        responseFuture.completeExceptionally(writeFuture.cause());
                        // 关闭有问题的连接
                        LOGGER.warning("请求发送失败: " + writeFuture.cause().getMessage());
                        connectionPool.closeConnection(address);
                    }
                });
                
                // 当请求完成后，将连接返回到池中
                responseFuture.whenComplete((response, throwable) -> {
                    connectionPool.returnConnection(address, channel);
                });
            }).exceptionally(ex -> {
                // 处理连接获取失败
                responseFuture.completeExceptionally(ex);
                return null;
            });
            
            // 等待响应，添加超时处理
            try {
                RpcResponse<?> response = responseFuture.get(requestTimeoutMs, TimeUnit.MILLISECONDS);
                @SuppressWarnings("unchecked")
                RpcResponse<T> typedResponse = (RpcResponse<T>) response;
                return typedResponse;
            } catch (Exception e) {
                pendingRequests.remove(request.getRequestId());
                throw new RuntimeException("等待响应超时，请求ID: " + request.getRequestId(), e);
            }
        } catch (Exception e) {
            pendingRequests.remove(request.getRequestId());
            throw new RuntimeException("发送RPC请求失败", e);
        }
    }
    
    /**
     * 检查通道是否已配置RPC处理器
     */
    private boolean hasRpcHandlers(Channel channel) {
        return channel.pipeline().get("rpcResponseHandler") != null;
    }
    
    /**
     * 配置通道处理器
     */
    private void setupChannelHandlers(Channel channel) {
        ChannelPipeline pipeline = channel.pipeline();
        
        // 添加基于长度的解码器
        if (pipeline.get("frameDecoder") == null) {
            pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(65536, 0, 4, 0, 4));
        }
        
        // 添加长度字段前置编码器
        if (pipeline.get("frameEncoder") == null) {
            pipeline.addLast("frameEncoder", new LengthFieldPrepender(4));
        }
        
        // 添加RPC编码器
        if (pipeline.get("rpcEncoder") == null) {
            pipeline.addLast("rpcEncoder", new RpcMessageEncoder());
        }
        
        // 添加RPC解码器
        if (pipeline.get("rpcDecoder") == null) {
            pipeline.addLast("rpcDecoder", new RpcMessageDecoder());
        }
        
        // 添加RPC响应处理器
        if (pipeline.get("rpcResponseHandler") == null) {
            pipeline.addLast("rpcResponseHandler", new RpcResponseHandler());
        }
    }
    
    @Override
    public void close() {
        try {
            // 关闭连接池
            if (connectionPool != null) {
                connectionPool.closeAll();
            }
            LOGGER.info("NettyTransport已关闭");
        } catch (Exception e) {
            LOGGER.warning("关闭NettyTransport时发生异常: " + e.getMessage());
        }
    }
    
    /**
     * RPC响应处理器
     */
    private class RpcResponseHandler extends SimpleChannelInboundHandler<RpcResponse<?>> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, RpcResponse<?> response) {
            String requestId = response.getRequestId();
            CompletableFuture<RpcResponse<?>> future = pendingRequests.remove(requestId);
            if (future != null) {
                future.complete(response);
                LOGGER.info("收到响应: " + requestId);
            } else {
                LOGGER.warning("收到未知请求的响应: " + requestId);
            }
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOGGER.warning("通道异常: " + cause.getMessage());
            // 不主动关闭通道，让连接池管理
        }
    }
    
    /**
     * RPC消息编码器
     */
    private class RpcMessageEncoder extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof RpcRequest) {
                // 使用配置的序列化器进行序列化
                Serializer serializer = SerializerFactory.getSerializer(serializerType);
                byte[] data = serializer.serialize(msg);
                ctx.writeAndFlush(data, promise);
            } else {
                ctx.write(msg, promise);
            }
        }
    }
    
    /**
     * RPC消息解码器
     */
    private class RpcMessageDecoder extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof byte[]) {
                // 使用配置的序列化器进行反序列化
                Serializer serializer = SerializerFactory.getSerializer(serializerType);
                RpcResponse<?> response = serializer.deserialize((byte[]) msg, RpcResponse.class);
                ctx.fireChannelRead(response);
            } else {
                ctx.fireChannelRead(msg);
            }
        }
    }
}