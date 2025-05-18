package com.cyber.rpc.metrics;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * 指标管理器
 * 负责管理RPC指标收集和报告
 */
public class MetricsManager {
    
    private static final Logger LOGGER = Logger.getLogger(MetricsManager.class.getName());
    
    // 单例实例
    private static final MetricsManager INSTANCE = new MetricsManager();
    
    // 默认指标收集器
    private final RpcMetrics rpcMetrics;
    
    // 最新的指标快照
    private final AtomicReference<MetricsSnapshot> latestSnapshot = new AtomicReference<>();
    
    // 定时任务执行器
    private final ScheduledExecutorService scheduler;
    
    // 是否启用定期报告
    private boolean reportingEnabled = false;
    
    /**
     * 私有构造函数
     */
    private MetricsManager() {
        this.rpcMetrics = new DefaultRpcMetrics();
        this.scheduler = new ScheduledThreadPoolExecutor(1, r -> {
            Thread thread = new Thread(r, "metrics-reporter");
            thread.setDaemon(true);
            return thread;
        });
        
        // 启动时更新一次快照
        updateSnapshot();
        
        LOGGER.info("指标管理器初始化完成");
    }
    
    /**
     * 获取单例实例
     *
     * @return 指标管理器实例
     */
    public static MetricsManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * 获取RPC指标收集器
     *
     * @return RPC指标收集器
     */
    public RpcMetrics getRpcMetrics() {
        return rpcMetrics;
    }
    
    /**
     * 获取最新的指标快照
     *
     * @return 指标快照
     */
    public MetricsSnapshot getLatestSnapshot() {
        // 如果没有快照或者超过10秒未更新，则更新快照
        MetricsSnapshot snapshot = latestSnapshot.get();
        if (snapshot == null || 
            System.currentTimeMillis() - snapshot.getTimestamp() > 10000) {
            updateSnapshot();
            snapshot = latestSnapshot.get();
        }
        return snapshot;
    }
    
    /**
     * 更新指标快照
     */
    public void updateSnapshot() {
        MetricsSnapshot snapshot = rpcMetrics.getSnapshot();
        latestSnapshot.set(snapshot);
    }
    
    /**
     * 启用定期指标报告
     *
     * @param periodSeconds 报告周期（秒）
     * @param reporter      报告处理器
     */
    public void enableReporting(int periodSeconds, Consumer<MetricsSnapshot> reporter) {
        if (reportingEnabled) {
            LOGGER.warning("指标报告已经启用，无需重复启用");
            return;
        }
        
        scheduler.scheduleAtFixedRate(() -> {
            try {
                MetricsSnapshot snapshot = getLatestSnapshot();
                reporter.accept(snapshot);
            } catch (Exception e) {
                LOGGER.warning("指标报告异常: " + e.getMessage());
            }
        }, periodSeconds, periodSeconds, TimeUnit.SECONDS);
        
        reportingEnabled = true;
        LOGGER.info("指标报告已启用，周期: " + periodSeconds + "秒");
    }
    
    /**
     * 启用控制台日志报告
     *
     * @param periodSeconds 报告周期（秒）
     */
    public void enableConsoleReporting(int periodSeconds) {
        enableReporting(periodSeconds, snapshot -> {
            LOGGER.info("======== RPC指标报告 ========");
            LOGGER.info("总请求数: " + snapshot.getTotalRequests());
            LOGGER.info("总错误数: " + snapshot.getTotalErrors());
            LOGGER.info("平均延迟: " + String.format("%.2f", snapshot.getAverageLatency()) + "ms");
            LOGGER.info("错误率: " + String.format("%.2f%%", snapshot.getErrorRate() * 100));
            LOGGER.info("服务数: " + snapshot.getServiceMetrics().size());
            
            // 输出每个服务的统计信息
            snapshot.getServiceMetrics().forEach((service, metrics) -> {
                LOGGER.info("  服务: " + service);
                LOGGER.info("    请求数: " + metrics.getRequests());
                LOGGER.info("    错误数: " + metrics.getErrors());
                LOGGER.info("    平均延迟: " + String.format("%.2f", metrics.getAverageLatency()) + "ms");
                LOGGER.info("    最大延迟: " + metrics.getMaxLatency() + "ms");
            });
            
            LOGGER.info("============================");
        });
    }
    
    /**
     * 关闭指标管理器
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("指标管理器已关闭");
    }
}
