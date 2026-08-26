package it.hyperenderchest.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import java.io.File;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CacheStorageRoundTripTest {
    private ServerMock server;
    private PluginMock plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void preservesSlotsAmountsAndCompleteItemData() {
        UUID owner = UUID.randomUUID();
        Inventory source = server.createInventory(null, 54);
        ItemStack sword = ItemStack.of(Material.DIAMOND_SWORD);
        NamespacedKey customKey = new NamespacedKey(plugin, "custom-data");
        sword.editMeta(meta -> {
            meta.displayName(Component.text("Persistent blade"));
            meta.lore(List.of(Component.text("NBT and components survive")));
            meta.addEnchant(Enchantment.SHARPNESS, 5, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.setCustomModelData(743);
            meta.getPersistentDataContainer().set(customKey, PersistentDataType.STRING, "hidden-value");
        });
        ItemStack armor = ItemStack.of(Material.LEATHER_CHESTPLATE);
        armor.editMeta(LeatherArmorMeta.class, meta -> {
            meta.setColor(Color.fromRGB(12, 34, 56));
            meta.setUnbreakable(true);
        });
        source.setItem(4, sword);
        source.setItem(37, armor);
        source.setItem(53, ItemStack.of(Material.DIAMOND, 42));

        CacheStorage storage = new CacheStorage(plugin);
        storage.savePersonalVault(new it.hyperenderchest.model.PersonalVaultKey(owner, org.bukkit.DyeColor.RED), source);
        Inventory restored = server.createInventory(null, 54);
        new CacheStorage(plugin).loadPersonalVault(
                new it.hyperenderchest.model.PersonalVaultKey(owner, org.bukkit.DyeColor.RED), restored);

        assertTrue(restored.getItem(0) == null || restored.getItem(0).isEmpty());
        assertEquals(sword, restored.getItem(4));
        assertEquals(armor, restored.getItem(37));
        assertEquals(42, restored.getItem(53).getAmount());
        assertArrayEquals(sword.serializeAsBytes(), restored.getItem(4).serializeAsBytes());
        assertArrayEquals(armor.serializeAsBytes(), restored.getItem(37).serializeAsBytes());
        assertEquals("hidden-value", restored.getItem(4).getItemMeta().getPersistentDataContainer()
                .get(customKey, PersistentDataType.STRING));
        assertTrue(new File(plugin.getDataFolder(), "data/" + owner + ".yaml").isFile());
        assertEquals(Color.fromRGB(12, 34, 56), ((LeatherArmorMeta) restored.getItem(37).getItemMeta()).getColor());
    }

    @Test
    void supportsStandardAttributeMetadata() {
        ItemStack sword = ItemStack.of(Material.DIAMOND_SWORD);
        sword.editMeta(meta -> meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                new AttributeModifier(new NamespacedKey(plugin, "damage"), 7.5,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND)));

        assertNotNull(sword.getItemMeta().getAttributeModifiers(Attribute.ATTACK_DAMAGE));
    }
}
