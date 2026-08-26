package it.hyperenderchest;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

public final class EnderChestManager {
    public enum ViewMode { PERSONAL, SHARED }

    private final JavaPlugin plugin;
    private final InventoryStorage storage;
    private final int inventorySize;
    private final Map<UUID, PairKey> pairByPlayer = new HashMap<>();
    private final Map<UUID, ViewMode> viewByPlayer = new HashMap<>();
    private final Map<PairKey, SharedInventoryHolder> loaded = new HashMap<>();
    private final File relationsFile;

    public EnderChestManager(JavaPlugin plugin, int inventorySize) {
        this.plugin = plugin;
        this.inventorySize = inventorySize;
        this.storage = new InventoryStorage(plugin.getDataFolder());
        this.relationsFile = new File(plugin.getDataFolder(), "relations.yml");
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

    public Inventory sharedInventory(PairKey key) {
        SharedInventoryHolder holder = loaded.computeIfAbsent(key, ignored -> {
            SharedInventoryHolder created = new SharedInventoryHolder(key, inventorySize);
            storage.load(key, created.getInventory());
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
            storage.save(key, holder.getInventory());
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save vault: " + key, exception);
        }
    }

    private void loadRelations() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(relationsFile);
        for (String player : yaml.getConfigurationSection("players") == null ? java.util.Set.<String>of() : yaml.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(player);
                PairKey key = PairKey.parse(yaml.getString("players." + player + ".pair", ""));
                if (key.contains(playerId)) {
                    pairByPlayer.put(playerId, key);
                    viewByPlayer.put(playerId, ViewMode.valueOf(yaml.getString("players." + player + ".view", "PERSONAL")));
                }
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Invalid relation ignored for " + player);
            }
        }
    }

    private void saveRelations() {
        YamlConfiguration yaml = new YamlConfiguration();
        pairByPlayer.forEach((player, key) -> {
            yaml.set("players." + player + ".pair", key.toString());
            yaml.set("players." + player + ".view", view(player).name());
        });
        try {
            yaml.save(relationsFile);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save relations", exception);
        }
    }
}
