package com.phcraft.rpgforge;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.entity.Player;

/**
 * 聊天输入监听器 —— 将玩家聊天消息路由到 GUI 系统的输入回调。
 * 用 LOWEST 优先级，尽量早拦截，避免触发其他聊天插件。
 *
 * <p>AsyncPlayerChatEvent 在异步线程触发，此监听器仅入队，
 * 由 ForgeGui 在主线程按顺序处理，避免线程安全问题和乱序。
 */
public class ChatListener implements Listener {

    private final RPGForgePlugin plugin;

    public ChatListener(RPGForgePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        // 在主线程处理（GUI 操作必须在主线程）
        boolean handled = plugin.gui().handleChatInput(player, message);
        if (handled) {
            event.setCancelled(true);
        }
    }
}
