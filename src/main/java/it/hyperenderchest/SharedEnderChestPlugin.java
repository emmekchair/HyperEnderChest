package it.hyperenderchest;

import it.hyperenderchest.command.EnderChestCommand;
import it.hyperenderchest.config.PluginSettings;
import it.hyperenderchest.listener.EnderChestListener;
import it.hyperenderchest.service.EnderChestManager;
import it.hyperenderchest.service.ShareRequestManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class SharedEnderChestPlugin extends JavaPlugin {
    private EnderChestManager manager;
    private PluginSettings settings;
    private final YamlConfiguration configuration = new YamlConfiguration();

    /**
     * Initializes runtime state after Paper has loaded worlds and plugin configuration.
     * Early bootstrap hooks are intentionally unnecessary because this plugin does not
     * register data packs, registries, or external classpath dependencies.
     */
    @Override
    public void onEnable() {
        saveYamlResource("config.yaml");
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
        loadSettings();
    }

    private void loadSettings() {
        try {
            configuration.load(new java.io.File(getDataFolder(), "config.yaml"));
            settings = PluginSettings.from(configuration);
        } catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException exception) {
            throw new IllegalArgumentException("Unable to load config.yaml", exception);
        }
    }

    private void saveYamlResource(String name) {
        java.io.File file = new java.io.File(getDataFolder(), name);
        if (!file.exists()) {
            saveResource(name, false);
        }
    }

    /** Persists every loaded shared inventory before Paper unloads the plugin. */
    @Override
    public void onDisable() {
        if (manager != null) {
            manager.saveAll();
        }
    }
}
