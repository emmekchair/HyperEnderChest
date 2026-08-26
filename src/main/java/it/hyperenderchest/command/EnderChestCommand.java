package it.hyperenderchest.command;

import it.hyperenderchest.SharedEnderChestPlugin;
import it.hyperenderchest.config.PluginSettings;
import it.hyperenderchest.listener.EnderChestListener;
import it.hyperenderchest.model.PairKey;
import it.hyperenderchest.model.PersonalVaultKey;
import it.hyperenderchest.service.EnderChestManager;
import it.hyperenderchest.service.ShareRequestManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class EnderChestCommand implements CommandExecutor, TabCompleter {
    private final SharedEnderChestPlugin plugin;
    private final EnderChestManager manager;
    private final ShareRequestManager requests;
    private final EnderChestListener listener;
    private final Supplier<PluginSettings> settings;
    private final Runnable reloadSettings;

    public EnderChestCommand(
            SharedEnderChestPlugin plugin,
            EnderChestManager manager,
            ShareRequestManager requests,
            EnderChestListener listener,
            Supplier<PluginSettings> settings,
            Runnable reloadSettings) {
        this.plugin = plugin;
        this.manager = manager;
        this.requests = requests;
        this.listener = listener;
        this.settings = settings;
        this.reloadSettings = reloadSettings;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command is available to players only.");
            return true;
        }
        String action = args.length == 0 ? "open" : args[0].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "open" -> open(player);
            case "share" -> share(player, args);
            case "accept" -> answer(player, args, true);
            case "deny" -> answer(player, args, false);
            case "view" -> view(player, args);
            case "unshare" -> unshare(player);
            case "hopperaxe" -> hopperAxe(player);
            case "vault" -> vault(player, args);
            case "reload" -> reload(player);
            default -> false;
        };
    }

    private boolean open(Player player) {
        player.openInventory(manager.selectedInventory(player));
        return true;
    }

    private boolean share(Player player, String[] args) {
        if (!canShare(player) || args.length != 2) {
            player.sendMessage(args.length != 2 ? "Usage: /enderchest share <player>" : "You do not have permission to share.");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || target.equals(player)) {
            player.sendMessage("Invalid player or player is offline.");
            return true;
        }
        if (manager.isShared(player.getUniqueId()) || manager.isShared(target.getUniqueId())) {
            player.sendMessage("One of the players already shares an Ender Chest.");
            return true;
        }
        long wait = requests.cooldownRemaining(player.getUniqueId()).toSeconds();
        if (wait > 0) {
            player.sendMessage("Wait " + wait + " seconds before sending another request.");
            return true;
        }
        requests.create(player.getUniqueId(), target.getUniqueId());
        player.sendMessage("Request sent to " + target.getName() + ".");
        target.sendMessage(player.getName() + " wants to share an Ender Chest. Use /enderchest accept " + player.getName() + ".");
        return true;
    }

    private boolean answer(Player player, String[] args, boolean accept) {
        if (!canShare(player)) {
            player.sendMessage("You do not have permission to share.");
            return true;
        }
        Optional<UUID> pending = requests.pendingSender(player.getUniqueId());
        if (pending.isEmpty()) {
            player.sendMessage("No valid pending request.");
            return true;
        }
        Player sender = Bukkit.getPlayer(pending.get());
        if (sender == null || args.length > 1 && !sender.getName().equalsIgnoreCase(args[1])) {
            player.sendMessage("Request not found or sender is offline.");
            return true;
        }
        if (!accept) {
            requests.deny(player.getUniqueId(), sender.getUniqueId());
            player.sendMessage("Request denied.");
            sender.sendMessage(player.getName() + " denied the request.");
            return true;
        }
        if (manager.isShared(player.getUniqueId()) || manager.isShared(sender.getUniqueId()) || requests.consume(player.getUniqueId(), sender.getUniqueId()).isEmpty()) {
            player.sendMessage("Request expired or sharing is no longer available.");
            return true;
        }
        manager.share(sender.getUniqueId(), player.getUniqueId());
        player.sendMessage("Ender Chest shared with " + sender.getName() + ".");
        sender.sendMessage("Ender Chest shared with " + player.getName() + ".");
        if (settings.get().logShareEvents()) {
            plugin.getLogger().info("Share created: " + sender.getUniqueId() + " and " + player.getUniqueId());
        }
        return true;
    }

    private boolean view(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage("Usage: /enderchest view <personal|shared>");
            return true;
        }
        try {
            EnderChestManager.ViewMode mode = EnderChestManager.ViewMode.valueOf(args[1].toUpperCase(Locale.ROOT));
            manager.setView(player.getUniqueId(), mode);
            player.sendMessage("View set to " + args[1].toLowerCase(Locale.ROOT) + ".");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage("Invalid view or no active share.");
        }
        return true;
    }

    private boolean unshare(Player player) {
        if (!canShare(player)) {
            player.sendMessage("You do not have permission to revoke sharing.");
            return true;
        }
        Optional<PairKey> removed = manager.unshare(player.getUniqueId());
        if (removed.isEmpty()) {
            player.sendMessage("No active share.");
            return true;
        }
        for (UUID member : List.of(removed.get().first(), removed.get().second())) {
            Player online = Bukkit.getPlayer(member);
            if (online != null) {
                online.sendMessage("Ender Chest sharing revoked. Items remain archived.");
            }
        }
        if (settings.get().logShareEvents()) {
            plugin.getLogger().info("Share revoked: " + removed.get());
        }
        return true;
    }

    private boolean hopperAxe(Player player) {
        if (!player.hasPermission("hyperenderchest.hopper")) {
            player.sendMessage("You do not have permission to configure hoppers.");
            return true;
        }
        var remaining = player.getInventory().addItem(listener.createHopperAxe());
        if (!remaining.isEmpty()) {
            player.sendMessage("Inventory is full.");
            return true;
        }
        player.sendMessage("Axe received. Click an Ender Chest to enable or disable hoppers.");
        return true;
    }

    private boolean vault(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage("Usage: /enderchest vault <color|list>");
            return true;
        }
        if (args[1].equalsIgnoreCase("list")) {
            List<String> colors = manager.personalVaultColors(player.getUniqueId()).stream()
                    .map(color -> color.name().toLowerCase(Locale.ROOT)).sorted().toList();
            player.sendMessage(colors.isEmpty() ? "You have no banner-linked personal vaults." : "Personal vaults: " + String.join(", ", colors));
            return true;
        }
        try {
            DyeColor color = DyeColor.valueOf(args[1].toUpperCase(Locale.ROOT));
            player.openInventory(manager.personalVault(new PersonalVaultKey(player.getUniqueId(), color)));
        } catch (IllegalArgumentException exception) {
            player.sendMessage("Invalid color. Use /enderchest vault list to see existing vaults.");
        }
        return true;
    }

    private boolean reload(Player player) {
        if (!player.hasPermission("hyperenderchest.reload")) {
            player.sendMessage("You do not have permission to reload the plugin.");
            return true;
        }
        try {
            reloadSettings.run();
            player.sendMessage("HyperEnderChest configuration reloaded.");
        } catch (IllegalArgumentException exception) {
            player.sendMessage("Configuration reload failed. Check the server log.");
            plugin.getLogger().warning(exception.getMessage());
        }
        return true;
    }

    private boolean canShare(Player player) {
        return !settings.get().requireSharePermission() || player.hasPermission("hyperenderchest.share");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        if (args.length == 1) {
            return prefix(List.of("open", "share", "accept", "deny", "view", "unshare", "hopperaxe", "vault", "reload"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("view")) {
            return prefix(List.of("personal", "shared"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("vault")) {
            List<String> colors = new ArrayList<>();
            colors.add("list");
            Arrays.stream(DyeColor.values()).map(color -> color.name().toLowerCase(Locale.ROOT)).forEach(colors::add);
            return prefix(colors, args[1]);
        }
        if (args.length == 2 && Arrays.asList("share", "accept", "deny").contains(args[0].toLowerCase(Locale.ROOT))) {
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(player -> names.add(player.getName()));
            return prefix(names, args[1]);
        }
        return List.of();
    }

    private List<String> prefix(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
