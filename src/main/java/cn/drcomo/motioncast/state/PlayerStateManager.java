package cn.drcomo.motioncast.state;

import cn.drcomo.corelib.util.DebugUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 玩家状态管理器
 * 管理所有在线玩家的状态会话
 */
public class PlayerStateManager implements Listener {
    
    // 插件实例（用于在主线程执行Bukkit相关API调用）
    private final Plugin plugin;
    private final DebugUtil logger;
    
    // 存储所有玩家的状态会话
    private final Map<UUID, PlayerStateSession> sessions = new ConcurrentHashMap<>();
    
    // 活跃会话集合与快照（避免每Tick全量筛选与分配）
    private final Set<PlayerStateSession> activeSessions = ConcurrentHashMap.newKeySet();
    private volatile List<PlayerStateSession> activeSnapshot = Collections.emptyList();
    
    // 定时清理任务
    private final ScheduledExecutorService cleanupExecutor;
    private final long contextMaxAge;
    private final long sessionMaxAge;
    
    // 统计信息
    private volatile long totalSessionsCreated = 0;
    private volatile long totalSessionsCleaned = 0;
    
    public PlayerStateManager(Plugin plugin, DebugUtil logger) {
        this.plugin = plugin;
        this.logger = logger;
        this.contextMaxAge = TimeUnit.MINUTES.toMillis(5); // 上下文数据保存5分钟
        this.sessionMaxAge = TimeUnit.HOURS.toMillis(1); // 会话最多保存1小时（离线玩家）
        
        // 创建清理任务执行器
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "PlayerStateManager-Cleanup");
            thread.setDaemon(true);
            return thread;
        });
        
        startCleanupTask();
    }

    /**
     * 重建活跃会话快照
     * 说明：仅在活跃集合发生变更时调用，降低每Tick分配与遍历成本
     */
    private void rebuildActiveSnapshot() {
        // 使用新的 ArrayList 作为不可变快照引用，避免迭代期间结构化修改带来的并发问题
        this.activeSnapshot = new ArrayList<>(activeSessions);
    }

    /**
     * 根据会话当前是否存在任一激活状态，更新活跃集合与快照
     */
    public void updateActiveStatus(PlayerStateSession session) {
        if (session == null) return;
        boolean hasActive = session.hasActiveState();
        boolean changed;
        if (hasActive) {
            changed = activeSessions.add(session);
        } else {
            changed = activeSessions.remove(session);
        }
        if (changed) {
            rebuildActiveSnapshot();
        }
    }
    
    /**
     * 启动定时清理任务
     */
    private void startCleanupTask() {
        cleanupExecutor.scheduleAtFixedRate(() -> {
            try {
                cleanupExpiredSessions();
                cleanupExpiredContext();
            } catch (Exception e) {
                logger.error("清理任务执行失败: " + e.getMessage());
            }
        }, 1, 1, TimeUnit.MINUTES);
    }
    
    /**
     * 获取或创建玩家状态会话
     */
    public PlayerStateSession getOrCreateSession(UUID playerUUID) {
        return sessions.computeIfAbsent(playerUUID, uuid -> {
            totalSessionsCreated++;
            PlayerStateSession session = new PlayerStateSession(uuid);
            logger.debug("为玩家 " + uuid + " 创建新的状态会话");
            return session;
        });
    }
    
    /**
     * 获取或创建玩家状态会话
     */
    public PlayerStateSession getOrCreateSession(Player player) {
        return getOrCreateSession(player.getUniqueId());
    }
    
    /**
     * 获取玩家状态会话（如果不存在则返回null）
     */
    public PlayerStateSession getSession(UUID playerUUID) {
        return sessions.get(playerUUID);
    }
    
    /**
     * 获取玩家状态会话（如果不存在则返回null）
     */
    public PlayerStateSession getSession(Player player) {
        return getSession(player.getUniqueId());
    }
    
    /**
     * 移除玩家状态会话
     */
    public PlayerStateSession removeSession(UUID playerUUID) {
        PlayerStateSession session = sessions.remove(playerUUID);
        if (session != null) {
            logger.debug("移除玩家 " + playerUUID + " 的状态会话");
            // 同步移除活跃集合并重建快照
            if (activeSessions.remove(session)) {
                rebuildActiveSnapshot();
            }
        }
        return session;
    }
    
    /**
     * 移除玩家状态会话
     */
    public PlayerStateSession removeSession(Player player) {
        return removeSession(player.getUniqueId());
    }
    
    /**
     * 获取所有有激活状态的玩家会话
     */
    public List<PlayerStateSession> getActiveStateSessions() {
        // 兼容旧接口：从快照复制，避免每次筛选
        return new ArrayList<>(activeSnapshot);
    }

    /**
     * 获取活跃会话快照（零拷贝引用，勿在外部修改）
     */
    public List<PlayerStateSession> getActiveSessionSnapshot() {
        return activeSnapshot;
    }
    
    /**
     * 获取所有会话
     */
    public Collection<PlayerStateSession> getAllSessions() {
        return new ArrayList<>(sessions.values());
    }
    
    /**
     * 获取在线玩家会话数量
     */
    public int getSessionCount() {
        return sessions.size();
    }
    
    /**
     * 获取有激活状态的玩家数量
     */
    public int getActiveStateCount() {
        return activeSessions.size();
    }
    
    /**
     * 增加所有激活状态的tick计数
     */
    public void incrementAllActiveTicks() {
        // 时间基准已修正为基于“起始时间+当前时间”的差值，本方法不再在 TickScheduler 中使用
        // 保留空实现以兼容潜在的外部调用场景
    }
    
    /**
     * 清理过期的会话
     */
    private void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();

        // 先筛选出超过最大会话时长的候选UUID
        List<UUID> candidates = new ArrayList<>();
        for (Map.Entry<UUID, PlayerStateSession> entry : sessions.entrySet()) {
            PlayerStateSession session = entry.getValue();
            if (now - session.getCreatedTime() > sessionMaxAge) {
                candidates.add(entry.getKey());
            }
        }

        if (candidates.isEmpty()) {
            return;
        }

        try {
            // 使用Bukkit调度器在主线程同步执行在线状态检查
            Future<List<UUID>> future = Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                List<UUID> offline = new ArrayList<>();
                for (UUID uuid : candidates) {
                    Player player = Bukkit.getPlayer(uuid);
                    boolean online = (player != null) && player.isOnline();
                    if (!online) {
                        offline.add(uuid);
                    }
                }
                return offline;
            });

            // 限时等待结果，避免潜在阻塞
            List<UUID> toRemove = future.get(2, TimeUnit.SECONDS);

            if (toRemove != null && !toRemove.isEmpty()) {
                for (UUID uuid : toRemove) {
                    PlayerStateSession removed = removeSession(uuid);
                    if (removed != null) totalSessionsCleaned++;
                }
                logger.debug("清理了 " + toRemove.size() + " 个过期且离线的会话");
            }
        } catch (TimeoutException te) {
            logger.warn("在线状态检查超时，跳过本轮过期会话清理");
        } catch (Exception e) {
            logger.error("在线状态检查失败: " + e.getMessage());
        }
    }
    
    /**
     * 清理过期的上下文数据
     */
    private void cleanupExpiredContext() {
        for (PlayerStateSession session : sessions.values()) {
            session.cleanupExpiredContext(contextMaxAge);
        }
    }
    
    /**
     * 清空所有会话
     */
    public void clearAllSessions() {
        int count = sessions.size();
        sessions.clear();
        // 清空活跃集合与快照
        activeSessions.clear();
        rebuildActiveSnapshot();
        if (count > 0) {
            logger.info("已清空所有 " + count + " 个玩家状态会话");
        }
    }
    
    /**
     * 重置指定玩家的所有状态
     */
    public void resetPlayerState(UUID playerUUID) {
        PlayerStateSession session = sessions.get(playerUUID);
        if (session != null) {
            session.reset();
            logger.debug("重置玩家 " + playerUUID + " 的所有状态");
            updateActiveStatus(session);
        }
    }
    
    /**
     * 重置指定玩家的所有状态
     */
    public void resetPlayerState(Player player) {
        resetPlayerState(player.getUniqueId());
    }
    
    /**
     * 获取统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("总会话数", sessions.size());
        stats.put("激活状态玩家数", getActiveStateCount());
        stats.put("累计创建会话数", totalSessionsCreated);
        stats.put("累计清理会话数", totalSessionsCleaned);
        
        // 状态分布统计
        Map<String, Integer> stateStats = new HashMap<>();
        for (PlayerStateSession session : sessions.values()) {
            if (session.isFlying()) stateStats.merge("飞行", 1, Integer::sum);
            if (session.isGliding()) stateStats.merge("滑翔", 1, Integer::sum);
            if (session.isSwimming()) stateStats.merge("游泳", 1, Integer::sum);
            if (session.isInBoat()) stateStats.merge("乘船", 1, Integer::sum);
            if (session.isRiding()) stateStats.merge("骑乘", 1, Integer::sum);
            if (session.isHovering()) stateStats.merge("悬停", 1, Integer::sum);
        }
        stats.put("状态分布", stateStats);
        
        return stats;
    }
    
    // 事件监听器
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        getOrCreateSession(player);
        logger.debug("玩家 " + player.getName() + " 加入，初始化状态会话");
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerStateSession session = getSession(player);
        if (session != null) {
            // 清理状态但不立即删除会话，让清理任务处理
            session.reset();
            logger.debug("玩家 " + player.getName() + " 离开，重置状态会话");
            updateActiveStatus(session);
        }
    }
    
    /**
     * 关闭管理器并清理资源
     */
    public void shutdown() {
        logger.info("正在关闭玩家状态管理器...");
        
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
        
        // 清空所有会话
        clearAllSessions();
        
        logger.info("玩家状态管理器已关闭");
    }
    
    @Override
    public String toString() {
        return "PlayerStateManager{" +
                "sessions=" + sessions.size() +
                ", activeStates=" + getActiveStateCount() +
                ", totalCreated=" + totalSessionsCreated +
                ", totalCleaned=" + totalSessionsCleaned +
                '}';
    }
}
