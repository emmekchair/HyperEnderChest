package it.hyperenderchest.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PairKeyTest {
    @Test
    void normalizesAndRoundTrips() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000001");
        PairKey key = new PairKey(first, second);

        assertEquals(second, key.first());
        assertEquals(first, key.second());
        assertEquals(key, PairKey.parse(key.toString()));
        assertTrue(key.contains(first));
    }

    @Test
    void rejectsSelfShare() {
        UUID player = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new PairKey(player, player));
    }
}
