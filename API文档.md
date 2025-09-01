# 1) 关键 API（5.4+）

* 获取入口与适配

    * `io.lumine.mythic.bukkit.MythicBukkit#inst()`：获取运行中的 MM 实例。([MythicCraft Minecraft Marketplace][1])
    * `io.lumine.mythic.bukkit.BukkitAPIHelper`：包含所有 `castSkill` 重载与其他便捷方法。可直接 `new` 或通过实例获取。重载包括：

        * `boolean castSkill(Entity e, String skillName)`
        * `boolean castSkill(Entity e, String skillName, Entity trigger, Location origin, Collection<Entity> eTargets, Collection<Location> lTargets, float power)`
        * 其他同名变体（支持 origin、targets、power）。([MythicCraft Minecraft Marketplace][2])
    * `io.lumine.mythic.bukkit.BukkitAdapter`：`adapt(Entity) / adapt(Location)` 等，用于 Bukkit 与 Mythic 抽象类型双向转换。([MythicCraft Minecraft Marketplace][3])
* 修改执行元数据

    * `io.lumine.mythic.api.skills.SkillMetadata`：可在 cast 时设置 `setPower(...) / setOrigin(...) / setEntityTargets(...) / setLocationTargets(...) / setTrigger(...) / setMetadata(...)`。([MythicCraft Minecraft Marketplace][4])
* 自定义目标选择器

    * `io.lumine.mythic.bukkit.events.MythicTargeterLoadEvent`：服务端加载时触发，第三方插件可 `register(ISkillTargeter)` 将自定义 targeter 挂到指定名上，供技能内或外部引用。([MythicCraft Minecraft Marketplace][5])
    * `io.lumine.mythic.api.skills.targeters.ISkillTargeter` 及其子接口 `IEntityTargeter/ILocationTargeter`：实现实体或位置的解析逻辑。([MythicCraft Minecraft Marketplace][6])
* 目标器文档（原生 targeter 说明，用于配置与对照）：官方 Wiki「Targeters」。([GitLab][7])

# 2) DrcomoMotionCast → MythicMobs 调用范式

## 2.1 最简触发（由技能内部自己选目标）

> 适合：规则只给技能名；目标逻辑写在 MythicMobs 技能 YAML 的 `@targeter` 中。

```java
// imports
// io.lumine.mythic.bukkit.MythicBukkit
// io.lumine.mythic.bukkit.BukkitAPIHelper

BukkitAPIHelper mm = MythicBukkit.inst().getAPIHelper();   // 获取助手
boolean ok = mm.castSkill(player, "JS1_Attack");            // 技能内自带 targeter
```

`ok==true` 表示成功进入技能执行流。([MythicCraft Minecraft Marketplace][1], [MythicCraft Minecraft Marketplace][2])

## 2.2 外部传入上下文与固定目标

> 适合：规则显式给出“触发者/原点/受击者”等上下文与目标集合。

```java
// imports 同上
Entity trigger = player;                 // 触发者
Location origin = player.getLocation();  // 技能原点
Collection<Entity> eTargets = List.of(victim); // 规则计算的目标集
boolean ok = mm.castSkill(player, "JS1_Attack", trigger, origin, eTargets, List.of(), 1.0f);
```

该重载允许覆盖技能默认 targeter，直接以 `eTargets/lTargets` 为输入。([MythicCraft Minecraft Marketplace][2])

## 2.3 细粒度改写 SkillMetadata（含“变量”与动态 power）

> 适合：规则需要在一次施放内精细调参，如动态威力、临时元数据、强制原点等。

```java
// imports
// io.lumine.mythic.bukkit.BukkitAdapter
// io.lumine.mythic.api.skills.SkillMetadata

boolean ok = mm.castSkill(player, "JS1_HoverTick", meta -> {
    meta.setPower(1.25f);
    meta.setTrigger(BukkitAdapter.adapt(player));
    meta.setOrigin(BukkitAdapter.adapt(player.getLocation()));
    meta.setEntityTargets(
        List.of(BukkitAdapter.adapt(victim))
    );
    meta.setMetadata("drcomo:model", "zy_js1"); // 供技能内<skill.meta[]>占位读取
});
```

可用 `BukkitAdapter` 将 Bukkit 对象适配为抽象类型以满足 `SkillMetadata` 的 setter。([MythicCraft Minecraft Marketplace][4])

# 3) “目标选择器（Targeter）”支持方案

优先顺序与落地做法如下：

**A. 规则→实体/位置集合→`castSkill(... eTargets, lTargets ...)`**

* 规则里允许填写如 `@victim / @self / @attacker / 半径选择 / 视线射线` 等“插件自定义 selector”。
* 插件侧解析后直接给出 `Collection<Entity>` 或 `Collection<Location>`，传给 `castSkill` 覆盖技能内 targeter。([MythicCraft Minecraft Marketplace][2])

**B. 使用 Mythic 原生 targeter（推荐在技能 YAML 内书写）**

* 当规则仅提供技能名时，目标逻辑全部在技能里用 `@PlayersInRadius{r=...}` 等原生 targeter 表达。参考官方 targeter 列表。([GitLab][7])

**C. 需要在“规则里”写原生 targeter 字符串**

* 在 `MythicTargeterLoadEvent` 中注册一个统一前缀的自定义 targeter（如 `drcomo{expr=...}`）。
* 规则层写入原生 targeter 片段或自定义语法，插件 targeter 解析并据此返回实体/位置集合。注册由 `register(ISkillTargeter)` 完成。([MythicCraft Minecraft Marketplace][5])

> 说明：公开 API 未提供“从任意字符串直接解析为 ISkillTargeter”的一般入口，工程上应选择 A 或 C。原生 targeter 的权威清单见官方 Wiki。([GitLab][7])

# 4) 规则到调用的映射建议（与本项目约束对齐）

* 单规则=单动作→单技能。动作触发时：

    1. 依据玩家态与事件上下文构造 `trigger/origin/targets/power`。
    2. 检冷却，通过后走 2.1 或 2.2/2.3 的 `castSkill`。
    3. 返回值判定与日志记录。
* 计时类动作（飞行 N tick、悬停每 M tick）：用内部时间轴驱动，周期性调用 `castSkill`。

# 5) 典型伪代码（导入路径完整，示例）

```java
// 基础导入
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.BukkitAPIHelper;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.api.skills.SkillMetadata;

// 攻击 -> 技能 @victim
void onAttack(Player player, Entity victim) {
    BukkitAPIHelper mm = MythicBukkit.inst().getAPIHelper();
    mm.castSkill(player, "JS1_Attack", player, player.getLocation(), List.of(victim), List.of(), 1.0f);
}

// 飞行满1秒 -> 技能，强制原点为玩家脚下，并附带模型标识
void onFlyCharge(Player player) {
    BukkitAPIHelper mm = MythicBukkit.inst().getAPIHelper();
    mm.castSkill(player, "JS1_FlyCharge", meta -> {
        meta.setOrigin(BukkitAdapter.adapt(player.getLocation()));
        meta.setTrigger(BukkitAdapter.adapt(player));
        meta.setMetadata("drcomo:model", "zy_js1");
    });
}

// 自定义 targeter 注册（服务端加载时）
@EventHandler
public void onMMTargeterLoad(io.lumine.mythic.bukkit.events.MythicTargeterLoadEvent e) {
    if (!"drcomo".equalsIgnoreCase(e.getTargeterName())) return;
    e.register(new MyDrcomoTargeter(e.getContainer().getManager(), e.getConfig())); // 实现 ISkillTargeter
}
```

`MythicTargeterLoadEvent#register(...)` 与 `ISkillTargeter` 参考上文。([MythicCraft Minecraft Marketplace][5])

# 6) 与 Drcomo 生态对接点

* 统一通过 `DrcomoCoreLib` 的调度、日志、冷却与配置工具实现：

    * 行为监听→状态机与时间轴→冷却→`castSkill`。
    * 调试开关与中文日志。
    * `/DrcomoMotionCast reload` 热重载，重建规则索引但不重启监听器。
* PlaceholderAPI：如需在技能内读取规则态，可在 `cast` 前用 `SkillMetadata#setMetadata(...)` 写入，再在 Mythic 技能里用占位读取。

# 7) 注意事项

* `castSkill(...)` 返回 `boolean`，失败通常是技能不存在、条件不满足或冷却阻断。需记录与回退。([MythicCraft Minecraft Marketplace][2])
* 线程：所有调用在主线程执行。技能内部个别机制可能异步，但外层事件请保持同步（按 Bukkit 常规）。
* 版本包名：5.4+ 统一使用 `io.lumine.mythic.*` 与 `io.lumine.mythic.bukkit.*`。([MythicCraft Minecraft Marketplace][8])

[1]: https://mythiccraft.io/javadocs/mythic/io/lumine/mythic/bukkit/MythicBukkit.html?utm_source=chatgpt.com "MythicBukkit (Mythic 5.4.0-SNAPSHOT API)"
[2]: https://www.mythiccraft.io/javadocs/mythic/allclasses-index.html "All Classes and Interfaces (Mythic 5.4.0-SNAPSHOT API)"
[3]: https://www.mythiccraft.io/javadocs/mythic/io/lumine/mythic/bukkit/BukkitAdapter.html "BukkitAdapter (Mythic 5.4.0-SNAPSHOT API)"
[4]: https://www.mythiccraft.io/javadocs/mythic/io/lumine/mythic/api/skills/SkillMetadata.html "SkillMetadata (Mythic 5.4.0-SNAPSHOT API)"
[5]: https://www.mythiccraft.io/javadocs/mythic/io/lumine/mythic/bukkit/events/MythicTargeterLoadEvent.html "MythicTargeterLoadEvent (Mythic 5.4.0-SNAPSHOT API)"
[6]: https://www.mythiccraft.io/javadocs/mythic/io/lumine/mythic/api/skills/targeters/ISkillTargeter.html "ISkillTargeter (Mythic 5.4.0-SNAPSHOT API)"
[7]: https://git.lumine.io/mythiccraft/MythicMobs/-/wikis/Skills/Targeters?version_id=b2a5ebf163976d1da092dc2f646babd410d368e3&utm_source=chatgpt.com "Targeters · Wiki · MythicCraft / MythicMobs - GitLab"
[8]: https://www.mythiccraft.io/javadocs/?utm_source=chatgpt.com "Overview (Mythic 5.4.0-SNAPSHOT API)"



# 关键 API（完整导入路径）

* `com.ticxo.modelengine.api.ModelEngineAPI.getModeledEntity(java.util.UUID uuid)`：取玩家对应的 `ModeledEntity`，无则返回 `null`。([Ticxo][1])
* `com.ticxo.modelengine.api.model.ModeledEntity.getModels()`：返回 `Map<String, ActiveModel>`，键为 **modelId**。非空即表示当前已“伪装/挂载”了模型。([Ticxo][2])
  -（可选）`ModelEngineAPI.createModeledEntity(org.bukkit.entity.Player)`：用于“玩家伪装为模型”的创建场景，便于理解机制来源。([Ticxo][1])
  -（参考）MythicMobs 技能 `ModelDisguise` 的文档说明“将玩家伪装为某模型”，属性含 `modelid`。本插件只读其结果，不负责施加。([GitLab][3])

# 判定与读取示例（伪代码）

```java
// imports
import com.ticxo.modelengine.api.ModelEngineAPI;                    // 入口
import com.ticxo.modelengine.api.model.ModeledEntity;              // 容器
import com.ticxo.modelengine.api.model.ActiveModel;                // 实例
import org.bukkit.entity.Player;

boolean isDisguised(Player p) {
    ModeledEntity me = ModelEngineAPI.getModeledEntity(p.getUniqueId()); // 取玩家模型容器
    return me != null && !me.getModels().isEmpty();                      // 非空即有模型
}

String getDisguiseModelId(Player p) {
    ModeledEntity me = ModelEngineAPI.getModeledEntity(p.getUniqueId());
    if (me == null || me.getModels().isEmpty()) return null;
    // 若可能存在多个模型，请在配置层规定唯一性；此处取第一个键
    return me.getModels().keySet().iterator().next();
}

boolean hasSpecificModel(Player p, String modelId) {
    ModeledEntity me = ModelEngineAPI.getModeledEntity(p.getUniqueId());
    return me != null && me.getModel(modelId) != null; // 精确匹配某个模型
}
```

以上方法与字段来源：`ModelEngineAPI.getModeledEntity(...)`、`ModeledEntity.getModels()`、`ModeledEntity.getModel(String)`。([Ticxo][1])

# 事件同步（建议）

监听 **模型添加/移除事件**，实时刷新玩家的“当前模型”缓存：

* `com.ticxo.modelengine.api.events.AddModelEvent`
* `com.ticxo.modelengine.api.events.RemoveModelEvent`
  事件类存在于 API 包，适合做状态回收与变更触发；若事件本身未暴露便捷取值，可在事件回调里再次调用 `ModelEngineAPI.getModeledEntity(...)` 读取当前快照。([Ticxo][4])

# 与 DrcomoMotionCast 的对接点

* 启动与玩家加入：计算并缓存 `currentModelId`。
* 收到 ModelEngine “加/移除模型”事件：更新缓存。
* 规则筛选：按模型文件名或 `modelId` 精确匹配到对应的“模型配置文件”（如 `zy_js1.yml`），然后进入你既定的“动作→技能”流程。

# 备注

* 以上 API 来自 ModelEngine v4 文档与 Javadocs，方法签名以 R3.1.6 文档为准，v4.0.x 仍保持这些入口点（少量注释可能有出入）。([Ticxo][1], [GitLab][5])
* `ModelDisguise` 是 MythicMobs 技能侧施加伪装，你只需读取结果；其用途与 `modelid` 参数定义可见官方页。([GitLab][3])

[1]: https://ticxo.github.io/Model-Engine-Javadocs/com/ticxo/modelengine/api/ModelEngineAPI.html "ModelEngineAPI (api R3.1.6 API)"
[2]: https://ticxo.github.io/Model-Engine-Javadocs/com/ticxo/modelengine/api/model/ModeledEntity.html "ModeledEntity (api R3.1.6 API)"
[3]: https://git.mythiccraft.io/mythiccraft/model-engine-4/-/wikis/Skills/Mechanics/ModelDisguise?version_id=058ef9f75d9120ff725d2ba9881b3c57ba2b830a&utm_source=chatgpt.com "ModelDisguise · Wiki · MythicCraft / Model Engine 4 - GitLab"
[4]: https://ticxo.github.io/Model-Engine-Javadocs/com/ticxo/modelengine/api/events/package-summary.html "com.ticxo.modelengine.api.events (api R3.1.6 API)"
[5]: https://git.mythiccraft.io/mythiccraft/model-engine-4/-/wikis/API/diff?version_id=998f846d4138a1456cddf5ce68c8f1a233ea4648&w=1&utm_source=chatgpt.com "Changes · API · Wiki · MythicCraft / Model Engine 4 - GitLab"
