package it.hyperenderchest.listener;

import it.hyperenderchest.SharedEnderChestPlugin;
import it.hyperenderchest.config.PluginSettings;
import it.hyperenderchest.inventory.SharedInventoryHolder;
import it.hyperenderchest.model.PairKey;
import it.hyperenderchest.service.EnderChestManager;

import java.util.UUID;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.EnderChest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.HopperInventorySearchEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Bridges physical Ender Chest blocks to virtual inventories for Paper's hopper search.
 * Block PDC stores only a stable owner or pair reference, never an inventory copy.
 */
public final class EnderChestListener implements Listener {
    private static final String PERSONAL_PREFIX = "personal:";
    private static final String SHARED_PREFIX = "shared:";

    private final SharedEnderChestPlugin plugin;
    private final EnderChestManager manager;
    private final NamespacedKey bindingKey;
    private final NamespacedKey axeKey;
    private final Supplier<PluginSettings> settings;

    public EnderChestListener(SharedEnderChestPlugin plugin, EnderChestManager manager, Supplier<PluginSettings> settings) {
        this.plugin = plugin;
        this.manager = manager;
        this.settings = settings;
        this.bindingKey = new NamespacedKey(plugin, "hopper-binding");
        this.axeKey = new NamespacedKey(plugin, "hopper-axe");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.ENDER_CHEST) {
            return;
        }
        Player player = event.getPlayer();
        EnderChestManager.ViewMode view = manager.view(player.getUniqueId());
        if (isHopperAxe(player.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
            if (!canBind(player)) {
                player.sendMessage("You do not have permission to configure hoppers.");
                return;
            }
            if (event.getClickedBlock().getState() instanceof EnderChest chest) {
                String current = chest.getPersistentDataContainer().get(bindingKey, PersistentDataType.STRING);
                if (current == null) {
                    String binding = view == EnderChestManager.ViewMode.SHARED
                            ? manager.pair(player.getUniqueId()).map(key -> SHARED_PREFIX + key).orElse(PERSONAL_PREFIX + player.getUniqueId())
                            : PERSONAL_PREFIX + player.getUniqueId();
                    chest.getPersistentDataContainer().set(bindingKey, PersistentDataType.STRING, binding);
                    player.sendMessage("Hoppers enabled for this Ender Chest.");
                } else {
                    chest.getPersistentDataContainer().remove(bindingKey);
                    player.sendMessage("Hoppers disabled for this Ender Chest.");
                }
                chest.update(true, false);
                consumeAxe(player);
            }
            return;
        }
        if (view != EnderChestManager.ViewMode.SHARED || manager.pair(player.getUniqueId()).isEmpty()) {
            return;
        }
        event.setCancelled(true);
        player.openInventory(manager.selectedInventory(player));
        player.getWorld().playSound(event.getClickedBlock().getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.0f);
    }

    /**
     * Supplies Paper's hopper with the selected virtual inventory for both source
     * and destination searches, preserving native transfer semantics.
     */
    @EventHandler
    public void onHopperSearch(HopperInventorySearchEvent event) {
        if (event.getSearchBlock().getType() != Material.ENDER_CHEST) {
            return;
        }
        if (!(event.getSearchBlock().getState() instanceof EnderChest chest)) {
            return;
        }
        String binding = chest.getPersistentDataContainer().get(bindingKey, PersistentDataType.STRING);
        if (binding == null) {
            return;
        }
        try {
            if (binding.startsWith(SHARED_PREFIX)) {
                PairKey key = PairKey.parse(binding.substring(SHARED_PREFIX.length()));
                if (manager.pair(key.first()).filter(key::equals).isPresent()) {
                    event.setInventory(manager.sharedInventory(key));
                }
            } else if (binding.startsWith(PERSONAL_PREFIX)) {
                UUID playerId = UUID.fromString(binding.substring(PERSONAL_PREFIX.length()));
                Player player = plugin.getServer().getPlayer(playerId);
                if (player != null) {
                    event.setInventory(player.getEnderChest());
                }
            }
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Invalid Ender Chest hopper binding at " + event.getSearchBlock().getLocation());
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        manager.save(event.getInventory());
    }

    /** Saves on the next tick because Paper applies the hopper move after this event. */
    @EventHandler(ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        Inventory source = event.getSource();
        Inventory destination = event.getDestination();
        Inventory sharedInventory = source.getHolder(false) instanceof SharedInventoryHolder ? source
                : destination.getHolder(false) instanceof SharedInventoryHolder ? destination
                : null;
        if (sharedInventory != null) {
            plugin.getServer().getScheduler().runTask(plugin, () -> manager.save(sharedInventory));
            if (settings.get().logHopperTransfers()) {
                String direction = sharedInventory == source ? "Extraction" : "Insertion";
                plugin.getLogger().info(direction + " by hopper for shared Ender Chest: " + event.getItem().getType());
            }
        }
    }

    public ItemStack createHopperAxe() {
        ItemStack axe = ItemStack.of(Material.WOODEN_AXE);
        axe.editMeta(meta -> meta.displayName(Component.text("Ender Chest Hopper Configurator")));
        axe.editPersistentDataContainer(pdc -> pdc.set(axeKey, PersistentDataType.BOOLEAN, true));
        return axe;
    }

    private boolean isHopperAxe(ItemStack item) {
        return item.getType() == Material.WOODEN_AXE
                && item.getPersistentDataContainer().getOrDefault(axeKey, PersistentDataType.BOOLEAN, false);
    }

    private void consumeAxe(Player player) {
        ItemStack axe = player.getInventory().getItemInMainHand();
        if (axe.getAmount() == 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            axe.setAmount(axe.getAmount() - 1);
        }
    }

    private boolean canBind(Player player) {
        return !settings.get().requireHopperPermission() || player.hasPermission("hyperenderchest.hopper");
    }
}
