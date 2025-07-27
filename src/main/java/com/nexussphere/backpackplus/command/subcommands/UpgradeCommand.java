package com.nexussphere.backpackplus.commands.subcommands;

import com.nexussphere.backpackplus.commands.SubCommand;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class UpgradeCommand extends SubCommand {
    @Override
    public String getName() { return "upgrade"; }
    @Override
    public String getDescription() { return "Nâng cấp số slot của ba lô."; }
    @Override
    public String getSyntax() { return "/backpack upgrade [tên_người_chơi]"; }
    @Override
    public String getPermission() { return "backpackplus.command.upgrade"; }

    @Override
    public void perform(CommandSender sender, String[] args) {
        if (!sender.hasPermission(getPermission())) {
            plugin.sendPluginMessage(sender, "no_permission");
            return;
        }

        Player targetPlayer;
        if (args.length < 1) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Bạn phải chỉ định tên người chơi khi dùng lệnh từ console.");
                return;
            }
            targetPlayer = (Player) sender;
        } else {
            targetPlayer = Bukkit.getPlayerExact(args[0]);
            if (targetPlayer == null) {
                plugin.sendPluginMessage(sender, "player_not_found", "player", args[0]);
                return;
            }
        }
        ItemStack backpackItem = targetPlayer.getInventory().getItemInMainHand();
        if (backpackItem.getType() == Material.AIR) {
            plugin.sendPluginMessage(sender, "upgrade_no_backpack_in_hand", "player", targetPlayer.getName());
            return;
        }
        ItemMeta meta = backpackItem.getItemMeta();
        if (meta == null) {
            plugin.sendPluginMessage(sender, "upgrade_no_backpack_in_hand", "player", targetPlayer.getName());
            return;
        }
        PersistentDataContainer nbt = meta.getPersistentDataContainer();
        if (!nbt.has(plugin.backpackDbIdKey, PersistentDataType.INTEGER) || !nbt.has(plugin.backpackSizeKey, PersistentDataType.INTEGER)) {
            plugin.sendPluginMessage(sender, "upgrade_no_backpack_in_hand", "player", targetPlayer.getName());
            return;
        }
        Integer backpackDbId = nbt.get(plugin.backpackDbIdKey, PersistentDataType.INTEGER);
        Integer currentSize = nbt.get(plugin.backpackSizeKey, PersistentDataType.INTEGER);
        int currentLevel = nbt.getOrDefault(plugin.backpackUpgradeLevelKey, PersistentDataType.INTEGER, 0);

        if (backpackDbId == null || currentSize == null) {
            plugin.sendPluginMessage(sender, "backpack_corrupted_data");
            return;
        }

        if (currentLevel >= plugin.getMaxUpgrades()) {
            plugin.sendPluginMessage(sender, "upgrade_max_upgrades_reached");
            return;
        }

        int rowsToUpgrade = plugin.getConfig().getInt("backpack.upgrade_increment_rows", 1);
        int slotsToUpgrade = rowsToUpgrade * 9;
        int newSize = currentSize + slotsToUpgrade;
        int newLevel = currentLevel + 1;
        final Player finalTargetPlayer = targetPlayer;

        plugin.getDatabaseManager().updateBackpackSizeAndLevel(backpackDbId, newSize, newLevel).thenAccept(success -> {
            if (success) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    ItemStack updatedBackpackItem = finalTargetPlayer.getInventory().getItemInMainHand();
                    if (updatedBackpackItem == null || !updatedBackpackItem.isSimilar(backpackItem)) {
                        plugin.sendPluginMessage(sender, "upgrade_error");
                        return;
                    }
                    plugin.updateBackpackItem(updatedBackpackItem, finalTargetPlayer, backpackDbId, newSize, newLevel);

                    if (sender.equals(finalTargetPlayer)) {
                        plugin.sendPluginMessage(sender, "upgrade_success_self", "new_total_slots", String.valueOf(newSize));
                    } else {
                        plugin.sendPluginMessage(sender, "upgrade_success", "player", finalTargetPlayer.getName(), "new_total_slots", String.valueOf(newSize));
                    }
                });
            } else {
                plugin.sendPluginMessage(sender, "upgrade_error");
            }
        });
    }

    @Override
    public List<String> getSubcommandArguments(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}