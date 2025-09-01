package cn.drcomo.motioncast.rules;

/**
 * 动作类型枚举
 * 定义插件支持的所有动作类型
 */
public enum ActionType {
    /**
     * 攻击动作 - EntityDamageByEntityEvent
     */
    ATTACK("attack"),
    
    /**
     * 受击动作 - EntityDamageEvent/EntityDamageByEntityEvent
     */
    DAMAGED("damaged"),
    
    /**
     * 上下船动作 - VehicleEnterEvent/VehicleExitEvent (Boat)
     */
    INBOAT("inboat"),
    
    /**
     * 骑乘动作 - VehicleEnterEvent/VehicleExitEvent (非Boat)
     */
    RIDE("ride"),
    
    /**
     * 飞行动作 - PlayerToggleFlightEvent
     */
    FLY("fly"),
    
    /**
     * 滑翔动作 - EntityToggleGlideEvent
     */
    GLIDE("glide"),
    
    /**
     * 游泳动作 - EntityToggleSwimEvent
     */
    SWIM("swim"),
    
    /**
     * 悬停动作 - 自定义判定
     */
    HOVER("hover"),
    
    /**
     * 动画播放动作 - ModelEngine集成
     */
    ANIM_PLAY("anim.play");
    
    private final String configName;
    
    ActionType(String configName) {
        this.configName = configName;
    }
    
    public String getConfigName() {
        return configName;
    }
    
    /**
     * 从配置字符串获取动作类型
     */
    public static ActionType fromString(String str) {
        if (str == null) return null;
        
        for (ActionType type : values()) {
            if (type.configName.equalsIgnoreCase(str)) {
                return type;
            }
        }
        return null;
    }
    
    @Override
    public String toString() {
        return configName;
    }
}