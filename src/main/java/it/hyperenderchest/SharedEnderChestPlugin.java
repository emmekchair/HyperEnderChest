package it.hyperenderchest;

import it.hyperenderchest.command.EnderChestCommand;
import it.hyperenderchest.config.PluginSettings;
import it.hyperenderchest.listener.EnderChestListener;
import it.hyperenderchest.service.EnderChestManager;
import it.hyperenderchest.service.ShareRequestManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class SharedEnderChestPlugin extends JavaPlugin {
    private EnderChestManager manager;
    private PluginSettings settings;

    /**
     * Initializes runtime state after Paper has loaded worlds and plugin configuration.
     * Early bootstrap hooks are intentionally unnecessary because this plugin does not
     * register data packs, registries, or external classpath dependencies.
     */
    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        manager = new EnderChestManager(this, settings.inventorySize());
        ShareRequestManager requests = new ShareRequestManager(settings.requestExpiry(), settings.requestCooldown());
        EnderChestListener listener = new EnderChestListener(this, manager, this::settings);
        EnderChestCommand executor = new EnderChestCommand(this, manager, requests, listener, this::settings, this::reloadSettings);
        PluginCommand command = getCommand("enderchest");
        if (command == null) {
            throw new IllegalStateException("enderchest command is missing from plugin.yml");
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        getServer().getPluginManager().registerEvents(listener, this);
    }

    public PluginSettings settings() {
        return settings;
    }

    public void reloadSettings() {
        reloadConfig();
        loadSettings();
    }

    private void loadSettings() {
        settings = PluginSettings.from(getConfig());
    }

    /** Persists every loaded shared inventory before Paper unloads the plugin. */
    @Override
    public void onDisable() {
        if (manager != null) {
            manager.saveAll();
        }
    }
}
