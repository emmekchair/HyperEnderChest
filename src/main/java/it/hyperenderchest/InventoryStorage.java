package it.hyperenderchest;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class InventoryStorage {
    private final File directory;

    public InventoryStorage(File dataFolder) {
        this.directory = new File(dataFolder, "vaults");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Unable to create " + directory);
        }
    }

    public void load(PairKey key, Inventory inventory) {
        File file = file(key);
        if (!file.isFile()) {
            return;
        }
        List<?> stored = YamlConfiguration.loadConfiguration(file).getList("items", List.of());
        for (int slot = 0; slot < Math.min(stored.size(), inventory.getSize()); slot++) {
            Object item = stored.get(slot);
            if (item instanceof ItemStack stack) {
                inventory.setItem(slot, stack);
            }
        }
    }

    public void save(PairKey key, Inventory inventory) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        List<ItemStack> items = new ArrayList<>(inventory.getSize());
        for (ItemStack item : inventory.getContents()) {
            items.add(item == null ? null : item.clone());
        }
        yaml.set("items", items);
        File destination = file(key);
        File temporary = new File(directory, key + ".yml.tmp");
        yaml.save(temporary);
        try {
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private File file(PairKey key) {
        return new File(directory, key + ".yml");
    }
}
