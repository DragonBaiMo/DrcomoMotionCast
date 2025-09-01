package cn.drcomo.motioncast.integration;

import cn.drcomo.corelib.util.DebugUtil;
import io.lumine.mythic.bukkit.BukkitAPIHelper;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;

/**
 * MythicMobs 集成模块（基于官方 API，无反射）
 * 说明：本类严格依赖 MythicMobs v5+ 的 Bukkit API，直接调用 BukkitAPIHelper#castSkill 的各类重载。
 */
public class MythicMobsIntegration {

    private final DebugUtil logger;
    private final BukkitAPIHelper apiHelper;

    /**
     * 初始化集成：检测插件并获取 API 助手
     */
    public MythicMobsIntegration(DebugUtil logger) {
        this.logger = logger;

        if (Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            BukkitAPIHelper helper;
            try {
                helper = MythicBukkit.inst().getAPIHelper();
                logger.debug("成功获取 MythicMobs API 助手");
            } catch (Throwable t) {
                helper = null;
                logger.warn("获取 MythicMobs API 助手失败: " + t.getMessage());
            }
            this.apiHelper = helper;
        } else {
            this.apiHelper = null;
        }

        if (isAvailable()) {
            logger.info("MythicMobs 集成已启用");
        } else {
            logger.info("MythicMobs 不可用，技能调用功能将被禁用");
        }
    }

    /**
     * 检查 MythicMobs 是否可用
     */
    public boolean isAvailable() {
        return apiHelper != null;
    }

    /**
     * 执行技能 - 简单版本（由技能自身 targeter 决定目标）
     * 等价于 BukkitAPIHelper#castSkill(Entity, String)
     */
    public boolean castSkill(Player caster, String skillName) {
        if (!isAvailable()) {
            logger.debug("MythicMobs 不可用，跳过技能执行: " + skillName);
            return false;
        }
        try {
            boolean result = apiHelper.castSkill(caster, skillName);
            if (result) {
                logger.debug("成功执行技能: " + skillName + " (施法者: " + caster.getName() + ")");
            } else {
                logger.debug("技能执行失败: " + skillName + " (施法者: " + caster.getName() + ")");
            }
            return result;
        } catch (Throwable t) {
            logger.error("执行技能时发生异常: " + t.getMessage());
            return false;
        }
    }

    /**
     * 执行技能 - 完整重载（外部指定触发者、原点、目标集合与强度）
     * 等价于 BukkitAPIHelper#castSkill(Entity, String, Entity, Location, Collection<Entity>, Collection<Location>, float)
     */
    public boolean castSkill(Player caster,
                             String skillName,
                             Entity trigger,
                             Location origin,
                             Collection<Entity> entityTargets,
                             Collection<Location> locationTargets,
                             float power) {
        if (!isAvailable()) {
            logger.debug("MythicMobs 不可用，跳过技能执行: " + skillName);
            return false;
        }
        try {
            boolean result = apiHelper.castSkill(caster, skillName, trigger, origin,
                    entityTargets, locationTargets, power);
            if (result) {
                int size = entityTargets != null ? entityTargets.size() : 0;
                logger.debug("成功执行技能: " + skillName + " (施法者: " + caster.getName() + ", 目标数: " + size + ")");
            } else {
                logger.debug("技能执行失败: " + skillName + " (施法者: " + caster.getName() + ")");
            }
            return result;
        } catch (Throwable t) {
            logger.error("执行技能时发生异常: " + t.getMessage());
            return false;
        }
    }

    /**
     * 执行技能 - 便捷方法（仅实体目标集合，其他参数默认）
     */
    public boolean castSkill(Player caster, String skillName, Collection<Entity> targets) {
        Collection<Entity> safeTargets = targets != null ? targets : Collections.emptyList();
        return castSkill(caster, skillName, caster, caster.getLocation(), safeTargets, Collections.emptyList(), 1.0f);
    }

    /**
     * 获取集成状态信息
     */
    public String getStatusInfo() {
        return isAvailable() ? "MythicMobs 集成已启用" : "MythicMobs 集成不可用";
    }
}
