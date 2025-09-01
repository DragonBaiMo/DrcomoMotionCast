package cn.drcomo.motioncast.tick;

import cn.drcomo.corelib.util.DebugUtil;
import cn.drcomo.motioncast.state.PlayerStateManager;
import cn.drcomo.motioncast.state.PlayerStateSession;
import cn.drcomo.motioncast.engine.ActionEngine;
import cn.drcomo.motioncast.rules.ActionType;
import cn.drcomo.motioncast.rules.TriggerWhen;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tick调度器
 * 负责处理需要定时执行的逻辑，如悬停检测、duration触发、tick触发等
 */
public class TickScheduler {
    
    private final JavaPlugin plugin;
    private final DebugUtil logger;
    private final PlayerStateManager stateManager;
    private final ActionEngine actionEngine;
    
    // Bukkit调度任务
    private BukkitTask tickTask;
    private volatile boolean running = false;
    
    // 性能配置
    private int maxPlayersPerTick = 200;
    private int currentPlayerIndex = 0;
    
    // 悬停检测配置
    private int hoverMinTicks = 8;
    private double hoverVelocityYThreshold = 0.03;
    private double hoverVelocityHorizontalThreshold = 0.06;
    
    // 统计信息
    private final AtomicLong totalTicks = new AtomicLong(0);
    private final AtomicLong totalPlayersProcessed = new AtomicLong(0);
    private final AtomicLong totalHoverChecks = new AtomicLong(0);
    private final AtomicLong totalRulesTrigger = new AtomicLong(0);
    
    public TickScheduler(JavaPlugin plugin, DebugUtil logger, PlayerStateManager stateManager, ActionEngine actionEngine) {
        this.plugin = plugin;
        this.logger = logger;
        this.stateManager = stateManager;
        this.actionEngine = actionEngine;
        
        // 从配置加载参数
        loadConfiguration();
        
        logger.debug("Tick调度器已创建");
    }
    
    /**
     * 从配置加载参数
     */
    private void loadConfiguration() {
        try {
            // 从主配置 settings.yml 读取调度相关参数
            // 注意：YamlUtil 会在首次访问时自动加载配置
            int cfgMaxPlayersPerTick = this.maxPlayersPerTick;
            int cfgHoverMinTicks = this.hoverMinTicks;
            double cfgHoverVAbsY = this.hoverVelocityYThreshold;
            double cfgHoverHSpeed = this.hoverVelocityHorizontalThreshold;

            // 仅当主插件提供 YamlUtil 时读取；否则维持默认值
            if (plugin instanceof cn.drcomo.motioncast.DrcomoMotionCast) {
                cn.drcomo.motioncast.DrcomoMotionCast main = (cn.drcomo.motioncast.DrcomoMotionCast) plugin;
                cn.drcomo.corelib.config.YamlUtil yaml = main.getYamlUtil();
                if (yaml != null) {
                    cfgMaxPlayersPerTick = yaml.getInt("settings", "tick.max_players_per_tick", this.maxPlayersPerTick);
                    cfgHoverMinTicks = yaml.getInt("settings", "hover.min_ticks", this.hoverMinTicks);
                    cfgHoverVAbsY = yaml.getDouble("settings", "hover.v_abs_y", this.hoverVelocityYThreshold);
                    cfgHoverHSpeed = yaml.getDouble("settings", "hover.h_speed", this.hoverVelocityHorizontalThreshold);
                }
            }

            // 基本参数校验与归一：避免出现非法值
            cfgMaxPlayersPerTick = Math.max(1, cfgMaxPlayersPerTick);
            cfgHoverMinTicks = Math.max(1, cfgHoverMinTicks);
            cfgHoverVAbsY = Math.max(0.0, cfgHoverVAbsY);
            cfgHoverHSpeed = Math.max(0.0, cfgHoverHSpeed);

            // 应用配置
            this.maxPlayersPerTick = cfgMaxPlayersPerTick;
            this.hoverMinTicks = cfgHoverMinTicks;
            this.hoverVelocityYThreshold = cfgHoverVAbsY;
            this.hoverVelocityHorizontalThreshold = cfgHoverHSpeed;

            logger.debug("Tick调度器配置已加载: maxPlayersPerTick=" + maxPlayersPerTick +
                    ", hoverMinTicks=" + hoverMinTicks +
                    ", v_abs_y=" + hoverVelocityYThreshold +
                    ", h_speed=" + hoverVelocityHorizontalThreshold);
        } catch (Exception e) {
            logger.error("加载Tick调度器配置时出现错误，将使用默认值: " + e.getMessage());
        }
    }
    
    /**
     * 启动调度器
     */
    public void start() {
        if (running) {
            logger.warn("Tick调度器已在运行");
            return;
        }
        
        tickTask = new TickRunnable().runTaskTimer(plugin, 1L, 1L);
        running = true;
        
        logger.info("Tick调度器已启动");
    }
    
    /**
     * 停止调度器
     */
    public void stop() {
        if (!running) {
            return;
        }
        
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        
        running = false;
        logger.info("Tick调度器已停止");
    }
    
    /**
     * 关闭调度器
     */
    public void shutdown() {
        stop();
        logger.info("Tick调度器已关闭");
    }
    
    /**
     * 检查是否正在运行
     */
    public boolean isRunning() {
        return running;
    }
    
    /**
     * 更新配置
     */
    public void updateConfiguration(int maxPlayersPerTick, int hoverMinTicks, 
                                   double velocityYThreshold, double velocityHorizontalThreshold) {
        this.maxPlayersPerTick = Math.max(1, maxPlayersPerTick);
        this.hoverMinTicks = Math.max(1, hoverMinTicks);
        this.hoverVelocityYThreshold = Math.max(0, velocityYThreshold);
        this.hoverVelocityHorizontalThreshold = Math.max(0, velocityHorizontalThreshold);
        
        logger.debug("Tick调度器配置已更新");
    }
    
    /**
     * Tick执行任务
     */
    private class TickRunnable extends BukkitRunnable {
        @Override
        public void run() {
            try {
                processTick();
            } catch (Exception e) {
                logger.error("处理Tick时发生异常: " + e.getMessage());
            }
        }
    }
    
    /**
     * 处理单次Tick
     */
    private void processTick() {
        totalTicks.incrementAndGet();
        
        // 使用活跃会话快照，避免每Tick全量筛选与分配
        List<PlayerStateSession> activeSessions = stateManager.getActiveSessionSnapshot();
        if (activeSessions.isEmpty()) {
            return;
        }
        
        // 分帧处理以避免性能问题
        if (currentPlayerIndex >= activeSessions.size()) {
            currentPlayerIndex = 0;
        }
        int endIndex = Math.min(currentPlayerIndex + maxPlayersPerTick, activeSessions.size());
        
        for (int i = currentPlayerIndex; i < endIndex; i++) {
            PlayerStateSession session = activeSessions.get(i);
            processPlayerSession(session);
        }
        
        // 更新索引，实现循环处理
        currentPlayerIndex = endIndex >= activeSessions.size() ? 0 : endIndex;
        
        // 时间基准修正后不再需要在轮末自增状态Tick
    }
    
    /**
     * 处理单个玩家会话
     */
    private void processPlayerSession(PlayerStateSession session) {
        totalPlayersProcessed.incrementAndGet();
        
        Player player = Bukkit.getPlayer(session.getPlayerUUID());
        if (player == null || !player.isOnline()) {
            return;
        }
        
        try {
            // 1. 悬停检测
            processHoverDetection(player, session);
            
            // 2. 处理duration类型的规则
            processDurationRules(player, session);
            
            // 3. 处理tick类型的规则
            processTickRules(player, session);
            
        } catch (Exception e) {
            logger.debug("处理玩家 " + player.getName() + " 的会话时发生异常: " + e.getMessage());
        }
    }
    
    /**
     * 处理悬停检测
     */
    private void processHoverDetection(Player player, PlayerStateSession session) {
        totalHoverChecks.incrementAndGet();
        
        Vector velocity = player.getVelocity();
        session.updateVelocity(velocity);
        
        boolean isHovering = checkHoverConditions(player, session, velocity);
        
        if (isHovering && !session.isHovering()) {
            // 检查是否满足最小稳定时间
            session.incrementHoverStableCount();
            
            if (session.getHoverStableCount() >= hoverMinTicks) {
                // 进入悬停状态
                session.setHovering(true);
                actionEngine.fireRules(player, ActionType.HOVER, TriggerWhen.START);
                totalRulesTrigger.incrementAndGet();
                // 活跃集合同步
                stateManager.updateActiveStatus(session);
            }
            
        } else if (!isHovering && session.isHovering()) {
            // 退出悬停状态
            session.setHovering(false);
            session.resetHoverStableCount();
            actionEngine.fireRules(player, ActionType.HOVER, TriggerWhen.END);
            totalRulesTrigger.incrementAndGet();
            // 活跃集合同步
            stateManager.updateActiveStatus(session);
            
        } else if (!isHovering) {
            // 重置稳定计数
            session.resetHoverStableCount();
        }
    }
    
    /**
     * 检查悬停条件
     */
    private boolean checkHoverConditions(Player player, PlayerStateSession session, Vector velocity) {
        // 基础条件：玩家离地、不在游泳、不在滑翔
        if (player.isOnGround() || session.isSwimming() || session.isGliding()) {
            return false;
        }
        
        // 速度条件：垂直速度和水平速度都要小于阈值
        // 水平速度采用平方比较以避免开平方开销
        double verticalSpeed = Math.abs(velocity.getY());
        double h2 = velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ();
        double hThreshold2 = hoverVelocityHorizontalThreshold * hoverVelocityHorizontalThreshold;
        
        return verticalSpeed <= hoverVelocityYThreshold && h2 <= hThreshold2;
    }
    
    /**
     * 处理duration类型的规则
     */
    private void processDurationRules(Player player, PlayerStateSession session) {
        // 针对所有可能的动作类型，仅在对应状态为激活时进行检查
        if (session.isFlying()) {
            int t = (int) (session.getFlyingDuration() / 50L);
            if (t > 0) actionEngine.checkDurationRules(player, ActionType.FLY, t);
        }
        if (session.isGliding()) {
            int t = (int) (session.getGlidingDuration() / 50L);
            if (t > 0) actionEngine.checkDurationRules(player, ActionType.GLIDE, t);
        }
        if (session.isSwimming()) {
            int t = (int) (session.getSwimmingDuration() / 50L);
            if (t > 0) actionEngine.checkDurationRules(player, ActionType.SWIM, t);
        }
        if (session.isInBoat()) {
            int t = (int) (session.getInBoatDuration() / 50L);
            if (t > 0) actionEngine.checkDurationRules(player, ActionType.INBOAT, t);
        }
        if (session.isRiding()) {
            int t = (int) (session.getRidingDuration() / 50L);
            if (t > 0) actionEngine.checkDurationRules(player, ActionType.RIDE, t);
        }
        if (session.isHovering()) {
            int t = (int) (session.getHoveringDuration() / 50L);
            if (t > 0) actionEngine.checkDurationRules(player, ActionType.HOVER, t);
        }
    }
    
    /**
     * 处理tick类型的规则
     */
    private void processTickRules(Player player, PlayerStateSession session) {
        // 针对所有可能的动作类型，仅在对应状态为激活时进行检查
        if (session.isFlying()) {
            int t = (int) (session.getFlyingDuration() / 50L);
            if (t > 0) actionEngine.checkTickRules(player, ActionType.FLY, t);
        }
        if (session.isGliding()) {
            int t = (int) (session.getGlidingDuration() / 50L);
            if (t > 0) actionEngine.checkTickRules(player, ActionType.GLIDE, t);
        }
        if (session.isSwimming()) {
            int t = (int) (session.getSwimmingDuration() / 50L);
            if (t > 0) actionEngine.checkTickRules(player, ActionType.SWIM, t);
        }
        if (session.isInBoat()) {
            int t = (int) (session.getInBoatDuration() / 50L);
            if (t > 0) actionEngine.checkTickRules(player, ActionType.INBOAT, t);
        }
        if (session.isRiding()) {
            int t = (int) (session.getRidingDuration() / 50L);
            if (t > 0) actionEngine.checkTickRules(player, ActionType.RIDE, t);
        }
        if (session.isHovering()) {
            int t = (int) (session.getHoveringDuration() / 50L);
            if (t > 0) actionEngine.checkTickRules(player, ActionType.HOVER, t);
        }
    }
    
    /**
     * 获取统计信息
     */
    public TickSchedulerStats getStatistics() {
        return new TickSchedulerStats(
            totalTicks.get(),
            totalPlayersProcessed.get(),
            totalHoverChecks.get(),
            totalRulesTrigger.get(),
            running,
            maxPlayersPerTick,
            currentPlayerIndex
        );
    }
    
    /**
     * 重置统计信息
     */
    public void resetStatistics() {
        totalTicks.set(0);
        totalPlayersProcessed.set(0);
        totalHoverChecks.set(0);
        totalRulesTrigger.set(0);
        currentPlayerIndex = 0;
        
        logger.info("Tick调度器统计信息已重置");
    }
    
    /**
     * Tick调度器统计信息类
     */
    public static class TickSchedulerStats {
        public final long totalTicks;
        public final long totalPlayersProcessed;
        public final long totalHoverChecks;
        public final long totalRulesTrigger;
        public final boolean running;
        public final int maxPlayersPerTick;
        public final int currentPlayerIndex;
        
        public TickSchedulerStats(long totalTicks, long totalPlayersProcessed, long totalHoverChecks, 
                                long totalRulesTrigger, boolean running, int maxPlayersPerTick, int currentPlayerIndex) {
            this.totalTicks = totalTicks;
            this.totalPlayersProcessed = totalPlayersProcessed;
            this.totalHoverChecks = totalHoverChecks;
            this.totalRulesTrigger = totalRulesTrigger;
            this.running = running;
            this.maxPlayersPerTick = maxPlayersPerTick;
            this.currentPlayerIndex = currentPlayerIndex;
        }
        
        @Override
        public String toString() {
            return "TickSchedulerStats{" +
                    "totalTicks=" + totalTicks +
                    ", totalPlayersProcessed=" + totalPlayersProcessed +
                    ", totalHoverChecks=" + totalHoverChecks +
                    ", totalRulesTrigger=" + totalRulesTrigger +
                    ", running=" + running +
                    ", maxPlayersPerTick=" + maxPlayersPerTick +
                    ", currentPlayerIndex=" + currentPlayerIndex +
                    '}';
        }
    }
    
    @Override
    public String toString() {
        TickSchedulerStats stats = getStatistics();
        return "TickScheduler{" +
                "running=" + stats.running +
                ", totalTicks=" + stats.totalTicks +
                ", playersProcessed=" + stats.totalPlayersProcessed +
                '}';
    }
}
