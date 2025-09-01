package cn.drcomo.motioncast.listener;

import cn.drcomo.motioncast.engine.ActionEngine;
import cn.drcomo.motioncast.state.PlayerStateManager;
import cn.drcomo.motioncast.state.PlayerStateSession;
import cn.drcomo.motioncast.rules.ActionType;
import cn.drcomo.motioncast.rules.TriggerWhen;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.EntityToggleSwimEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;

/**
 * 玩家事件监听器
 * 处理与玩家直接相关的事件
 */
public class PlayerEventListener implements Listener {
    
    private final ActionEngine actionEngine;
    private final PlayerStateManager stateManager;
    
    public PlayerEventListener(ActionEngine actionEngine, PlayerStateManager stateManager) {
        this.actionEngine = actionEngine;
        this.stateManager = stateManager;
    }
    
    /**
     * 玩家攻击事件
     * 使用 HIGH 优先级以支持技能中的 CancelEvent 机制取消原始攻击
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        
        Player player = (Player) event.getDamager();
        PlayerStateSession session = stateManager.getOrCreateSession(player);
        
        // 记录受害者
        session.setLastVictim(event.getEntity());
        
        // 触发攻击规则
        actionEngine.fireRules(player, ActionType.ATTACK, TriggerWhen.INSTANT);
    }
    
    /**
     * 玩家受击事件
     * 使用 HIGH 优先级以支持技能中的 CancelEvent 机制取消原始伤害
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onPlayerDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        
        Player player = (Player) event.getEntity();
        PlayerStateSession session = stateManager.getOrCreateSession(player);
        
        // 记录攻击者（如果是实体攻击）
        if (event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent entityEvent = (EntityDamageByEntityEvent) event;
            session.setLastAttacker(entityEvent.getDamager());
        }
        
        // 触发受击规则
        actionEngine.fireRules(player, ActionType.DAMAGED, TriggerWhen.INSTANT);
    }
    
    /**
     * 飞行切换事件
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        PlayerStateSession session = stateManager.getOrCreateSession(player);
        
        boolean isFlying = event.isFlying();
        session.setFlying(isFlying);
        
        if (isFlying) {
            actionEngine.fireRules(player, ActionType.FLY, TriggerWhen.START);
        } else {
            actionEngine.fireRules(player, ActionType.FLY, TriggerWhen.END);
        }
    }
    
    /**
     * 滑翔切换事件
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityToggleGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        
        Player player = (Player) event.getEntity();
        PlayerStateSession session = stateManager.getOrCreateSession(player);
        
        boolean isGliding = event.isGliding();
        session.setGliding(isGliding);
        
        if (isGliding) {
            actionEngine.fireRules(player, ActionType.GLIDE, TriggerWhen.START);
        } else {
            actionEngine.fireRules(player, ActionType.GLIDE, TriggerWhen.END);
        }
    }
    
    /**
     * 游泳切换事件
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityToggleSwim(EntityToggleSwimEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        
        Player player = (Player) event.getEntity();
        PlayerStateSession session = stateManager.getOrCreateSession(player);
        
        boolean isSwimming = event.isSwimming();
        session.setSwimming(isSwimming);
        
        if (isSwimming) {
            actionEngine.fireRules(player, ActionType.SWIM, TriggerWhen.START);
        } else {
            actionEngine.fireRules(player, ActionType.SWIM, TriggerWhen.END);
        }
    }
    
    /**
     * 载具进入事件
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getEntered() instanceof Player)) return;
        
        Player player = (Player) event.getEntered();
        PlayerStateSession session = stateManager.getOrCreateSession(player);
        
        // 判断是船只还是其他载具
        if (event.getVehicle().getType().name().contains("BOAT")) {
            session.setInBoat(true);
            session.setCurrentVehicle(event.getVehicle());
            actionEngine.fireRules(player, ActionType.INBOAT, TriggerWhen.START);
        } else {
            session.setRiding(true);
            session.setCurrentMount(event.getVehicle());
            actionEngine.fireRules(player, ActionType.RIDE, TriggerWhen.START);
        }
    }
    
    /**
     * 载具退出事件
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleExit(VehicleExitEvent event) {
        if (!(event.getExited() instanceof Player)) return;
        
        Player player = (Player) event.getExited();
        PlayerStateSession session = stateManager.getOrCreateSession(player);
        
        // 判断是船只还是其他载具
        if (event.getVehicle().getType().name().contains("BOAT")) {
            if (session.isInBoat()) {
                session.setInBoat(false);
                session.setCurrentVehicle(null);
                actionEngine.fireRules(player, ActionType.INBOAT, TriggerWhen.END);
            }
        } else {
            if (session.isRiding()) {
                session.setRiding(false);
                session.setCurrentMount(null);
                actionEngine.fireRules(player, ActionType.RIDE, TriggerWhen.END);
            }
        }
    }
    
    /**
     * 玩家移动事件 - 用于悬停检测
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // 悬停检测由TickScheduler处理，这里只更新速度
        Player player = event.getPlayer();
        PlayerStateSession session = stateManager.getSession(player);
        
        if (session != null) {
            session.updateVelocity(player.getVelocity());
        }
    }
}