package it.hyperenderchest;

import java.time.Duration;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class SharedEnderChestPlugin extends JavaPlugin {
    private EnderChestManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        int inventorySize = getConfig().getInt("inventory-size", 27);
        if (inventorySize < 9 || inventorySize > 54 || inventorySize % 9 != 0) {
            throw new IllegalStateException("inventory-size must be a multiple of 9 between 9 and 54");
        }
        manager = new EnderChestManager(this, inventorySize);
        ShareRequestManager requests = new ShareRequestManager(
                Duration.ofSeconds(getConfig().getLong("request-expiry-seconds", 60)),
                Duration.ofSeconds(getConfig().getLong("request-cooldown-seconds", 30)));
        EnderChestListener listener = new EnderChestListener(this, manager);
        EnderChestCommand executor = new EnderChestCommand(this, manager, requests, listener);
        PluginCommand command = getCommand("enderchest");
        if (command == null) {
            throw new IllegalStateException("enderchest command is missing from plugin.yml");
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        getServer().getPluginManager().registerEvents(listener, this);
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.saveAll();
        }
    }
}
