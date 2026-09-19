package com.phcraft.rpgforge;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * RPGForge 命令注册。
 */
public class ForgeCommand {

    private final RPGForgePlugin plugin;

    public ForgeCommand(RPGForgePlugin plugin) {
        this.plugin = plugin;
    }

    public Collection<LiteralCommandNode<CommandSourceStack>> build() {
        List<LiteralCommandNode<CommandSourceStack>> nodes = new ArrayList<>();

        // /rpg —— 打开编辑器
        nodes.add(Commands.literal("rpg")
                .requires(src -> src.getSender().hasPermission(RPGForgePlugin.PERM_USE))
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("§c该命令只能由玩家执行。");
                        return 0;
                    }
                    plugin.gui().openItemList(player);
                    return Command.SINGLE_SUCCESS;
                })
                .build());

        // /rpgforge —— 管理命令
        nodes.add(Commands.literal("rpgforge")
                .requires(src -> src.getSender().hasPermission(RPGForgePlugin.PERM_ADMIN))
                .then(Commands.literal("give")
                        .then(Commands.argument("item", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (String id : plugin.registry().ids()) {
                                        builder.suggest(id);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    CommandSender sender = ctx.getSource().getSender();
                                    if (!(sender instanceof Player player)) {
                                        sender.sendMessage("§c请指定玩家。");
                                        return 0;
                                    }
                                    String itemId = StringArgumentType.getString(ctx, "item");
                                    giveItem(player, itemId, 1, player);
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                                                builder.suggest(p.getName());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            CommandSender sender = ctx.getSource().getSender();
                                            String itemId = StringArgumentType.getString(ctx, "item");
                                            String targetName = StringArgumentType.getString(ctx, "player");
                                            Player target = org.bukkit.Bukkit.getPlayer(targetName);
                                            if (target == null) {
                                                sender.sendMessage("§c找不到玩家：" + targetName);
                                                return 0;
                                            }
                                            giveItem(sender, itemId, 1, target);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                                .executes(ctx -> {
                                                    CommandSender sender = ctx.getSource().getSender();
                                                    String itemId = StringArgumentType.getString(ctx, "item");
                                                    String targetName = StringArgumentType.getString(ctx, "player");
                                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                    Player target = org.bukkit.Bukkit.getPlayer(targetName);
                                                    if (target == null) {
                                                        sender.sendMessage("§c找不到玩家：" + targetName);
                                                        return 0;
                                                    }
                                                    giveItem(sender, itemId, amount, target);
                                                    return Command.SINGLE_SUCCESS;
                                                })))))
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            listItems(sender);
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            plugin.registry().loadAll();
                            plugin.registry().reloadRecipes();
                            sender.sendMessage("§a[RPGForge] 重载完成，共 "
                                    + plugin.registry().all().size() + " 个物品，"
                                    + plugin.registry().allRecipes().size() + " 个配方。");
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("create")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> {
                                    CommandSender sender = ctx.getSource().getSender();
                                    String id = StringArgumentType.getString(ctx, "id").toLowerCase();
                                    if (!ItemRegistry.isValidId(id)) {
                                        sender.sendMessage(RPGForgePlugin.cc("&c物品 ID 只能包含字母、数字、下划线和连字符。"));
                                        return 0;
                                    }
                                    if (plugin.registry().exists(id)) {
                                        sender.sendMessage("§c物品 " + id + " 已存在。");
                                        return 0;
                                    }
                                    RPGItem item = new RPGItem(id);
                                    plugin.registry().add(item);
                                    sender.sendMessage("§a[RPGForge] 已创建物品 " + id + "。" +
                                            (sender instanceof Player ? " 输入 /rpg 打开编辑器。" : ""));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("repair")
                        .requires(src -> src.getSender().hasPermission(RPGForgePlugin.PERM_REPAIR))
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            if (!(sender instanceof Player player)) {
                                sender.sendMessage("§c该命令只能由玩家执行。");
                                return 0;
                            }
                            repairItem(player, -1);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    CommandSender sender = ctx.getSource().getSender();
                                    if (!(sender instanceof Player player)) {
                                        sender.sendMessage("§c该命令只能由玩家执行。");
                                        return 0;
                                    }
                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                    repairItem(player, amount);
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("recipe")
                        // /rpgforge recipe list
                        .then(Commands.literal("list")
                                .executes(ctx -> {
                                    CommandSender sender = ctx.getSource().getSender();
                                    listRecipes(sender);
                                    return Command.SINGLE_SUCCESS;
                                }))
                        // /rpgforge recipe create <id> <rpgitem-id> shaped/shapeless/furnace
                        .then(Commands.literal("create")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .then(Commands.argument("rpgitem-id", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    for (String id : plugin.registry().ids()) {
                                                        builder.suggest(id);
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .then(Commands.argument("type", StringArgumentType.word())
                                                        .suggests((ctx, builder) -> {
                                                            builder.suggest("shaped");
                                                            builder.suggest("shapeless");
                                                            builder.suggest("furnace");
                                                            return builder.buildFuture();
                                                        })
                                                        .executes(ctx -> {
                                                            CommandSender sender = ctx.getSource().getSender();
                                                            String id = StringArgumentType.getString(ctx, "id").toLowerCase();
                                                            String rpgItemId = StringArgumentType.getString(ctx, "rpgitem-id").toLowerCase();
                                                            String typeName = StringArgumentType.getString(ctx, "type").toLowerCase();

                                                            if (plugin.registry().recipeExists(id)) {
                                                                sender.sendMessage("§c配方 " + id + " 已存在。");
                                                                return 0;
                                                            }
                                                            if (!plugin.registry().exists(rpgItemId)) {
                                                                sender.sendMessage("§c找不到物品：" + rpgItemId);
                                                                return 0;
                                                            }

                                                            RPGRecipe.RecipeType type;
                                                            try {
                                                                type = RPGRecipe.RecipeType.valueOf(typeName.toUpperCase());
                                                            } catch (IllegalArgumentException e) {
                                                                sender.sendMessage("§c无效的配方类型：" + typeName
                                                                        + "（可选：shaped, shapeless, furnace）");
                                                                return 0;
                                                            }

                                                            RPGRecipe recipe = new RPGRecipe(id);
                                                            recipe.setResultItemId(rpgItemId);
                                                            recipe.setType(type);
                                                            plugin.registry().addRecipe(recipe);
                                                            // 立即注册新配方
                                                            recipe.register(plugin);

                                                            sender.sendMessage("§a[RPGForge] 已创建 " + typeName + " 配方 " + id
                                                                    + "，产出物品：" + rpgItemId);
                                                            return Command.SINGLE_SUCCESS;
                                                        })))))
                        // /rpgforge recipe delete <id>
                        .then(Commands.literal("delete")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (String id : plugin.registry().recipeIds()) {
                                                builder.suggest(id);
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            CommandSender sender = ctx.getSource().getSender();
                                            String id = StringArgumentType.getString(ctx, "id").toLowerCase();
                                            if (!plugin.registry().recipeExists(id)) {
                                                sender.sendMessage("§c配方 " + id + " 不存在。");
                                                return 0;
                                            }
                                            plugin.registry().removeRecipe(id);
                                            sender.sendMessage("§a[RPGForge] 已删除配方 " + id + "。");
                                            return Command.SINGLE_SUCCESS;
                                        })))
                        // /rpgforge recipe give <id>
                        .then(Commands.literal("give")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (String id : plugin.registry().recipeIds()) {
                                                builder.suggest(id);
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            CommandSender sender = ctx.getSource().getSender();
                                            if (!(sender instanceof Player player)) {
                                                sender.sendMessage("§c该命令只能由玩家执行。");
                                                return 0;
                                            }
                                            String id = StringArgumentType.getString(ctx, "id").toLowerCase();
                                            RPGRecipe recipe = plugin.registry().getRecipe(id);
                                            if (recipe == null) {
                                                sender.sendMessage("§c配方 " + id + " 不存在。");
                                                return 0;
                                            }
                                            RPGItem item = plugin.registry().get(recipe.resultItemId());
                                            if (item == null) {
                                                sender.sendMessage("§c配方产出物品不存在：" + recipe.resultItemId());
                                                return 0;
                                            }
                                            var leftover = player.getInventory().addItem(item.buildItemStack(recipe.resultAmount()));
                                            if (!leftover.isEmpty()) {
                                                for (var drop : leftover.values()) {
                                                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                                                }
                                            }
                                            player.sendMessage("§a你获得了 " + recipe.resultAmount() + " 个 "
                                                    + RPGForgePlugin.cc(item.displayName()) + "§a！");
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .build());

        return nodes;
    }

    private void giveItem(CommandSender sender, String itemId, int amount, Player target) {
        RPGItem item = plugin.registry().get(itemId);
        if (item == null) {
            sender.sendMessage("§c找不到物品：" + itemId);
            return;
        }
        var leftover = target.getInventory().addItem(item.buildItemStack(amount));
        if (!leftover.isEmpty()) {
            for (var drop : leftover.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), drop);
            }
        }
        if (sender != target) {
            sender.sendMessage("§a已给予 " + target.getName() + " " + amount + " 个 "
                    + RPGForgePlugin.cc(item.displayName()) + "§a。");
        }
        target.sendMessage("§a你获得了 " + amount + " 个 "
                + RPGForgePlugin.cc(item.displayName()) + "§a！");
    }

    private void listItems(CommandSender sender) {
        sender.sendMessage("§e===== RPG 物品列表 (" + plugin.registry().all().size() + ") =====");
        for (RPGItem item : plugin.registry().all()) {
            sender.sendMessage(" §7▪ §f" + item.id()
                    + " §8- " + RPGForgePlugin.cc(item.displayName())
                    + " §7(" + item.powers().size() + " 个能力)");
        }
    }

    private void listRecipes(CommandSender sender) {
        sender.sendMessage("§e===== 配方列表 (" + plugin.registry().allRecipes().size() + ") =====");
        for (RPGRecipe recipe : plugin.registry().allRecipes()) {
            String typeLabel = switch (recipe.type()) {
                case SHAPED -> "§b有序";
                case SHAPELESS -> "§a无序";
                case FURNACE -> "§6熔炉";
            };
            sender.sendMessage(" §7▪ §f" + recipe.id()
                    + " §8- 产出: §7" + recipe.resultItemId()
                    + " §8x" + recipe.resultAmount()
                    + " §8[" + typeLabel + "§8]");
        }
    }

    /**
     * 修复玩家主手中的 RPG 物品耐久。
     *
     * @param player 玩家
     * @param amount 修复点数，-1 表示完全修复
     */
    private void repairItem(Player player, int amount) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            player.sendMessage("§c你手中没有物品。");
            return;
        }

        String itemId = RPGItem.readItemId(item);
        if (itemId == null) {
            player.sendMessage("§c手中的物品不是 RPGForge 物品。");
            return;
        }

        RPGItem rpgItem = plugin.registry().get(itemId);
        if (rpgItem == null) {
            player.sendMessage("§c找不到物品定义：" + itemId);
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            player.sendMessage("§c该物品没有耐久属性。");
            return;
        }

        int maxDur = rpgItem.getEffectiveMaxDurability();
        if (maxDur <= 0) {
            player.sendMessage("§c该物品没有耐久属性。");
            return;
        }

        int currentDamage = damageable.getDamage();
        if (currentDamage <= 0) {
            player.sendMessage("§a该物品已经是满耐久状态。");
            return;
        }

        int repairAmount;
        if (amount < 0) {
            // 完全修复
            repairAmount = currentDamage;
        } else {
            repairAmount = Math.min(amount, currentDamage);
        }

        int newDamage = currentDamage - repairAmount;
        damageable.setDamage(newDamage);
        item.setItemMeta(meta);

        // 刷新耐久 Lore 显示
        RPGItem.updateDurabilityLore(item, itemId);

        // 将修改后的物品设置回玩家主手（确保 1.21+ 数据组件系统下生效）
        player.getInventory().setItemInMainHand(item);

        int currentDurability = maxDur - newDamage;
        if (amount < 0) {
            player.sendMessage("§a[RPGForge] 物品已完全修复！（" + currentDurability + "/" + maxDur + "）");
        } else {
            player.sendMessage("§a[RPGForge] 已修复 " + repairAmount + " 点耐久。（"
                    + currentDurability + "/" + maxDur + "）");
        }
    }
}
