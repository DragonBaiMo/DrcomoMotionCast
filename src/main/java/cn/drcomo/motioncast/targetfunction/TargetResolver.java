package cn.drcomo.motioncast.targetfunction;

import org.bukkit.entity.Entity;

import java.util.Collection;

/**
 * 目标解析器接口
 * 用于解析目标选择器字符串并返回对应的实体集合
 */
public interface TargetResolver {
    
    /**
     * 获取解析器名称
     * 用于注册和识别解析器
     */
    String getName();
    
    /**
     * 检查是否支持解析指定的目标选择器
     * 
     * @param targetSelector 目标选择器字符串
     * @return 是否支持解析
     */
    boolean supports(String targetSelector);
    
    /**
     * 解析目标选择器并返回实体集合
     * 
     * @param targetSelector 目标选择器字符串
     * @param context 解析上下文
     * @return 解析出的实体集合，如果无法解析或没有目标则返回空集合
     */
    Collection<Entity> resolve(String targetSelector, TargetContext context);
    
    /**
     * 获取解析器的描述信息
     */
    default String getDescription() {
        return "目标解析器: " + getName();
    }
    
    /**
     * 获取解析器的优先级
     * 数值越小优先级越高，默认为100
     */
    default int getPriority() {
        return 100;
    }
    
    /**
     * 验证解析器是否正常工作
     */
    default boolean isHealthy() {
        return true;
    }
}