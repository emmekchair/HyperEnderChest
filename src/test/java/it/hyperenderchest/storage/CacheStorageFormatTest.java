package it.hyperenderchest.storage;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class CacheStorageFormatTest {
    @Test
    void yamlResourcesExist() {
        assertNotNull(getClass().getClassLoader().getResource("config.yaml"));
        assertNotNull(getClass().getClassLoader().getResource("plugin.yml"));
    }
}
