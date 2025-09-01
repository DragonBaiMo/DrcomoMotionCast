package cn.drcomo.motioncast.command;

import cn.drcomo.corelib.util.DebugUtil;
import cn.drcomo.corelib.config.YamlUtil;
import cn.drcomo.motioncast.config.ModelRuleLoader;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;

/**
 * 重载命令处理器
 * 处理 /DrcomoMotionCast reload 命令
 */
public class ReloadCommand implements CommandExecutor, TabCompleter {
    
    private final JavaPlugin plugin;
    private final ModelRuleLoader ruleLoader;
    private final DebugUtil logger;
    private final YamlUtil yamlUtil;
    
    public ReloadCommand(JavaPlugin plugin, ModelRuleLoader ruleLoader, DebugUtil logger, YamlUtil yamlUtil) {
        this.plugin = plugin;
        this.ruleLoader = ruleLoader;
        this.logger = logger;
        this.yamlUtil = yamlUtil;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 检查权限
        if (!sender.hasPermission("drcomo.motioncast.reload")) {
            sender.sendMessage("§c你没有权限执行此命令");
            return true;
        }
        
        // 检查参数
        if (args.length == 0) {
            sender.sendMessage("§e用法: /" + label + " reload");
            return true;
        }
        
        if (!"reload".equalsIgnoreCase(args[0])) {
            sender.sendMessage("§c未知的子命令: " + args[0]);
            sender.sendMessage("§e用法: /" + label + " reload");
            return true;
        }
        
        // 执行重载
        try {
            sender.sendMessage("§e正在重载配置文件...");
            
            long startTime = System.currentTimeMillis();
            
            // 重载主配置文件
            yamlUtil.loadConfig("settings");
            yamlUtil.loadConfig("lang");
            
            // 重载规则文件
            ruleLoader.loadAllRules();
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            // 获取统计信息
            int ruleCount = ruleLoader.getTotalRuleCount();
            int modelCount = ruleLoader.getLoadedModels().size();
            
            sender.sendMessage("§a配置重载完成！");
            sender.sendMessage("§7加载了 §e" + modelCount + "§7 个模型，共 §e" + ruleCount + "§7 条规则");
            sender.sendMessage("§7耗时: §e" + duration + "ms");
            
            logger.info("配置重载完成，加载了 " + modelCount + " 个模型，" + ruleCount + " 条规则，耗时 " + duration + "ms");
            
        } catch (Exception e) {
            sender.sendMessage("§c重载配置时发生错误: " + e.getMessage());
            logger.error("重载配置失败: " + e.getMessage());
        }
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // 检查权限
        if (!sender.hasPermission("drcomo.motioncast.reload")) {
            return null;
        }
        
        // Tab补全
        if (args.length == 1) {
            return Arrays.asList("reload");
        }
        
        return null;
    }
}