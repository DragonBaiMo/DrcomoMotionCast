package cn.drcomo.motioncast.rules;

/**
 * 动作规则实体类
 * 表示一个完整的动作到技能的映射规则
 */
public class ActionRule {
    
    /**
     * 规则唯一标识符
     */
    private String id;
    
    /**
     * 模型标识
     */
    private String modelId;
    
    /**
     * 动作类型
     */
    private ActionType action;
    
    /**
     * 触发时机
     */
    private TriggerWhen when;
    
    /**
     * 触发周期（tick），仅when=TICK时有效
     */
    private int every = 1;
    
    /**
     * 持续时长要求（tick），仅when=DURATION时有效
     */
    private int after = 0;
    
    /**
     * MythicMobs技能名称
     */
    private String skill;
    
    /**
     * 目标选择器
     */
    private String target;
    
    /**
     * 冷却时间（tick）
     */
    private int cooldown = 0;
    
    /**
     * 条件表达式
     */
    private String require;
    
    /**
     * 规则元数据
     */
    private RuleMeta meta;
    
    public ActionRule() {
        this.meta = new RuleMeta();
    }
    
    // Getter和Setter方法
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getModelId() {
        return modelId;
    }
    
    public void setModelId(String modelId) {
        this.modelId = modelId;
    }
    
    public ActionType getAction() {
        return action;
    }
    
    public void setAction(ActionType action) {
        this.action = action;
    }
    
    public TriggerWhen getWhen() {
        return when;
    }
    
    public void setWhen(TriggerWhen when) {
        this.when = when;
    }
    
    public int getEvery() {
        return every;
    }
    
    public void setEvery(int every) {
        this.every = Math.max(1, every); // 至少1tick
    }
    
    public int getAfter() {
        return after;
    }
    
    public void setAfter(int after) {
        this.after = Math.max(0, after);
    }
    
    public String getSkill() {
        return skill;
    }
    
    public void setSkill(String skill) {
        this.skill = skill;
    }
    
    public String getTarget() {
        return target;
    }
    
    public void setTarget(String target) {
        this.target = target;
    }
    
    public int getCooldown() {
        return cooldown;
    }
    
    public void setCooldown(int cooldown) {
        this.cooldown = Math.max(0, cooldown);
    }
    
    public String getRequire() {
        return require;
    }
    
    public void setRequire(String require) {
        this.require = require;
    }
    
    public RuleMeta getMeta() {
        return meta;
    }
    
    public void setMeta(RuleMeta meta) {
        this.meta = meta != null ? meta : new RuleMeta();
    }
    
    /**
     * 验证规则是否有效
     */
    public boolean isValid() {
        // 检查必需字段
        if (id == null || id.trim().isEmpty()) {
            return false;
        }
        
        if (action == null || when == null) {
            return false;
        }
        
        if (skill == null || skill.trim().isEmpty()) {
            return false;
        }
        
        // 检查TICK类型的every参数
        if (when == TriggerWhen.TICK && every <= 0) {
            return false;
        }
        
        // 检查DURATION类型的after参数
        if (when == TriggerWhen.DURATION && after <= 0) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 创建规则的唯一键
     * 用于冷却和缓存
     */
    public String getUniqueKey() {
        return modelId + ":" + id;
    }
    
    /**
     * 检查规则是否匹配指定的动作和触发时机
     */
    public boolean matches(ActionType action, TriggerWhen when) {
        return this.action == action && this.when == when;
    }
    
    @Override
    public String toString() {
        return "ActionRule{" +
                "id='" + id + '\'' +
                ", modelId='" + modelId + '\'' +
                ", action=" + action +
                ", when=" + when +
                ", skill='" + skill + '\'' +
                (target != null ? ", target='" + target + '\'' : "") +
                (cooldown > 0 ? ", cooldown=" + cooldown : "") +
                (when == TriggerWhen.TICK ? ", every=" + every : "") +
                (when == TriggerWhen.DURATION ? ", after=" + after : "") +
                (require != null ? ", require='" + require + '\'' : "") +
                (!meta.isEmpty() ? ", meta=" + meta : "") +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        ActionRule rule = (ActionRule) o;
        return getUniqueKey().equals(rule.getUniqueKey());
    }
    
    @Override
    public int hashCode() {
        return getUniqueKey().hashCode();
    }
}