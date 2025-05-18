package com.cyber.rpc.transport;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * 基于Netty实现的连接池
 * 提供高效的连接管理和复用
 */
public class NettyConnectionPool implements ConnectionPool {
    
    private static final Logger LOGGER = Logger.getLogger(NettyConnectionPool.class.getName());
    
    // 每个地址的最大连接数
    private final int maxConnectionsPerAddress;
    // 连接空闲时间(s)，超过此时间将执行心跳检测
    private final int idleTimeSeconds;
    // 连接最大空闲时间(s)，超过此时间将关闭连接
    private final int maxIdleTimeSeconds;
    
    // 事件循环组
    private final NioEventLoopGroup eventLoopGroup;
    // Netty启动器
    private final Bootstrap bootstrap;
    // 连接池，按地址分组
    private final Map<InetSocketAddress, ChannelPoolEntry> connectionPools;
    // 连接健康检查定时任务
    private final ScheduledExecutorService healthCheckExecutor;
    
    /**
     * 构造函数，使用默认配置
     */
    public NettyConnectionPool() {
        this(16, 5000, 30, 300);
    }
    
    /**
     * 构造函数，自定义配置
     *
     * @param maxConnectionsPerAddress 每个地址的最大连接数
     * @param connectTimeoutMillis     连接超时时间(ms)
     * @param idleTimeSeconds          连接空闲时间(s)
     * @param maxIdleTimeSeconds       连接最大空闲时间(s)
     */
    public NettyConnectionPool(int maxConnectionsPerAddress, int connectTimeoutMillis, 
                              int idleTimeSeconds, int maxIdleTimeSeconds) {
        this.maxConnectionsPerAddress = maxConnectionsPerAddress;
        this.idleTimeSeconds = idleTimeSeconds;
        this.maxIdleTimeSeconds = maxIdleTimeSeconds;
        
        // 保存连接超时时间到启动器选项
        
        this.connectionPools = new ConcurrentHashMap<>();
        this.eventLoopGroup = new NioEventLoopGroup(
                Runtime.getRuntime().availableProcessors() * 2,
                new DefaultThreadFactory("netty-client-worker", true));
                
        this.bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis);
                
        // 启动健康检查线程
        this.healthCheckExecutor = Executors.newSingleThreadScheduledExecutor(
                new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "connection-health-check");
                        t.setDaemon(true);
                        return t;
                    }
                });
                
        // 每30秒执行一次健康检查
        this.healthCheckExecutor.scheduleWithFixedDelay(
                this::healthCheck, 30, 30, TimeUnit.SECONDS);
    }
    
    @Override
    public CompletableFuture<Channel> getConnection(InetSocketAddress address) {
        CompletableFuture<Channel> future = new CompletableFuture<>();
        
        ChannelPoolEntry poolEntry = connectionPools.computeIfAbsent(
                address, addr -> new ChannelPoolEntry(maxConnectionsPerAddress));
        
        // 尝试获取闲置连接
        Channel channel = poolEntry.idleChannels.poll();
        
        // 如果有可用的闲置连接
        if (channel != null && channel.isActive()) {
            poolEntry.activeChannels.add(channel);
            future.complete(channel);
            return future;
        }
        
        // 如果已达到最大连接数，等待连接返回池中
        if (poolEntry.activeChannels.size() >= maxConnectionsPerAddress) {
            LOGGER.warning("连接池已满，等待连接释放: " + address);
            // 将请求加入等待队列
            poolEntry.waitingFutures.add(future);
            return future;
        }
        
        // 创建新连接
        createNewConnection(address, future, poolEntry);
        return future;
    }
    
    private void createNewConnection(InetSocketAddress address, 
                                   CompletableFuture<Channel> future,
                                   ChannelPoolEntry poolEntry) {
        // 克隆Bootstrap以添加具体的处理器
        Bootstrap clonedBootstrap = bootstrap.clone();
        clonedBootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ChannelPipeline pipeline = ch.pipeline();
                // 添加空闲检测
                pipeline.addLast(new IdleStateHandler(0, 0, idleTimeSeconds));
                // 可以在这里添加其他编解码器和处理器
            }
        });
        
        // 连接到服务器
        clonedBootstrap.connect(address).addListener((ChannelFutureListener) channelFuture -> {
            if (channelFuture.isSuccess()) {
                Channel newChannel = channelFuture.channel();
                
                // 设置关闭监听器
                newChannel.closeFuture().addListener((ChannelFutureListener) closeFuture -> {
                    poolEntry.activeChannels.remove(newChannel);
                    LOGGER.info("连接已关闭: " + address);
                });
                
                // 添加到活动连接集合
                poolEntry.activeChannels.add(newChannel);
                future.complete(newChannel);
                LOGGER.info("已创建新连接: " + address);
            } else {
                // 连接失败
                future.completeExceptionally(channelFuture.cause());
                LOGGER.warning("创建连接失败: " + address + ", 原因: " + channelFuture.cause().getMessage());
            }
        });
    }
    
    @Override
    public void returnConnection(InetSocketAddress address, Channel channel) {
        if (channel == null || !channel.isActive()) {
            return;
        }
        
        ChannelPoolEntry poolEntry = connectionPools.get(address);
        if (poolEntry == null) {
            // 关闭不再需要的连接
            safeCloseChannel(channel);
            return;
        }
        
        // 从活动连接中移除
        poolEntry.activeChannels.remove(channel);
        
        // 检查是否有等待的获取请求
        CompletableFuture<Channel> waitingFuture = poolEntry.waitingFutures.poll();
        if (waitingFuture != null) {
            // 有等待的请求，直接将连接交给它
            poolEntry.activeChannels.add(channel);
            waitingFuture.complete(channel);
            LOGGER.info("连接已分配给等待的请求: " + address);
        } else {
            // 无等待请求，将连接放入空闲池
            // 记录返回时间
            channel.attr(AttributeKey.valueOf("lastReturnTime")).set(System.currentTimeMillis());
            poolEntry.idleChannels.offer(channel);
            LOGGER.info("连接已返回池中: " + address);
        }
    }
    
    @Override
    public boolean isHealthy(InetSocketAddress address, Channel channel) {
        return channel != null && channel.isActive();
    }
    
    @Override
    public void closeConnection(InetSocketAddress address) {
        ChannelPoolEntry poolEntry = connectionPools.remove(address);
        if (poolEntry == null) {
            return;
        }
        
        // 关闭所有活动连接
        for (Channel channel : poolEntry.activeChannels) {
            safeCloseChannel(channel);
        }
        poolEntry.activeChannels.clear();
        
        // 关闭所有空闲连接
        Channel idleChannel;
        while ((idleChannel = poolEntry.idleChannels.poll()) != null) {
            safeCloseChannel(idleChannel);
        }
        
        // 取消所有等待的Future
        CompletableFuture<Channel> waitingFuture;
        while ((waitingFuture = poolEntry.waitingFutures.poll()) != null) {
            waitingFuture.completeExceptionally(
                    new IllegalStateException("连接池已关闭: " + address));
        }
        
        LOGGER.info("已关闭地址的所有连接: " + address);
    }
    
    @Override
    public void closeAll() {
        // 复制一份地址列表，避免并发修改异常
        for (InetSocketAddress address : connectionPools.keySet().toArray(new InetSocketAddress[0])) {
            closeConnection(address);
        }
        
        // 停止健康检查
        healthCheckExecutor.shutdown();
        
        // 关闭事件循环组
        if (!eventLoopGroup.isShutdown()) {
            eventLoopGroup.shutdownGracefully();
        }
        
        LOGGER.info("连接池已完全关闭");
    }
    
    @Override
    public PoolStatus getStatus() {
        int totalConnections = 0;
        int activeConnections = 0;
        int idleConnections = 0;
        
        for (ChannelPoolEntry entry : connectionPools.values()) {
            int active = entry.activeChannels.size();
            int idle = entry.idleChannels.size();
            
            activeConnections += active;
            idleConnections += idle;
            totalConnections += active + idle;
        }
        
        return new PoolStatus(totalConnections, activeConnections, idleConnections);
    }
    
    /**
     * 执行连接健康检查
     * 清理超时的空闲连接
     */
    private void healthCheck() {
        try {
            long now = System.currentTimeMillis();
            long maxIdleTimeMillis = maxIdleTimeSeconds * 1000L;
            
            for (Map.Entry<InetSocketAddress, ChannelPoolEntry> entry : connectionPools.entrySet()) {
                InetSocketAddress address = entry.getKey();
                ChannelPoolEntry poolEntry = entry.getValue();
                
                // 检查空闲连接
                Queue<Channel> checkedChannels = new ConcurrentLinkedQueue<>();
                Channel idleChannel;
                while ((idleChannel = poolEntry.idleChannels.poll()) != null) {
                    // 检查是否超过最大空闲时间
                    Long lastReturnTime = (Long) idleChannel.attr(
                            AttributeKey.valueOf("lastReturnTime")).get();
                    
                    if (lastReturnTime != null && (now - lastReturnTime > maxIdleTimeMillis)) {
                        // 超时，关闭连接
                        safeCloseChannel(idleChannel);
                        LOGGER.info("关闭超时空闲连接: " + address);
                    } else if (idleChannel.isActive()) {
                        // 连接正常，放回队列
                        checkedChannels.add(idleChannel);
                    } else {
                        // 连接已关闭，丢弃
                        safeCloseChannel(idleChannel);
                    }
                }
                
                // 将检查后的健康连接放回空闲池
                for (Channel channel : checkedChannels) {
                    poolEntry.idleChannels.offer(channel);
                }
            }
        } catch (Exception e) {
            LOGGER.warning("健康检查异常: " + e.getMessage());
        }
    }
    
    /**
     * 安全关闭Channel
     */
    private void safeCloseChannel(Channel channel) {
        try {
            if (channel != null && channel.isActive()) {
                channel.close();
            }
        } catch (Exception e) {
            LOGGER.warning("关闭连接异常: " + e.getMessage());
        }
    }
    
    /**
     * 连接池条目，管理特定地址的连接
     */
    private static class ChannelPoolEntry {
        // 活动连接集合
        private final Set<Channel> activeChannels = ConcurrentHashMap.newKeySet();
        // 空闲连接队列
        private final Queue<Channel> idleChannels = new ConcurrentLinkedQueue<>();
        // 等待连接的Future队列
        private final Queue<CompletableFuture<Channel>> waitingFutures = new ConcurrentLinkedQueue<>();
        // 连接计数器，记录当前运行的连接总数（活动+空闲）
        private final AtomicInteger connectionCount = new AtomicInteger(0);
        // 最大连接数
        private final int maxConnections;
        
        public ChannelPoolEntry(int maxConnections) {
            this.maxConnections = maxConnections;
        }
        
        // 检查是否已达到最大连接数
        public boolean reachedMaxConnections() {
            return connectionCount.get() >= maxConnections;
        }
        
        // 增加连接计数
        public void incrementConnectionCount() {
            connectionCount.incrementAndGet();
        }
        
        // 减少连接计数
        public void decrementConnectionCount() {
            connectionCount.decrementAndGet();
        }
    }
}
