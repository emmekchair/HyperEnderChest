package it.hyperenderchest;

import java.util.UUID;

public record PairKey(UUID first, UUID second) {
    public PairKey {
        if (first.equals(second)) {
            throw new IllegalArgumentException("Sharing requires two different players");
        }
        if (first.toString().compareTo(second.toString()) > 0) {
            UUID swap = first;
            first = second;
            second = swap;
        }
    }

    public static PairKey parse(String value) {
        String[] parts = value.split("_", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid pair key");
        }
        return new PairKey(UUID.fromString(parts[0]), UUID.fromString(parts[1]));
    }

    public boolean contains(UUID playerId) {
        return first.equals(playerId) || second.equals(playerId);
    }

    @Override
    public String toString() {
        return first + "_" + second;
    }
}
