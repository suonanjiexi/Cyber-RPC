package com.cyber.rpc.server;

import com.cyber.rpc.governance.GovernanceManager;
import com.cyber.rpc.governance.RateLimiter;
import com.cyber.rpc.metrics.RpcMetrics;
import com.cyber.rpc.protocol.RpcRequest;
import com.cyber.rpc.protocol.RpcResponse;
import com.cyber.rpc.registry.ServiceRegistry;
import com.cyber.rpc.serialize.Serializer;
import com.cyber.rpc.serialize.SerializerFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 默认RPC服务器实现
 */
public class DefaultRpcServer implements RpcServer {

    private static final Logger LOGGER = Logger.getLogger(DefaultRpcServer.class.getName());
    
    // RPC服务器配置
    private final RpcServerConfig config;
    
    // 服务注册中心
    private final ServiceRegistry serviceRegistry;
    
    // 治理管理器
    private final GovernanceManager governanceManager;
    
    // 指标收集器
    private final RpcMetrics metrics;
    
    // 线程池
    private final ExecutorService executorService;
    
    // 序列化器
    private final Serializer serializer;
    
    // 服务实现映射表
    private final Map<String, Object> serviceImpls = new ConcurrentHashMap<>();
    
    // Netty相关成员
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    
    /**
     * 创建默认RPC服务器
     */
    public DefaultRpcServer(RpcServerConfig config,
                           ServiceRegistry serviceRegistry,
                           GovernanceManager governanceManager,
                           RpcMetrics metrics,
                           ExecutorService executorService) {
        this.config = config;
        this.serviceRegistry = serviceRegistry;
        this.governanceManager = governanceManager;
        this.metrics = metrics;
        this.executorService = executorService;
        this.serializer = SerializerFactory.getSerializer(config.getSerializerType());
    }

    @Override
    public void registerService(String serviceName, Object serviceImpl) {
        serviceImpls.put(serviceName, serviceImpl);
        LOGGER.info("服务注册成功: " + serviceName);
    }

    @Override
    public void start() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(config.getWorkerThreads());
        
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(
                                // 解码器：处理粘包/拆包问题
                                new LengthFieldBasedFrameDecoder(65536, 0, 4, 0, 4),
                                // 编码器：在消息前加上长度字段
                                new LengthFieldPrepender(4),
                                // 业务处理器
                                new RpcServerHandler()
                            );
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            // 绑定端口
            ChannelFuture future = bootstrap.bind(config.getPort()).sync();
            serverChannel = future.channel();
            
            // 如果配置了注册到服务中心，则注册所有服务
            if (config.isRegisterToRegistry() && serviceRegistry != null) {
                registerServicesToRegistry();
            }
            
            LOGGER.info("RPC服务器已启动，监听端口: " + config.getPort());
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "RPC服务器启动失败", e);
            stop();
        }
    }

    @Override
    public void stop() {
        try {
            // 从服务注册中心注销服务
            if (config.isRegisterToRegistry() && serviceRegistry != null) {
                unregisterServicesFromRegistry();
            }
            
            // 关闭服务器通道
            if (serverChannel != null) {
                serverChannel.close().sync();
            }
            
            // 关闭线程组
            if (bossGroup != null) {
                bossGroup.shutdownGracefully().sync();
            }
            if (workerGroup != null) {
                workerGroup.shutdownGracefully().sync();
            }
            
            LOGGER.info("RPC服务器已停止");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "RPC服务器停止异常", e);
        }
    }

    @Override
    public int getPort() {
        return config.getPort();
    }
    
    /**
     * 将服务注册到服务中心
     */
    private void registerServicesToRegistry() {
        try {
            InetSocketAddress address = new InetSocketAddress(InetAddress.getLocalHost(), config.getPort());
            
            for (String serviceName : serviceImpls.keySet()) {
                serviceRegistry.register(serviceName, address);
                LOGGER.info("服务已注册到服务中心: " + serviceName);
            }
        } catch (UnknownHostException e) {
            LOGGER.log(Level.SEVERE, "获取本地地址失败", e);
        }
    }
    
    /**
     * 从服务中心注销服务
     */
    private void unregisterServicesFromRegistry() {
        try {
            InetSocketAddress address = new InetSocketAddress(InetAddress.getLocalHost(), config.getPort());
            
            for (String serviceName : serviceImpls.keySet()) {
                serviceRegistry.unregister(serviceName, address);
                LOGGER.info("服务已从服务中心注销: " + serviceName);
            }
        } catch (UnknownHostException e) {
            LOGGER.log(Level.SEVERE, "获取本地地址失败", e);
        }
    }
    
    /**
     * RPC服务器处理器
     */
    private class RpcServerHandler extends SimpleChannelInboundHandler<ByteBuf> {
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
            // 创建字节数组
            byte[] bytes = new byte[msg.readableBytes()];
            msg.readBytes(bytes);
            
            // 提交到线程池处理请求
            executorService.submit(() -> processRequest(ctx, bytes));
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOGGER.log(Level.WARNING, "RPC服务器处理异常", cause);
            ctx.close();
        }
        
        /**
         * 处理RPC请求
         */
        private void processRequest(ChannelHandlerContext ctx, byte[] requestBytes) {
            RpcResponse response = new RpcResponse();
            long startTime = System.currentTimeMillis();
            String serviceName = null;
            String methodName = null;
            
            try {
                // 反序列化请求
                RpcRequest request = serializer.deserialize(requestBytes, RpcRequest.class);
                
                // 提取服务名和方法名
                serviceName = request.getServiceName();
                methodName = request.getMethodName();
                
                // 设置响应ID
                response.setRequestId(request.getRequestId());
                
                // 限流检查
                if (config.isEnableRateLimiter() && governanceManager != null) {
                    // 检查全局限流
                    RateLimiter globalLimiter = governanceManager.getRateLimiter("global");
                    if (globalLimiter != null && !globalLimiter.tryAcquire()) {
                        throw new RuntimeException("请求被全局限流");
                    }
                    
                    // 检查服务级限流
                    RateLimiter serviceLimiter = governanceManager.getRateLimiter(serviceName);
                    if (serviceLimiter != null && !serviceLimiter.tryAcquire()) {
                        throw new RuntimeException("请求被服务级限流");
                    }
                }
                
                // 获取服务实现
                Object serviceImpl = serviceImpls.get(serviceName);
                if (serviceImpl == null) {
                    throw new RuntimeException("服务未找到: " + serviceName);
                }
                
                // 获取方法参数类型
                Class<?>[] parameterTypes = getParameterTypes(request);
                
                // 获取方法
                Method method = serviceImpl.getClass().getMethod(methodName, parameterTypes);
                
                // 调用方法
                Object result = method.invoke(serviceImpl, request.getParameters());
                
                // 设置响应结果
                response.setResult(result);
                
                // 记录指标
                if (metrics != null && config.isEnableMetrics()) {
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    metrics.recordRequest(serviceName, methodName, elapsedTime);
                }
                
            } catch (Exception e) {
                // 记录异常
                LOGGER.log(Level.WARNING, "处理RPC请求异常", e);
                
                // 设置响应异常
                response.setException(e);
                
                // 记录错误指标
                if (metrics != null && config.isEnableMetrics() && serviceName != null && methodName != null) {
                    metrics.recordError(serviceName, methodName);
                }
            }
            
            try {
                // 序列化响应
                byte[] responseBytes = serializer.serialize(response);
                
                // 发送响应
                ByteBuf responseBuf = Unpooled.wrappedBuffer(responseBytes);
                ctx.writeAndFlush(responseBuf);
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "序列化或发送响应异常", e);
                ctx.close();
            }
        }
        
        /**
         * 获取请求参数类型
         */
        private Class<?>[] getParameterTypes(RpcRequest request) throws ClassNotFoundException {
            Class<?>[] parameterTypes = request.getParameterTypes();
            
            if (parameterTypes == null && request.getParameters() != null) {
                // 如果请求中没有提供参数类型，则使用参数对象的类型
                Object[] parameters = request.getParameters();
                parameterTypes = new Class<?>[parameters.length];
                for (int i = 0; i < parameters.length; i++) {
                    if (parameters[i] != null) {
                        parameterTypes[i] = parameters[i].getClass();
                    }
                }
            }
            
            return parameterTypes;
        }
    }
}
