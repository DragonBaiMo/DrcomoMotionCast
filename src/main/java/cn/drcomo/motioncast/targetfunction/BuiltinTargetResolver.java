package cn.drcomo.motioncast.targetfunction;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 内置目标解析器
 * 处理固定的内置目标关键词
 */
public class BuiltinTargetResolver implements TargetResolver {
    
    private static final Set<String> SUPPORTED_SELECTORS = Set.of(
        "@self", "@victim", "@attacker", "@vehicle", "@mount"
    );
    
    @Override
    public String getName() {
        return "builtin";
    }
    
    @Override
    public boolean supports(String targetSelector) {
        if (targetSelector == null) return false;
        
        String normalized = targetSelector.toLowerCase().trim();
        return SUPPORTED_SELECTORS.contains(normalized);
    }
    
    @Override
    public Collection<Entity> resolve(String targetSelector, TargetContext context) {
        if (targetSelector == null || context == null) {
            return Collections.emptyList();
        }
        
        String normalized = targetSelector.toLowerCase().trim();
        
        switch (normalized) {
            case "@self":
                return resolveSelf(context);
            case "@victim":
                return resolveVictim(context);
            case "@attacker":
                return resolveAttacker(context);
            case "@vehicle":
                return resolveVehicle(context);
            case "@mount":
                return resolveMount(context);
            default:
                return Collections.emptyList();
        }
    }
    
    /**
     * 解析 @self - 玩家自身
     */
    private Collection<Entity> resolveSelf(TargetContext context) {
        Player player = context.getPlayer();
        if (player != null && player.isOnline()) {
            return Collections.singletonList(player);
        }
        return Collections.emptyList();
    }
    
    /**
     * 解析 @victim - 最近的受害者
     */
    private Collection<Entity> resolveVictim(TargetContext context) {
        if (context.hasValidVictim()) {
            return Collections.singletonList(context.getLastVictim());
        }
        return Collections.emptyList();
    }
    
    /**
     * 解析 @attacker - 最近的攻击者
     */
    private Collection<Entity> resolveAttacker(TargetContext context) {
        if (context.hasValidAttacker()) {
            return Collections.singletonList(context.getLastAttacker());
        }
        return Collections.emptyList();
    }
    
    /**
     * 解析 @vehicle - 当前载具
     */
    private Collection<Entity> resolveVehicle(TargetContext context) {
        if (context.hasValidVehicle()) {
            return Collections.singletonList(context.getCurrentVehicle());
        }
        return Collections.emptyList();
    }
    
    /**
     * 解析 @mount - 当前坐骑
     */
    private Collection<Entity> resolveMount(TargetContext context) {
        if (context.hasValidMount()) {
            return Collections.singletonList(context.getCurrentMount());
        }
        return Collections.emptyList();
    }
    
    @Override
    public String getDescription() {
        return "内置目标解析器 - 支持: " + String.join(", ", SUPPORTED_SELECTORS);
    }
    
    @Override
    public int getPriority() {
        return 10; // 高优先级
    }
    
    /**
     * 获取支持的选择器列表
     */
    public Set<String> getSupportedSelectors() {
        return new HashSet<>(SUPPORTED_SELECTORS);
    }
}