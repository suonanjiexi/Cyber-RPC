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
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * 基于Netty实现的连接池
 * 提供高效的连接管理和复用
 * 支持自适应调整连接池大小和连接状态监控
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
    
    // 自适应连接池相关配置
    private boolean adaptivePoolSizingEnabled = false; // 是否启用自适应连接池
    private int minPoolSize = 4;                      // 最小连接数
    private int maxPoolSize = 64;                     // 最大连接数
    private double loadThresholdLow = 0.2;            // 低负载阈值，低于此值时减少连接
    private double loadThresholdHigh = 0.7;           // 高负载阈值，高于此值时增加连接
    private final ScheduledExecutorService poolSizeAdjustExecutor; // 连接池大小调整定时任务
    
    // 创建时间
    private final long creationTime = System.currentTimeMillis();
    // 总请求计数
    private final AtomicLong totalRequestCount = new AtomicLong(0);
    
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
                
        // 初始化自适应连接池大小调整线程
        this.poolSizeAdjustExecutor = Executors.newSingleThreadScheduledExecutor(
                new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "pool-size-adjust");
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
        
        // 请求计数增加
        totalRequestCount.incrementAndGet();
        poolEntry.recordRequestStart();
        
        // 尝试获取闲置连接
        Channel channel = poolEntry.idleChannels.poll();
        
        // 如果有可用的闲置连接
        if (channel != null && channel.isActive()) {
            poolEntry.activeChannels.add(channel);
            future.complete(channel);
            return future;
        }
        
        // 如果已达到最大连接数，等待连接返回池中
        if (poolEntry.reachedMaxConnections()) {
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
        
        // 记录连接计数
        poolEntry.incrementConnectionCount();
        
        // 记录连接开始时间
        long startTime = System.currentTimeMillis();
        
        // 连接到服务器
        clonedBootstrap.connect(address).addListener((ChannelFutureListener) channelFuture -> {
            if (channelFuture.isSuccess()) {
                Channel newChannel = channelFuture.channel();
                
                // 计算连接时间并记录成功
                long responseTime = System.currentTimeMillis() - startTime;
                poolEntry.recordRequestSuccess(responseTime);
                
                // 设置关闭监听器
                newChannel.closeFuture().addListener((ChannelFutureListener) closeFuture -> {
                    poolEntry.activeChannels.remove(newChannel);
                    poolEntry.decrementConnectionCount();
                    LOGGER.info("连接已关闭: " + address);
                });
                
                // 添加到活动连接集合
                poolEntry.activeChannels.add(newChannel);
                future.complete(newChannel);
                LOGGER.info("已创建新连接: " + address + ", 响应时间: " + responseTime + "ms");
            } else {
                // 连接失败
                poolEntry.recordRequestFailure();
                poolEntry.decrementConnectionCount();
                future.completeExceptionally(channelFuture.cause());
                LOGGER.warning("创建连接失败: " + address + ", 原因: " + channelFuture.cause().getMessage());
            }
        });
        
        // 设置超时处理
        ScheduledFuture<?> timeoutTask = Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            if (!future.isDone()) {
                poolEntry.recordRequestTimeout();
                future.completeExceptionally(new TimeoutException("连接超时: " + address));
                LOGGER.warning("连接超时: " + address);
            }
        }, 10, TimeUnit.SECONDS);
        
        // 清理超时任务
        future.whenComplete((result, ex) -> {
            timeoutTask.cancel(false);
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
        
        // 检查连接健康状态
        if (!channel.isActive()) {
            safeCloseChannel(channel);
            poolEntry.recordRequestFailure(); // 记录失效连接
            LOGGER.warning("返回了不活跃的连接: " + address);
            return;
        }
        
        // 检查是否有等待的获取请求
        CompletableFuture<Channel> waitingFuture = poolEntry.waitingFutures.poll();
        if (waitingFuture != null) {
            // 有等待的请求，直接将连接交给它
            poolEntry.activeChannels.add(channel);
            waitingFuture.complete(channel);
            LOGGER.info("连接已分配给等待的请求: " + address + ", 当前活跃连接: " 
                    + poolEntry.activeChannels.size() + "/" + poolEntry.maxConnections);
        } else {
            // 无等待请求，将连接放入空闲池
            
            // 记录返回时间
            long returnTime = System.currentTimeMillis();
            channel.attr(AttributeKey.valueOf("lastReturnTime")).set(returnTime);
            poolEntry.idleChannels.offer(channel);
            
            // 不超过最大连接数的情况下，记录成功
            if (poolEntry.reachedMaxConnections()) {
                // 一般不应该达到这里，因为如果达到最大连接数，应该有等待的未完成Future
                LOGGER.warning("连接池满负荷运行: " + address + ", 空闲连接: " 
                        + poolEntry.idleChannels.size() + ", 活跃连接: " + poolEntry.activeChannels.size());
                
                // 如果启用了自适应池大小，可能需要增加池大小
                if (adaptivePoolSizingEnabled && poolEntry.maxConnections < maxPoolSize) {
                    // 触发一次自适应调整
                    adjustPoolSizes();
                }
            }
            
            LOGGER.info("连接已返回池中: " + address + ", 当前空闲连接: " 
                    + poolEntry.idleChannels.size() + ", 活跃连接: " + poolEntry.activeChannels.size());
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
        int pendingRequests = 0;
        long totalRequestsCount = 0;
        double avgResponseTime = 0.0;
        
        for (ChannelPoolEntry entry : connectionPools.values()) {
            int active = entry.activeChannels.size();
            int idle = entry.idleChannels.size();
            int pending = entry.waitingFutures.size();
            
            activeConnections += active;
            idleConnections += idle;
            totalConnections += active + idle;
            pendingRequests += pending;
            totalRequestsCount += entry.totalRequests.get();
            
            // 累加响应时间（按请求数加权平均）
            if (entry.successRequests.get() > 0) {
                avgResponseTime += entry.getAverageResponseTime() * entry.successRequests.get();
            }
        }
        
        // 计算总体平均响应时间
        long totalSuccessRequests = 0;
        for (ChannelPoolEntry entry : connectionPools.values()) {
            totalSuccessRequests += entry.successRequests.get();
        }
        
        if (totalSuccessRequests > 0) {
            avgResponseTime /= totalSuccessRequests;
        }
        
        return new PoolStatus(
                totalConnections,
                activeConnections,
                idleConnections,
                pendingRequests,
                creationTime,
                System.currentTimeMillis(),  // 作为最后调整时间
                maxPoolSize,
                totalRequestsCount,
                avgResponseTime
        );
    }
    
    @Override
    public Map<InetSocketAddress, PoolStatus> getDetailedStatus() {
        Map<InetSocketAddress, PoolStatus> statusMap = new HashMap<>();
        
        for (Map.Entry<InetSocketAddress, ChannelPoolEntry> entry : connectionPools.entrySet()) {
            statusMap.put(entry.getKey(), entry.getValue().getPoolStatus());
        }
        
        return statusMap;
    }
    
    @Override
    public void resizePool(InetSocketAddress address, int newSize) {
        if (newSize <= 0) {
            throw new IllegalArgumentException("新的连接池大小必须大于0: " + newSize);
        }
        
        ChannelPoolEntry poolEntry = connectionPools.get(address);
        if (poolEntry == null) {
            // 如果地址不存在，创建新的池条目
            connectionPools.put(address, new ChannelPoolEntry(newSize));
            LOGGER.info("为地址创建新连接池: " + address + ", 大小: " + newSize);
            return;
        }
        
        int oldSize = poolEntry.maxConnections;
        poolEntry.setMaxConnections(newSize);
        
        // 如果缩小了连接池，可能需要关闭一些空闲连接
        if (newSize < oldSize) {
            int connectionsToClose = poolEntry.idleChannels.size() - 
                    (newSize - poolEntry.activeChannels.size());
            
            if (connectionsToClose > 0) {
                for (int i = 0; i < connectionsToClose; i++) {
                    Channel idleChannel = poolEntry.idleChannels.poll();
                    if (idleChannel != null) {
                        safeCloseChannel(idleChannel);
                        poolEntry.decrementConnectionCount();
                    }
                }
            }
        }
        
        LOGGER.info("已调整连接池大小: " + address + ", 从 " + oldSize + " 到 " + newSize);
    }
    
    @Override
    public ConnectionStats getConnectionStats(InetSocketAddress address) {
        ChannelPoolEntry poolEntry = connectionPools.get(address);
        if (poolEntry == null) {
            // 如果没有连接记录，返回空统计
            return new ConnectionStats(0, 0, 0, 0, 0, 0, 0, 0);
        }
        
        return poolEntry.getConnectionStats();
    }
    
    @Override
    public void enableAdaptivePoolSizing(boolean enabled, int minSize, int maxSize, 
                                      double loadThresholdLow, double loadThresholdHigh) {
        if (maxSize < minSize) {
            throw new IllegalArgumentException("最大连接数不能小于最小连接数");
        }
        
        if (loadThresholdLow >= loadThresholdHigh) {
            throw new IllegalArgumentException("低负载阈值必须小于高负载阈值");
        }
        
        this.adaptivePoolSizingEnabled = enabled;
        this.minPoolSize = minSize;
        this.maxPoolSize = maxSize;
        this.loadThresholdLow = loadThresholdLow;
        this.loadThresholdHigh = loadThresholdHigh;
        
        if (enabled) {
            // 启动自适应调整任务，每10秒检查一次负载并调整连接池大小
            this.poolSizeAdjustExecutor.scheduleWithFixedDelay(
                    this::adjustPoolSizes, 10, 10, TimeUnit.SECONDS);
            LOGGER.info("已启用自适应连接池大小调整, 连接范围: [" + minSize + ", " + maxSize + "], " + 
                    "负载阈值: [" + loadThresholdLow + ", " + loadThresholdHigh + "]");
        } else {
            LOGGER.info("已禁用自适应连接池大小调整");
        }
    }
    
    /**
     * 自适应调整所有连接池大小
     */
    private void adjustPoolSizes() {
        if (!adaptivePoolSizingEnabled) {
            return;
        }
        
        try {
            for (Map.Entry<InetSocketAddress, ChannelPoolEntry> entry : connectionPools.entrySet()) {
                InetSocketAddress address = entry.getKey();
                ChannelPoolEntry poolEntry = entry.getValue();
                
                // 计算活跃比例
                double activeRatio = 0;
                int total = poolEntry.activeChannels.size() + poolEntry.idleChannels.size();
                if (total > 0) {
                    activeRatio = (double) poolEntry.activeChannels.size() / total;
                }
                
                int currentMaxConnections = poolEntry.maxConnections;
                int newMaxConnections = currentMaxConnections;
                
                // 根据负载调整池大小
                if (activeRatio > loadThresholdHigh && currentMaxConnections < maxPoolSize) {
                    // 高负载，增加连接数上限
                    newMaxConnections = Math.min(maxPoolSize, (int)(currentMaxConnections * 1.5));
                } else if (activeRatio < loadThresholdLow && currentMaxConnections > minPoolSize) {
                    // 低负载，减少连接数上限
                    newMaxConnections = Math.max(minPoolSize, (int)(currentMaxConnections * 0.8));
                }
                
                // 如果需要调整大小
                if (newMaxConnections != currentMaxConnections) {
                    resizePool(address, newMaxConnections);
                    LOGGER.info("自适应调整连接池大小: " + address + ", 活跃比例: " + 
                            String.format("%.2f", activeRatio) + ", 新大小: " + newMaxConnections);
                }
            }
        } catch (Exception e) {
            LOGGER.warning("自适应调整连接池大小出错: " + e.getMessage());
        }
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
        private volatile int maxConnections;
        // 创建时间
        private final long creationTime = System.currentTimeMillis();
        // 最近一次调整大小的时间
        private volatile long lastResizeTime = System.currentTimeMillis();
        // 请求统计
        private final AtomicLong totalRequests = new AtomicLong(0);
        private final AtomicLong successRequests = new AtomicLong(0);
        private final AtomicLong failedRequests = new AtomicLong(0);
        private final AtomicLong timeoutRequests = new AtomicLong(0);
        // 响应时间统计
        private final AtomicLong totalResponseTime = new AtomicLong(0);
        // 连续失败计数
        private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        // 最后活跃时间
        private volatile long lastActiveTime = System.currentTimeMillis();

        public ChannelPoolEntry(int maxConnections) {
            this.maxConnections = maxConnections;
        }

        // 检查是否已达到最大连接数
        public boolean reachedMaxConnections() {
            return connectionCount.get() >= maxConnections;
        }

        // 增加连接计数
        public int incrementConnectionCount() {
            return connectionCount.incrementAndGet();
        }

        // 减少连接计数
        public int decrementConnectionCount() {
            return connectionCount.decrementAndGet();
        }

        // 调整最大连接数
        public void setMaxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
            this.lastResizeTime = System.currentTimeMillis();
        }

        // 记录请求开始
        public void recordRequestStart() {
            totalRequests.incrementAndGet();
            lastActiveTime = System.currentTimeMillis();
        }

        // 记录请求成功
        public void recordRequestSuccess(long responseTimeMs) {
            successRequests.incrementAndGet();
            totalResponseTime.addAndGet(responseTimeMs);
            consecutiveFailures.set(0);
        }

        // 记录请求失败
        public void recordRequestFailure() {
            failedRequests.incrementAndGet();
            consecutiveFailures.incrementAndGet();
        }

        // 记录请求超时
        public void recordRequestTimeout() {
            timeoutRequests.incrementAndGet();
            consecutiveFailures.incrementAndGet();
        }

        // 获取平均响应时间
        public double getAverageResponseTime() {
            long successCount = successRequests.get();
            return successCount > 0 ? (double) totalResponseTime.get() / successCount : 0;
        }

        // 获取错误率
        public double getErrorRate() {
            long total = totalRequests.get();
            return total > 0 ? (double) (failedRequests.get() + timeoutRequests.get()) / total : 0;
        }

        // 获取连接统计信息
        public ConnectionStats getConnectionStats() {
            return new ConnectionStats(
                    getAverageResponseTime(),
                    getErrorRate(),
                    totalRequests.get(),
                    successRequests.get(),
                    failedRequests.get(),
                    timeoutRequests.get(),
                    lastActiveTime,
                    consecutiveFailures.get()
            );
        }

        // 获取池状态
        public PoolStatus getPoolStatus() {
            int active = activeChannels.size();
            int idle = idleChannels.size();
            int pending = waitingFutures.size();

            return new PoolStatus(
                    active + idle,
                    active,
                    idle,
                    pending,
                    creationTime,
                    lastResizeTime,
                    maxConnections,
                    totalRequests.get(),
                    getAverageResponseTime()
            );
        }
    }
}
