package cn.drcomo.motioncast.rules;

/**
 * 触发时机枚举
 * 定义规则在何时触发技能
 */
public enum TriggerWhen {
    /**
     * 立即触发 - 事件发生时立刻执行
     */
    INSTANT("instant"),
    
    /**
     * 开始时触发 - 状态开始时执行
     */
    START("start"),
    
    /**
     * 结束时触发 - 状态结束时执行
     */
    END("end"),
    
    /**
     * 每tick触发 - 状态持续期间按周期执行
     */
    TICK("tick"),
    
    /**
     * 持续时长触发 - 状态持续指定时间后执行一次
     */
    DURATION("duration");
    
    private final String configName;
    
    TriggerWhen(String configName) {
        this.configName = configName;
    }
    
    public String getConfigName() {
        return configName;
    }
    
    /**
     * 从配置字符串获取触发时机
     */
    public static TriggerWhen fromString(String str) {
        if (str == null) return null;
        
        for (TriggerWhen when : values()) {
            if (when.configName.equalsIgnoreCase(str)) {
                return when;
            }
        }
        return null;
    }
    
    @Override
    public String toString() {
        return configName;
    }
}