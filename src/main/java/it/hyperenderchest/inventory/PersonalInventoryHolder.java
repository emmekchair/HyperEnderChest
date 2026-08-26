package it.hyperenderchest.inventory;

import it.hyperenderchest.model.PersonalVaultKey;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class PersonalInventoryHolder implements InventoryHolder {
    private final PersonalVaultKey key;
    private final Inventory inventory;

    public PersonalInventoryHolder(PersonalVaultKey key, int size) {
        this.key = key;
        this.inventory = Bukkit.createInventory(this, size, Component.text(key.color().name() + " Personal Vault"));
    }

    public PersonalVaultKey key() {
        return key;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
