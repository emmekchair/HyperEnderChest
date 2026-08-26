package it.hyperenderchest.storage;

import it.hyperenderchest.model.PairKey;
import it.hyperenderchest.model.PersonalVaultKey;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Stores each player's state and raw NBT inventories in a separate atomic YAML file. */
public final class CacheStorage {
    private final JavaPlugin plugin;
    private final File dataDirectory;
    private final File legacyFile;
    private final YamlConfiguration legacy = new YamlConfiguration();

    public CacheStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataDirectory = new File(plugin.getDataFolder(), "data");
        this.legacyFile = new File(plugin.getDataFolder(), "cache.yml");
        if (!dataDirectory.exists() && !dataDirectory.mkdirs()) {
            throw new IllegalStateException("Unable to create data directory");
        }
        loadLegacy();
    }

    public Set<String> players() {
        Set<String> players = new HashSet<>();
        File[] files = dataDirectory.listFiles((directory, name) -> name.endsWith(".yaml"));
        if (files != null) {
            for (File file : files) {
                players.add(file.getName().substring(0, file.getName().length() - 5));
            }
        }
        if (legacy.isConfigurationSection("Players")) {
            players.addAll(legacy.getConfigurationSection("Players").getKeys(false));
        }
        return players;
    }

    public String pair(UUID playerId) {
        String value = player(playerId).getString("Pair", "");
        return value.isEmpty() ? legacy.getString("Players." + playerId + ".Pair", "") : value;
    }

    public String view(UUID playerId) {
        String value = player(playerId).getString("View", "");
        return value.isEmpty() ? legacy.getString("Players." + playerId + ".View", "PERSONAL") : value;
    }

    public void savePlayer(UUID playerId, PairKey key, String view) {
        YamlConfiguration data = player(playerId);
        data.set("Pair", key.toString());
        data.set("View", view);
        savePlayerFile(playerId, data);
    }

    public void removePlayer(UUID playerId) {
        YamlConfiguration data = player(playerId);
        data.set("Pair", null);
        data.set("View", "PERSONAL");
        savePlayerFile(playerId, data);
    }

    public void loadVault(PairKey key, Inventory inventory) {
        String path = "SharedVaults." + key + ".Inventory";
        if (!loadInventory(player(key.first()), path, inventory)) {
            loadLegacyInventory("Vaults." + key + ".Items", inventory);
        }
    }

    public void saveVault(PairKey key, Inventory inventory) {
        UUID owner = key.first();
        YamlConfiguration data = player(owner);
        saveInventory(data, "SharedVaults." + key, inventory);
        savePlayerFile(owner, data);
    }

    public void loadPersonalVault(PersonalVaultKey key, Inventory inventory) {
        String path = "PersonalVaults." + key.color().name() + ".Inventory";
        if (!loadInventory(player(key.owner()), path, inventory)) {
            loadLegacyInventory("PersonalVaults." + key.owner() + "." + key.color().name() + ".Items", inventory);
        }
    }

    public void savePersonalVault(PersonalVaultKey key, Inventory inventory) {
        YamlConfiguration data = player(key.owner());
        saveInventory(data, "PersonalVaults." + key.color().name(), inventory);
        savePlayerFile(key.owner(), data);
    }

    public Set<String> personalVaultColors(UUID owner) {
        Set<String> colors = new HashSet<>();
        YamlConfiguration data = player(owner);
        if (data.isConfigurationSection("PersonalVaults")) {
            colors.addAll(data.getConfigurationSection("PersonalVaults").getKeys(false));
        }
        String legacyPath = "PersonalVaults." + owner;
        if (legacy.isConfigurationSection(legacyPath)) {
            colors.addAll(legacy.getConfigurationSection(legacyPath).getKeys(false));
        }
        return colors;
    }

    private void saveInventory(YamlConfiguration data, String path, Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        data.set(path + ".Size", inventory.getSize());
        data.set(path + ".Inventory", Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(contents)));
    }

    private boolean loadInventory(YamlConfiguration data, String path, Inventory inventory) {
        String encoded = data.getString(path, "");
        if (encoded.isEmpty()) {
            return false;
        }
        try {
            ItemStack[] contents = ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(encoded));
            inventory.setContents(java.util.Arrays.copyOf(contents, inventory.getSize()));
            return true;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid Base64 inventory at " + path, exception);
        }
    }

    private void loadLegacyInventory(String path, Inventory inventory) {
        List<?> stored = legacy.getList(path, List.of());
        for (int slot = 0; slot < Math.min(stored.size(), inventory.getSize()); slot++) {
            if (stored.get(slot) instanceof ItemStack stack) {
                inventory.setItem(slot, stack.clone());
            }
        }
    }

    private YamlConfiguration player(UUID playerId) {
        File file = playerFile(playerId);
        YamlConfiguration data = new YamlConfiguration();
        if (!file.exists()) {
            return data;
        }
        try {
            data.load(file);
            return data;
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalStateException("Unable to load " + file.getName(), exception);
        }
    }

    private void savePlayerFile(UUID playerId, YamlConfiguration data) {
        File destination = playerFile(playerId);
        File temporary = new File(dataDirectory, playerId + ".yaml.tmp");
        try {
            Files.writeString(temporary.toPath(), data.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save " + destination.getName(), exception);
        }
    }

    private File playerFile(UUID playerId) {
        return new File(dataDirectory, playerId + ".yaml");
    }

    private void loadLegacy() {
        if (!legacyFile.exists()) {
            return;
        }
        try {
            legacy.load(legacyFile);
            plugin.getLogger().info("Legacy cache.yml loaded; data migrates to data/*.yaml on save.");
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalStateException("Unable to load legacy cache.yml", exception);
        }
    }
}
