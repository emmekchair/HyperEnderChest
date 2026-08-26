package it.hyperenderchest.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class BannerResolverTest {
    @Test
    void resolvesWallBannerOneBlockBehindAndAboveChest() {
        var server = MockBukkit.mock();
        try {
            var world = server.addSimpleWorld("world");
            var chest = world.getBlockAt(0, 64, 0);
            chest.setType(Material.ENDER_CHEST);
            var banner = world.getBlockAt(0, 65, 1);
            banner.setType(Material.RED_WALL_BANNER);
            Directional data = (Directional) banner.getBlockData();
            data.setFacing(BlockFace.NORTH);
            banner.setBlockData(data);

            assertEquals(DyeColor.RED, new BannerResolver().resolve(chest).orElseThrow());
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void resolvesWallBannerAttachedDirectlyToAnyChestSide() {
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
            var server = MockBukkit.mock();
            try {
                var world = server.addSimpleWorld("world");
                var chest = world.getBlockAt(0, 64, 0);
                chest.setType(Material.ENDER_CHEST);
                var banner = chest.getRelative(face);
                banner.setType(Material.PURPLE_WALL_BANNER);
                Directional data = (Directional) banner.getBlockData();
                data.setFacing(face);
                banner.setBlockData(data);

                assertEquals(DyeColor.PURPLE, new BannerResolver().resolve(chest).orElseThrow());
            } finally {
                MockBukkit.unmock();
            }
        }
    }

    @Test
    void rejectsMultipleAdjacentWallBanners() {
        var server = MockBukkit.mock();
        try {
            var world = server.addSimpleWorld("world");
            var chest = world.getBlockAt(0, 64, 0);
            chest.setType(Material.ENDER_CHEST);
            world.getBlockAt(0, 65, 1).setType(Material.RED_WALL_BANNER);
            world.getBlockAt(1, 65, 0).setType(Material.BLUE_WALL_BANNER);

            assertTrue(new BannerResolver().resolve(chest).isEmpty());
        } finally {
            MockBukkit.unmock();
        }
    }
}
