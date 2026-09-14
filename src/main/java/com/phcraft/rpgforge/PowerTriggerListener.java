package com.phcraft.rpgforge;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Power 触发监听器 —— 监听各种游戏事件，找到对应的 RPGItem，
 * 按 Trigger 匹配并执行其上绑定的 Power。
 */
public class PowerTriggerListener implements Listener {

    private final RPGForgePlugin plugin;

    /** 冷却记录：playerUUID + ":" + itemId + ":" + powerIndex → 结束时间戳(ms) */
    private final Map<String, Long> cooldowns = new HashMap<>();

    public PowerTriggerListener(RPGForgePlugin plugin) {
        this.plugin = plugin;

        // 每 5 秒清理过期冷却记录
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                cooldowns.entrySet().removeIf(e -> e.getValue() <= now);
            }
        }.runTaskTimer(plugin, 100, 100);
    }

    // ===== 右键 / 左键 =====
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // 只处理主手，避免左右手双触发
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;

        String itemId = RPGItem.readItemId(item);
        if (itemId == null) return;

        RPGItem rpgItem = plugin.registry().get(itemId);
        if (rpgItem == null) return;

        TriggerType trigger = switch (event.getAction()) {
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> TriggerType.RIGHT_CLICK;
            case LEFT_CLICK_AIR, LEFT_CLICK_BLOCK -> TriggerType.LEFT_CLICK;
            default -> null;
        };
        if (trigger == null) return;

        // 只有物品上确实有该 trigger 的 Power 时才取消事件
        // 否则保留默认交互（放置方块、开门等）
        boolean hasPowers = rpgItem.powers().stream()
                .anyMatch(p -> p.triggers().contains(trigger));
        if (!hasPowers) return;

        if (trigger == TriggerType.RIGHT_CLICK) {
            event.setCancelled(true);
        }

        executePowers(player, null, null, rpgItem, trigger, item);
    }

    // ===== 攻击命中 =====
    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        String itemId = RPGItem.readItemId(item);
        if (itemId == null) return;

        RPGItem rpgItem = plugin.registry().get(itemId);
        if (rpgItem == null) return;

        // 伤害加成、暴击、吸血 Power（需要在事件中直接修改伤害，不走 executePowers）
        // 但仍然走冷却检查
        double totalDamage = event.getDamage();
        boolean isCrit = false;

        for (RPGPower power : rpgItem.powers()) {
            if (!power.triggers().contains(TriggerType.HIT)) continue;

            if (power.type() == PowerType.DAMAGE_BOOST) {
                // 冷却检查
                if (!checkAndApplyCooldown(player, rpgItem.id(), power)) continue;

                double bonus = power.paramDouble("bonus", 0);
                String mode = power.paramString("mode", "add");
                if ("multiply".equalsIgnoreCase(mode)) {
                    totalDamage = totalDamage * bonus;
                } else {
                    totalDamage = totalDamage + bonus;
                }
            }

            if (power.type() == PowerType.CRITICAL_HIT) {
                // 冷却检查
                if (!checkAndApplyCooldown(player, rpgItem.id(), power)) continue;

                double chance = power.paramDouble("chance", 20.0);
                double multiplier = power.paramDouble("multiplier", 2.0);
                if (Math.random() * 100 < chance) {
                    totalDamage = totalDamage * multiplier;
                    isCrit = true;
                }
            }
        }

        event.setDamage(totalDamage);

        // 暴击视觉效果
        if (isCrit) {
            target.getWorld().spawnParticle(org.bukkit.Particle.CRIT,
                    target.getLocation().add(0, target.getHeight() / 2, 0),
                    15, 0.5, 0.5, 0.5, 0.2);
        }

        // 吸血 Power（在伤害确定后计算回血）
        final double finalDamage = totalDamage;
        for (RPGPower power : rpgItem.powers()) {
            if (!power.triggers().contains(TriggerType.HIT)) continue;
            if (power.type() != PowerType.VAMPIRIC) continue;

            // 冷却检查
            if (!checkAndApplyCooldown(player, rpgItem.id(), power)) continue;

            double percent = power.paramDouble("percent", 30.0);
            double healAmount = finalDamage * percent / 100.0;
            double maxHealth = player.getMaxHealth();
            player.setHealth(Math.min(maxHealth, player.getHealth() + healAmount));

            // 吸血粒子
            player.getWorld().spawnParticle(org.bukkit.Particle.HEART,
                    player.getLocation().add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0.1);
        }

        // 执行 HIT trigger 的其他 Power
        executePowers(player, target, null, rpgItem, TriggerType.HIT, item);
    }

    // ===== 受到攻击 =====
    @EventHandler
    public void onDamageTaken(EntityDamageEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        // 检查主手、副手、四件盔甲
        ItemStack[] checkItems = {
                player.getInventory().getItemInMainHand(),
                player.getInventory().getItemInOffHand(),
                player.getInventory().getHelmet(),
                player.getInventory().getChestplate(),
                player.getInventory().getLeggings(),
                player.getInventory().getBoots()
        };

        for (ItemStack item : checkItems) {
            if (item == null) continue;
            String itemId = RPGItem.readItemId(item);
            if (itemId == null) continue;
            RPGItem rpgItem = plugin.registry().get(itemId);
            if (rpgItem == null) continue;
            executePowers(player, null, null, rpgItem, TriggerType.HIT_TAKEN, item);
        }
    }

    // ===== 食用/饮用 =====
    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        String itemId = RPGItem.readItemId(item);
        if (itemId == null) return;

        RPGItem rpgItem = plugin.registry().get(itemId);
        if (rpgItem == null) return;

        executePowers(player, null, null, rpgItem, TriggerType.CONSUME, item);
    }

    // ===== 潜行 =====
    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player player = event.getPlayer();

        ItemStack item = player.getInventory().getItemInMainHand();
        String itemId = RPGItem.readItemId(item);
        if (itemId == null) return;

        RPGItem rpgItem = plugin.registry().get(itemId);
        if (rpgItem == null) return;

        executePowers(player, null, null, rpgItem, TriggerType.SNEAK, item);
    }

    // ===== 疾跑 =====
    @EventHandler
    public void onSprint(PlayerToggleSprintEvent event) {
        if (!event.isSprinting()) return;
        Player player = event.getPlayer();

        ItemStack item = player.getInventory().getItemInMainHand();
        String itemId = RPGItem.readItemId(item);
        if (itemId == null) return;

        RPGItem rpgItem = plugin.registry().get(itemId);
        if (rpgItem == null) return;

        executePowers(player, null, null, rpgItem, TriggerType.SPRINT, item);
    }

    // ===== 死亡 =====
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // 检查背包中所有物品
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            String itemId = RPGItem.readItemId(item);
            if (itemId == null) continue;
            RPGItem rpgItem = plugin.registry().get(itemId);
            if (rpgItem == null) continue;
            executePowers(player, null, null, rpgItem, TriggerType.DEATH, item);
        }
    }

    // ===== 复活 =====
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            String itemId = RPGItem.readItemId(item);
            if (itemId == null) continue;
            RPGItem rpgItem = plugin.registry().get(itemId);
            if (rpgItem == null) continue;
            executePowers(player, null, null, rpgItem, TriggerType.RESPAWN, item);
        }
    }

    // ===== 弹射物发射：标记来源物品 =====
    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) return;

        // 检查主手和副手中哪个有 RPG 物品且有弹射物相关 Power
        // 优先主手，其次副手
        ItemStack item = findRpgItemWithProjectilePower(player);
        if (item == null) return;

        String itemId = RPGItem.readItemId(item);
        RPGItem rpgItem = plugin.registry().get(itemId);
        if (rpgItem == null) return;

        // 给弹射物打 PDC 标记，记录来源物品 ID
        Projectile proj = event.getEntity();
        proj.getPersistentDataContainer().set(
                RPGForgePlugin.itemIdKey(), PersistentDataType.STRING, itemId);

        // 触发 PROJECTILE_LAUNCH
        executePowers(player, null, null, rpgItem, TriggerType.PROJECTILE_LAUNCH, item);
    }

    /**
     * 在玩家主副手找一个有弹射物 Power 的 RPG 物品。优先主手。
     */
    private ItemStack findRpgItemWithProjectilePower(Player player) {
        for (ItemStack item : new ItemStack[]{
                player.getInventory().getItemInMainHand(),
                player.getInventory().getItemInOffHand()
        }) {
            if (item == null) continue;
            String itemId = RPGItem.readItemId(item);
            if (itemId == null) continue;
            RPGItem rpgItem = plugin.registry().get(itemId);
            if (rpgItem == null) continue;
            boolean hasProjPower = rpgItem.powers().stream()
                    .anyMatch(p -> p.triggers().contains(TriggerType.PROJECTILE_LAUNCH)
                                || p.triggers().contains(TriggerType.PROJECTILE_HIT));
            if (hasProjPower) return item;
        }
        return null;
    }

    // ===== 弹射物命中：触发 PROJECTILE_HIT =====
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) return;

        Projectile proj = event.getEntity();
        String itemId = proj.getPersistentDataContainer().get(
                RPGForgePlugin.itemIdKey(), PersistentDataType.STRING);
        if (itemId == null) return;

        RPGItem rpgItem = plugin.registry().get(itemId);
        if (rpgItem == null) return;

        // 命中点位置
        Location hitLocation = event.getHitBlock() != null
                ? event.getHitBlock().getLocation().add(0.5, 0.5, 0.5)
                : (event.getHitEntity() != null
                ? event.getHitEntity().getLocation()
                : proj.getLocation());

        // 被命中的目标实体
        LivingEntity target = (event.getHitEntity() instanceof LivingEntity le) ? le : null;

        // 在玩家背包中寻找对应物品（用于 CONSUME/DURABILITY 处理）
        // 优先主手，其次副手，最后遍历背包
        ItemStack item = findItemInInventory(player, itemId);
        if (item == null) {
            // 找不到也没关系，只是不能消耗/掉耐久，效果照出
            item = rpgItem.buildItemStack(); // 构造一个临时的，仅用于传递
        }

        executePowers(player, target, hitLocation, rpgItem, TriggerType.PROJECTILE_HIT, item);
    }

    /**
     * 在玩家背包中找指定 itemId 的物品。优先主手 → 副手 → 遍历。
     */
    private ItemStack findItemInInventory(Player player, String itemId) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (itemId.equals(RPGItem.readItemId(main))) return main;

        ItemStack off = player.getInventory().getItemInOffHand();
        if (itemId.equals(RPGItem.readItemId(off))) return off;

        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null) continue;
            if (itemId.equals(RPGItem.readItemId(slot))) return slot;
        }
        return null;
    }

    // ============================================================
    //  核心执行逻辑
    // ============================================================

    /**
     * 执行物品上所有绑定了指定 Trigger 的 Power。
     * 按顺序执行，检查冷却，处理消耗/耐久。
     *
     * @param itemStack 实际物品栈引用（必须是背包中的真实物品，消耗/耐久才会生效）
     */
    private void executePowers(Player player, LivingEntity target,
                               Location hitLocation,
                               RPGItem rpgItem, TriggerType trigger, ItemStack itemStack) {
        // 计算物品当前耐久（用于耐久条件检查）
        int currentDurability = getItemDurability(itemStack);

        for (RPGPower power : rpgItem.powers()) {
            if (!power.triggers().contains(trigger)) continue;

            // 冷却检查
            if (!checkAndApplyCooldown(player, rpgItem.id(), power)) {
                sendFailMessage(player, power.failMessage(), "冷却中");
                continue;
            }

            // 条件检查（所有条件都满足才执行）
            RPGCondition failedCond = checkAllConditions(player, target, currentDurability, power);
            if (failedCond != null) {
                // 优先用条件自己的失败消息，其次用 Power 的失败消息
                String msg = failedCond.failMessage();
                if (msg == null || msg.isEmpty()) {
                    msg = power.failMessage();
                }
                if (msg != null && !msg.isEmpty()) {
                    sendFailMessage(player, msg, null);
                }
                continue; // 条件不满足，跳过
            }

            // 执行 Power
            boolean success = power.type().execute(player, target, hitLocation, power.params(), rpgItem);

            // 消耗物品 Power（独立处理）
            if (power.type() == PowerType.CONSUME) {
                int amount = power.paramInt("amount", 1);
                // 只在 itemStack 是 RPG 物品时消耗
                if (itemIdMatches(itemStack, rpgItem.id())) {
                    int remaining = itemStack.getAmount() - amount;
                    if (remaining <= 0) {
                        // 物品耗尽 —— 尝试从玩家背包移除
                        player.getInventory().removeItem(itemStack);
                    } else {
                        itemStack.setAmount(remaining);
                    }
                }
            }

            // 耐久消耗 Power（独立处理）
            if (power.type() == PowerType.DURABILITY) {
                int amount = power.paramInt("amount", 1);
                ItemMeta meta = itemStack.getItemMeta();
                if (meta instanceof Damageable damageable && damageable.hasMaxDamage()) {
                    int newDmg = damageable.getDamage() + amount;
                    if (newDmg >= damageable.getMaxDamage()) {
                        // 耐久耗尽 —— 播放破坏效果并移除
                        player.getInventory().removeItem(itemStack);
                    } else {
                        damageable.setDamage(newDmg);
                        itemStack.setItemMeta((ItemMeta) damageable);
                    }
                }
            }
        }
    }

    /**
     * 检查 Power 上的所有条件。返回第一个不满足的条件，全部满足返回 null。
     */
    private RPGCondition checkAllConditions(Player player, LivingEntity target,
                                            int itemDurability, RPGPower power) {
        for (RPGCondition cond : power.conditions()) {
            if (!cond.type().check(player, target, itemDurability, cond.params())) {
                return cond;
            }
        }
        return null;
    }

    /** 获取物品当前剩余耐久值（不是已损耗值）。无耐久返回 -1。 */
    private int getItemDurability(ItemStack item) {
        if (item == null) return -1;
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable dmg)) return -1;
        if (!dmg.hasMaxDamage()) return -1;
        return dmg.getMaxDamage() - dmg.getDamage();
    }

    /** 发送失败提示消息（支持 & 颜色码） */
    private void sendFailMessage(Player player, String message, String defaultMsg) {
        String msg = (message != null && !message.isEmpty()) ? message : defaultMsg;
        if (msg == null || msg.isEmpty()) return;
        msg = msg.replace('&', '§');
        player.sendMessage(msg);
    }

    /** 检查 itemStack 的 RPGItem ID 是否匹配 */
    private boolean itemIdMatches(ItemStack item, String expectedId) {
        if (item == null) return false;
        String id = RPGItem.readItemId(item);
        return expectedId.equals(id);
    }

    /**
     * 检查并应用冷却。true = 可以执行（已写入新冷却），false = 冷却中。
     */
    private boolean checkAndApplyCooldown(Player player, String itemId, RPGPower power) {
        int cooldownSeconds = power.paramInt("cooldown", 0);
        if (cooldownSeconds <= 0) return true;

        String key = player.getUniqueId() + ":" + itemId + ":" + power.index();
        long now = System.currentTimeMillis();
        Long end = cooldowns.get(key);

        if (end != null && end > now) {
            return false; // 冷却中
        }

        cooldowns.put(key, now + cooldownSeconds * 1000L);
        return true;
    }

    /**
     * 获取某个 Power 的剩余冷却时间（秒）。
     */
    public int remainingCooldown(Player player, String itemId, int powerIndex) {
        String key = player.getUniqueId() + ":" + itemId + ":" + powerIndex;
        Long end = cooldowns.get(key);
        if (end == null) return 0;
        long now = System.currentTimeMillis();
        long remaining = end - now;
        if (remaining <= 0) return 0;
        return (int) Math.ceil(remaining / 1000.0);
    }
}
