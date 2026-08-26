package it.hyperenderchest.model;

import java.util.Locale;
import java.util.UUID;
import org.bukkit.DyeColor;

public record PersonalVaultKey(UUID owner, DyeColor color) {
    public static PersonalVaultKey parse(String value) {
        String[] parts = value.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid personal vault key");
        }
        return new PersonalVaultKey(UUID.fromString(parts[0]), DyeColor.valueOf(parts[1].toUpperCase(Locale.ROOT)));
    }

    @Override
    public String toString() {
        return owner + ":" + color.name();
    }
}
