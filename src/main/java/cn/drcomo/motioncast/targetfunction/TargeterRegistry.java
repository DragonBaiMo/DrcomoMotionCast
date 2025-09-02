package cn.drcomo.motioncast.targetfunction;

import cn.drcomo.corelib.util.DebugUtil;
import org.bukkit.entity.Entity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 目标选择器注册表
 * 管理所有的目标解析器，提供统一的目标解析服务
 */
public class TargeterRegistry {
    
    private final DebugUtil logger;
    
    // 按优先级排序的解析器列表
    private final List<TargetResolver> resolvers = new CopyOnWriteArrayList<>();
    
    // 按名称索引的解析器映射
    private final Map<String, TargetResolver> resolversByName = new ConcurrentHashMap<>();
    
    // 解析统计
    private volatile long totalResolveAttempts = 0;
    private volatile long successfulResolves = 0;
    private volatile long failedResolves = 0;
    private final Map<String, Long> resolverUsageStats = new ConcurrentHashMap<>();
    
    public TargeterRegistry(DebugUtil logger) {
        this.logger = logger;
        
        // 注册内置解析器
        registerBuiltinResolvers();
        
        logger.debug("目标选择器注册表已初始化");
    }
    
    /**
     * 注册内置解析器
     */
    private void registerBuiltinResolvers() {
        // 注册内置目标解析器
        register(new BuiltinTargetResolver());
        
        logger.info("已注册内置目标解析器");
    }
    
    /**
     * 注册目标解析器
     */
    public void register(TargetResolver resolver) {
        if (resolver == null) {
            logger.warn("尝试注册null解析器");
            return;
        }
        
        String name = resolver.getName();
        if (name == null || name.trim().isEmpty()) {
            logger.warn("解析器名称不能为空");
            return;
        }
        
        // 检查名称冲突
        if (resolversByName.containsKey(name)) {
            logger.warn("解析器名称 '" + name + "' 已存在，将被替换");
            unregister(name);
        }
        
        // 添加到列表并按优先级排序
        resolvers.add(resolver);
        resolvers.sort(Comparator.comparing(TargetResolver::getPriority));
        
        // 添加到名称映射
        resolversByName.put(name, resolver);
        
        logger.info("已注册目标解析器: " + name + " (优先级: " + resolver.getPriority() + ")");
    }
    
    /**
     * 取消注册目标解析器
     */
    public void unregister(String name) {
        TargetResolver resolver = resolversByName.remove(name);
        if (resolver != null) {
            resolvers.remove(resolver);
            logger.info("已取消注册目标解析器: " + name);
        }
    }
    
    /**
     * 获取指定名称的解析器
     */
    public TargetResolver getResolver(String name) {
        return resolversByName.get(name);
    }
    
    /**
     * 获取所有注册的解析器名称
     */
    public Set<String> getResolverNames() {
        return new HashSet<>(resolversByName.keySet());
    }
    
    /**
     * 获取所有解析器
     */
    public List<TargetResolver> getAllResolvers() {
        return new ArrayList<>(resolvers);
    }
    
    /**
     * 解析目标选择器
     * 按优先级尝试所有解析器，直到找到支持的解析器
     */
    public Collection<Entity> resolve(String targetSelector, TargetContext context) {
        totalResolveAttempts++;
        
        // 空或null选择器返回空集合
        if (targetSelector == null || targetSelector.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        String trimmedSelector = targetSelector.trim();
        
        try {
            // 按优先级尝试解析器
            for (TargetResolver resolver : resolvers) {
                if (resolver.supports(trimmedSelector)) {
                    try {
                        Collection<Entity> result = resolver.resolve(trimmedSelector, context);
                        
                        // 更新统计
                        resolverUsageStats.merge(resolver.getName(), 1L, Long::sum);
                        
                        if (result != null && !result.isEmpty()) {
                            successfulResolves++;
                            logger.debug("目标选择器 '" + trimmedSelector + "' 解析成功，" +
                                       "解析器: " + resolver.getName() + "，目标数: " + result.size());
                            return result;
                        } else {
                            logger.debug("解析器 " + resolver.getName() + " 支持选择器 '" + 
                                       trimmedSelector + "' 但未返回有效目标");
                        }
                    } catch (Exception e) {
                        logger.error("解析器 " + resolver.getName() + " 处理选择器 '" + 
                                   trimmedSelector + "' 时发生异常: " + e.getMessage());
                    }
                }
            }
            
            // 没有解析器能处理
            failedResolves++;
            logger.debug("没有解析器能处理目标选择器: '" + trimmedSelector + "'");
            
        } catch (Exception e) {
            failedResolves++;
            logger.error("解析目标选择器时发生异常: " + e.getMessage());
        }
        
        return Collections.emptyList();
    }
    
    /**
     * 检查是否有解析器支持指定的选择器
     */
    public boolean isSupported(String targetSelector) {
        if (targetSelector == null || targetSelector.trim().isEmpty()) {
            return false;
        }
        
        String trimmedSelector = targetSelector.trim();
        
        for (TargetResolver resolver : resolvers) {
            if (resolver.supports(trimmedSelector)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 获取支持指定选择器的解析器
     */
    public TargetResolver findSupportingResolver(String targetSelector) {
        if (targetSelector == null || targetSelector.trim().isEmpty()) {
            return null;
        }
        
        String trimmedSelector = targetSelector.trim();
        
        for (TargetResolver resolver : resolvers) {
            if (resolver.supports(trimmedSelector)) {
                return resolver;
            }
        }
        
        return null;
    }
    
    /**
     * 验证所有解析器的健康状态
     */
    public Map<String, Boolean> checkResolverHealth() {
        Map<String, Boolean> healthStatus = new HashMap<>();
        
        for (TargetResolver resolver : resolvers) {
            try {
                boolean healthy = resolver.isHealthy();
                healthStatus.put(resolver.getName(), healthy);
                
                if (!healthy) {
                    logger.warn("解析器 " + resolver.getName() + " 健康检查失败");
                }
            } catch (Exception e) {
                healthStatus.put(resolver.getName(), false);
                logger.error("检查解析器 " + resolver.getName() + " 健康状态时发生异常: " + e.getMessage());
            }
        }
        
        return healthStatus;
    }
    
    /**
     * 获取解析统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("注册的解析器数量", resolvers.size());
        stats.put("总解析尝试次数", totalResolveAttempts);
        stats.put("成功解析次数", successfulResolves);
        stats.put("失败解析次数", failedResolves);
        
        if (totalResolveAttempts > 0) {
            double successRate = (double) successfulResolves / totalResolveAttempts * 100;
            stats.put("成功率", String.format("%.1f%%", successRate));
        } else {
            stats.put("成功率", "0.0%");
        }
        
        // 解析器使用统计
        Map<String, Long> sortedUsage = new LinkedHashMap<>();
        resolverUsageStats.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(entry -> sortedUsage.put(entry.getKey(), entry.getValue()));
        stats.put("解析器使用次数", sortedUsage);
        
        return stats;
    }
    
    /**
     * 重置统计信息
     */
    public void resetStatistics() {
        totalResolveAttempts = 0;
        successfulResolves = 0;
        failedResolves = 0;
        resolverUsageStats.clear();
        logger.info("已重置目标解析统计信息");
    }
    
    /**
     * 获取注册器状态信息
     */
    public String getStatusInfo() {
        return String.format("目标选择器注册表 - 解析器数: %d，总解析次数: %d，成功率: %.1f%%",
                resolvers.size(),
                totalResolveAttempts,
                totalResolveAttempts > 0 ? (double) successfulResolves / totalResolveAttempts * 100 : 0.0);
    }
    
    /**
     * 清空所有解析器
     */
    public void clear() {
        int count = resolvers.size();
        resolvers.clear();
        resolversByName.clear();
        resolverUsageStats.clear();
        
        if (count > 0) {
            logger.info("已清空所有 " + count + " 个目标解析器");
        }
    }
    
    /**
     * 关闭注册表
     */
    public void shutdown() {
        logger.info("正在关闭目标选择器注册表...");
        clear();
        resetStatistics();
        logger.info("目标选择器注册表已关闭");
    }
    
    @Override
    public String toString() {
        return "TargeterRegistry{" +
                "resolvers=" + resolvers.size() +
                ", attempts=" + totalResolveAttempts +
                ", success=" + successfulResolves +
                ", failed=" + failedResolves +
                '}';
    }
}