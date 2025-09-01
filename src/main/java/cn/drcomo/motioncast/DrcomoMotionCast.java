package cn.drcomo.motioncast;

import cn.drcomo.corelib.util.DebugUtil;
import cn.drcomo.corelib.config.YamlUtil;
import cn.drcomo.motioncast.config.ModelRuleLoader;
import cn.drcomo.motioncast.state.PlayerStateManager;
import cn.drcomo.motioncast.engine.ActionEngine;
import cn.drcomo.motioncast.target.TargeterRegistry;
import cn.drcomo.motioncast.tick.TickScheduler;
import cn.drcomo.motioncast.cooldown.CooldownService;
import cn.drcomo.motioncast.command.ReloadCommand;
import cn.drcomo.motioncast.listener.PlayerEventListener;
import cn.drcomo.motioncast.listener.VehicleEventListener;
import cn.drcomo.motioncast.listener.EntityEventListener;
import cn.drcomo.motioncast.integration.MythicMobsIntegration;
import cn.drcomo.motioncast.integration.ModelEngineIntegration;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * DrcomoMotionCast - 动作技能映射插件
 * 将玩家的动作（飞行、滑翔、游泳、骑乘等）映射到MythicMobs技能
 * 
 * @author Drcomo
 */
public final class DrcomoMotionCast extends JavaPlugin {
    
    private DebugUtil logger;
    private YamlUtil yamlUtil;
    private ModelRuleLoader ruleLoader;
    private PlayerStateManager stateManager;
    private ActionEngine actionEngine;
    private TargeterRegistry targeterRegistry;
    private TickScheduler tickScheduler;
    private CooldownService cooldownService;
    private MythicMobsIntegration mythicMobsIntegration;
    private ModelEngineIntegration modelEngineIntegration;
    
    // 事件监听器
    private PlayerEventListener playerEventListener;
    private VehicleEventListener vehicleEventListener;
    private EntityEventListener entityEventListener;
    
    @Override
    public void onEnable() {
        // 1. 初始化DrcomoCoreLib工具
        initializeCoreLibTools();
        
        // 2. 检查依赖
        if (!checkDependencies()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // 3. 初始化核心组件
        initializeComponents();
        
        // 4. 加载配置和规则
        loadConfigurations();
        
        // 5. 注册事件监听器
        registerEventListeners();
        
        // 6. 注册命令
        registerCommands();
        
        // 7. 启动定时调度器
        startSchedulers();
        
        logger.info("DrcomoMotionCast 插件已成功加载！");
    }
    
    @Override
    public void onDisable() {
        logger.info("正在卸载 DrcomoMotionCast 插件...");
        
        // 停止所有调度器
        if (tickScheduler != null) {
            tickScheduler.shutdown();
        }
        
        // 关闭配置文件监听（如果DrcomoCoreLib版本支持的话）
        // 注意：根据实际DrcomoCoreLib API，可能需要其他方式来关闭资源
        // if (yamlUtil != null) {
        //     yamlUtil.close();
        // }
        
        // 清理所有玩家状态
        if (stateManager != null) {
            stateManager.clearAllSessions();
        }
        
        logger.info("DrcomoMotionCast 插件已安全卸载");
    }
    
    /**
     * 初始化DrcomoCoreLib工具
     */
    private void initializeCoreLibTools() {
        // 创建独立的日志工具
        logger = new DebugUtil(this, DebugUtil.LogLevel.INFO);
        logger.setPrefix("&f[&bDrcomoMotionCast&r]&f ");
        
        // 创建配置工具
        yamlUtil = new YamlUtil(this, logger);
    }
    
    /**
     * 检查必需的依赖插件
     */
    private boolean checkDependencies() {
        if (!getServer().getPluginManager().isPluginEnabled("DrcomoCoreLib")) {
            getLogger().severe("缺少必需依赖: DrcomoCoreLib");
            return false;
        }
        
        if (!getServer().getPluginManager().isPluginEnabled("MythicMobs")) {
            logger.warn("未检测到 MythicMobs 插件，部分功能将无法使用");
        }
        
        if (!getServer().getPluginManager().isPluginEnabled("ModelEngine")) {
            logger.info("未检测到 ModelEngine 插件，将跳过模型检测功能");
        }
        
        return true;
    }
    
    /**
     * 初始化核心组件
     */
    private void initializeComponents() {
        // 初始化集成模块
        mythicMobsIntegration = new MythicMobsIntegration(logger);
        modelEngineIntegration = new ModelEngineIntegration(logger);
        
        // 初始化核心服务
        cooldownService = new CooldownService(logger);
        targeterRegistry = new TargeterRegistry(logger);
        stateManager = new PlayerStateManager(this, logger);
        ruleLoader = new ModelRuleLoader(this, yamlUtil, logger);
        
        // 初始化引擎和调度器
        actionEngine = new ActionEngine(this, logger, ruleLoader, stateManager, 
                                      cooldownService, targeterRegistry, mythicMobsIntegration);
        tickScheduler = new TickScheduler(this, logger, stateManager, actionEngine);
    }
    
    /**
     * 加载配置文件和规则
     */
    private void loadConfigurations() {
        try {
            // 确保配置目录存在
            getDataFolder().mkdirs();
            
            // 加载主配置
            yamlUtil.loadConfig("settings");
            yamlUtil.loadConfig("lang");
            
            // 加载模型规则
            ruleLoader.loadAllRules();
            
            logger.info("配置文件和规则加载完成");
        } catch (Exception e) {
            logger.error("配置文件加载失败: " + e.getMessage());
        }
    }
    
    /**
     * 注册事件监听器
     */
    private void registerEventListeners() {
        playerEventListener = new PlayerEventListener(actionEngine, stateManager);
        vehicleEventListener = new VehicleEventListener(actionEngine, stateManager);
        entityEventListener = new EntityEventListener(actionEngine, stateManager);
        
        getServer().getPluginManager().registerEvents(playerEventListener, this);
        getServer().getPluginManager().registerEvents(vehicleEventListener, this);
        getServer().getPluginManager().registerEvents(entityEventListener, this);
        
        logger.info("事件监听器注册完成");
    }
    
    /**
     * 注册命令
     */
    private void registerCommands() {
        ReloadCommand reloadCommand = new ReloadCommand(this, ruleLoader, logger, yamlUtil);
        getCommand("drcomomotioncast").setExecutor(reloadCommand);
        getCommand("drcomomotioncast").setTabCompleter(reloadCommand);
        
        logger.info("命令系统注册完成");
    }
    
    /**
     * 启动定时调度器
     */
    private void startSchedulers() {
        tickScheduler.start();
        logger.info("定时调度器启动完成");
    }
    
    // Getter 方法供其他类使用
    public DebugUtil getDebugUtil() {
        return logger;
    }
    
    public YamlUtil getYamlUtil() {
        return yamlUtil;
    }
    
    public ModelRuleLoader getRuleLoader() {
        return ruleLoader;
    }
    
    public PlayerStateManager getStateManager() {
        return stateManager;
    }
    
    public ActionEngine getActionEngine() {
        return actionEngine;
    }
    
    public CooldownService getCooldownService() {
        return cooldownService;
    }
    
    public TargeterRegistry getTargeterRegistry() {
        return targeterRegistry;
    }
    
    public MythicMobsIntegration getMythicMobsIntegration() {
        return mythicMobsIntegration;
    }
    
    public ModelEngineIntegration getModelEngineIntegration() {
        return modelEngineIntegration;
    }
}
