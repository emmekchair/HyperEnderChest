package it.hyperenderchest.storage;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class CacheStorageFormatTest {
    @Test
    void cacheResourceContainsRequiredSections() throws Exception {
        var resource = CacheStorageFormatTest.class.getClassLoader().getResource("cache.yml");
        assertTrue(resource != null);

        var cache = YamlConfiguration.loadConfiguration(new File(resource.toURI()));

        assertTrue(cache.isConfigurationSection("Players"));
        assertTrue(cache.isConfigurationSection("Vaults"));
    }
}
