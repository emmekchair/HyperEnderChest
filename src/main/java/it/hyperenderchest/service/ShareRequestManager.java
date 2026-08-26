package it.hyperenderchest.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ShareRequestManager {
    private final Map<UUID, Request> requestsByTarget = new HashMap<>();
    private final Map<UUID, Instant> lastRequestBySender = new HashMap<>();
    private final Duration expiry;
    private final Duration cooldown;
    private final Clock clock;

    public ShareRequestManager(Duration expiry, Duration cooldown) {
        this(expiry, cooldown, Clock.systemUTC());
    }

    ShareRequestManager(Duration expiry, Duration cooldown, Clock clock) {
        this.expiry = expiry;
        this.cooldown = cooldown;
        this.clock = clock;
    }

    public Duration cooldownRemaining(UUID sender) {
        Instant allowedAt = lastRequestBySender.getOrDefault(sender, Instant.MIN).plus(cooldown);
        Duration remaining = Duration.between(clock.instant(), allowedAt);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public void create(UUID sender, UUID target) {
        Instant now = clock.instant();
        requestsByTarget.put(target, new Request(sender, now.plus(expiry)));
        lastRequestBySender.put(sender, now);
    }

    public Optional<UUID> consume(UUID target, UUID expectedSender) {
        Request request = requestsByTarget.remove(target);
        if (request == null || !request.sender().equals(expectedSender) || !request.expiresAt().isAfter(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(request.sender());
    }

    public Optional<UUID> pendingSender(UUID target) {
        Request request = requestsByTarget.get(target);
        if (request == null || !request.expiresAt().isAfter(clock.instant())) {
            requestsByTarget.remove(target);
            return Optional.empty();
        }
        return Optional.of(request.sender());
    }

    public boolean deny(UUID target, UUID expectedSender) {
        return consume(target, expectedSender).isPresent();
    }

    private record Request(UUID sender, Instant expiresAt) {
    }
}
