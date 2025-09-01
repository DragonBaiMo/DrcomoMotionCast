package cn.drcomo.motioncast.listener;

import cn.drcomo.motioncast.engine.ActionEngine;
import cn.drcomo.motioncast.state.PlayerStateManager;
import org.bukkit.event.Listener;

/**
 * 载具事件监听器
 * 处理载具相关的事件
 */
public class VehicleEventListener implements Listener {
    
    private final ActionEngine actionEngine;
    private final PlayerStateManager stateManager;
    
    public VehicleEventListener(ActionEngine actionEngine, PlayerStateManager stateManager) {
        this.actionEngine = actionEngine;
        this.stateManager = stateManager;
    }
    
    // 载具相关事件处理将在这里实现
}