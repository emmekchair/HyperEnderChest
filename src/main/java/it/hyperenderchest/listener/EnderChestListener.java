package it.hyperenderchest.listener;

import it.hyperenderchest.SharedEnderChestPlugin;
import it.hyperenderchest.config.PluginSettings;
import it.hyperenderchest.inventory.PersonalInventoryHolder;
import it.hyperenderchest.inventory.SharedInventoryHolder;
import it.hyperenderchest.model.PairKey;
import it.hyperenderchest.model.PersonalVaultKey;
import it.hyperenderchest.service.BannerResolver;
import it.hyperenderchest.service.EnderChestManager;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.DyeColor;
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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Bridges physical Ender Chest blocks to virtual inventories for Paper's hopper search.
 * Block PDC stores only a stable owner or pair reference, never an inventory copy.
 */
public final class EnderChestListener implements Listener {
    private static final String PERSONAL_PREFIX = "personal:";
    private static final String PERSONAL_VAULT_PREFIX = "personal-vault:";
    private static final String SHARED_PREFIX = "shared:";

    private final SharedEnderChestPlugin plugin;
    private final EnderChestManager manager;
    private final NamespacedKey bindingKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey colorKey;
    private final NamespacedKey axeKey;
    private final BannerResolver bannerResolver = new BannerResolver();
    private final Supplier<PluginSettings> settings;
    private final Set<Inventory> pendingSaves = new HashSet<>();
    private boolean saveScheduled;

    public EnderChestListener(SharedEnderChestPlugin plugin, EnderChestManager manager, Supplier<PluginSettings> settings) {
        this.plugin = plugin;
        this.manager = manager;
        this.settings = settings;
        this.bindingKey = new NamespacedKey(plugin, "hopper-binding");
        this.ownerKey = new NamespacedKey(plugin, "personal-vault-owner");
        this.colorKey = new NamespacedKey(plugin, "personal-vault-color");
        this.axeKey = new NamespacedKey(plugin, "hopper-axe");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.ENDER_CHEST) {
            return;
        }
        Player player = event.getPlayer();
        EnderChestManager.ViewMode view = manager.view(player.getUniqueId());
        EnderChest chest = (EnderChest) event.getClickedBlock().getState();
        if (isHopperAxe(player.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
            if (!canBind(player)) {
                player.sendMessage("You do not have permission to configure hoppers.");
                return;
            }
            String current = chest.getPersistentDataContainer().get(bindingKey, PersistentDataType.STRING);
            if (current == null) {
                var bannerColor = bannerResolver.resolve(event.getClickedBlock());
                String binding;
                if (bannerColor.isPresent()) {
                    String owner = chest.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
                    if (owner != null && !owner.equals(player.getUniqueId().toString())) {
                        player.sendMessage("This banner-linked Ender Chest belongs to another player.");
                        return;
                    }
                    PersonalVaultKey key = new PersonalVaultKey(player.getUniqueId(), bannerColor.get());
                    chest.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, key.owner().toString());
                    chest.getPersistentDataContainer().set(colorKey, PersistentDataType.STRING, key.color().name());
                    binding = PERSONAL_VAULT_PREFIX + key;
                } else {
                    binding = view == EnderChestManager.ViewMode.SHARED
                            ? manager.pair(player.getUniqueId()).map(key -> SHARED_PREFIX + key).orElse(PERSONAL_PREFIX + player.getUniqueId())
                            : PERSONAL_PREFIX + player.getUniqueId();
                }
                chest.getPersistentDataContainer().set(bindingKey, PersistentDataType.STRING, binding);
                player.sendMessage("Hoppers enabled for this Ender Chest.");
            } else {
                chest.getPersistentDataContainer().remove(bindingKey);
                player.sendMessage("Hoppers disabled for this Ender Chest.");
            }
            chest.update(true, false);
            consumeAxe(player);
            return;
        }
        var bannerColor = bannerResolver.resolve(event.getClickedBlock());
        if (bannerColor.isPresent()) {
            event.setCancelled(true);
            String owner = chest.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
            if (owner != null && !owner.equals(player.getUniqueId().toString())) {
                player.sendMessage("This banner-linked Ender Chest belongs to another player.");
                return;
            }
            PersonalVaultKey key = new PersonalVaultKey(player.getUniqueId(), bannerColor.get());
            String previousOwner = chest.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
            String previousColor = chest.getPersistentDataContainer().get(colorKey, PersistentDataType.STRING);
            chest.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, key.owner().toString());
            chest.getPersistentDataContainer().set(colorKey, PersistentDataType.STRING, key.color().name());
            chest.update(true, false);
            if (!key.owner().toString().equals(previousOwner) || !key.color().name().equals(previousColor)) {
                notifyOperators(event.getClickedBlock(), player, key.color());
            }
            player.openInventory(manager.personalVault(key));
            player.getWorld().playSound(event.getClickedBlock().getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.0f);
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
        String owner = chest.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        String color = chest.getPersistentDataContainer().get(colorKey, PersistentDataType.STRING);
        if (binding.startsWith(PERSONAL_VAULT_PREFIX) && owner != null && color != null) {
            try {
                PersonalVaultKey key = new PersonalVaultKey(UUID.fromString(owner), DyeColor.valueOf(color));
                if (binding.equals(PERSONAL_VAULT_PREFIX + key)) {
                    event.setInventory(manager.personalVault(key));
                }
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Invalid personal vault binding at " + event.getSearchBlock().getLocation());
            }
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
    public void onVaultMenuClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof VaultMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getCurrentItem() == null) {
            return;
        }
        DyeColor color = holder.color(event.getRawSlot());
        if (color != null) {
            player.openInventory(manager.personalVault(new PersonalVaultKey(player.getUniqueId(), color)));
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
        Inventory pluginInventory = source.getHolder(false) instanceof SharedInventoryHolder
                        || source.getHolder(false) instanceof PersonalInventoryHolder ? source
                : destination.getHolder(false) instanceof SharedInventoryHolder
                        || destination.getHolder(false) instanceof PersonalInventoryHolder ? destination
                : null;
        if (pluginInventory != null) {
            scheduleSave(pluginInventory);
            if (settings.get().logHopperTransfers()) {
                String direction = pluginInventory == source ? "Extraction" : "Insertion";
                plugin.getLogger().info(direction + " by hopper for plugin Ender Chest: " + event.getItem().getType());
            }
        }
    }

    private void scheduleSave(Inventory inventory) {
        pendingSaves.add(inventory);
        if (saveScheduled) {
            return;
        }
        saveScheduled = true;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Set<Inventory> saves = Set.copyOf(pendingSaves);
            pendingSaves.clear();
            saveScheduled = false;
            saves.forEach(manager::save);
        });
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

    private void notifyOperators(org.bukkit.block.Block chest, Player owner, DyeColor color) {
        String message = "[HyperEnderChest] Activated " + color.name() + " vault for " + owner.getName()
                + " at " + chest.getWorld().getName() + " " + chest.getX() + ", " + chest.getY() + ", " + chest.getZ() + ".";
        plugin.getServer().getOnlinePlayers().stream().filter(Player::isOp).forEach(operator -> operator.sendMessage(message));
    }

    public static final class VaultMenuHolder implements InventoryHolder {
        private final Inventory inventory = org.bukkit.Bukkit.createInventory(this, 18, Component.text("Personal Vaults"));
        private final DyeColor[] colors = DyeColor.values();

        public VaultMenuHolder() {
            for (int slot = 0; slot < colors.length; slot++) {
                ItemStack item = ItemStack.of(Material.valueOf(colors[slot].name() + "_BANNER"));
                DyeColor color = colors[slot];
                item.editMeta(meta -> meta.displayName(Component.text(color.name().toLowerCase(java.util.Locale.ROOT))));
                inventory.setItem(slot, item);
            }
        }

        public DyeColor color(int slot) {
            return slot >= 0 && slot < colors.length ? colors[slot] : null;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private boolean canBind(Player player) {
        return !settings.get().requireHopperPermission() || player.hasPermission("hyperenderchest.hopper");
    }
}
