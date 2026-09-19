package com.phcraft.rpgforge;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ItemRegistryTest {
    @Test void pathLikeIdsCannotWriteOrDeleteOutsideItemsDirectory() {
        var registry = new ItemRegistry(mock(RPGForgePlugin.class));
        for (String id : new String[]{"../config", "..\\config", "C:/config", "", "a/b"}) {
            var item = mock(RPGItem.class); when(item.id()).thenReturn(id);
            assertThrows(IllegalArgumentException.class, () -> registry.add(item));
            assertThrows(IllegalArgumentException.class, () -> registry.saveItem(item));
            assertThrows(IllegalArgumentException.class, () -> registry.remove(id));
        }
        assertTrue(registry.all().isEmpty());
    }
    @Test void documentedIdsRemainValid() {
        assertTrue(ItemRegistry.isValidId("flame_sword"));
        assertTrue(ItemRegistry.isValidId("test-item_123"));
    }
}
