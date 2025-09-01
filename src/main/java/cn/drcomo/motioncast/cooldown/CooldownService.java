package cn.drcomo.motioncast.cooldown;

import cn.drcomo.corelib.util.DebugUtil;
import cn.drcomo.motioncast.rules.ActionRule;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 冷却管理服务
 * 管理规则的冷却时间，基于 playerUUID + ruleId 级别
 */
public class CooldownService {
    
    private final DebugUtil logger;
    
    // 冷却数据存储: playerUUID -> (ruleUniqueKey -> expireTime)
    private final Map<UUID, Map<String, Long>> playerCooldowns = new ConcurrentHashMap<>();
    
    // 全局冷却统计
    private volatile long totalCooldownsSet = 0;
    private volatile long totalCooldownsChecked = 0;
    private volatile long totalCooldownsBlocked = 0;
    
    // 清理任务执行器
    private final ScheduledExecutorService cleanupExecutor;
    
    public CooldownService(DebugUtil logger) {
        this.logger = logger;
        
        // 创建清理任务执行器
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "CooldownService-Cleanup");
            thread.setDaemon(true);
            return thread;
        });
        
        // 启动定期清理过期冷却的任务
        startCleanupTask();
        
        logger.debug("冷却管理服务已启动");
    }
    
    /**
     * 启动清理任务
     */
    private void startCleanupTask() {
        cleanupExecutor.scheduleAtFixedRate(() -> {
            try {
                cleanupExpiredCooldowns();
            } catch (Exception e) {
                logger.error("清理过期冷却时发生异常: " + e.getMessage());
            }
        }, 30, 30, TimeUnit.SECONDS); // 每30秒清理一次
    }
    
    /**
     * 设置规则冷却
     */
    public void setCooldown(Player player, ActionRule rule) {
        setCooldown(player.getUniqueId(), rule);
    }
    
    /**
     * 设置规则冷却
     */
    public void setCooldown(UUID playerUUID, ActionRule rule) {
        int cooldownTicks = rule.getCooldown();
        if (cooldownTicks <= 0) {
            return; // 无冷却
        }
        
        long expireTime = System.currentTimeMillis() + (cooldownTicks * 50L); // tick转毫秒
        String ruleKey = rule.getUniqueKey();
        
        playerCooldowns.computeIfAbsent(playerUUID, k -> new ConcurrentHashMap<>())
                      .put(ruleKey, expireTime);
        
        totalCooldownsSet++;
        
        logger.debug("为玩家 " + playerUUID + " 设置规则 " + ruleKey + " 冷却 " + cooldownTicks + " tick");
    }
    
    /**
     * 检查规则是否在冷却中
     */
    public boolean isOnCooldown(Player player, ActionRule rule) {
        return isOnCooldown(player.getUniqueId(), rule);
    }
    
    /**
     * 检查规则是否在冷却中
     */
    public boolean isOnCooldown(UUID playerUUID, ActionRule rule) {
        totalCooldownsChecked++;
        
        Map<String, Long> playerCds = playerCooldowns.get(playerUUID);
        if (playerCds == null) {
            return false;
        }
        
        String ruleKey = rule.getUniqueKey();
        Long expireTime = playerCds.get(ruleKey);
        if (expireTime == null) {
            return false;
        }
        
        long now = System.currentTimeMillis();
        if (now >= expireTime) {
            // 冷却已过期，清理
            playerCds.remove(ruleKey);
            if (playerCds.isEmpty()) {
                playerCooldowns.remove(playerUUID);
            }
            return false;
        }
        
        totalCooldownsBlocked++;
        return true;
    }
    
    /**
     * 获取规则剩余冷却时间（tick）
     */
    public int getRemainingCooldown(Player player, ActionRule rule) {
        return getRemainingCooldown(player.getUniqueId(), rule);
    }
    
    /**
     * 获取规则剩余冷却时间（tick）
     */
    public int getRemainingCooldown(UUID playerUUID, ActionRule rule) {
        Map<String, Long> playerCds = playerCooldowns.get(playerUUID);
        if (playerCds == null) {
            return 0;
        }
        
        String ruleKey = rule.getUniqueKey();
        Long expireTime = playerCds.get(ruleKey);
        if (expireTime == null) {
            return 0;
        }
        
        long now = System.currentTimeMillis();
        if (now >= expireTime) {
            return 0;
        }
        
        long remainingMs = expireTime - now;
        return (int) Math.ceil(remainingMs / 50.0); // 毫秒转tick，向上取整
    }
    
    /**
     * 获取规则剩余冷却时间（毫秒）
     */
    public long getRemainingCooldownMs(Player player, ActionRule rule) {
        return getRemainingCooldownMs(player.getUniqueId(), rule);
    }
    
    /**
     * 获取规则剩余冷却时间（毫秒）
     */
    public long getRemainingCooldownMs(UUID playerUUID, ActionRule rule) {
        Map<String, Long> playerCds = playerCooldowns.get(playerUUID);
        if (playerCds == null) {
            return 0;
        }
        
        String ruleKey = rule.getUniqueKey();
        Long expireTime = playerCds.get(ruleKey);
        if (expireTime == null) {
            return 0;
        }
        
        long now = System.currentTimeMillis();
        return Math.max(0, expireTime - now);
    }
    
    /**
     * 清除玩家的单个规则冷却
     */
    public void clearCooldown(Player player, ActionRule rule) {
        clearCooldown(player.getUniqueId(), rule);
    }
    
    /**
     * 清除玩家的单个规则冷却
     */
    public void clearCooldown(UUID playerUUID, ActionRule rule) {
        Map<String, Long> playerCds = playerCooldowns.get(playerUUID);
        if (playerCds != null) {
            String ruleKey = rule.getUniqueKey();
            if (playerCds.remove(ruleKey) != null) {
                logger.debug("清除玩家 " + playerUUID + " 的规则 " + ruleKey + " 冷却");
            }
            
            if (playerCds.isEmpty()) {
                playerCooldowns.remove(playerUUID);
            }
        }
    }
    
    /**
     * 清除玩家的所有冷却
     */
    public void clearAllCooldowns(Player player) {
        clearAllCooldowns(player.getUniqueId());
    }
    
    /**
     * 清除玩家的所有冷却
     */
    public void clearAllCooldowns(UUID playerUUID) {
        Map<String, Long> removed = playerCooldowns.remove(playerUUID);
        if (removed != null && !removed.isEmpty()) {
            logger.debug("清除玩家 " + playerUUID + " 的所有 " + removed.size() + " 个冷却");
        }
    }
    
    /**
     * 清除所有玩家的所有冷却
     */
    public void clearAllCooldowns() {
        int count = playerCooldowns.size();
        playerCooldowns.clear();
        if (count > 0) {
            logger.info("清除了所有 " + count + " 个玩家的冷却数据");
        }
    }
    
    /**
     * 获取玩家当前的冷却数量
     */
    public int getPlayerCooldownCount(Player player) {
        return getPlayerCooldownCount(player.getUniqueId());
    }
    
    /**
     * 获取玩家当前的冷却数量
     */
    public int getPlayerCooldownCount(UUID playerUUID) {
        Map<String, Long> playerCds = playerCooldowns.get(playerUUID);
        return playerCds != null ? playerCds.size() : 0;
    }
    
    /**
     * 获取所有玩家的总冷却数量
     */
    public int getTotalCooldownCount() {
        return playerCooldowns.values().stream()
                .mapToInt(Map::size)
                .sum();
    }
    
    /**
     * 获取有冷却的玩家数量
     */
    public int getPlayersWithCooldownCount() {
        return playerCooldowns.size();
    }
    
    /**
     * 清理过期的冷却
     */
    private void cleanupExpiredCooldowns() {
        long now = System.currentTimeMillis();
        int totalCleaned = 0;
        
        for (UUID playerUUID : playerCooldowns.keySet()) {
            Map<String, Long> playerCds = playerCooldowns.get(playerUUID);
            if (playerCds == null) continue;
            
            // 计算并移除过期的冷却
            int beforeSize = playerCds.size();
            playerCds.entrySet().removeIf(entry -> now >= entry.getValue());
            int afterSize = playerCds.size();
            totalCleaned += (beforeSize - afterSize);
            
            // 如果玩家没有冷却了，移除整个玩家条目
            if (playerCds.isEmpty()) {
                playerCooldowns.remove(playerUUID);
            }
        }
        
        if (totalCleaned > 0) {
            logger.debug("清理了 " + totalCleaned + " 个过期冷却");
        }
    }
    
    /**
     * 检查规则是否会被冷却阻止
     * 这是一个便捷方法，结合了检查和统计
     */
    public boolean wouldBlock(Player player, ActionRule rule) {
        return wouldBlock(player.getUniqueId(), rule);
    }
    
    /**
     * 检查规则是否会被冷却阻止
     */
    public boolean wouldBlock(UUID playerUUID, ActionRule rule) {
        boolean blocked = isOnCooldown(playerUUID, rule);
        
        if (blocked) {
            int remainingTicks = getRemainingCooldown(playerUUID, rule);
            logger.debug("规则 " + rule.getUniqueKey() + " 被冷却阻止，剩余 " + remainingTicks + " tick");
        }
        
        return blocked;
    }
    
    /**
     * 获取冷却统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = Map.of(
            "有冷却的玩家数", getPlayersWithCooldownCount(),
            "总冷却数量", getTotalCooldownCount(),
            "累计设置冷却数", totalCooldownsSet,
            "累计检查冷却数", totalCooldownsChecked,
            "累计被阻止数", totalCooldownsBlocked,
            "阻止率", totalCooldownsChecked > 0 ? 
                String.format("%.1f%%", (double) totalCooldownsBlocked / totalCooldownsChecked * 100) : "0.0%"
        );
        
        return stats;
    }
    
    /**
     * 关闭冷却服务
     */
    public void shutdown() {
        logger.info("正在关闭冷却管理服务...");
        
        // 停止清理任务
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // 清空所有冷却数据
        clearAllCooldowns();
        
        logger.info("冷却管理服务已关闭");
    }
    
    @Override
    public String toString() {
        return "CooldownService{" +
                "players=" + getPlayersWithCooldownCount() +
                ", totalCooldowns=" + getTotalCooldownCount() +
                ", totalSet=" + totalCooldownsSet +
                ", totalBlocked=" + totalCooldownsBlocked +
                '}';
    }
}