package it.hyperenderchest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShareRequestManagerTest {
    @Test
    void consumesMatchingRequestOnce() {
        UUID sender = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        ShareRequestManager manager = new ShareRequestManager(
                Duration.ofSeconds(60), Duration.ofSeconds(30), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        manager.create(sender, target);

        assertEquals(sender, manager.consume(target, sender).orElseThrow());
        assertTrue(manager.consume(target, sender).isEmpty());
        assertEquals(Duration.ofSeconds(30), manager.cooldownRemaining(sender));
    }

    @Test
    void expiresOldRequest() {
        UUID sender = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        ShareRequestManager created = new ShareRequestManager(
                Duration.ZERO, Duration.ZERO, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        created.create(sender, target);

        assertTrue(created.pendingSender(target).isEmpty());
    }
}
