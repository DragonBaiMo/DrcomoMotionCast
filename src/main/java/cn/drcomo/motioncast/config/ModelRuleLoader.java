package cn.drcomo.motioncast.config;

import cn.drcomo.corelib.util.DebugUtil;
import cn.drcomo.corelib.config.YamlUtil;
import cn.drcomo.corelib.config.ConfigValidator;
import cn.drcomo.corelib.config.ValidationResult;
import cn.drcomo.motioncast.rules.ActionRule;
import cn.drcomo.motioncast.rules.ActionType;
import cn.drcomo.motioncast.rules.TriggerWhen;
import cn.drcomo.motioncast.rules.RuleMeta;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 模型规则加载器
 * 负责从YAML文件加载、解析和管理所有的动作规则
 */
public class ModelRuleLoader {
    
    private final JavaPlugin plugin;
    private final YamlUtil yamlUtil;
    private final DebugUtil logger;
    private final ConfigValidator configValidator;
    
    // 存储所有已加载的规则 - 线程安全
    private final Map<String, List<ActionRule>> modelRules = new ConcurrentHashMap<>();
    
    // 规则索引 - 用于快速查询
    private final Map<String, Map<ActionType, Map<TriggerWhen, List<ActionRule>>>> ruleIndex = new ConcurrentHashMap<>();
    
    // 文件监控句柄
    private final Map<String, YamlUtil.ConfigWatchHandle> watchHandles = new ConcurrentHashMap<>();
    
    public ModelRuleLoader(JavaPlugin plugin, YamlUtil yamlUtil, DebugUtil logger) {
        this.plugin = plugin;
        this.yamlUtil = yamlUtil;
        this.logger = logger;
        this.configValidator = new ConfigValidator(yamlUtil, logger);
        
        initializeValidator();
    }
    
    /**
     * 初始化配置验证器
     */
    private void initializeValidator() {
        // 全局初始化校验器实例（规则按文件动态声明，不在此处静态注册）
        logger.debug("配置验证器初始化完成");
    }
    
    /**
     * 加载所有模型规则文件
     */
    public void loadAllRules() {
        // 1) 确保 models 目录存在
        yamlUtil.ensureDirectory("models");
        // 2) 首次安装时将 JAR 内 models 文件夹整体复制到数据目录（已存在则跳过）
        try {
            yamlUtil.ensureFolderAndCopyDefaults("models", "models");
        } catch (Exception e) {
            logger.warn("复制默认 models 资源失败: " + e.getMessage());
        }

        // 3) 清空现有数据
        clearAllRules();

        // 4) 遍历加载数据目录 models/ 下的全部 yml
        Map<String, org.bukkit.configuration.file.YamlConfiguration> configs;
        try {
            configs = yamlUtil.loadAllConfigsInFolder("models");
        } catch (Exception e) {
            logger.error("扫描 models 目录失败: " + e.getMessage());
            return;
        }

        if (configs == null || configs.isEmpty()) {
            logger.warn("models 目录下没有找到任何配置文件");
            return;
        }

        int loadedCount = 0;
        int totalRules = 0;

        for (Map.Entry<String, org.bukkit.configuration.file.YamlConfiguration> entry : configs.entrySet()) {
            String configName = entry.getKey();
            FileConfiguration config = entry.getValue();
            try {
                if (loadModelConfig(configName, config)) {
                    loadedCount++;
                    List<ActionRule> rules = modelRules.get(configName);
                    if (rules != null) {
                        totalRules += rules.size();
                    }
                }
            } catch (Exception e) {
                logger.error("加载模型文件 " + configName + ".yml 失败: " + e.getMessage());
            }
        }

        logger.info("成功加载 " + loadedCount + " 个模型文件，共 " + totalRules + " 条规则");

        // 5) 重建索引
        rebuildRuleIndex();
    }
    
    /**
     * 加载单个模型文件
     */
    private boolean loadModelFile(String configName, File file) {
        try {
            // 加载配置文件
            yamlUtil.loadConfig(configName);
            FileConfiguration config = yamlUtil.getConfig(configName);
            
            if (config == null) {
                logger.error("无法加载配置文件: " + file.getName());
                return false;
            }
            
            // 使用 ConfigValidator 进行完整的配置结构与字段校验
            ConfigValidator validator = new ConfigValidator(yamlUtil, logger);
            
            // 顶层字段：模型ID
            validator.validateString("model")
                    .required()
                    .custom(v -> v != null && v.toString().trim().length() > 0, "模型ID不能为空");
            
            // 顶层字段：rules 为列表，必须存在且至少包含1条规则
            java.util.List<?> rulesList = config.getList("rules");
            if (rulesList == null) {
                logger.error("配置文件 " + file.getName() + " 缺少必需的 'rules' 列表");
                return false;
            }
            if (rulesList.isEmpty()) {
                logger.error("配置文件 " + file.getName() + " 的 'rules' 列表为空");
                return false;
            }

            // 逐条规则校验（在同一个 validator 中声明所有规则路径，统一校验）
            for (int i = 0; i < rulesList.size(); i++) {
                String base = "rules." + i;
                
                // 基本必填项
                validator.validateString(base + ".id").required()
                        .custom(v -> v != null && v.toString().trim().length() > 0, "规则ID不能为空");
                validator.validateEnum(base + ".action", ActionType.class).required();
                validator.validateEnum(base + ".when", TriggerWhen.class).required();
                validator.validateString(base + ".skill").required()
                        .custom(v -> v != null && v.toString().trim().length() > 0, "技能名称不能为空");
                
                // 可选项 + 约束
                validator.validateNumber(base + ".cd")
                        .custom(v -> v == null || ((Number) v).intValue() >= 0, "cd 必须为>=0的整数");
                validator.validateString(base + ".target");
                validator.validateString(base + ".require");
                
                // when 与 every/after 的关联约束
                validator.validateNumber(base + ".every")
                        .custom(v -> {
                            TriggerWhen w = TriggerWhen.fromString(config.getString(base + ".when"));
                            if (w == TriggerWhen.TICK) {
                                if (v == null) return false;
                                return ((Number) v).intValue() >= 1;
                            }
                            return true;
                        }, "当 when=tick 时，every 必须为>=1的整数");
                
                validator.validateNumber(base + ".after")
                        .custom(v -> {
                            TriggerWhen w = TriggerWhen.fromString(config.getString(base + ".when"));
                            if (w == TriggerWhen.DURATION) {
                                if (v == null) return false;
                                return ((Number) v).intValue() > 0;
                            }
                            return true;
                        }, "当 when=duration 时，after 必须为>0的整数");
                
                // meta 子项（部分类型可校验）
                validator.validateEnum(base + ".meta.mount", EntityType.class);
                validator.validateNumber(base + ".meta.hover_min_ticks")
                        .custom(v -> v == null || ((Number) v).intValue() >= 0, "hover_min_ticks 必须为>=0的整数");
                // meta.boat 为布尔值，当前 ConfigValidator 未提供布尔类型专用校验，交由后续解析处理
            }
            
            ValidationResult result = validator.validate(config);
            if (!result.isSuccess()) {
                logger.error("配置文件 " + file.getName() + " 存在以下错误：");
                for (String err : result.getErrors()) {
                    logger.error(" - " + err);
                }
                return false;
            }
            
            // 解析模型规则
            String modelId = config.getString("model");
            List<ActionRule> rules = parseRules(modelId, config);
            
            if (rules.isEmpty()) {
                logger.warn("配置文件 " + file.getName() + " 中没有有效的规则");
                return false;
            }
            
            // 存储规则
            modelRules.put(configName, rules);
            
            // 不启用文件监听（按需手动重载）
            // setupFileWatch(configName, file);
            
            logger.info("成功加载模型 " + modelId + " 的 " + rules.size() + " 条规则");
            return true;
            
        } catch (Exception e) {
            logger.error("加载模型文件 " + file.getName() + " 时发生异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 基于已加载的配置对象进行单文件加载（用于从 models/ 目录遍历后的加载流程）
     */
    private boolean loadModelConfig(String configName, FileConfiguration config) {
        try {
            // 使用 ConfigValidator 进行完整的配置结构与字段校验
            ConfigValidator validator = new ConfigValidator(yamlUtil, logger);

            // 顶层字段：模型ID
            validator.validateString("model")
                    .required()
                    .custom(v -> v != null && v.toString().trim().length() > 0, "模型ID不能为空");

            // 顶层字段：rules 为列表，必须存在且至少包含1条规则
            java.util.List<?> rulesList2 = config.getList("rules");
            if (rulesList2 == null) {
                logger.error("配置文件 " + configName + ".yml 缺少必需的 'rules' 列表");
                return false;
            }
            if (rulesList2.isEmpty()) {
                logger.error("配置文件 " + configName + ".yml 的 'rules' 列表为空");
                return false;
            }

            // 逐条规则校验（使用 Map 列表，兼容 Bukkit YamlConfiguration 对列表项的表示）
            java.util.List<String> errors = new java.util.ArrayList<>();
            java.util.List<java.util.Map<?, ?>> rulesMapList = config.getMapList("rules");
            for (int i = 0; i < rulesMapList.size(); i++) {
                java.util.Map<?, ?> rs = rulesMapList.get(i);
                if (rs == null) {
                    errors.add("rules." + i + " 不是对象");
                    continue;
                }

                String id = getString(rs, "id");
                if (id == null || id.trim().isEmpty()) {
                    errors.add("缺少配置项: rules." + i + ".id");
                }

                String actionStr = getString(rs, "action");
                ActionType action = ActionType.fromString(actionStr);
                if (actionStr == null || actionStr.trim().isEmpty()) {
                    errors.add("缺少配置项: rules." + i + ".action");
                } else if (action == null) {
                    errors.add("无效动作类型: rules." + i + ".action=" + actionStr);
                }

                String whenStr = getString(rs, "when");
                TriggerWhen when = whenStr == null ? null : TriggerWhen.fromString(whenStr);
                if (whenStr == null || whenStr.trim().isEmpty()) {
                    errors.add("缺少配置项: rules." + i + ".when");
                } else if (when == null) {
                    errors.add("无效触发时机: rules." + i + ".when=" + whenStr);
                }

                String skill = getString(rs, "skill");
                if (skill == null || skill.trim().isEmpty()) {
                    errors.add("缺少配置项: rules." + i + ".skill");
                }

                Integer cdVal = getInteger(rs, "cd");
                if (cdVal != null && cdVal.intValue() < 0) {
                    errors.add("cd 必须为>=0的整数: rules." + i + ".cd");
                }

                if (when == TriggerWhen.TICK) {
                    Integer everyVal = getInteger(rs, "every");
                    if (everyVal == null || everyVal.intValue() < 1) {
                        errors.add("当 when=tick 时，every 必须为>=1的整数: rules." + i + ".every");
                    }
                }

                if (when == TriggerWhen.DURATION) {
                    Integer afterVal = getInteger(rs, "after");
                    if (afterVal == null || afterVal.intValue() <= 0) {
                        errors.add("当 when=duration 时，after 必须为>0的整数: rules." + i + ".after");
                    }
                }
            }

            if (!errors.isEmpty()) {
                logger.warn("配置校验失败，共 " + errors.size() + " 处错误");
                for (String err : errors) {
                    logger.warn(err);
                }
                return false;
            }

            ValidationResult result = validator.validate(config);
            if (!result.isSuccess()) {
                logger.error("配置文件 " + configName + ".yml 存在以下错误：");
                for (String err : result.getErrors()) {
                    logger.error(" - " + err);
                }
                return false;
            }

            // 解析模型规则
            String modelId = config.getString("model");
            List<ActionRule> rules = parseRules(modelId, config);

            if (rules.isEmpty()) {
                logger.warn("配置文件 " + configName + ".yml 中没有有效的规则");
                return false;
            }

            // 存储规则
            modelRules.put(configName, rules);

            // 暂不启用文件监听（loadAllConfigsInFolder 的键空间可能不包含目录信息，避免误监听）

            logger.info("成功加载模型 " + modelId + " 的 " + rules.size() + " 条规则");
            return true;

        } catch (Exception e) {
            logger.error("加载模型文件 " + configName + ".yml 时发生异常: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 解析规则列表
     */
    private List<ActionRule> parseRules(String modelId, FileConfiguration config) {
        List<ActionRule> rules = new ArrayList<>();

        java.util.List<java.util.Map<?, ?>> list = config.getMapList("rules");
        if (list == null || list.isEmpty()) {
            return rules;
        }

        for (int i = 0; i < list.size(); i++) {
            java.util.Map<?, ?> ruleMap = list.get(i);
            if (ruleMap == null) continue;

            try {
                ActionRule rule = parseRule(modelId, ruleMap);
                if (rule != null && rule.isValid()) {
                    rules.add(rule);
                } else {
                    logger.warn("规则索引 " + i + " 无效，已跳过");
                }
            } catch (Exception e) {
                logger.error("解析规则索引 " + i + " 失败: " + e.getMessage());
            }
        }

        return rules;
    }

    private ActionRule parseRule(String modelId, java.util.Map<?, ?> section) {
        // 前置已完成严格校验；此处做防御性检查避免空指针
        String idStr = getString(section, "id");
        String actionStr = getString(section, "action");
        String whenStr = getString(section, "when");
        String skillStr = getString(section, "skill");
        if (idStr == null || actionStr == null || whenStr == null || skillStr == null) {
            logger.error("规则配置验证失败: 缺少必需字段");
            return null;
        }

        ActionRule rule = new ActionRule();

        // 基本信息
        rule.setModelId(modelId);
        rule.setId(idStr);

        // 动作类型
        ActionType action = ActionType.fromString(actionStr);
        if (action == null) {
            logger.error("不支持的动作类型: " + actionStr);
            return null;
        }
        rule.setAction(action);

        // 触发时机
        TriggerWhen when = TriggerWhen.fromString(whenStr);
        if (when == null) {
            logger.error("不支持的触发时机: " + whenStr);
            return null;
        }
        rule.setWhen(when);

        // 技能名称
        rule.setSkill(skillStr);

        // 可选参数
        rule.setEvery(getInt(section, "every", 1));
        rule.setAfter(getInt(section, "after", 0));
        rule.setCooldown(getInt(section, "cd", 0));
        rule.setTarget(getString(section, "target"));
        rule.setRequire(getString(section, "require"));

        // 解析元数据
        RuleMeta meta = parseMeta(getMap(section, "meta"));
        rule.setMeta(meta);

        return rule;
    }
    
    /**
     * 解析规则元数据
     */
    private RuleMeta parseMeta(java.util.Map<?, ?> metaSection) {
        RuleMeta meta = new RuleMeta();

        if (metaSection == null) {
            return meta;
        }

        // 解析骑乘类型
        String mountStr = getString(metaSection, "mount");
        if (mountStr != null) {
            try {
                EntityType mountType = EntityType.valueOf(mountStr.toUpperCase());
                meta.setMountType(mountType);
            } catch (IllegalArgumentException e) {
                logger.warn("无效的骑乘类型: " + mountStr);
            }
        }

        // 解析船只限制
        Boolean boatOnly = getBoolean(metaSection, "boat");
        meta.setBoatOnly(boatOnly != null ? boatOnly.booleanValue() : false);

        // 解析悬停最小tick数
        Integer hoverTicks = getInteger(metaSection, "hover_min_ticks");
        if (hoverTicks != null) {
            meta.setHoverMinTicks(hoverTicks.intValue());
        }

        // 解析内部取消事件开关
        Boolean cancelEvent = getBoolean(metaSection, "cancel_event");
        if (cancelEvent != null) {
            meta.setCancelEvent(cancelEvent.booleanValue());
        }

        return meta;
    }

    // ——— Map 安全取值工具方法 ———
    private String getString(java.util.Map<?, ?> map, String key) {
        if (map == null) return null;
        Object v = map.get(key);
        if (v == null) return null;
        return String.valueOf(v);
    }

    private Integer getInteger(java.util.Map<?, ?> map, String key) {
        if (map == null) return null;
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private int getInt(java.util.Map<?, ?> map, String key, int def) {
        Integer v = getInteger(map, key);
        return v != null ? v.intValue() : def;
    }

    private Boolean getBoolean(java.util.Map<?, ?> map, String key) {
        if (map == null) return null;
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof Boolean) return (Boolean) v;
        String s = String.valueOf(v).toLowerCase(Locale.ROOT);
        if ("true".equals(s)) return Boolean.TRUE;
        if ("false".equals(s)) return Boolean.FALSE;
        return null;
    }

    private java.util.Map<?, ?> getMap(java.util.Map<?, ?> map, String key) {
        if (map == null) return null;
        Object v = map.get(key);
        if (v instanceof java.util.Map) return (java.util.Map<?, ?>) v;
        return null;
    }
    /**
     * 重建规则索引
     */
    private void rebuildRuleIndex() {
        ruleIndex.clear();
        
        for (List<ActionRule> rules : modelRules.values()) {
            for (ActionRule rule : rules) {
                String modelId = rule.getModelId();
                ActionType action = rule.getAction();
                TriggerWhen when = rule.getWhen();
                
                ruleIndex.computeIfAbsent(modelId, k -> new ConcurrentHashMap<>())
                         .computeIfAbsent(action, k -> new ConcurrentHashMap<>())
                         .computeIfAbsent(when, k -> new ArrayList<>())
                         .add(rule);
            }
        }
        
        logger.debug("规则索引重建完成");
    }
    
    /**
     * 获取指定模型、动作和触发时机的规则列表
     */
    public List<ActionRule> getRules(String modelId, ActionType action, TriggerWhen when) {
        Map<ActionType, Map<TriggerWhen, List<ActionRule>>> modelIndex = ruleIndex.get(modelId);
        if (modelIndex == null) {
            return Collections.emptyList();
        }
        
        Map<TriggerWhen, List<ActionRule>> actionIndex = modelIndex.get(action);
        if (actionIndex == null) {
            return Collections.emptyList();
        }
        
        List<ActionRule> rules = actionIndex.get(when);
        return rules != null ? new ArrayList<>(rules) : Collections.emptyList();
    }
    
    /**
     * 获取指定模型的所有规则
     */
    public List<ActionRule> getModelRules(String modelId) {
        return modelRules.values().stream()
                .flatMap(List::stream)
                .filter(rule -> modelId.equals(rule.getModelId()))
                .collect(Collectors.toList());
    }
    
    /**
     * 获取所有已加载的模型ID
     */
    public Set<String> getLoadedModels() {
        return modelRules.values().stream()
                .flatMap(List::stream)
                .map(ActionRule::getModelId)
                .collect(Collectors.toSet());
    }
    
    /**
     * 获取规则总数
     */
    public int getTotalRuleCount() {
        return modelRules.values().stream()
                .mapToInt(List::size)
                .sum();
    }
    
    /**
     * 清空所有规则
     */
    public void clearAllRules() {
        // 关闭文件监听
        watchHandles.values().forEach(handle -> {
            try {
                handle.close();
            } catch (Exception e) {
                logger.warn("关闭文件监听失败: " + e.getMessage());
            }
        });
        watchHandles.clear();
        
        // 清空数据
        modelRules.clear();
        ruleIndex.clear();
        
        logger.debug("已清空所有规则数据");
    }
    
    /**
     * 关闭资源
     */
    public void shutdown() {
        clearAllRules();
        logger.info("模型规则加载器已关闭");
    }
}
