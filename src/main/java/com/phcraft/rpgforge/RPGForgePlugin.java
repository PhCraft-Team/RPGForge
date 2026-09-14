package com.phcraft.rpgforge;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * RPGForge —— 可视化 RPG 道具编辑器插件。
 *
 * <p>核心特性：
 * <ul>
 *   <li>全 GUI 可视化编辑：无需记忆命令，点点点就能做道具</li>
 *   <li>Power 系统：15+ 种内置能力（命令、药水、闪电、爆炸、粒子、传送…）</li>
 *   <li>Trigger 系统：15 种触发方式（右键、左键、命中、食用、持有刻…）</li>
 *   <li>物品数据存储：每个物品一个 YAML 文件，易于备份和分享</li>
 * </ul>
 */
public final class RPGForgePlugin extends JavaPlugin {

    public static final String PERM_USE = "rpgforge.use";
    public static final String PERM_ADMIN = "rpgforge.admin";
    public static final String PERM_REPAIR = "rpgforge.admin.repair";

    private static RPGForgePlugin instance;

    private ItemRegistry registry;
    private ForgeGui gui;
    private PowerTriggerListener triggerListener;

    private NamespacedKey itemIdKey;
    private net.milkbowl.vault.economy.Economy economy; // Vault 经济（软依赖）

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        itemIdKey = new NamespacedKey(this, "item-id");

        registry = new ItemRegistry(this);
        registry.loadAll();
        registry.loadRecipes();
        registry.registerAllRecipes();

        gui = new ForgeGui(this);
        triggerListener = new PowerTriggerListener(this);

        getServer().getPluginManager().registerEvents(gui, this);
        getServer().getPluginManager().registerEvents(triggerListener, this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);

        // 尝试挂钩 Vault 经济（软依赖）
        setupEconomy();

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            for (var node : new ForgeCommand(this).build()) {
                event.registrar().register(node);
            }
        });

        getLogger().info("RPGForge v" + getDescription().getVersion() + " 已启用。"
                + " 共 " + registry.all().size() + " 个 RPG 物品，"
                + registry.allRecipes().size() + " 个配方，"
                + PowerType.values().length + " 种 Power。");
    }

    @Override
    public void onDisable() {
        if (registry != null) {
            registry.saveAll();
            registry.saveRecipes();
        }
        instance = null;
        getLogger().info("RPGForge 已卸载。");
    }

    public static RPGForgePlugin instance() {
        return instance;
    }

    public ItemRegistry registry() {
        return registry;
    }

    public ForgeGui gui() {
        return gui;
    }

    public static NamespacedKey itemIdKey() {
        return instance.itemIdKey;
    }

    /** 获取 Vault 经济实例（可能为 null） */
    public net.milkbowl.vault.economy.Economy economy() {
        return economy;
    }

    /** 尝试挂钩 Vault 经济 */
    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            economy = null;
            return;
        }
        try {
            var rsp = getServer().getServicesManager().getRegistration(
                    net.milkbowl.vault.economy.Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
                getLogger().info("已挂钩 Vault 经济系统。");
            }
        } catch (Exception e) {
            economy = null;
        }
    }

    // ===== 工具方法 =====

    public static String cc(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public static int paramInt(java.util.Map<String, Object> map, String key, int def) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.intValue();
        return def;
    }

    public static double paramDouble(java.util.Map<String, Object> map, String key, double def) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.doubleValue();
        return def;
    }

    public static String msg(String key) {
        return instance.getConfig().getString("messages." + key, key);
    }
}
