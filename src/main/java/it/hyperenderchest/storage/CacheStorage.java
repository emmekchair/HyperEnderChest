package it.hyperenderchest.storage;

import it.hyperenderchest.model.PairKey;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Owns the plugin cache file for player relationships and shared inventories. */
public final class CacheStorage {
    private final File file;
    private final YamlConfiguration cache = new YamlConfiguration();

    public CacheStorage(JavaPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "cache.yml");
        if (!file.exists()) {
            plugin.saveResource("cache.yml", false);
        }
        load();
    }

    public void load() {
        try {
            cache.load(file);
            boolean changed = false;
            if (!cache.isConfigurationSection("Players")) {
                cache.createSection("Players");
                changed = true;
            }
            if (!cache.isConfigurationSection("Vaults")) {
                cache.createSection("Vaults");
                changed = true;
            }
            if (changed) {
                saveFile();
            }
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalStateException("Unable to load cache.yml", exception);
        }
    }

    public Set<String> players() {
        return cache.getConfigurationSection("Players").getKeys(false);
    }

    public String pair(UUID playerId) {
        return cache.getString("Players." + playerId + ".Pair", "");
    }

    public String view(UUID playerId) {
        return cache.getString("Players." + playerId + ".View", "PERSONAL");
    }

    public void savePlayer(UUID playerId, PairKey key, String view) {
        String path = "Players." + playerId;
        cache.set(path + ".Pair", key.toString());
        cache.set(path + ".View", view);
        saveFile();
    }

    public void removePlayer(UUID playerId) {
        cache.set("Players." + playerId, null);
        saveFile();
    }

    public void loadVault(PairKey key, Inventory inventory) {
        List<?> stored = cache.getList("Vaults." + key + ".Items", List.of());
        for (int slot = 0; slot < Math.min(stored.size(), inventory.getSize()); slot++) {
            if (stored.get(slot) instanceof ItemStack stack) {
                inventory.setItem(slot, stack);
            }
        }
    }

    public void saveVault(PairKey key, Inventory inventory) {
        List<ItemStack> items = new ArrayList<>(inventory.getSize());
        for (ItemStack item : inventory.getContents()) {
            items.add(item == null ? null : item.clone());
        }
        cache.set("Vaults." + key + ".Items", items);
        saveFile();
    }

    private void saveFile() {
        try {
            cache.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save cache.yml", exception);
        }
    }
}
