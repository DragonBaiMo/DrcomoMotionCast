package cn.drcomo.motioncast.engine;

import cn.drcomo.corelib.util.DebugUtil;
import cn.drcomo.motioncast.config.ModelRuleLoader;
import cn.drcomo.motioncast.state.PlayerStateManager;
import cn.drcomo.motioncast.state.PlayerStateSession;
import cn.drcomo.motioncast.cooldown.CooldownService;
import cn.drcomo.motioncast.targetfunction.TargeterRegistry;
import cn.drcomo.motioncast.targetfunction.TargetContext;
import cn.drcomo.motioncast.integration.MythicMobsIntegration;
import cn.drcomo.motioncast.integration.ModelEngineIntegration;
import cn.drcomo.motioncast.rules.ActionRule;
import cn.drcomo.motioncast.rules.ActionType;
import cn.drcomo.motioncast.rules.TriggerWhen;

import cn.drcomo.corelib.hook.placeholder.PlaceholderAPIUtil;
import cn.drcomo.corelib.hook.placeholder.parse.PlaceholderConditionEvaluator;
import cn.drcomo.corelib.hook.placeholder.parse.ParseException;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 动作引擎 - 核心业务逻辑处理器
 * 负责监听事件→匹配规则→冷却判断→解析目标→调用MythicMobs技能
 */
public class ActionEngine {
    
    private final JavaPlugin plugin;
    private final DebugUtil logger;
    private final ModelRuleLoader ruleLoader;
    private final PlayerStateManager stateManager;
    private final CooldownService cooldownService;
    private final TargeterRegistry targeterRegistry;
    private final MythicMobsIntegration mythicMobsIntegration;
    private final cn.drcomo.motioncast.integration.MythicAttackBridge mythicAttackBridge;
    private final ModelEngineIntegration modelEngineIntegration;
    // 条件解析相关
    private final PlaceholderAPIUtil placeholderAPIUtil;
    private final PlaceholderConditionEvaluator conditionEvaluator;
    
    // 统计信息
    private final AtomicLong totalRuleFires = new AtomicLong(0);
    private final AtomicLong successfulExecutions = new AtomicLong(0);
    private final AtomicLong cooldownBlocked = new AtomicLong(0);
    private final AtomicLong targetResolutionFailed = new AtomicLong(0);
    private final AtomicLong skillExecutionFailed = new AtomicLong(0);
    
    public ActionEngine(JavaPlugin plugin, DebugUtil logger, ModelRuleLoader ruleLoader,
                       PlayerStateManager stateManager, CooldownService cooldownService,
                       TargeterRegistry targeterRegistry, MythicMobsIntegration mythicMobsIntegration,
                       ModelEngineIntegration modelEngineIntegration,
                       cn.drcomo.motioncast.integration.MythicAttackBridge mythicAttackBridge) {
        this.plugin = plugin;
        this.logger = logger;
        this.ruleLoader = ruleLoader;
        this.stateManager = stateManager;
        this.cooldownService = cooldownService;
        this.targeterRegistry = targeterRegistry;
        this.mythicMobsIntegration = mythicMobsIntegration;
        this.modelEngineIntegration = modelEngineIntegration;
        this.mythicAttackBridge = mythicAttackBridge;
        // 初始化占位符工具与条件解析引擎（均为中文日志、无反射实现）
        // 占位符标识符使用插件名小写，保证唯一性与可读性
        this.placeholderAPIUtil = new PlaceholderAPIUtil(plugin, plugin.getName().toLowerCase());
        this.conditionEvaluator = new PlaceholderConditionEvaluator(plugin, logger, this.placeholderAPIUtil);
        
        logger.debug("动作引擎已初始化");
    }
    
    /**
     * 触发规则执行
     * 这是引擎的核心入口方法
     */
    public void fireRules(Player player, ActionType action, TriggerWhen when) {
        if (player == null || action == null || when == null) {
            return;
        }
        
        totalRuleFires.incrementAndGet();
        
        try {
            // 获取玩家状态会话
            PlayerStateSession session = stateManager.getOrCreateSession(player);
            
            // 确定玩家的模型ID
            String modelId = getPlayerModelId(player);
            if (modelId == null) {
                logger.debug("玩家 " + player.getName() + " 没有关联的模型ID，跳过规则处理");
                return;
            }
            
            // 获取匹配的规则
            List<ActionRule> rules = ruleLoader.getRules(modelId, action, when);
            if (rules.isEmpty()) {
                logger.debug("没有找到匹配的规则: 模型=" + modelId + ", 动作=" + action + ", 时机=" + when);
                return;
            }
            
            // 创建目标解析上下文
            TargetContext targetContext = TargetContext.fromPlayer(player, session);
            
            // 处理每个规则
            for (ActionRule rule : rules) {
                processRule(player, session, rule, targetContext);
            }
            
        } catch (Exception e) {
            logger.error("处理规则触发时发生异常: " + e.getMessage());
        }
    }
    
    /**
     * 获取玩家的模型ID
     */
    private String getPlayerModelId(Player player) {
        // 使用 ModelEngine 集成直接获取玩家模型ID
        // 若不可用或未找到模型，则返回 null，由上层逻辑决定是否跳过
        if (modelEngineIntegration != null && modelEngineIntegration.isAvailable()) {
            String modelId = modelEngineIntegration.getPlayerModelId(player);
            if (modelId != null && !modelId.trim().isEmpty()) {
                return modelId;
            }
        }
        // 无可用模型：返回 null，避免使用占位“默认模型ID”带来歧义
        return null;
    }
    
    /**
     * 处理单个规则
     */
    private void processRule(Player player, PlayerStateSession session, ActionRule rule, TargetContext targetContext) {
        try {
            // 1. 检查冷却
            if (cooldownService.isOnCooldown(player, rule)) {
                cooldownBlocked.incrementAndGet();
                int remainingTicks = cooldownService.getRemainingCooldown(player, rule);
                logger.debug("规则 " + rule.getId() + " 被冷却阻止，剩余 " + remainingTicks + " tick");
                return;
            }
            
            // 2. 检查条件（如果有）
            if (!checkRuleCondition(player, rule)) {
                logger.debug("规则 " + rule.getId() + " 条件检查失败");
                return;
            }
            
            // 3. 解析目标
            Collection<Entity> targets = resolveTargets(player, rule, targetContext);
            
            // 4. 执行技能
            boolean success = executeSkill(player, session, rule, targets);
            
            if (success) {
                successfulExecutions.incrementAndGet();
                
                // 设置冷却
                if (rule.getCooldown() > 0) {
                    cooldownService.setCooldown(player, rule);
                }
                
                logger.debug("成功执行规则: " + rule.getId() + " -> 技能: " + rule.getSkill());
            } else {
                skillExecutionFailed.incrementAndGet();
                logger.debug("技能执行失败: " + rule.getSkill() + " (规则: " + rule.getId() + ")");
            }
            
        } catch (Exception e) {
            logger.error("处理规则 " + rule.getId() + " 时发生异常: " + e.getMessage());
        }
    }
    
    /**
     * 检查规则条件
     */
    private boolean checkRuleCondition(Player player, ActionRule rule) {
        String condition = rule.getRequire();
        if (condition == null || condition.trim().isEmpty()) {
            return true; // 没有条件，直接通过
        }
        try {
            // 使用 DrcomoCoreLib 的条件引擎进行同步解析与求值
            // 该引擎内部会解析所有 PAPI 占位符并按优先级计算逻辑/比较运算
            boolean result = conditionEvaluator.parse(player, condition);
            if (!result) {
                logger.debug("规则 " + rule.getId() + " 的条件未通过: " + condition);
            }
            return result;
        } catch (ParseException e) {
            // 解析失败：记录中文错误并返回 false，防止错误条件导致未定义行为
            logger.error("解析规则条件时发生错误 (规则: " + rule.getId() + ")，表达式: " + condition + "，原因: " + e.getMessage());
            return false;
        } catch (Exception e) {
            // 其他异常保护
            logger.error("校验规则条件时发生异常 (规则: " + rule.getId() + ")，原因: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 解析规则目标
     */
    private Collection<Entity> resolveTargets(Player player, ActionRule rule, TargetContext targetContext) {
        String targetSelector = rule.getTarget();
        
        // 如果没有指定目标，返回空集合（让MythicMobs技能内部处理）
        if (targetSelector == null || targetSelector.trim().isEmpty()) {
            return List.of();
        }
        
        Collection<Entity> targets = targeterRegistry.resolve(targetSelector, targetContext);
        
        if (targets.isEmpty()) {
            targetResolutionFailed.incrementAndGet();
            logger.debug("目标解析失败: " + targetSelector + " (规则: " + rule.getId() + ")");
        }
        
        return targets;
    }
    
    /**
     * 执行MythicMobs技能
     */
    private boolean executeSkill(Player player, PlayerStateSession session, ActionRule rule, Collection<Entity> targets) {
        if (mythicMobsIntegration == null || !mythicMobsIntegration.isAvailable()) {
            logger.debug("MythicMobs不可用，跳过技能执行");
            return false;
        }
        
        String skillName = rule.getSkill();

        // ATTACK 动作：若存在绑定的 Bukkit 原始事件，则优先通过近战桥接执行，确保 CancelEvent 生效
        if (rule.getAction() == ActionType.ATTACK && mythicAttackBridge != null && mythicAttackBridge.isAvailable()) {
            EntityDamageByEntityEvent attackEvent = session.getCustomData("last_attack_event", EntityDamageByEntityEvent.class);
            if (attackEvent != null) {
                boolean ok = mythicAttackBridge.castSkillWithEvent(player, skillName, attackEvent, targets);
                if (ok) return true; // 桥接成功即返回
                // 桥接失败则继续回退到常规 API
                logger.warn("近战桥接执行失败，回退至常规API: " + skillName);
            }
        }

        // 常规执行：根据目标数量选择合适的 API Helper 调用
        if (targets.isEmpty()) {
            return mythicMobsIntegration.castSkill(player, skillName);
        }
        return mythicMobsIntegration.castSkill(player, skillName, targets);
    }
    
    /**
     * 检查duration规则（由TickScheduler调用）
     */
    public void checkDurationRules(Player player, ActionType action, int currentTicks) {
        try {
            String modelId = getPlayerModelId(player);
            if (modelId == null) return;
            
            List<ActionRule> rules = ruleLoader.getRules(modelId, action, TriggerWhen.DURATION);
            
            PlayerStateSession session = stateManager.getSession(player);
            if (session == null) return;
            
            TargetContext targetContext = TargetContext.fromPlayer(player, session);
            
            for (ActionRule rule : rules) {
                // 检查是否达到触发时间
                if (currentTicks >= rule.getAfter()) {
                    processRule(player, session, rule, targetContext);
                }
            }
            
        } catch (Exception e) {
            logger.debug("检查duration规则时发生异常: " + e.getMessage());
        }
    }
    
    /**
     * 检查tick规则（由TickScheduler调用）
     */
    public void checkTickRules(Player player, ActionType action, int currentTicks) {
        try {
            String modelId = getPlayerModelId(player);
            if (modelId == null) return;
            
            List<ActionRule> rules = ruleLoader.getRules(modelId, action, TriggerWhen.TICK);
            
            PlayerStateSession session = stateManager.getSession(player);
            if (session == null) return;
            
            TargetContext targetContext = TargetContext.fromPlayer(player, session);
            
            for (ActionRule rule : rules) {
                // 检查是否到了触发周期
                if (currentTicks % rule.getEvery() == 0) {
                    processRule(player, session, rule, targetContext);
                }
            }
            
        } catch (Exception e) {
            logger.debug("检查tick规则时发生异常: " + e.getMessage());
        }
    }
    
    /**
     * 获取引擎统计信息
     */
    public ActionEngineStats getStatistics() {
        return new ActionEngineStats(
            totalRuleFires.get(),
            successfulExecutions.get(),
            cooldownBlocked.get(),
            targetResolutionFailed.get(),
            skillExecutionFailed.get()
        );
    }
    
    /**
     * 重置统计信息
     */
    public void resetStatistics() {
        totalRuleFires.set(0);
        successfulExecutions.set(0);
        cooldownBlocked.set(0);
        targetResolutionFailed.set(0);
        skillExecutionFailed.set(0);
        
        logger.info("动作引擎统计信息已重置");
    }

    /**
     * 判断是否应当取消对应事件
     * 用途：在 Bukkit 事件监听中调用，如玩家攻击/受击等可取消事件。
     * 规则来源：当前玩家模型下，匹配 action + when 的规则中任意一条 meta.cancel_event=true 即视为需要取消。
     *
     * 说明：
     * - MythicMobs 的 cancelevent 机制仅在其内部事件上下文生效；
     *   当通过 API castSkill 触发时无法直接关联 Bukkit 原事件。
     * - 为保证可控性与直观性，这里按规则元数据进行插件侧显式取消。
     */
    public boolean shouldCancelEvent(Player player, ActionType action, TriggerWhen when) {
        try {
            if (player == null || action == null || when == null) return false;

            String modelId = getPlayerModelId(player);
            if (modelId == null) return false;

            java.util.List<ActionRule> rules = ruleLoader.getRules(modelId, action, when);
            if (rules == null || rules.isEmpty()) return false;

            for (ActionRule rule : rules) {
                if (rule != null && rule.getMeta() != null && rule.getMeta().isCancelEvent()) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            logger.debug("判断是否取消事件时发生异常: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 动作引擎统计信息类
     */
    public static class ActionEngineStats {
        public final long totalRuleFires;
        public final long successfulExecutions;
        public final long cooldownBlocked;
        public final long targetResolutionFailed;
        public final long skillExecutionFailed;
        
        public ActionEngineStats(long totalRuleFires, long successfulExecutions, long cooldownBlocked,
                               long targetResolutionFailed, long skillExecutionFailed) {
            this.totalRuleFires = totalRuleFires;
            this.successfulExecutions = successfulExecutions;
            this.cooldownBlocked = cooldownBlocked;
            this.targetResolutionFailed = targetResolutionFailed;
            this.skillExecutionFailed = skillExecutionFailed;
        }
        
        public double getSuccessRate() {
            return totalRuleFires > 0 ? (double) successfulExecutions / totalRuleFires * 100 : 0.0;
        }
        
        @Override
        public String toString() {
            return "ActionEngineStats{" +
                    "totalRuleFires=" + totalRuleFires +
                    ", successfulExecutions=" + successfulExecutions +
                    ", successRate=" + String.format("%.1f%%", getSuccessRate()) +
                    ", cooldownBlocked=" + cooldownBlocked +
                    ", targetFailed=" + targetResolutionFailed +
                    ", skillFailed=" + skillExecutionFailed +
                    '}';
        }
    }
    
    @Override
    public String toString() {
        ActionEngineStats stats = getStatistics();
        return "ActionEngine{" +
                "totalFires=" + stats.totalRuleFires +
                ", success=" + stats.successfulExecutions +
                ", successRate=" + String.format("%.1f%%", stats.getSuccessRate()) +
                '}';
    }
}
