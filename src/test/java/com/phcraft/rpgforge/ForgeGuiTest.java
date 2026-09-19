package com.phcraft.rpgforge;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ForgeGuiTest {
    @Test void itemEntryCannotOpenEditorWithoutPermission() {
        var plugin = mock(RPGForgePlugin.class);
        var player = mock(Player.class);
        new ForgeGui(plugin).openItemList(player);
        verify(player, never()).openInventory(any(Inventory.class));
        verifyNoInteractions(plugin);
    }
    @Test void chatCallbackRunsOnSchedulerAndRechecksPermission() throws Exception {
        var plugin = mock(RPGForgePlugin.class);
        var gui = new ForgeGui(plugin);
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission(RPGForgePlugin.PERM_USE)).thenReturn(true);
        var field = ForgeGui.class.getDeclaredField("chatWaiters"); field.setAccessible(true);
        var callback = mock(Consumer.class);
        ((Map<UUID, Consumer<String>>)field.get(gui)).put(player.getUniqueId(), callback);
        var scheduler = mock(BukkitScheduler.class);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            assertTrue(gui.handleChatInput(player, "test"));
            verifyNoInteractions(callback);
            var task = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).runTask(eq(plugin), task.capture());
            task.getValue().run();
            verify(callback).accept("test");
        }
    }
    @Test void revokedPermissionDiscardsPendingEdit() throws Exception {
        var plugin = mock(RPGForgePlugin.class); var gui = new ForgeGui(plugin);
        var player = mock(Player.class); when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        var field = ForgeGui.class.getDeclaredField("chatWaiters"); field.setAccessible(true);
        var callback = mock(Consumer.class);
        ((Map<UUID, Consumer<String>>)field.get(gui)).put(player.getUniqueId(), callback);
        var scheduler = mock(BukkitScheduler.class);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            assertTrue(gui.handleChatInput(player, "test"));
            var task = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).runTask(eq(plugin), task.capture()); task.getValue().run();
            verifyNoInteractions(callback);
        }
    }
    @Test void dragCannotOverwriteEditorIcons() {
        var gui = new ForgeGui(mock(RPGForgePlugin.class));
        var inv = mock(Inventory.class); var view = mock(InventoryView.class);
        when(inv.getHolder()).thenReturn(new ForgeGui.ForgeHolder(ForgeGui.ForgeHolder.Type.ITEM_LIST));
        when(inv.getSize()).thenReturn(54); when(view.getTopInventory()).thenReturn(inv);
        var event = mock(InventoryDragEvent.class); when(event.getView()).thenReturn(view);
        when(event.getRawSlots()).thenReturn(Set.of(10, 60));
        gui.onDrag(event); verify(event).setCancelled(true);
    }
}
