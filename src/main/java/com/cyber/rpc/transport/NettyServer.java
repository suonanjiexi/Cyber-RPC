package com.cyber.rpc.transport;

import com.cyber.rpc.common.RpcRequest;
import com.cyber.rpc.common.RpcResponse;
import com.cyber.rpc.registry.ServiceRegistry;
import com.cyber.rpc.serialize.Serializer;
import com.cyber.rpc.serialize.SerializerFactory;
import com.cyber.rpc.serialize.SerializerType;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于Netty的RPC服务器实现
 */
public class NettyServer {
    
    private final int port;
    private final String host;
    private final ServiceRegistry serviceRegistry;
    private final Map<String, Object> serviceMap = new ConcurrentHashMap<>();
    
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel channel;
    
    public NettyServer(String host, int port, ServiceRegistry serviceRegistry) {
        this.host = host;
        this.port = port;
        this.serviceRegistry = serviceRegistry;
    }
    
    /**
     * 注册服务
     * 
     * @param serviceName 服务名称
     * @param serviceImpl 服务实现
     */
    public void registerService(String serviceName, Object serviceImpl) {
        serviceMap.put(serviceName, serviceImpl);
        // 将服务注册到注册中心
        serviceRegistry.register(serviceName, new InetSocketAddress(host, port));
        System.out.println("服务已注册: " + serviceName);
    }
    
    /**
     * 启动服务器
     */
    public void start() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            // 添加基于长度的解码器
                            pipeline.addLast(new LengthFieldBasedFrameDecoder(65536, 0, 4, 0, 4));
                            // 添加长度字段前置编码器
                            pipeline.addLast(new LengthFieldPrepender(4));
                            // 添加自定义的RPC编解码器
                            pipeline.addLast(new RpcMessageDecoder());
                            pipeline.addLast(new RpcMessageEncoder());
                            // 添加RPC请求处理器
                            pipeline.addLast(new RpcRequestHandler());
                        }
                    });
            
            // 绑定端口并启动服务器
            ChannelFuture future = bootstrap.bind(host, port).sync();
            System.out.println("RPC服务器已启动，监听端口: " + port);
            channel = future.channel();
            // 等待服务器关闭
            channel.closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            stop();
        }
    }
    
    /**
     * 停止服务器
     */
    public void stop() {
        if (channel != null) {
            channel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        System.out.println("RPC服务器已关闭");
    }
    
    /**
     * RPC请求处理器
     */
    private class RpcRequestHandler extends SimpleChannelInboundHandler<RpcRequest> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, RpcRequest request) {
            // 创建响应对象
            RpcResponse<Object> response = new RpcResponse<>();
            response.setRequestId(request.getRequestId());
            
            try {
                // 处理请求
                Object result = handleRequest(request);
                response.setCode(200);
                response.setMessage("调用成功");
                response.setData(result);
            } catch (Exception e) {
                response.setCode(500);
                response.setMessage("调用失败: " + e.getMessage());
                e.printStackTrace();
            }
            
            // 发送响应
            ctx.writeAndFlush(response);
        }
        
        private Object handleRequest(RpcRequest request) throws Exception {
            // 获取服务实现
            String serviceName = request.getInterfaceName();
            Object serviceImpl = serviceMap.get(serviceName);
            
            if (serviceImpl == null) {
                throw new RuntimeException("未找到服务实现: " + serviceName);
            }
            
            // 获取方法和参数
            Class<?> serviceClass = serviceImpl.getClass();
            String methodName = request.getMethodName();
            Class<?>[] parameterTypes = request.getParameterTypes();
            Object[] parameters = request.getParameters();
            
            // 调用方法
            Method method = serviceClass.getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(serviceImpl, parameters);
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }
    
    /**
     * RPC消息编码器
     */
    private class RpcMessageEncoder extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof RpcResponse) {
                Serializer serializer = SerializerFactory.getSerializer(SerializerType.JDK);
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
                Serializer serializer = SerializerFactory.getSerializer(SerializerType.JDK);
                RpcRequest request = serializer.deserialize((byte[]) msg, RpcRequest.class);
                ctx.fireChannelRead(request);
            } else {
                ctx.fireChannelRead(msg);
            }
        }
    }
}