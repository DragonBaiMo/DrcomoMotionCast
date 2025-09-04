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
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
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
     * 挥手（左键主手挥动）事件
     * 说明：
     * - 使用 PlayerAnimationEvent 捕获玩家主手的 ARM_SWING 动画；
     * - 仅当类型为主手挥动（ARM_SWING）时触发，避免副手/盾牌导致的误触发；
     * - 触发后映射为动作类型 ActionType.WAVE，触发时机为 INSTANT。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerSwing(PlayerAnimationEvent event) {
        PlayerAnimationType type = event.getAnimationType();
        if (type != PlayerAnimationType.ARM_SWING) {
            return; // 仅处理主手挥动，避免副手挥动
        }

        Player player = event.getPlayer();
        // 更新会话（仅确保存在，不引入额外状态）
        stateManager.getOrCreateSession(player);

        // 触发挥手动作规则（即时）
        actionEngine.fireRules(player, ActionType.SWING, TriggerWhen.INSTANT);
    }

    /**
     * 玩家攻击事件（物理近战入口）
     * 使用 HIGHEST + ignoreCancelled=true，便于技能内 CancelEvent 同步生效并避免重复处理。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;

        // 仅处理物理近战（排除投射物、技能等其他伤害类型）
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK &&
            cause != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            return;
        }

        Player player = (Player) event.getDamager();
        PlayerStateSession session = stateManager.getOrCreateSession(player);

        // 绑定本次原始攻击事件，供引擎在执行技能时注入 Mythic 元数据
        session.setCustomData("last_attack_event", event);

        // 记录受害者
        session.setLastVictim(event.getEntity());

        // 触发攻击规则
        actionEngine.fireRules(player, ActionType.ATTACK, TriggerWhen.INSTANT);

        // 根据规则元数据决定是否取消原始伤害事件（作为保险兜底）
        if (actionEngine.shouldCancelEvent(player, ActionType.ATTACK, TriggerWhen.INSTANT)) {
            event.setCancelled(true);
        }

        // 清理事件上下文，避免泄露到其他动作流程
        session.setCustomData("last_attack_event", null);
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

        // 根据规则元数据决定是否取消原始受击事件
        if (actionEngine.shouldCancelEvent(player, ActionType.DAMAGED, TriggerWhen.INSTANT)) {
            event.setCancelled(true);
        }
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

        // 同步更新活跃会话集合
        stateManager.updateActiveStatus(session);
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

        // 同步更新活跃会话集合
        stateManager.updateActiveStatus(session);
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

        // 同步更新活跃会话集合
        stateManager.updateActiveStatus(session);
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

        // 同步更新活跃会话集合
        stateManager.updateActiveStatus(session);
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

        // 同步更新活跃会话集合
        stateManager.updateActiveStatus(session);
    }
    
    /**
     * 玩家移动事件 - 用于悬停检测
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // 悬停检测由 TickScheduler 统一处理，这里仅在位置发生变化时才更新速度，减少高频事件的无效写入
        // 检查位置是否发生变化，避免视角变化等无关事件导致的冗余处理
        if (event.getFrom().equals(event.getTo())) {
            return;
        }
        Player player = event.getPlayer();
        PlayerStateSession session = stateManager.getSession(player);
        
        if (session != null) {
            session.updateVelocity(player.getVelocity());
        }
    }
}
