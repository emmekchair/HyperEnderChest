package it.hyperenderchest.inventory;

import it.hyperenderchest.model.PairKey;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class SharedInventoryHolder implements InventoryHolder {
    private final PairKey key;
    private final Inventory inventory;

    public SharedInventoryHolder(PairKey key, int size) {
        this.key = key;
        this.inventory = Bukkit.createInventory(this, size, Component.text("Shared Ender Chest"));
    }

    public PairKey key() {
        return key;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
