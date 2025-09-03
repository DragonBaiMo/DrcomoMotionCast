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
     * 悬停最小tick数（用于hover动作）
     */
    private int hoverMinTicks;

    /**
     * 是否在命中该规则时取消可取消的原始事件（如攻击/受击事件）
     * 默认 false
     */
    private boolean cancelEvent;
    
    /**
     * 事件取消模式（扩展策略）
     * 默认为 NEVER（不取消）
     */
    private CancelEventMode cancelEventMode;
    
    /**
     * 取消条件表达式（当模式为 CONDITION 时生效）
     */
    private String cancelCondition;
    
    public RuleMeta() {
        this.boatOnly = false;
        this.hoverMinTicks = -1; // -1表示使用全局设置
        this.cancelEvent = false;
        this.cancelEventMode = CancelEventMode.NEVER;
        this.cancelCondition = null;
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
    
    public int getHoverMinTicks() {
        return hoverMinTicks;
    }
    
    public void setHoverMinTicks(int hoverMinTicks) {
        this.hoverMinTicks = hoverMinTicks;
    }

    public boolean isCancelEvent() {
        return cancelEvent;
    }

    public void setCancelEvent(boolean cancelEvent) {
        this.cancelEvent = cancelEvent;
    }
    
    public CancelEventMode getCancelEventMode() {
        return cancelEventMode;
    }

    public void setCancelEventMode(CancelEventMode cancelEventMode) {
        this.cancelEventMode = cancelEventMode != null ? cancelEventMode : CancelEventMode.NEVER;
    }

    public String getCancelCondition() {
        return cancelCondition;
    }

    public void setCancelCondition(String cancelCondition) {
        this.cancelCondition = cancelCondition;
    }

    /**
     * 获取生效的取消模式（兼容旧版 cancelEvent 布尔开关）
     */
    public CancelEventMode getEffectiveCancelMode() {
        if (cancelEventMode != null && cancelEventMode != CancelEventMode.NEVER) {
            return cancelEventMode;
        }
        return cancelEvent ? CancelEventMode.ALWAYS : CancelEventMode.NEVER;
    }
    
    /**
     * 检查是否有任何元数据设置
     */
    public boolean isEmpty() {
        return mountType == null && 
               !boatOnly && 
               hoverMinTicks == -1 &&
               !cancelEvent &&
               (cancelEventMode == null || cancelEventMode == CancelEventMode.NEVER) &&
               (cancelCondition == null || cancelCondition.trim().isEmpty());
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
        
        if (hoverMinTicks != -1) {
            if (hasContent) sb.append(", ");
            sb.append("hoverMinTicks=").append(hoverMinTicks);
            hasContent = true;
        }

        if (cancelEvent) {
            if (hasContent) sb.append(", ");
            sb.append("cancelEvent=true");
            hasContent = true;
        }

        if (cancelEventMode != null && cancelEventMode != CancelEventMode.NEVER) {
            if (hasContent) sb.append(", ");
            sb.append("cancelEventMode=").append(cancelEventMode);
            hasContent = true;
        }

        if (cancelCondition != null && !cancelCondition.trim().isEmpty()) {
            if (hasContent) sb.append(", ");
            sb.append("cancelCondition=").append(cancelCondition);
            hasContent = true;
        }
        
        sb.append('}');
        return sb.toString();
    }
}