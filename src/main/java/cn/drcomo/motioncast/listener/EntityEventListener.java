package cn.drcomo.motioncast.listener;

import cn.drcomo.motioncast.engine.ActionEngine;
import cn.drcomo.motioncast.state.PlayerStateManager;
import org.bukkit.event.Listener;

/**
 * 实体事件监听器
 * 处理实体相关的事件
 */
public class EntityEventListener implements Listener {
    
    private final ActionEngine actionEngine;
    private final PlayerStateManager stateManager;
    
    public EntityEventListener(ActionEngine actionEngine, PlayerStateManager stateManager) {
        this.actionEngine = actionEngine;
        this.stateManager = stateManager;
    }
    
    // 实体相关事件处理将在这里实现
}