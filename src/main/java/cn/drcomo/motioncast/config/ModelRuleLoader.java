package cn.drcomo.motioncast.config;

import cn.drcomo.corelib.util.DebugUtil;
import cn.drcomo.corelib.config.YamlUtil;
import cn.drcomo.corelib.config.ConfigValidator;
import cn.drcomo.corelib.config.ValidationResult;
import cn.drcomo.motioncast.rules.ActionRule;
import cn.drcomo.motioncast.rules.ActionType;
import cn.drcomo.motioncast.rules.TriggerWhen;
import cn.drcomo.motioncast.rules.RuleMeta;

import org.bukkit.configuration.ConfigurationSection;
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
        // 确保models目录存在
        File modelsDir = new File(plugin.getDataFolder(), "models");
        if (!modelsDir.exists()) {
            modelsDir.mkdirs();
            logger.info("已创建 models 目录");
        }
        
        // 清空现有数据
        clearAllRules();
        
        // 加载所有.yml文件
        File[] files = modelsDir.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null || files.length == 0) {
            logger.warn("models 目录下没有找到任何配置文件");
            return;
        }
        
        int loadedCount = 0;
        int totalRules = 0;
        
        for (File file : files) {
            try {
                String fileName = file.getName();
                String configName = fileName.substring(0, fileName.lastIndexOf('.'));
                
                if (loadModelFile(configName, file)) {
                    loadedCount++;
                    List<ActionRule> rules = modelRules.get(configName);
                    if (rules != null) {
                        totalRules += rules.size();
                    }
                }
            } catch (Exception e) {
                logger.error("加载模型文件 " + file.getName() + " 失败: " + e.getMessage());
            }
        }
        
        logger.info("成功加载 " + loadedCount + " 个模型文件，共 " + totalRules + " 条规则");
        
        // 重建索引
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
            
            // 顶层字段：rules 节必须存在且包含至少1条规则（通过手动检查补充）
            ConfigurationSection rulesSection = config.getConfigurationSection("rules");
            if (rulesSection == null) {
                logger.error("配置文件 " + file.getName() + " 缺少必需的 'rules' 配置节");
                return false;
            }
            if (rulesSection.getKeys(false).isEmpty()) {
                logger.error("配置文件 " + file.getName() + " 的 'rules' 配置节为空");
                return false;
            }
            
            // 逐条规则校验（在同一个 validator 中声明所有规则路径，统一校验）
            for (String key : rulesSection.getKeys(false)) {
                String base = "rules." + key;
                
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
            
            // 设置文件监听
            setupFileWatch(configName, file);
            
            logger.info("成功加载模型 " + modelId + " 的 " + rules.size() + " 条规则");
            return true;
            
        } catch (Exception e) {
            logger.error("加载模型文件 " + file.getName() + " 时发生异常: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 解析规则列表
     */
    private List<ActionRule> parseRules(String modelId, FileConfiguration config) {
        List<ActionRule> rules = new ArrayList<>();
        
        ConfigurationSection rulesSection = config.getConfigurationSection("rules");
        if (rulesSection == null) {
            return rules;
        }
        
        for (String key : rulesSection.getKeys(false)) {
            ConfigurationSection ruleSection = rulesSection.getConfigurationSection(key);
            if (ruleSection == null) continue;
            
            try {
                ActionRule rule = parseRule(modelId, ruleSection);
                if (rule != null && rule.isValid()) {
                    rules.add(rule);
                } else {
                    logger.warn("规则 " + key + " 无效，已跳过");
                }
            } catch (Exception e) {
                logger.error("解析规则 " + key + " 失败: " + e.getMessage());
            }
        }
        
        return rules;
    }
    
    /**
     * 解析单个规则
     */
    private ActionRule parseRule(String modelId, ConfigurationSection section) {
        // 前置已完成严格校验；此处做防御性检查避免空指针
        if (section.getString("id") == null || section.getString("action") == null ||
            section.getString("when") == null || section.getString("skill") == null) {
            logger.error("规则配置验证失败: 缺少必需字段");
            return null;
        }
        
        ActionRule rule = new ActionRule();
        
        // 基本信息
        rule.setModelId(modelId);
        rule.setId(section.getString("id"));
        
        // 动作类型
        ActionType action = ActionType.fromString(section.getString("action"));
        if (action == null) {
            logger.error("不支持的动作类型: " + section.getString("action"));
            return null;
        }
        rule.setAction(action);
        
        // 触发时机
        TriggerWhen when = TriggerWhen.fromString(section.getString("when"));
        if (when == null) {
            logger.error("不支持的触发时机: " + section.getString("when"));
            return null;
        }
        rule.setWhen(when);
        
        // 技能名称
        rule.setSkill(section.getString("skill"));
        
        // 可选参数
        rule.setEvery(section.getInt("every", 1));
        rule.setAfter(section.getInt("after", 0));
        rule.setCooldown(section.getInt("cd", 0));
        rule.setTarget(section.getString("target"));
        rule.setRequire(section.getString("require"));
        
        // 解析元数据
        RuleMeta meta = parseMeta(section.getConfigurationSection("meta"));
        rule.setMeta(meta);
        
        return rule;
    }
    
    /**
     * 解析规则元数据
     */
    private RuleMeta parseMeta(ConfigurationSection metaSection) {
        RuleMeta meta = new RuleMeta();
        
        if (metaSection == null) {
            return meta;
        }
        
        // 解析骑乘类型
        String mountStr = metaSection.getString("mount");
        if (mountStr != null) {
            try {
                EntityType mountType = EntityType.valueOf(mountStr.toUpperCase());
                meta.setMountType(mountType);
            } catch (IllegalArgumentException e) {
                logger.warn("无效的骑乘类型: " + mountStr);
            }
        }
        
        // 解析船只限制
        meta.setBoatOnly(metaSection.getBoolean("boat", false));
        
        // 解析悬停最小tick数
        if (metaSection.contains("hover_min_ticks")) {
            meta.setHoverMinTicks(metaSection.getInt("hover_min_ticks"));
        }
        
        return meta;
    }
    
    /**
     * 设置文件监听
     */
    private void setupFileWatch(String configName, File file) {
        try {
            // 移除旧的监听
            YamlUtil.ConfigWatchHandle oldHandle = watchHandles.remove(configName);
            if (oldHandle != null) {
                oldHandle.close();
            }
            
            // 设置新的监听
            YamlUtil.ConfigWatchHandle handle = yamlUtil.watchConfig(
                configName,
                updatedConfig -> {
                    logger.info("检测到模型文件 " + file.getName() + " 发生变更，正在重新加载...");
                    if (loadModelFile(configName, file)) {
                        rebuildRuleIndex();
                        logger.info("模型文件 " + file.getName() + " 重新加载完成");
                    }
                },
                null, // 使用默认线程池
                java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
            );
            
            watchHandles.put(configName, handle);
            
        } catch (Exception e) {
            logger.warn("设置文件监听失败: " + e.getMessage());
        }
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
