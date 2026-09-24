package com.phcraft.rpgforge;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * RPGForge 主 GUI 系统。
 *
 * <p>三层界面：
 * <ol>
 *   <li>物品列表（item list）—— 分页显示所有物品，可创建/编辑/删除/获取</li>
 *   <li>物品编辑（item edit）—— 修改基础属性、管理 Power 列表</li>
 *   <li>Power 编辑（power edit）—— 修改 Power 参数、绑定 Trigger</li>
 * </ol>
 *
 * <p>编辑状态通过 inventory → 编辑上下文 的映射来追踪。
 */
public final class ForgeGui implements Listener {

    private final RPGForgePlugin plugin;

    // 编辑上下文：正在编辑的物品 / Power
    private final Map<Inventory, EditContext> contexts = new HashMap<>();

    private record EditContext(String itemId, Integer powerIndex, PageType page, int pageNum) {
        enum PageType { LIST, EDIT, POWER_EDIT, POWER_SELECT, TRIGGER_SELECT, PARAM_EDIT }
    }

    public ForgeGui(RPGForgePlugin plugin) {
        this.plugin = plugin;
    }

    // ============================================================
    //  第一层：物品列表
    // ============================================================

    public void openItemList(Player player) {
        openItemList(player, 0);
    }

    private void openItemList(Player player, int page) {
        var holder = new ForgeHolder(ForgeHolder.Type.ITEM_LIST);
        Inventory inv = Bukkit.createInventory(holder, 54, RPGForgePlugin.cc("&b&l⚒ RPG 道具锻造台 ⚒"));
        holder.inventory = inv;

        contexts.put(inv, new EditContext(null, null, EditContext.PageType.LIST, page));

        // 装饰边框
        ItemStack border = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, border);
            }
        }
        ItemStack accent = namedItem(Material.ORANGE_STAINED_GLASS_PANE, " ", null);
        inv.setItem(0, accent);
        inv.setItem(8, accent);
        inv.setItem(45, accent);
        inv.setItem(53, accent);

        // 填充物品（每页 28 个，4x7，从 slot 10 开始）
        List<RPGItem> items = new ArrayList<>(plugin.registry().all());
        int perPage = 28;
        int totalPages = (int) Math.ceil((double) items.size() / perPage);
        if (totalPages == 0) totalPages = 1;
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        int start = page * perPage;
        int slot = 10;
        for (int i = start; i < Math.min(start + perPage, items.size()); i++) {
            RPGItem item = items.get(i);
            inv.setItem(slot, itemCard(item));
            slot++;
            if ((slot + 1) % 9 == 0) slot += 2; // 跳过右边框
        }

        // 底部按钮
        // 上一页
        if (page > 0) {
            inv.setItem(48, namedItem(Material.ARROW, "&e上一页",
                    List.of("&7当前第 " + (page + 1) + " / " + totalPages + " 页")));
        }
        // 下一页
        if (page < totalPages - 1) {
            inv.setItem(50, namedItem(Material.ARROW, "&e下一页",
                    List.of("&7当前第 " + (page + 1) + " / " + totalPages + " 页")));
        }
        // 创建新物品
        inv.setItem(49, namedItem(Material.EMERALD, "&a&l创建新物品",
                List.of("&7点击创建一个新的 RPG 物品",
                        "&e也可以用命令：&f/rpgforge create <id>")));

        // 页码信息
        inv.setItem(4, namedItem(Material.BOOK, "&e物品列表",
                List.of("&7共 &f" + items.size() + " &7个物品",
                        "&7第 &e" + (page + 1) + " &7/ &e" + totalPages + " &7页")));

        // 给一个示例提示
        inv.setItem(5, namedItem(Material.NETHER_STAR, "&6提示",
                List.of("&7左键物品：编辑属性 / 能力",
                        "&7右键物品：直接获取一个到背包",
                        "&7Shift+左键物品：删除物品")));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.8f, 1.0f);
    }

    /** 物品卡片（用于列表显示） */
    private ItemStack itemCard(RPGItem item) {
        ItemStack stack = item.buildItemStack(1);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add(RPGForgePlugin.cc("&e左键 &7编辑  |  &a右键 &7获取  |  &cShift+左键 &7删除"));
        meta.setLore(lore);

        stack.setItemMeta(meta);
        return stack;
    }

    // ============================================================
    //  第二层：物品编辑
    // ============================================================

    private void openItemEdit(Player player, String itemId, int page) {
        RPGItem item = plugin.registry().get(itemId);
        if (item == null) {
            player.sendMessage(RPGForgePlugin.cc("&c物品不存在：" + itemId));
            return;
        }

        var holder = new ForgeHolder(ForgeHolder.Type.ITEM_EDIT);
        Inventory inv = Bukkit.createInventory(holder, 54,
                RPGForgePlugin.cc("&b编辑 &f" + item.displayName()));
        holder.inventory = inv;

        contexts.put(inv, new EditContext(itemId, null, EditContext.PageType.EDIT, page));

        // 装饰
        ItemStack border = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, border);
            }
        }
        ItemStack accent = namedItem(Material.ORANGE_STAINED_GLASS_PANE, " ", null);
        inv.setItem(0, accent);
        inv.setItem(8, accent);
        inv.setItem(45, accent);
        inv.setItem(53, accent);

        // 左上：物品预览
        inv.setItem(13, item.buildItemStack(1));
        inv.setItem(22, namedItem(Material.NAME_TAG, "&e物品 ID：&f" + item.id(),
                List.of("&7物品的唯一标识符，不可修改")));

        // 基础属性按钮（第二行左侧开始）
        inv.setItem(19, namedItem(Material.NAME_TAG, "&e修改名称",
                List.of("&7当前：" + item.displayName(),
                        "&e点击 &7在聊天栏输入新名称（带 & 颜色码）")));
        inv.setItem(20, namedItem(Material.IRON_SWORD, "&e修改材质",
                List.of("&7当前：&f" + item.material().name(),
                        "&e点击 &7在聊天栏输入新材质名")));
        inv.setItem(21, namedItem(Material.BOOK, "&e修改描述",
                List.of("&7当前行数：&f" + item.lore().size() + " 行",
                        "&e点击 &7在聊天栏逐行编辑描述")));
        inv.setItem(23, namedItem(Material.COMMAND_BLOCK, "&e自定义模型数据",
                List.of("&7当前：&f" + item.customModelData(),
                        "&e点击 &7修改 CustomModelData")));
        inv.setItem(24, namedItem(item.unbreakable() ? Material.ENCHANTED_BOOK : Material.BOOK,
                "&e不可破坏：" + (item.unbreakable() ? "&a开启" : "&c关闭"),
                List.of("&7点击切换")));
        inv.setItem(25, namedItem(Material.ANVIL, "&e最大耐久",
                List.of("&7当前：&f" + item.maxDurability(),
                        "&e点击 &7修改最大耐久值（0=使用默认）")));

        // Power 列表区（第 4 行，中间 7 格，支持翻页）
        // 标题
        int totalPowers = item.powers().size();
        int powersPerPage = 7;
        int totalPowerPages = Math.max(1, (int) Math.ceil((double) totalPowers / powersPerPage));
        if (page < 0) page = 0;
        if (page >= totalPowerPages) page = totalPowerPages - 1;

        inv.setItem(28, namedItem(Material.BLAZE_POWDER, "&6&l能力 (Power) 列表",
                List.of("&7共 &e" + totalPowers + " &7个能力",
                        "&7第 &e" + (page + 1) + " / " + totalPowerPages + " &7页",
                        "&e点击能力 &7编辑参数和触发方式",
                        "&a点击下方 + 号 &7添加新能力")));

        // 展示 Powers（每页 7 个，一行）
        int powerStart = page * powersPerPage;
        for (int i = 0; i < powersPerPage; i++) {
            int idx = powerStart + i;
            if (idx >= totalPowers) break;
            RPGPower power = item.powers().get(idx);
            inv.setItem(29 + i, powerCard(power, idx + 1));
        }

        // 翻页按钮
        if (page > 0) {
            inv.setItem(27, namedItem(Material.ARROW, "&a上一页",
                    List.of("&7第 " + (page + 1) + " / " + totalPowerPages + " 页")));
        } else {
            inv.setItem(27, namedItem(Material.GRAY_STAINED_GLASS_PANE, " ", null));
        }
        if (page < totalPowerPages - 1) {
            inv.setItem(36, namedItem(Material.ARROW, "&a下一页",
                    List.of("&7第 " + (page + 1) + " / " + totalPowerPages + " 页")));
        } else {
            inv.setItem(36, namedItem(Material.GRAY_STAINED_GLASS_PANE, " ", null));
        }

        // 添加 Power 按钮
        inv.setItem(37, namedItem(Material.LIME_DYE, "&a&l＋ 添加能力",
                List.of("&7点击从列表中选择一种能力添加")));

        // 底部：返回 + 保存 + 测试获取
        inv.setItem(48, namedItem(Material.ARROW, "&e返回物品列表", null));
        inv.setItem(49, namedItem(Material.EMERALD_BLOCK, "&a&l保存",
                List.of("&7保存当前物品到配置文件",
                        "&e（编辑时会自动保存，点击也可手动保存）")));
        inv.setItem(50, namedItem(Material.CHEST, "&a获取到背包",
                List.of("&7拿一个到背包里测试效果")));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
    }

    /** Power 卡片（用于物品编辑界面展示） */
    private ItemStack powerCard(RPGPower power, int index) {
        Material mat;
        try {
            mat = Material.valueOf(power.type().iconMaterial());
        } catch (IllegalArgumentException e) {
            mat = Material.FIREWORK_STAR;
        }
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(RPGForgePlugin.cc("&e" + power.type().displayName()
                + " &8#" + (power.index() + 1)));

        List<String> lore = new ArrayList<>();
        lore.add(RPGForgePlugin.cc("&7" + power.type().description()));
        lore.add("");
        lore.add(RPGForgePlugin.cc("&7触发方式："));
        for (TriggerType t : power.triggers()) {
            lore.add(RPGForgePlugin.cc(" &8▪ &f" + t.displayName()));
        }
        lore.add("");
        lore.add(RPGForgePlugin.cc("&e左键 &7编辑参数"));
        lore.add(RPGForgePlugin.cc("&cShift+左键 &7移除能力"));

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ============================================================
    //  Power 选择（添加新 Power 时弹出）
    // ============================================================

    private void openPowerSelect(Player player, String itemId, int page) {
        var holder = new ForgeHolder(ForgeHolder.Type.POWER_SELECT);
        Inventory inv = Bukkit.createInventory(holder, 54,
                RPGForgePlugin.cc("&b选择要添加的能力"));
        holder.inventory = inv;

        contexts.put(inv, new EditContext(itemId, null, EditContext.PageType.POWER_SELECT, page));

        ItemStack border = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, border);
            }
        }

        PowerType[] types = PowerType.values();
        int perPage = 28; // 4行 × 7列
        int totalPages = (int) Math.ceil((double) types.length / perPage);
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        int startIdx = page * perPage;
        int slot = 10; // 从第 2 行第 2 列开始
        for (int i = 0; i < perPage; i++) {
            int idx = startIdx + i;
            if (idx >= types.length) break;

            inv.setItem(slot, powerTypeCard(types[idx], idx + 1));
            slot++;
            if ((slot + 1) % 9 == 0) slot += 2; // 跳过右边框
        }

        // 返回按钮
        inv.setItem(49, namedItem(Material.ARROW, "&e返回", null));

        // 翻页按钮
        if (page > 0) {
            inv.setItem(48, namedItem(Material.ARROW, "&a上一页",
                    List.of("&7第 " + (page + 1) + " / " + totalPages + " 页")));
        } else {
            inv.setItem(48, namedItem(Material.GRAY_STAINED_GLASS_PANE, " ", null));
        }
        if (page < totalPages - 1) {
            inv.setItem(50, namedItem(Material.ARROW, "&a下一页",
                    List.of("&7第 " + (page + 1) + " / " + totalPages + " 页")));
        } else {
            inv.setItem(50, namedItem(Material.GRAY_STAINED_GLASS_PANE, " ", null));
        }

        // 页码提示（中间显示总数）
        inv.setItem(53, namedItem(Material.BOOK, "&b共 " + types.length + " 种能力",
                List.of("&7第 " + (page + 1) + " / " + totalPages + " 页")));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
    }

    private ItemStack powerTypeCard(PowerType type, int index) {
        Material mat;
        try {
            mat = Material.valueOf(type.iconMaterial());
        } catch (IllegalArgumentException e) {
            mat = Material.FIREWORK_STAR;
        }
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(RPGForgePlugin.cc("&e" + type.displayName() + " &8#" + index));
        List<String> lore = new ArrayList<>();
        lore.add(RPGForgePlugin.cc("&7" + type.description()));
        lore.add("");
        lore.add(RPGForgePlugin.cc("&7参数：&f" + type.paramInfo().size() + " 个"));
        for (var entry : type.paramInfo().entrySet()) {
            lore.add(RPGForgePlugin.cc(" &8▪ &e" + entry.getValue().name()
                    + " &7(" + entry.getValue().type().name().toLowerCase() + ")"));
        }
        lore.add("");
        lore.add(RPGForgePlugin.cc("&a点击 &7添加此能力"));

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ============================================================
    //  第三层：Power 编辑
    // ============================================================

    private void openPowerEdit(Player player, String itemId, int powerIndex) {
        RPGItem item = plugin.registry().get(itemId);
        if (item == null || powerIndex >= item.powers().size()) return;
        RPGPower power = item.powers().get(powerIndex);

        var holder = new ForgeHolder(ForgeHolder.Type.POWER_EDIT);
        Inventory inv = Bukkit.createInventory(holder, 54,
                RPGForgePlugin.cc("&b编辑能力：&f" + power.type().displayName()));
        holder.inventory = inv;

        contexts.put(inv, new EditContext(itemId, powerIndex, EditContext.PageType.POWER_EDIT, 0));

        ItemStack border = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, border);
            }
        }
        ItemStack accent = namedItem(Material.PURPLE_STAINED_GLASS_PANE, " ", null);
        inv.setItem(0, accent);
        inv.setItem(8, accent);

        // 顶部：Power 类型信息
        Material iconMat;
        try {
            iconMat = Material.valueOf(power.type().iconMaterial());
        } catch (IllegalArgumentException e) {
            iconMat = Material.FIREWORK_STAR;
        }
        ItemStack icon = new ItemStack(iconMat);
        ItemMeta iconMeta = icon.getItemMeta();
        if (iconMeta != null) {
            iconMeta.setDisplayName(RPGForgePlugin.cc("&e&l" + power.type().displayName()));
            iconMeta.setLore(List.of(
                    RPGForgePlugin.cc("&7" + power.type().description()),
                    "",
                    RPGForgePlugin.cc("&7执行顺序：&e第 " + (powerIndex + 1) + " 个")
            ));
            icon.setItemMeta(iconMeta);
        }
        inv.setItem(13, icon);

        // 参数编辑区（第 3、4 行）
        int slot = 19;
        for (var entry : power.type().paramInfo().entrySet()) {
            String paramKey = entry.getKey();
            PowerType.ParamInfo info = entry.getValue();
            Object value = power.params().get(paramKey);

            Material mat = switch (info.type()) {
                case INTEGER -> Material.COMPARATOR;
                case DOUBLE -> Material.REPEATER;
                case STRING -> Material.PAPER;
                case BOOLEAN -> Material.LEVER;
            };

            ItemStack paramItem = new ItemStack(mat);
            ItemMeta meta = paramItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(RPGForgePlugin.cc("&e" + info.name()));
                List<String> lore = new ArrayList<>();
                lore.add(RPGForgePlugin.cc("&7类型：&f" + info.type().name().toLowerCase()));
                lore.add(RPGForgePlugin.cc("&7当前值：&f" + value));
                lore.add(RPGForgePlugin.cc("&8" + info.description()));
                lore.add("");
                if (info.type() == PowerType.ParamType.BOOLEAN) {
                    lore.add(RPGForgePlugin.cc("&e点击 &7切换 true/false"));
                } else {
                    lore.add(RPGForgePlugin.cc("&e点击 &7在聊天栏输入新值"));
                }
                meta.setLore(lore);
                paramItem.setItemMeta(meta);
            }

            inv.setItem(slot, paramItem);
            slot++;
            if (slot == 26) slot = 28; // 跳到下一行（留出空位）
        }

        // Trigger 管理
        List<String> triggerLore = new ArrayList<>();
        triggerLore.add("&7当前绑定：&e" + power.triggers().size() + " 种");
        triggerLore.add("");
        for (TriggerType t : power.triggers()) {
            triggerLore.add(" &8▪ &f" + t.displayName());
        }
        inv.setItem(37, namedItem(Material.LEVER, "&6触发方式 (Trigger)", triggerLore));

        // 上下移
        inv.setItem(40, namedItem(Material.HOPPER, "&e调整顺序",
                List.of("&7当前位置：&e第 " + (powerIndex + 1) + " 个",
                        "&a左键 &7上移  |  &c右键 &7下移")));

        // 底部按钮
        inv.setItem(48, namedItem(Material.ARROW, "&e返回物品编辑", null));
        inv.setItem(49, namedItem(Material.RED_DYE, "&c&l删除此能力",
                List.of("&7从物品上移除此能力")));
        inv.setItem(50, namedItem(Material.EMERALD_BLOCK, "&a保存",
                List.of("&7保存更改到配置文件")));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
    }

    // ============================================================
    //  Trigger 选择界面
    // ============================================================

    private void openTriggerSelect(Player player, String itemId, int powerIndex) {
        RPGItem item = plugin.registry().get(itemId);
        if (item == null || powerIndex >= item.powers().size()) return;
        RPGPower power = item.powers().get(powerIndex);

        var holder = new ForgeHolder(ForgeHolder.Type.TRIGGER_SELECT);
        Inventory inv = Bukkit.createInventory(holder, 54,
                RPGForgePlugin.cc("&b选择触发方式"));
        holder.inventory = inv;

        contexts.put(inv, new EditContext(itemId, powerIndex, EditContext.PageType.TRIGGER_SELECT, 0));

        ItemStack border = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, border);
            }
        }

        TriggerType[] triggers = TriggerType.values();
        int slot = 10;
        for (TriggerType trigger : triggers) {
            boolean selected = power.triggers().contains(trigger);
            ItemStack tItem = new ItemStack(selected ? Material.LIME_DYE : Material.GRAY_DYE);
            ItemMeta meta = tItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(RPGForgePlugin.cc(
                        (selected ? "&a✔ " : "&7○ ") + trigger.displayName()));
                meta.setLore(List.of(
                        RPGForgePlugin.cc("&7" + trigger.description()),
                        "",
                        RPGForgePlugin.cc(selected ? "&e点击 &7取消绑定" : "&e点击 &7绑定此触发方式")
                ));
                tItem.setItemMeta(meta);
            }
            inv.setItem(slot, tItem);
            slot++;
            if ((slot + 1) % 9 == 0) slot += 2;
            if (slot >= 44) break;
        }

        inv.setItem(49, namedItem(Material.ARROW, "&e返回", null));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
    }

    // ============================================================
    //  点击处理
    // ============================================================

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof ForgeHolder holder)) return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();

        // 权限检查：撤销权限后拦截所有编辑操作
        if (!player.hasPermission(RPGForgePlugin.PERM_USE)) {
            player.sendMessage(RPGForgePlugin.cc("&c你没有权限使用 RPGForge 编辑器。"));
            player.closeInventory();
            cleanupChat(player);
            return;
        }

        int raw = event.getRawSlot();
        if (raw < 0 || raw >= top.getSize()) return;

        EditContext ctx = contexts.get(top);
        if (ctx == null) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        switch (holder.type) {
            case ITEM_LIST -> handleListClick(player, top, raw, ctx, event);
            case ITEM_EDIT -> handleEditClick(player, top, raw, ctx, event);
            case POWER_SELECT -> handlePowerSelectClick(player, top, raw, ctx);
            case POWER_EDIT -> handlePowerEditClick(player, top, raw, ctx, event);
            case TRIGGER_SELECT -> handleTriggerSelectClick(player, top, raw, ctx);
            default -> {}
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof ForgeHolder)) return;
        contexts.remove(top);
    }

    /** 禁止拖拽真实物品进编辑菜单，避免关掉菜单后丢失 */
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof ForgeHolder) {
            event.setCancelled(true);
        }
    }

    // ----- 列表界面点击 -----
    private void handleListClick(Player player, Inventory top, int raw, EditContext ctx,
                                 InventoryClickEvent event) {
        // 上一页
        if (raw == 48 && event.getCurrentItem().getType() == Material.ARROW) {
            openItemList(player, ctx.pageNum() - 1);
            return;
        }
        // 下一页
        if (raw == 50 && event.getCurrentItem().getType() == Material.ARROW) {
            openItemList(player, ctx.pageNum() + 1);
            return;
        }
        // 创建
        if (raw == 49 && event.getCurrentItem().getType() == Material.EMERALD) {
            player.closeInventory();
            player.sendMessage(RPGForgePlugin.cc(
                    "&e请在聊天栏输入新物品的 ID（小写字母+下划线）："));
            // 注册一次性聊天监听
            awaitChatInput(player, "create-item", input -> {
                String id = input.trim().toLowerCase();
                if (!ItemRegistry.isValidId(id)) {
                    player.sendMessage(RPGForgePlugin.cc("&c无效的物品 ID。"));
                    return;
                }
                if (plugin.registry().exists(id)) {
                    player.sendMessage(RPGForgePlugin.cc("&c物品 " + id + " 已存在。"));
                    return;
                }
                RPGItem newItem = new RPGItem(id);
                plugin.registry().add(newItem);
                player.sendMessage(RPGForgePlugin.cc("&a已创建物品 &e" + id + "&a！"));
                openItemEdit(player, id, 0);
            });
            return;
        }

        // 点击了某个物品卡片
        String itemId = findItemIdFromSlot(top, raw, ctx.pageNum());
        if (itemId == null) return;

        if (event.isShiftClick() && event.isLeftClick()) {
            // Shift+左键：删除
            RPGItem item = plugin.registry().get(itemId);
            if (item != null) {
                plugin.registry().remove(itemId);
                player.sendMessage(RPGForgePlugin.cc("&c已删除物品 &e" + itemId + "&c。"));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
                openItemList(player, ctx.pageNum());
            }
            return;
        }

        if (event.isRightClick()) {
            // 右键：获取到背包
            RPGItem item = plugin.registry().get(itemId);
            if (item != null) {
                player.getInventory().addItem(item.buildItemStack(1));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.0f);
            }
            return;
        }

        // 左键：编辑
        openItemEdit(player, itemId, 0);
    }

    /** 从列表界面的 slot 反查物品 ID */
    private String findItemIdFromSlot(Inventory top, int slot, int page) {
        List<RPGItem> items = new ArrayList<>(plugin.registry().all());
        int perPage = 28;
        int start = page * perPage;

        // 计算在内容区的索引
        int row = slot / 9;
        int col = slot % 9;
        if (row < 1 || row > 4 || col < 1 || col > 7) return null;

        int idx = (row - 1) * 7 + (col - 1);
        int globalIdx = start + idx;
        if (globalIdx < 0 || globalIdx >= items.size()) return null;
        return items.get(globalIdx).id();
    }

    // ----- 物品编辑界面点击 -----
    private void handleEditClick(Player player, Inventory top, int raw, EditContext ctx,
                                 InventoryClickEvent event) {
        RPGItem item = plugin.registry().get(ctx.itemId());
        if (item == null) return;

        // 返回
        if (raw == 48) {
            openItemList(player, 0);
            return;
        }
        // 保存
        if (raw == 49) {
            plugin.registry().saveItem(item);
            player.sendMessage(RPGForgePlugin.cc("&a物品 &e" + item.id() + " &a已保存！"));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.0f);
            return;
        }
        // 获取
        if (raw == 50) {
            player.getInventory().addItem(item.buildItemStack(1));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.0f);
            return;
        }

        // 修改名称
        if (raw == 19) {
            promptChatInput(player, "输入新的物品名称（支持 & 颜色码）：", input -> {
                item.setDisplayName(input);
                plugin.registry().saveItem(item);
                openItemEdit(player, item.id(), 0);
            });
            return;
        }
        // 修改材质
        if (raw == 20) {
            promptChatInput(player, "输入新的材质名（如 DIAMOND_SWORD）：", input -> {
                try {
                    Material mat = Material.valueOf(input.trim().toUpperCase());
                    item.setMaterial(mat);
                    plugin.registry().saveItem(item);
                } catch (IllegalArgumentException e) {
                    player.sendMessage(RPGForgePlugin.cc("&c无效的材质名：" + input));
                }
                openItemEdit(player, item.id(), 0);
            });
            return;
        }
        // 修改描述
        if (raw == 21) {
            DescEditor editor = new DescEditor(item, player, this);
            promptChatInput(player, "逐行编辑描述。输入第 1 行内容（输入 cancel 取消）：",
                    editor::handleLine);
            return;
        }
        // CustomModelData
        if (raw == 23) {
            promptChatInput(player, "输入 CustomModelData 数值：", input -> {
                try {
                    int val = Integer.parseInt(input.trim());
                    item.setCustomModelData(val);
                    plugin.registry().saveItem(item);
                } catch (NumberFormatException e) {
                    player.sendMessage(RPGForgePlugin.cc("&c无效的数字：" + input));
                }
                openItemEdit(player, item.id(), 0);
            });
            return;
        }
        // 不可破坏切换
        if (raw == 24) {
            item.setUnbreakable(!item.unbreakable());
            plugin.registry().saveItem(item);
            openItemEdit(player, item.id(), 0);
            return;
        }
        // 最大耐久
        if (raw == 25) {
            promptChatInput(player, "输入最大耐久值（0 = 使用默认）：", input -> {
                try {
                    int val = Integer.parseInt(input.trim());
                    item.setMaxDurability(Math.max(0, val));
                    plugin.registry().saveItem(item);
                } catch (NumberFormatException e) {
                    player.sendMessage(RPGForgePlugin.cc("&c无效的数字：" + input));
                }
                openItemEdit(player, item.id(), 0);
            });
            return;
        }

        // 添加 Power
        if (raw == 37) {
            openPowerSelect(player, item.id(), 0);
            return;
        }

        // 上一页（Power 列表）
        if (raw == 27) {
            if (ctx.pageNum() > 0) {
                openItemEdit(player, item.id(), ctx.pageNum() - 1);
            }
            return;
        }

        // 下一页（Power 列表）
        if (raw == 36) {
            int totalPowerPages = Math.max(1, (int) Math.ceil((double) item.powers().size() / 7));
            if (ctx.pageNum() < totalPowerPages - 1) {
                openItemEdit(player, item.id(), ctx.pageNum() + 1);
            }
            return;
        }

        // 点击 Power 卡片（slot 29-35）
        if (raw >= 29 && raw <= 35) {
            int powerIdx = ctx.pageNum() * 7 + (raw - 29);
            if (powerIdx < item.powers().size()) {
                if (event.isShiftClick() && event.isLeftClick()) {
                    // Shift+左键：移除
                    item.removePower(powerIdx);
                    plugin.registry().saveItem(item);
                    player.sendMessage(RPGForgePlugin.cc("&c已移除能力。"));
                    openItemEdit(player, item.id(), ctx.pageNum());
                } else {
                    openPowerEdit(player, item.id(), powerIdx);
                }
            }
            return;
        }
    }

    // ----- Power 选择界面点击 -----
    private void handlePowerSelectClick(Player player, Inventory top, int raw, EditContext ctx) {
        // 返回
        if (raw == 49) {
            openItemEdit(player, ctx.itemId(), 0);
            return;
        }

        // 上一页
        if (raw == 48) {
            if (ctx.pageNum() > 0) {
                openPowerSelect(player, ctx.itemId(), ctx.pageNum() - 1);
            }
            return;
        }

        // 下一页
        if (raw == 50) {
            int totalPages = (int) Math.ceil((double) PowerType.values().length / 28);
            if (ctx.pageNum() < totalPages - 1) {
                openPowerSelect(player, ctx.itemId(), ctx.pageNum() + 1);
            }
            return;
        }

        PowerType type = findPowerTypeFromSlot(raw, ctx.pageNum());
        if (type == null) return;

        RPGItem item = plugin.registry().get(ctx.itemId());
        if (item == null) return;

        RPGPower power = new RPGPower(type);
        item.addPower(power);
        plugin.registry().saveItem(item);

        player.sendMessage(RPGForgePlugin.cc(
                "&a已添加能力 &e" + type.displayName() + " &a！"));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);

        // 直接进入编辑
        openPowerEdit(player, item.id(), item.powers().size() - 1);
    }

    private PowerType findPowerTypeFromSlot(int slot, int page) {
        int row = slot / 9;
        int col = slot % 9;
        if (row < 1 || row > 4 || col < 1 || col > 7) return null;
        int idx = page * 28 + (row - 1) * 7 + (col - 1);
        PowerType[] types = PowerType.values();
        if (idx < 0 || idx >= types.length) return null;
        return types[idx];
    }

    // ----- Power 编辑界面点击 -----
    private void handlePowerEditClick(Player player, Inventory top, int raw, EditContext ctx,
                                      InventoryClickEvent event) {
        RPGItem item = plugin.registry().get(ctx.itemId());
        if (item == null || ctx.powerIndex() == null) return;
        RPGPower power = item.powers().get(ctx.powerIndex());

        // 返回
        if (raw == 48) {
            openItemEdit(player, item.id(), 0);
            return;
        }
        // 删除
        if (raw == 49) {
            item.removePower(ctx.powerIndex());
            plugin.registry().saveItem(item);
            player.sendMessage(RPGForgePlugin.cc("&c已移除能力。"));
            openItemEdit(player, item.id(), 0);
            return;
        }
        // 保存
        if (raw == 50) {
            plugin.registry().saveItem(item);
            player.sendMessage(RPGForgePlugin.cc("&a已保存！"));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.0f);
            return;
        }
        // Trigger 选择
        if (raw == 37) {
            openTriggerSelect(player, item.id(), ctx.powerIndex());
            return;
        }
        // 调整顺序
        if (raw == 40) {
            int idx = ctx.powerIndex();
            if (event.isLeftClick() && idx > 0) {
                // 上移
                java.util.Collections.swap(item.powers(), idx, idx - 1);
                for (int i = 0; i < item.powers().size(); i++) {
                    item.powers().get(i).setIndex(i);
                }
                plugin.registry().saveItem(item);
                openPowerEdit(player, item.id(), idx - 1);
            } else if (event.isRightClick() && idx < item.powers().size() - 1) {
                // 下移
                java.util.Collections.swap(item.powers(), idx, idx + 1);
                for (int i = 0; i < item.powers().size(); i++) {
                    item.powers().get(i).setIndex(i);
                }
                plugin.registry().saveItem(item);
                openPowerEdit(player, item.id(), idx + 1);
            }
            return;
        }

        // 参数编辑：找到对应的参数 key
        String paramKey = findParamKeyFromSlot(power, raw);
        if (paramKey == null) return;

        PowerType.ParamInfo info = power.type().paramInfo().get(paramKey);
        if (info == null) return;

        if (info.type() == PowerType.ParamType.BOOLEAN) {
            // 布尔值直接切换
            boolean current = power.paramBool(paramKey, false);
            power.params().put(paramKey, !current);
            plugin.registry().saveItem(item);
            openPowerEdit(player, item.id(), ctx.powerIndex());
        } else {
            // 其他类型走聊天输入
            promptChatInput(player, "输入 " + info.name() + " 的新值（"
                    + info.type().name().toLowerCase() + "）：", input -> {
                try {
                    Object val = switch (info.type()) {
                        case INTEGER -> Integer.parseInt(input.trim());
                        case DOUBLE -> Double.parseDouble(input.trim());
                        case STRING -> input;
                        case BOOLEAN -> Boolean.parseBoolean(input.trim());
                    };
                    power.params().put(paramKey, val);
                    plugin.registry().saveItem(item);
                } catch (NumberFormatException e) {
                    player.sendMessage(RPGForgePlugin.cc("&c无效的值：" + input));
                }
                openPowerEdit(player, item.id(), ctx.powerIndex());
            });
        }
    }

    /** 从 slot 反查参数 key（参数从 slot 19 开始，到 25，然后 28-34） */
    private String findParamKeyFromSlot(RPGPower power, int slot) {
        List<String> keys = new ArrayList<>(power.type().paramInfo().keySet());
        int idx = -1;
        if (slot >= 19 && slot <= 25) {
            idx = slot - 19;
        } else if (slot >= 28 && slot <= 34) {
            idx = 7 + (slot - 28);
        }
        if (idx < 0 || idx >= keys.size()) return null;
        return keys.get(idx);
    }

    // ----- Trigger 选择界面点击 -----
    private void handleTriggerSelectClick(Player player, Inventory top, int raw, EditContext ctx) {
        if (raw == 49) {
            openPowerEdit(player, ctx.itemId(), ctx.powerIndex());
            return;
        }

        TriggerType trigger = findTriggerFromSlot(raw);
        if (trigger == null) return;

        RPGItem item = plugin.registry().get(ctx.itemId());
        if (item == null || ctx.powerIndex() == null) return;
        RPGPower power = item.powers().get(ctx.powerIndex());

        if (power.triggers().contains(trigger)) {
            if (power.triggers().size() > 1) {
                power.triggers().remove(trigger);
            } else {
                player.sendMessage(RPGForgePlugin.cc("&c至少需要保留一个触发方式！"));
                return;
            }
        } else {
            power.triggers().add(trigger);
        }

        plugin.registry().saveItem(item);
        openTriggerSelect(player, item.id(), ctx.powerIndex());
    }

    private TriggerType findTriggerFromSlot(int slot) {
        int row = slot / 9;
        int col = slot % 9;
        if (row < 1 || col < 1 || col > 7) return null;
        int idx = (row - 1) * 7 + (col - 1);
        TriggerType[] triggers = TriggerType.values();
        if (idx < 0 || idx >= triggers.length) return null;
        return triggers[idx];
    }

    // ============================================================
    //  聊天输入回调系统（队列 + 主线程执行）
    // ============================================================

    /**
     * The async listener only touches this concurrent map and each state's queue.
     * The callback and all Bukkit access are confined to the primary thread.
     */
    private final Map<UUID, ChatInputState> chatInputs = new ConcurrentHashMap<>();

    private static final class ChatInputState {
        private final ConcurrentLinkedQueue<String> messages = new ConcurrentLinkedQueue<>();
        private final AtomicBoolean scheduled = new AtomicBoolean();
        private volatile boolean active = true;
        private Consumer<String> callback;
    }

    /** 提示玩家在聊天栏输入内容，回调在主线程执行 */
    private void promptChatInput(Player player, String prompt, Consumer<String> callback) {
        player.closeInventory();
        player.sendMessage(RPGForgePlugin.cc("&e[RPGForge] " + prompt));
        player.sendMessage(RPGForgePlugin.cc("&7（输入 cancel 取消）"));
        cleanupChat(player);
        awaitChatInput(player, "prompt", callback);
    }

    private void awaitChatInput(Player player, String context, Consumer<String> callback) {
        UUID id = player.getUniqueId();
        ChatInputState state = chatInputs.get(id);
        if (state == null || !state.active) {
            state = new ChatInputState();
            chatInputs.put(id, state);
        }
        state.callback = callback;
    }

    /** 异步线程安全：该玩家是否存在等待中的聊天输入（供事件监听器先行取消事件） */
    public boolean hasPendingInput(Player player) {
        ChatInputState state = chatInputs.get(player.getUniqueId());
        return state != null && state.active;
    }

    /** AsyncPlayerChatEvent: cancel immediately, enqueue without accessing Bukkit state. */
    public boolean handleChatInput(Player player, String message) {
        ChatInputState state = chatInputs.get(player.getUniqueId());
        if (state == null || !state.active) return false;

        state.messages.add(message);
        if (state.scheduled.compareAndSet(false, true)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> processChatQueue(player, state));
        }
        return true;
    }

    /** Main thread only. A completed input discards messages queued for that input. */
    private void processChatQueue(Player player, ChatInputState state) {
        UUID id = player.getUniqueId();
        try {
            String message;
            while (state.active && chatInputs.get(id) == state
                    && (message = state.messages.poll()) != null) {
                if (!player.isOnline()) {
                    cleanupChat(player);
                    break;
                }
                if (!player.hasPermission(RPGForgePlugin.PERM_USE)) {
                    cleanupChat(player);
                    player.sendMessage(RPGForgePlugin.cc("&c你的 RPGForge 编辑权限已被撤销。"));
                    break;
                }

                Consumer<String> callback = state.callback;
                if (callback == null) {
                    discardChatInput(id, state);
                    break;
                }
                state.callback = null;
                if ("cancel".equalsIgnoreCase(message.trim())) {
                    discardChatInput(id, state);
                    player.sendMessage(RPGForgePlugin.cc("&7已取消。"));
                    break;
                }

                callback.accept(message);
                // A description editor may re-arm this same input for its next line.
                // Other callbacks finish here; their queued messages must not leak.
                if (state.callback == null) {
                    discardChatInput(id, state);
                    break;
                }
            }
        } finally {
            state.scheduled.set(false);
            if (state.active && chatInputs.get(id) == state && !state.messages.isEmpty()
                    && state.scheduled.compareAndSet(false, true)) {
                plugin.getServer().getScheduler().runTask(plugin, () -> processChatQueue(player, state));
            }
        }
    }

    private void discardChatInput(UUID id, ChatInputState state) {
        state.active = false;
        chatInputs.remove(id, state);
        state.messages.clear();
    }

    private void cleanupChat(Player player) {
        ChatInputState state = chatInputs.remove(player.getUniqueId());
        if (state != null) {
            state.active = false;
            state.messages.clear();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cleanupChat(event.getPlayer());
    }

    // ============================================================
    //  描述编辑器（逐行编辑）
    // ============================================================

    private static class DescEditor {
        private final RPGItem item;
        private final Player player;
        private final ForgeGui gui;
        private final List<String> newLore;
        private int lineIndex;

        DescEditor(RPGItem item, Player player, ForgeGui gui) {
            this.item = item;
            this.player = player;
            this.gui = gui;
            this.newLore = new ArrayList<>(item.lore());
            this.lineIndex = 0;
        }

        private void handleLine(String input) {
            String cmd = input.trim().toLowerCase();
            if ("done".equals(cmd)) {
                item.setLore(newLore);
                gui.plugin.registry().saveItem(item);
                player.sendMessage(RPGForgePlugin.cc("&a描述已更新！"));
                gui.openItemEdit(player, item.id(), 0);
                return;
            }
            if ("cancel".equals(cmd)) {
                player.sendMessage(RPGForgePlugin.cc("&7已取消。"));
                gui.openItemEdit(player, item.id(), 0);
                return;
            }
            if ("skip".equals(cmd)) {
                lineIndex++;
                nextPrompt();
                return;
            }
            if ("delete".equals(cmd)) {
                if (lineIndex < newLore.size()) {
                    newLore.remove(lineIndex);
                }
                nextPrompt();
                return;
            }

            // 正常内容
            if (lineIndex < newLore.size()) {
                newLore.set(lineIndex, input);
            } else {
                newLore.add(input);
            }
            lineIndex++;
            nextPrompt();
        }

        private void nextPrompt() {
            if (lineIndex < newLore.size()) {
                player.sendMessage(RPGForgePlugin.cc(
                        "&e第 " + (lineIndex + 1) + " 行（当前：&f" + newLore.get(lineIndex) + "&e）："));
                player.sendMessage(RPGForgePlugin.cc(
                        "&7输入新内容，或输入 &eskip &7保留，&edelete &7删除此行，&edone &7完成"));
            } else {
                player.sendMessage(RPGForgePlugin.cc(
                        "&e第 " + (lineIndex + 1) + " 行（新行）："));
                player.sendMessage(RPGForgePlugin.cc(
                        "&7输入新内容，或输入 &edone &7完成"));
            }
            gui.awaitChatInput(player, "description", this::handleLine);
        }
    }

    // ============================================================
    //  工具
    // ============================================================

    private static ItemStack namedItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(RPGForgePlugin.cc(name));
            if (lore != null) {
                meta.setLore(lore.stream().map(RPGForgePlugin::cc).toList());
            }
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    /** GUI 归属标记 */
    public static final class ForgeHolder implements InventoryHolder {
        enum Type { ITEM_LIST, ITEM_EDIT, POWER_EDIT, POWER_SELECT, TRIGGER_SELECT, PARAM_EDIT }

        private final Type type;
        private Inventory inventory;

        ForgeHolder(Type type) {
            this.type = type;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
