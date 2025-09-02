package cn.drcomo.motioncast.targetfunction;

import cn.drcomo.motioncast.state.PlayerStateSession;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * 目标解析上下文
 * 提供目标选择器解析时需要的上下文信息
 */
public class TargetContext {
    
    private final Player player;
    private final PlayerStateSession session;
    private final Location triggerLocation;
    
    // 事件上下文
    private Entity lastAttacker;
    private Entity lastVictim;
    private Entity currentVehicle;
    private Entity currentMount;
    
    // 额外的上下文数据
    private Object eventData;
    
    public TargetContext(Player player, PlayerStateSession session, Location triggerLocation) {
        this.player = player;
        this.session = session;
        this.triggerLocation = triggerLocation;
        
        // 从会话中获取上下文信息
        if (session != null) {
            this.lastAttacker = session.getLastAttacker();
            this.lastVictim = session.getLastVictim();
            this.currentVehicle = session.getCurrentVehicle();
            this.currentMount = session.getCurrentMount();
        }
    }
    
    // 基础信息获取
    
    public Player getPlayer() {
        return player;
    }
    
    public PlayerStateSession getSession() {
        return session;
    }
    
    public Location getTriggerLocation() {
        return triggerLocation != null ? triggerLocation : player.getLocation();
    }
    
    // 上下文实体获取
    
    public Entity getLastAttacker() {
        return lastAttacker;
    }
    
    public void setLastAttacker(Entity lastAttacker) {
        this.lastAttacker = lastAttacker;
    }
    
    public Entity getLastVictim() {
        return lastVictim;
    }
    
    public void setLastVictim(Entity lastVictim) {
        this.lastVictim = lastVictim;
    }
    
    public Entity getCurrentVehicle() {
        return currentVehicle;
    }
    
    public void setCurrentVehicle(Entity currentVehicle) {
        this.currentVehicle = currentVehicle;
    }
    
    public Entity getCurrentMount() {
        return currentMount;
    }
    
    public void setCurrentMount(Entity currentMount) {
        this.currentMount = currentMount;
    }
    
    // 事件数据
    
    public Object getEventData() {
        return eventData;
    }
    
    public void setEventData(Object eventData) {
        this.eventData = eventData;
    }
    
    /**
     * 获取特定类型的事件数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getEventData(Class<T> type) {
        if (eventData != null && type.isInstance(eventData)) {
            return (T) eventData;
        }
        return null;
    }
    
    // 便捷方法
    
    /**
     * 检查玩家是否有效
     */
    public boolean isPlayerValid() {
        return player != null && player.isOnline();
    }
    
    /**
     * 检查最近的攻击者是否有效
     */
    public boolean hasValidAttacker() {
        return lastAttacker != null && lastAttacker.isValid();
    }
    
    /**
     * 检查最近的受害者是否有效
     */
    public boolean hasValidVictim() {
        return lastVictim != null && lastVictim.isValid();
    }
    
    /**
     * 检查当前载具是否有效
     */
    public boolean hasValidVehicle() {
        return currentVehicle != null && currentVehicle.isValid();
    }
    
    /**
     * 检查当前坐骑是否有效
     */
    public boolean hasValidMount() {
        return currentMount != null && currentMount.isValid();
    }
    
    /**
     * 创建玩家位置的上下文
     */
    public static TargetContext fromPlayer(Player player, PlayerStateSession session) {
        return new TargetContext(player, session, player.getLocation());
    }
    
    /**
     * 创建指定位置的上下文
     */
    public static TargetContext fromLocation(Player player, PlayerStateSession session, Location location) {
        return new TargetContext(player, session, location);
    }
    
    /**
     * 创建带事件数据的上下文
     */
    public static TargetContext fromEvent(Player player, PlayerStateSession session, Object eventData) {
        TargetContext context = fromPlayer(player, session);
        context.setEventData(eventData);
        return context;
    }
    
    @Override
    public String toString() {
        return "TargetContext{" +
                "player=" + (player != null ? player.getName() : "null") +
                ", hasAttacker=" + hasValidAttacker() +
                ", hasVictim=" + hasValidVictim() +
                ", hasVehicle=" + hasValidVehicle() +
                ", hasMount=" + hasValidMount() +
                ", hasEventData=" + (eventData != null) +
                '}';
    }
}