package cn.drcomo.motioncast.state;

import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 玩家状态会话
 * 跟踪单个玩家的各种动作状态和时间轴
 */
public class PlayerStateSession {
    
    private final UUID playerUUID;
    private final long createdTime;
    
    // 状态标志位
    private volatile boolean flying = false;
    private volatile boolean gliding = false;
    private volatile boolean swimming = false;
    private volatile boolean inBoat = false;
    private volatile boolean riding = false;
    private volatile boolean hovering = false;
    
    // 状态开始时间（用于计算持续时间）
    private final AtomicLong flyingStartTime = new AtomicLong(0);
    private final AtomicLong glidingStartTime = new AtomicLong(0);
    private final AtomicLong swimmingStartTime = new AtomicLong(0);
    private final AtomicLong inBoatStartTime = new AtomicLong(0);
    private final AtomicLong ridingStartTime = new AtomicLong(0);
    private final AtomicLong hoveringStartTime = new AtomicLong(0);
    
    // 状态计数器（tick计数）
    private final AtomicInteger flyingTicks = new AtomicInteger(0);
    private final AtomicInteger glidingTicks = new AtomicInteger(0);
    private final AtomicInteger swimmingTicks = new AtomicInteger(0);
    private final AtomicInteger inBoatTicks = new AtomicInteger(0);
    private final AtomicInteger ridingTicks = new AtomicInteger(0);
    private final AtomicInteger hoveringTicks = new AtomicInteger(0);
    
    // 悬停检测相关
    private Vector lastVelocity = new Vector(0, 0, 0);
    private int hoverStableCount = 0;
    
    // 事件上下文缓存
    private volatile Entity lastAttacker;
    private volatile Entity lastVictim;
    private volatile Entity currentVehicle;
    private volatile Entity currentMount;
    
    // 上下文缓存时间戳
    private volatile long lastAttackerTime = 0;
    private volatile long lastVictimTime = 0;
    private volatile long vehicleChangeTime = 0;
    private volatile long mountChangeTime = 0;
    
    // 自定义数据存储
    private final ConcurrentHashMap<String, Object> customData = new ConcurrentHashMap<>();
    
    public PlayerStateSession(UUID playerUUID) {
        this.playerUUID = playerUUID;
        this.createdTime = System.currentTimeMillis();
    }
    
    // 基础信息
    public UUID getPlayerUUID() {
        return playerUUID;
    }
    
    public long getCreatedTime() {
        return createdTime;
    }
    
    // 状态管理方法
    
    /**
     * 设置飞行状态
     */
    public void setFlying(boolean flying) {
        if (this.flying != flying) {
            this.flying = flying;
            if (flying) {
                flyingStartTime.set(System.currentTimeMillis());
                flyingTicks.set(0);
            } else {
                flyingStartTime.set(0);
            }
        }
    }
    
    /**
     * 设置滑翔状态
     */
    public void setGliding(boolean gliding) {
        if (this.gliding != gliding) {
            this.gliding = gliding;
            if (gliding) {
                glidingStartTime.set(System.currentTimeMillis());
                glidingTicks.set(0);
            } else {
                glidingStartTime.set(0);
            }
        }
    }
    
    /**
     * 设置游泳状态
     */
    public void setSwimming(boolean swimming) {
        if (this.swimming != swimming) {
            this.swimming = swimming;
            if (swimming) {
                swimmingStartTime.set(System.currentTimeMillis());
                swimmingTicks.set(0);
            } else {
                swimmingStartTime.set(0);
            }
        }
    }
    
    /**
     * 设置乘船状态
     */
    public void setInBoat(boolean inBoat) {
        if (this.inBoat != inBoat) {
            this.inBoat = inBoat;
            if (inBoat) {
                inBoatStartTime.set(System.currentTimeMillis());
                inBoatTicks.set(0);
            } else {
                inBoatStartTime.set(0);
            }
        }
    }
    
    /**
     * 设置骑乘状态
     */
    public void setRiding(boolean riding) {
        if (this.riding != riding) {
            this.riding = riding;
            if (riding) {
                ridingStartTime.set(System.currentTimeMillis());
                ridingTicks.set(0);
            } else {
                ridingStartTime.set(0);
            }
        }
    }
    
    /**
     * 设置悬停状态
     */
    public void setHovering(boolean hovering) {
        if (this.hovering != hovering) {
            this.hovering = hovering;
            if (hovering) {
                hoveringStartTime.set(System.currentTimeMillis());
                hoveringTicks.set(0);
            } else {
                hoveringStartTime.set(0);
            }
        }
    }
    
    // 状态查询方法
    
    public boolean isFlying() { return flying; }
    public boolean isGliding() { return gliding; }
    public boolean isSwimming() { return swimming; }
    public boolean isInBoat() { return inBoat; }
    public boolean isRiding() { return riding; }
    public boolean isHovering() { return hovering; }
    
    // 时间查询方法
    
    public long getFlyingDuration() {
        return flying ? System.currentTimeMillis() - flyingStartTime.get() : 0;
    }
    
    public long getGlidingDuration() {
        return gliding ? System.currentTimeMillis() - glidingStartTime.get() : 0;
    }
    
    public long getSwimmingDuration() {
        return swimming ? System.currentTimeMillis() - swimmingStartTime.get() : 0;
    }
    
    public long getInBoatDuration() {
        return inBoat ? System.currentTimeMillis() - inBoatStartTime.get() : 0;
    }
    
    public long getRidingDuration() {
        return riding ? System.currentTimeMillis() - ridingStartTime.get() : 0;
    }
    
    public long getHoveringDuration() {
        return hovering ? System.currentTimeMillis() - hoveringStartTime.get() : 0;
    }
    
    // Tick计数管理
    
    /**
     * 增加状态tick计数
     */
    public void incrementTicks() {
        if (flying) flyingTicks.incrementAndGet();
        if (gliding) glidingTicks.incrementAndGet();
        if (swimming) swimmingTicks.incrementAndGet();
        if (inBoat) inBoatTicks.incrementAndGet();
        if (riding) ridingTicks.incrementAndGet();
        if (hovering) hoveringTicks.incrementAndGet();
    }
    
    // Tick计数查询
    
    public int getFlyingTicks() { return flyingTicks.get(); }
    public int getGlidingTicks() { return glidingTicks.get(); }
    public int getSwimmingTicks() { return swimmingTicks.get(); }
    public int getInBoatTicks() { return inBoatTicks.get(); }
    public int getRidingTicks() { return ridingTicks.get(); }
    public int getHoveringTicks() { return hoveringTicks.get(); }
    
    // 悬停检测相关
    
    /**
     * 更新速度用于悬停检测
     */
    public void updateVelocity(Vector velocity) {
        this.lastVelocity = velocity.clone();
    }
    
    public Vector getLastVelocity() {
        return lastVelocity.clone();
    }
    
    /**
     * 增加悬停稳定计数
     */
    public void incrementHoverStableCount() {
        hoverStableCount++;
    }
    
    /**
     * 重置悬停稳定计数
     */
    public void resetHoverStableCount() {
        hoverStableCount = 0;
    }
    
    public int getHoverStableCount() {
        return hoverStableCount;
    }
    
    // 事件上下文管理
    
    public void setLastAttacker(Entity attacker) {
        this.lastAttacker = attacker;
        this.lastAttackerTime = System.currentTimeMillis();
    }
    
    public Entity getLastAttacker() {
        return lastAttacker;
    }
    
    public long getLastAttackerTime() {
        return lastAttackerTime;
    }
    
    public void setLastVictim(Entity victim) {
        this.lastVictim = victim;
        this.lastVictimTime = System.currentTimeMillis();
    }
    
    public Entity getLastVictim() {
        return lastVictim;
    }
    
    public long getLastVictimTime() {
        return lastVictimTime;
    }
    
    public void setCurrentVehicle(Entity vehicle) {
        this.currentVehicle = vehicle;
        this.vehicleChangeTime = System.currentTimeMillis();
    }
    
    public Entity getCurrentVehicle() {
        return currentVehicle;
    }
    
    public void setCurrentMount(Entity mount) {
        this.currentMount = mount;
        this.mountChangeTime = System.currentTimeMillis();
    }
    
    public Entity getCurrentMount() {
        return currentMount;
    }
    
    // 自定义数据存储
    
    /**
     * 设置自定义数据
     */
    public void setCustomData(String key, Object value) {
        if (value == null) {
            customData.remove(key);
        } else {
            customData.put(key, value);
        }
    }
    
    /**
     * 获取自定义数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getCustomData(String key, Class<T> type) {
        Object value = customData.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
    
    /**
     * 获取自定义数据（带默认值）
     */
    public <T> T getCustomData(String key, Class<T> type, T defaultValue) {
        T value = getCustomData(key, type);
        return value != null ? value : defaultValue;
    }
    
    /**
     * 检查是否有任何激活的状态
     */
    public boolean hasActiveState() {
        return flying || gliding || swimming || inBoat || riding || hovering;
    }
    
    /**
     * 获取激活的状态数量
     */
    public int getActiveStateCount() {
        int count = 0;
        if (flying) count++;
        if (gliding) count++;
        if (swimming) count++;
        if (inBoat) count++;
        if (riding) count++;
        if (hovering) count++;
        return count;
    }
    
    /**
     * 清理过期的上下文数据
     */
    public void cleanupExpiredContext(long maxAge) {
        long now = System.currentTimeMillis();
        
        if (lastAttacker != null && now - lastAttackerTime > maxAge) {
            lastAttacker = null;
            lastAttackerTime = 0;
        }
        
        if (lastVictim != null && now - lastVictimTime > maxAge) {
            lastVictim = null;
            lastVictimTime = 0;
        }
    }
    
    /**
     * 重置所有状态
     */
    public void reset() {
        flying = false;
        gliding = false;
        swimming = false;
        inBoat = false;
        riding = false;
        hovering = false;
        
        flyingStartTime.set(0);
        glidingStartTime.set(0);
        swimmingStartTime.set(0);
        inBoatStartTime.set(0);
        ridingStartTime.set(0);
        hoveringStartTime.set(0);
        
        flyingTicks.set(0);
        glidingTicks.set(0);
        swimmingTicks.set(0);
        inBoatTicks.set(0);
        ridingTicks.set(0);
        hoveringTicks.set(0);
        
        hoverStableCount = 0;
        lastVelocity = new Vector(0, 0, 0);
        
        lastAttacker = null;
        lastVictim = null;
        currentVehicle = null;
        currentMount = null;
        
        customData.clear();
    }
    
    @Override
    public String toString() {
        return "PlayerStateSession{" +
                "playerUUID=" + playerUUID +
                ", flying=" + flying +
                ", gliding=" + gliding +
                ", swimming=" + swimming +
                ", inBoat=" + inBoat +
                ", riding=" + riding +
                ", hovering=" + hovering +
                ", activeStates=" + getActiveStateCount() +
                '}';
    }
}