package it.hyperenderchest.service;

import java.util.Optional;
import org.bukkit.DyeColor;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public final class BannerResolver {
    private static final BlockFace[] HORIZONTAL_FACES = {
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };

    public Optional<DyeColor> resolve(Block enderChest) {
        Block above = enderChest.getRelative(BlockFace.UP);
        DyeColor resolved = bannerColor(above).orElse(null);
        for (BlockFace face : HORIZONTAL_FACES) {
            Optional<DyeColor> candidate = bannerColor(enderChest.getRelative(face));
            if (candidate.isEmpty()) {
                candidate = bannerColor(above.getRelative(face));
            }
            if (candidate.isEmpty()) {
                continue;
            }
            if (resolved != null) {
                return Optional.empty();
            }
            resolved = candidate.get();
        }
        return Optional.ofNullable(resolved);
    }

    private Optional<DyeColor> bannerColor(Block block) {
        return block.getState() instanceof Banner banner ? Optional.of(banner.getBaseColor()) : Optional.empty();
    }
}
