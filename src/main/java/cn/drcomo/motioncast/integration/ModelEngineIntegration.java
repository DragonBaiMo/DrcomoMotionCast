package cn.drcomo.motioncast.integration;

import cn.drcomo.corelib.util.DebugUtil;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.events.AddModelEvent;
import com.ticxo.modelengine.api.events.RemoveModelEvent;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ModelEngine集成模块
 * 直接使用 ModelEngine API，无反射
 */
public class ModelEngineIntegration implements Listener {

    private final DebugUtil logger;
    private final boolean available;

    // 玩家模型缓存：playerUUID -> modelId
    private final Map<UUID, String> playerModelCache = new ConcurrentHashMap<>();

    public ModelEngineIntegration(DebugUtil logger) {
        this.logger = logger;
        this.available = Bukkit.getPluginManager().isPluginEnabled("ModelEngine");

        if (available) {
            logger.info("ModelEngine 集成已启用");
        } else {
            logger.info("ModelEngine 不可用，将跳过模型检测功能");
        }
    }

    /**
     * 检查ModelEngine是否可用
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * 检查玩家是否使用了模型伪装
     */
    public boolean isPlayerDisguised(Player player) {
        if (!available || player == null) return false;
        return isPlayerDisguised(player.getUniqueId());
    }

    /**
     * 检查玩家是否使用了模型伪装
     */
    public boolean isPlayerDisguised(UUID playerUUID) {
        if (!available || playerUUID == null) return false;

        ModeledEntity modeled = ModelEngineAPI.getModeledEntity(playerUUID);
        return modeled != null && !modeled.getModels().isEmpty();
    }

    /**
     * 获取玩家当前的模型ID（若存在，取首个键）
     */
    public String getPlayerModelId(Player player) {
        if (!available || player == null) return null;
        return getPlayerModelId(player.getUniqueId());
    }

    /**
     * 获取玩家当前的模型ID（若存在，取首个键）
     */
    public String getPlayerModelId(UUID playerUUID) {
        if (!available || playerUUID == null) return null;

        // 先查缓存
        String cached = playerModelCache.get(playerUUID);
        if (cached != null) return cached;

        ModeledEntity modeled = ModelEngineAPI.getModeledEntity(playerUUID);
        if (modeled == null) return null;

        Map<String, ActiveModel> models = modeled.getModels();
        if (models == null || models.isEmpty()) return null;

        String modelId = models.keySet().iterator().next();
        playerModelCache.put(playerUUID, modelId);
        return modelId;
    }

    /**
     * 检查玩家是否使用了指定的模型
     */
    public boolean hasSpecificModel(Player player, String modelId) {
        if (!available || player == null || modelId == null) return false;
        return hasSpecificModel(player.getUniqueId(), modelId);
    }

    /**
     * 检查玩家是否使用了指定的模型
     */
    public boolean hasSpecificModel(UUID playerUUID, String modelId) {
        if (!available || playerUUID == null || modelId == null) return false;

        ModeledEntity modeled = ModelEngineAPI.getModeledEntity(playerUUID);
        if (modeled == null) return false;
        return modeled.getModel(modelId) != null;
    }

    /**
     * 获取玩家所有的模型ID
     */
    public String[] getPlayerModelIds(Player player) {
        if (!available || player == null) return new String[0];
        return getPlayerModelIds(player.getUniqueId());
    }

    /**
     * 获取玩家所有的模型ID
     */
    public String[] getPlayerModelIds(UUID playerUUID) {
        if (!available || playerUUID == null) return new String[0];

        ModeledEntity modeled = ModelEngineAPI.getModeledEntity(playerUUID);
        if (modeled == null) return new String[0];

        Map<String, ActiveModel> models = modeled.getModels();
        if (models == null || models.isEmpty()) return new String[0];
        return models.keySet().toArray(new String[0]);
    }

    /**
     * 刷新玩家模型缓存（重新计算并覆盖缓存）
     */
    public void refreshPlayerModelCache(UUID playerUUID) {
        if (playerUUID == null) return;
        playerModelCache.remove(playerUUID);
        // 主动回源更新一次，降低后续读取延迟
        getPlayerModelId(playerUUID);
    }

    /**
     * 刷新玩家模型缓存（重载）
     */
    public void refreshPlayerModelCache(Player player) {
        if (player == null) return;
        refreshPlayerModelCache(player.getUniqueId());
    }

    /**
     * 清理某玩家模型缓存
     */
    public void clearPlayerModelCache(UUID playerUUID) {
        if (playerUUID == null) return;
        playerModelCache.remove(playerUUID);
    }

    /**
     * 清理某玩家模型缓存（重载）
     */
    public void clearPlayerModelCache(Player player) {
        if (player == null) return;
        clearPlayerModelCache(player.getUniqueId());
    }

    /**
     * 清空所有缓存
     */
    public void clearAllModelCache() {
        int count = playerModelCache.size();
        playerModelCache.clear();
        if (count > 0) {
            logger.debug("已清空 " + count + " 个玩家的模型缓存");
        }
    }

    /**
     * 获取当前缓存玩家数量
     */
    public int getCachedPlayerCount() {
        return playerModelCache.size();
    }

    /**
     * 获取状态信息
     */
    public String getStatusInfo() {
        if (!available) return "ModelEngine集成不可用";
        return String.format("ModelEngine集成已启用 (缓存玩家数: %d)", playerModelCache.size());
    }

    // 事件监听（无反射）：保持框架，采用保守缓存失效策略

    /**
     * 模型添加事件：由于事件文档未暴露玩家句柄，这里采取全量失效策略，确保一致性。
     */
    @EventHandler
    public void onAddModel(AddModelEvent event) {
        if (!available) return;
        clearAllModelCache();
        logger.debug("捕获 AddModelEvent，已全量清空模型缓存");
    }

    /**
     * 模型移除事件：同上，采取全量失效策略。
     */
    @EventHandler
    public void onRemoveModel(RemoveModelEvent event) {
        if (!available) return;
        clearAllModelCache();
        logger.debug("捕获 RemoveModelEvent，已全量清空模型缓存");
    }
}
