package it.hyperenderchest.service;

import it.hyperenderchest.inventory.SharedInventoryHolder;
import it.hyperenderchest.model.PairKey;
import it.hyperenderchest.storage.CacheStorage;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Owns active share relationships and the single live inventory for each pair.
 * Keeping one Bukkit inventory per pair prevents divergent copies and item duplication.
 */
public final class EnderChestManager {
    public enum ViewMode { PERSONAL, SHARED }

    private final JavaPlugin plugin;
    private final CacheStorage storage;
    private final int inventorySize;
    private final Map<UUID, PairKey> pairByPlayer = new HashMap<>();
    private final Map<UUID, ViewMode> viewByPlayer = new HashMap<>();
    private final Map<PairKey, SharedInventoryHolder> loaded = new HashMap<>();

    public EnderChestManager(JavaPlugin plugin, int inventorySize) {
        this.plugin = plugin;
        this.inventorySize = inventorySize;
        this.storage = new CacheStorage(plugin);
        loadRelations();
    }

    public boolean isShared(UUID playerId) {
        return pairByPlayer.containsKey(playerId);
    }

    public Optional<PairKey> pair(UUID playerId) {
        return Optional.ofNullable(pairByPlayer.get(playerId));
    }

    public ViewMode view(UUID playerId) {
        return viewByPlayer.getOrDefault(playerId, ViewMode.PERSONAL);
    }

    public void setView(UUID playerId, ViewMode view) {
        if (view == ViewMode.SHARED && !isShared(playerId)) {
            throw new IllegalStateException("No active shared Ender Chest");
        }
        viewByPlayer.put(playerId, view);
        saveRelations();
    }

    public PairKey share(UUID first, UUID second) {
        if (isShared(first) || isShared(second)) {
            throw new IllegalStateException("One of the players already shares an Ender Chest");
        }
        PairKey key = new PairKey(first, second);
        pairByPlayer.put(first, key);
        pairByPlayer.put(second, key);
        viewByPlayer.put(first, ViewMode.SHARED);
        viewByPlayer.put(second, ViewMode.SHARED);
        saveRelations();
        return key;
    }

    public Optional<PairKey> unshare(UUID playerId) {
        PairKey key = pairByPlayer.remove(playerId);
        if (key == null) {
            return Optional.empty();
        }
        pairByPlayer.remove(key.first());
        pairByPlayer.remove(key.second());
        storage.removePlayer(key.first());
        storage.removePlayer(key.second());
        viewByPlayer.put(key.first(), ViewMode.PERSONAL);
        viewByPlayer.put(key.second(), ViewMode.PERSONAL);
        SharedInventoryHolder holder = loaded.get(key);
        if (holder != null) {
            java.util.List.copyOf(holder.getInventory().getViewers()).forEach(viewer -> ((Player) viewer).closeInventory());
            save(key);
        }
        saveRelations();
        return Optional.of(key);
    }

    /** Returns the canonical inventory for a pair and loads persisted contents once. */
    public Inventory sharedInventory(PairKey key) {
        SharedInventoryHolder holder = loaded.computeIfAbsent(key, ignored -> {
            SharedInventoryHolder created = new SharedInventoryHolder(key, inventorySize);
            storage.loadVault(key, created.getInventory());
            return created;
        });
        return holder.getInventory();
    }

    public Inventory selectedInventory(Player player) {
        if (view(player.getUniqueId()) == ViewMode.SHARED) {
            PairKey key = pairByPlayer.get(player.getUniqueId());
            if (key != null) {
                return sharedInventory(key);
            }
        }
        return player.getEnderChest();
    }

    public void save(Inventory inventory) {
        if (inventory.getHolder(false) instanceof SharedInventoryHolder holder) {
            save(holder.key());
        }
    }

    /** Flushes all loaded shared inventories during plugin shutdown. */
    public void saveAll() {
        loaded.keySet().forEach(this::save);
        saveRelations();
    }

    private void save(PairKey key) {
        SharedInventoryHolder holder = loaded.get(key);
        if (holder == null) {
            return;
        }
        try {
            storage.saveVault(key, holder.getInventory());
        } catch (IllegalStateException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save vault: " + key, exception);
        }
    }

    private void loadRelations() {
        for (String player : storage.players()) {
            try {
                UUID playerId = UUID.fromString(player);
                PairKey key = PairKey.parse(storage.pair(playerId));
                if (key.contains(playerId)) {
                    pairByPlayer.put(playerId, key);
                    viewByPlayer.put(playerId, ViewMode.valueOf(storage.view(playerId)));
                }
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Invalid relation ignored for " + player);
            }
        }
    }

    private void saveRelations() {
        pairByPlayer.forEach((player, key) -> storage.savePlayer(player, key, view(player).name()));
    }
}
