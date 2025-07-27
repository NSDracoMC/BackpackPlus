package com.nexussphere.backpackplus.commands.subcommands;

import com.nexussphere.backpackplus.commands.SubCommand;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class RecoverCommand extends SubCommand {
    @Override
    public String getName() {
        return "recover";
    }

    @Override
    public String getDescription() {
        return "Lấy lại ba lô bị mất gần nhất.";
    }

    @Override
    public String getSyntax() {
        return "/backpack recover [tên_người_chơi]";
    }

    @Override
    public String getPermission() {
        return "backpackplus.recover";
    }

    @Override
    public void perform(CommandSender sender, String[] args) {
        final OfflinePlayer targetPlayer;
        final OfflinePlayer feePayer;

        boolean isConsole = sender instanceof ConsoleCommandSender;
        boolean isAdmin = sender.hasPermission("backpackplus.admin.recover");

        if (isConsole) {
            if (args.length < 1) {
                sender.sendMessage("Sử dụng: /backpack recover <player>");
                return;
            }
            targetPlayer = Bukkit.getOfflinePlayer(args[0]);
            feePayer = targetPlayer;
        } else if (sender instanceof Player player) {
            if (args.length >= 1 && isAdmin) {
                targetPlayer = Bukkit.getOfflinePlayer(args[0]);
                feePayer = player;
            } else if (args.length == 0 && (player.hasPermission(getPermission()))){
                targetPlayer = player;
                feePayer = player;
            } else {
                plugin.sendPluginMessage(player, "no_permission");
                return;
            }
        } else {
            sender.sendMessage("Lệnh này không thể chạy từ người gửi này.");
            return;
        }

        if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline()) {
            plugin.sendPluginMessage(sender, "player_not_found", "player", args.length > 0 ? args[0] : sender.getName());
            return;
        }

        Economy economy = plugin.getEconomy();
        double recoverCost = plugin.getRecoverCost();

        if (economy != null && recoverCost > 0) {
            if (economy.getBalance(feePayer) < recoverCost) {
                String messageKey = (sender instanceof Player && ((Player)sender).getUniqueId().equals(feePayer.getUniqueId())) ? "recover_not_enough_money" : "recover_target_not_enough_money";
                plugin.sendPluginMessage(sender, messageKey, "player", targetPlayer.getName(), "cost", String.valueOf(recoverCost));
                return;
            }
            economy.withdrawPlayer(feePayer, recoverCost);
        }

        plugin.getDatabaseManager().getMostRecentlyLostBackpack(targetPlayer.getUniqueId()).thenAccept(backpackDbId -> {
            if (backpackDbId == -1) {
                plugin.sendPluginMessage(sender, "recover_no_backpack_lost", "player", targetPlayer.getName());
                return;
            }

            final String newInstanceUUID = UUID.randomUUID().toString();

            plugin.getDatabaseManager().markBackpackAsRecovered(backpackDbId, newInstanceUUID).thenRun(() -> {
                if (isConsole) {
                    plugin.sendPluginMessage(sender, "recover_success_console", "player", targetPlayer.getName());
                } else if (sender.getName().equals(targetPlayer.getName())) {
                    plugin.sendPluginMessage(sender, "recover_success", "cost", String.valueOf(recoverCost));
                } else {
                    plugin.sendPluginMessage(sender, "recover_success_admin", "player", targetPlayer.getName());
                }

                Player onlineTarget = targetPlayer.getPlayer();
                if (onlineTarget != null && onlineTarget.isOnline()) {
                    plugin.getDatabaseManager().getBackpackSizeByDbId(backpackDbId).thenAcceptAsync(size -> {
                        plugin.getDatabaseManager().getBackpackUpgradeLevelByDbId(backpackDbId).thenAcceptAsync(level -> {
                            plugin.getDatabaseManager().getBackpackSkin(backpackDbId).thenAcceptAsync(skinId -> {
                                plugin.getDatabaseManager().getBackpackName(backpackDbId).thenAcceptAsync(customName -> {
                                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                                        ItemStack bpItem = plugin.createBackpackItemStack(targetPlayer, backpackDbId, size, level, newInstanceUUID, skinId, customName);
                                        if (!onlineTarget.getInventory().addItem(bpItem).isEmpty()) {
                                            onlineTarget.getWorld().dropItemNaturally(onlineTarget.getLocation(), bpItem);
                                            plugin.sendPluginMessage(onlineTarget, "recover_inventory_full");
                                        }
                                    });
                                });
                            });
                        });
                    });
                }
            });
        });
    }

    @Override
    public List<String> getSubcommandArguments(CommandSender sender, String[] args) {
        if (args.length == 1 && (sender.hasPermission("backpackplus.admin.recover") || sender instanceof ConsoleCommandSender )) {
            List<String> playerNames = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());
            return StringUtil.copyPartialMatches(args[0], playerNames, new ArrayList<>());
        }
        return Collections.emptyList();
    }
}