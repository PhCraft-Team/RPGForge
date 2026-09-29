package com.phcraft.rpgforge;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ItemRegistryTest {
    @Test void pathLikeIdsCannotEnterRegistry() {
        var registry = new ItemRegistry(mock(RPGForgePlugin.class));
        for (String id : new String[]{"../config", "..\\config", "C:/config", "", "a/b", "UPPER"}) {
            var item = mock(RPGItem.class);
            when(item.id()).thenReturn(id);
            assertThrows(IllegalArgumentException.class, () -> registry.add(item));
            assertFalse(registry.exists(id));
        }
        assertTrue(registry.all().isEmpty());
    }
    @Test void documentedIdsRemainValid() {
        assertTrue(ItemRegistry.isValidId("flame_sword"));
        assertTrue(ItemRegistry.isValidId("test123"));
        assertFalse(ItemRegistry.isValidId("test-item"));
        assertFalse(ItemRegistry.isValidId("../x"));
        assertFalse(ItemRegistry.isValidId(""));
        assertFalse(ItemRegistry.isValidId(null));
    }
}
