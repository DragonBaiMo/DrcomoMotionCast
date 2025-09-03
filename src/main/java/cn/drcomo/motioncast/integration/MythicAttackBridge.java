package cn.drcomo.motioncast.integration;

import cn.drcomo.corelib.util.DebugUtil;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.adapters.AbstractLocation;
import io.lumine.mythic.api.skills.Skill;
import io.lumine.mythic.api.skills.SkillCaster;
import io.lumine.mythic.api.skills.SkillManager;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillTrigger;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.adapters.BukkitTriggerMetadata;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 近战攻击桥接：
 * 使用 Bukkit 的 EntityDamageByEntityEvent 作为唯一物理近战入口，
 * 构造 Mythic 的 SkillMetadata，并用 BukkitTriggerMetadata 绑定原始事件，
 * 然后执行规则技能，使技能内的 CancelEvent{forcesync=true} 可以精准取消本次伤害。
 */
public class MythicAttackBridge {

    private final DebugUtil logger;
    private final SkillManager skillManager;
    // 使用自注册的触发器标识，避免依赖核心是否预注册特定常量（如 ATTACK）
    // 仅使用唯一标识，不设任何别名，避免与核心触发器重名造成歧义
    private static final SkillTrigger<?> API_ATTACK_TRIGGER = SkillTrigger.create("API_ATTACK_BRIDGE");

    public MythicAttackBridge(DebugUtil logger) {
        this.logger = logger;
        SkillManager sm = null;
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
                sm = MythicBukkit.inst().getSkillManager();
            }
        } catch (Throwable t) {
            logger.warn("获取 MythicMobs SkillManager 失败: " + t.getMessage());
        }
        this.skillManager = sm;
        if (isAvailable()) {
            logger.info("近战攻击桥接已启用（绑定 Bukkit 原事件）");
        } else {
            logger.info("近战攻击桥接不可用（未检测到 MythicMobs 或 API 不可用）");
        }
    }

    public boolean isAvailable() {
        return this.skillManager != null;
    }

    /**
     * 使用原始近战事件上下文执行指定技能。
     * 注意：技能内请使用 CancelEvent{forcesync=true} 来取消本次 Bukkit 伤害事件。
     */
    public boolean castSkillWithEvent(Player caster,
                                      String skillName,
                                      EntityDamageByEntityEvent originalEvent,
                                      Collection<Entity> targets) {
        if (!isAvailable()) return false;
        try {
            Skill skill = skillManager.getSkill(skillName).orElse(null);
            if (skill == null) {
                logger.debug("未找到技能: " + skillName);
                return false;
            }

            // 适配 Mythic 抽象对象
            final AbstractEntity mCaster = BukkitAdapter.adapt(caster);
            final SkillCaster mSkillCaster = skillManager.getCaster(mCaster);
            final AbstractLocation mOrigin = BukkitAdapter.adapt(caster.getLocation());

            // 触发器实体：使用本次受害者（若存在）
            final AbstractEntity mTrigger = BukkitAdapter.adapt(originalEvent.getEntity());

            // 目标集合（可为空，留给技能 targeter 决定）
            final List<AbstractEntity> mTargets = new ArrayList<>();
            if (targets != null) {
                for (Entity e : targets) {
                    try { mTargets.add(BukkitAdapter.adapt(e)); } catch (Throwable ignore) {}
                }
            }

            // 构造元数据（5.x：SkillMetadataImpl 由 core 包提供实现）
            io.lumine.mythic.core.skills.SkillMetadataImpl meta =
                    new io.lumine.mythic.core.skills.SkillMetadataImpl(
                            API_ATTACK_TRIGGER, // 近战触发类型（自注册），不依赖核心是否存在 ATTACK 常量
                            mSkillCaster,
                            mTrigger,
                            mOrigin,
                            mTargets,
                            Collections.<AbstractLocation>emptyList(),
                            1.0f
                    );

            // 将 Bukkit 原事件绑定到元数据，便于在技能中 CancelEvent 精准取消
            SkillMetadata bound = BukkitTriggerMetadata.apply(meta, originalEvent);

            // Mythic 5.7 的 Skill#execute(meta) 为 void，执行成功与否不提供布尔返回
            skill.execute(bound);
            logger.debug("桥接执行技能 " + skillName + " 已调用执行（无返回值）");
            return true;

        } catch (Throwable t) {
            logger.error("使用近战桥接执行技能失败: " + t.getMessage());
            return false;
        }
    }
}
