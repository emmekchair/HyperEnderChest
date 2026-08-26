package it.hyperenderchest.config;

import java.time.Duration;
import org.bukkit.configuration.file.FileConfiguration;

/** Validated, reloadable snapshot of every plugin configuration value. */
public record PluginSettings(
        int inventorySize,
        Duration requestExpiry,
        Duration requestCooldown,
        boolean requireSharePermission,
        boolean requireHopperPermission,
        boolean logShareEvents,
        boolean logHopperTransfers) {

    public static PluginSettings from(FileConfiguration config) {
        int inventorySize = config.getInt("inventory-size", 27);
        if (inventorySize < 9 || inventorySize > 54 || inventorySize % 9 != 0) {
            throw new IllegalArgumentException("inventory-size must be a multiple of 9 between 9 and 54");
        }
        return new PluginSettings(
                inventorySize,
                positiveDuration(config, "request-expiry-seconds", 60),
                positiveDuration(config, "request-cooldown-seconds", 30),
                config.getBoolean("require-share-permission", true),
                config.getBoolean("require-hopper-permission", true),
                config.getBoolean("logging.share-events", true),
                config.getBoolean("logging.hopper-transfers", false));
    }

    private static Duration positiveDuration(FileConfiguration config, String path, long fallback) {
        long seconds = config.getLong(path, fallback);
        if (seconds < 0) {
            throw new IllegalArgumentException(path + " cannot be negative");
        }
        return Duration.ofSeconds(seconds);
    }
}
