package com.phcraft.rpgforge;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ForgeGuiTest {
    @SuppressWarnings("unchecked")
    private static void awaitChat(ForgeGui gui, Player player, Consumer<String> callback) throws Exception {
        var method = ForgeGui.class.getDeclaredMethod("awaitChatInput", Player.class, String.class, Consumer.class);
        method.setAccessible(true);
        method.invoke(gui, player, "prompt", callback);
    }

    private static Player chatPlayer() {
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission(RPGForgePlugin.PERM_USE)).thenReturn(true);
        return player;
    }

    @Test void chatCallbackRunsOnSchedulerNotAsyncThread() throws Exception {
        var plugin = mock(RPGForgePlugin.class);
        var gui = new ForgeGui(plugin);
        var player = chatPlayer();
        var callback = mock(Consumer.class);
        var scheduler = mock(BukkitScheduler.class);
        var server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        awaitChat(gui, player, callback);
        assertTrue(gui.handleChatInput(player, "test"));
        verifyNoInteractions(callback);
        var task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(eq(plugin), task.capture());
        task.getValue().run();
        verify(callback).accept("test");
    }

    @Test void revokedPermissionDiscardsPendingEdit() throws Exception {
        var plugin = mock(RPGForgePlugin.class);
        var gui = new ForgeGui(plugin);
        var player = chatPlayer();
        when(player.hasPermission(RPGForgePlugin.PERM_USE)).thenReturn(false);
        var callback = mock(Consumer.class);
        var scheduler = mock(BukkitScheduler.class);
        var server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        awaitChat(gui, player, callback);
        assertTrue(gui.handleChatInput(player, "test"));
        var task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(eq(plugin), task.capture());
        task.getValue().run();
        verifyNoInteractions(callback);
        assertFalse(gui.handleChatInput(player, "later")); // 会话已清理，输入不再被拦截
    }

    @Test void cancelKeywordDiscardsPendingEdit() throws Exception {
        var plugin = mock(RPGForgePlugin.class);
        var gui = new ForgeGui(plugin);
        var player = chatPlayer();
        var callback = mock(Consumer.class);
        var scheduler = mock(BukkitScheduler.class);
        var server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        awaitChat(gui, player, callback);
        assertTrue(gui.handleChatInput(player, "CANCEL"));
        var task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(eq(plugin), task.capture());
        task.getValue().run();
        verifyNoInteractions(callback);
        assertFalse(gui.handleChatInput(player, "ordinary chat"));
    }

    @Test void burstResponsesStayPrivateAndReachSuccessiveCallbacksInOrder() throws Exception {
        var plugin = mock(RPGForgePlugin.class);
        var gui = new ForgeGui(plugin);
        var player = chatPlayer();
        var received = new ArrayList<String>();
        var scheduler = mock(BukkitScheduler.class);
        var server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        awaitChat(gui, player, line -> {
            received.add(line);
            try {
                // 回调执行中也保留输入归属：模拟描述编辑器为下一行重新挂起等待
                awaitChat(gui, player, received::add);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        assertTrue(gui.handleChatInput(player, "first"));
        assertTrue(gui.handleChatInput(player, "second"));
        assertTrue(received.isEmpty());
        var task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(eq(plugin), task.capture());
        task.getValue().run();
        assertEquals(List.of("first", "second"), received);
        assertFalse(gui.handleChatInput(player, "ordinary chat"));
    }

    @Test void queuedInputCannotRunAfterQuit() throws Exception {
        var plugin = mock(RPGForgePlugin.class);
        var gui = new ForgeGui(plugin);
        var player = chatPlayer();
        var callback = mock(Consumer.class);
        var scheduler = mock(BukkitScheduler.class);
        var server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        awaitChat(gui, player, callback);
        assertTrue(gui.handleChatInput(player, "private input"));
        var task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(eq(plugin), task.capture());
        var quit = mock(PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(player);
        gui.onQuit(quit);
        task.getValue().run();
        verifyNoInteractions(callback);
    }

    @Test void clickWithoutPermissionClosesEditorAndCancelsEvent() {
        var gui = new ForgeGui(mock(RPGForgePlugin.class));
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission(RPGForgePlugin.PERM_USE)).thenReturn(false);
        var inv = mock(Inventory.class);
        var view = mock(InventoryView.class);
        var event = mock(InventoryClickEvent.class);
        when(event.getView()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(inv);
        when(inv.getHolder()).thenReturn(new ForgeGui.ForgeHolder(ForgeGui.ForgeHolder.Type.ITEM_LIST));
        when(event.getWhoClicked()).thenReturn(player);
        gui.onClick(event);
        verify(event).setCancelled(true);
        verify(player).closeInventory();
    }

    @Test void dragInsideEditorIsCancelled() {
        var gui = new ForgeGui(mock(RPGForgePlugin.class));
        var inv = mock(Inventory.class);
        var view = mock(InventoryView.class);
        when(inv.getHolder()).thenReturn(new ForgeGui.ForgeHolder(ForgeGui.ForgeHolder.Type.ITEM_EDIT));
        when(view.getTopInventory()).thenReturn(inv);
        var event = mock(InventoryDragEvent.class);
        when(event.getView()).thenReturn(view);
        gui.onDrag(event);
        verify(event).setCancelled(true);
    }

    @Test void dragOutsideEditorIsAllowed() {
        var gui = new ForgeGui(mock(RPGForgePlugin.class));
        var inv = mock(Inventory.class);
        var view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(inv);
        var event = mock(InventoryDragEvent.class);
        when(event.getView()).thenReturn(view);
        gui.onDrag(event);
        verify(event, never()).setCancelled(anyBoolean());
    }
}
