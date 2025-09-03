package cn.drcomo.motioncast.rules;

import org.bukkit.entity.EntityType;

/**
 * 规则元数据类
 * 存储规则的额外配置信息
 */
public class RuleMeta {
    
    /**
     * 骑乘类型过滤（用于ride动作）
     */
    private EntityType mountType;
    
    /**
     * 是否仅限船只（用于inboat动作）
     */
    private boolean boatOnly;
    
    /**
     * 是否在匹配规则时主动取消对应的原始事件（用于attack/damaged等可取消事件）
     * 设计原因：MythicMobs 的 CancelEvent 仅在其内部事件触发上下文中生效，
     * 通过 API castSkill 触发时无法关联 Bukkit 原事件。为保证可控性，
     * 在规则层提供该显式开关，由插件在对应监听中主动取消事件。
     */
    private boolean cancelEvent;

    /**
     * 悬停最小tick数（用于hover动作）
     */
    private int hoverMinTicks;
    
    public RuleMeta() {
        this.boatOnly = false;
        this.cancelEvent = false;
        this.hoverMinTicks = -1; // -1表示使用全局设置
    }
    
    // Getter和Setter方法
    
    public EntityType getMountType() {
        return mountType;
    }
    
    public void setMountType(EntityType mountType) {
        this.mountType = mountType;
    }
    
    public boolean isBoatOnly() {
        return boatOnly;
    }
    
    public void setBoatOnly(boolean boatOnly) {
        this.boatOnly = boatOnly;
    }

    public boolean isCancelEvent() {
        return cancelEvent;
    }

    public void setCancelEvent(boolean cancelEvent) {
        this.cancelEvent = cancelEvent;
    }
    
    public int getHoverMinTicks() {
        return hoverMinTicks;
    }
    
    public void setHoverMinTicks(int hoverMinTicks) {
        this.hoverMinTicks = hoverMinTicks;
    }
    
    /**
     * 检查是否有任何元数据设置
     */
    public boolean isEmpty() {
        return mountType == null && 
               !boatOnly && 
               !cancelEvent &&
               hoverMinTicks == -1;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("RuleMeta{");
        boolean hasContent = false;
        
        if (mountType != null) {
            sb.append("mountType=").append(mountType);
            hasContent = true;
        }
        
        if (boatOnly) {
            if (hasContent) sb.append(", ");
            sb.append("boatOnly=true");
            hasContent = true;
        }
        
        if (cancelEvent) {
            if (hasContent) sb.append(", ");
            sb.append("cancelEvent=true");
            hasContent = true;
        }

        if (hoverMinTicks != -1) {
            if (hasContent) sb.append(", ");
            sb.append("hoverMinTicks=").append(hoverMinTicks);
        }
        
        sb.append('}');
        return sb.toString();
    }
}
