package com.nexussphere.backpackplus.commands.subcommands;

import com.nexussphere.backpackplus.commands.SubCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class GiveCommand extends SubCommand {
    @Override
    public String getName() { return "give"; }
    @Override
    public String getDescription() { return "Đưa lại vật phẩm ba lô cho người chơi."; }
    @Override
    public String getSyntax() { return "/backpack give <tên_người_chơi> <thứ_tự_balo>"; }
    @Override
    public String getPermission() { return "backpackplus.admin.give"; }

    @Override
    public void perform(CommandSender sender, String[] args) {
        if (!sender.hasPermission(getPermission())) {
            plugin.sendPluginMessage(sender, "no_permission");
            return;
        }

        if (args.length < 2) {
            plugin.sendPluginMessage(sender, "usage_give");
            return;
        }

        Player targetPlayer = Bukkit.getPlayerExact(args[0]);
        if (targetPlayer == null) {
            plugin.sendPluginMessage(sender, "player_not_found", "player", args[0]);
            return;
        }

        int backpackIndex;
        try {
            backpackIndex = Integer.parseInt(args[1]);
            if (backpackIndex <= 0) {
                plugin.sendPluginMessage(sender, "invalid_backpack_index");
                return;
            }
        } catch (NumberFormatException e) {
            plugin.sendPluginMessage(sender, "invalid_backpack_index");
            return;
        }

        final int finalBackpackIndex = backpackIndex;

        plugin.getDatabaseManager().getNthBackpackDbIdForPlayer(targetPlayer.getUniqueId(), finalBackpackIndex - 1).thenAccept(backpackDbId -> {
            if (backpackDbId == -1) {
                plugin.sendPluginMessage(sender, "player_no_backpacks_at_index", "player", targetPlayer.getName(), "index", String.valueOf(finalBackpackIndex));
                return;
            }

            final String newInstanceUUID = UUID.randomUUID().toString();
            final Player finalTargetPlayer = targetPlayer;
            plugin.getDatabaseManager().updateItemInstanceUUID(backpackDbId, newInstanceUUID).thenRun(() -> {
                plugin.getDatabaseManager().getBackpackSizeByDbId(backpackDbId).thenAcceptAsync(size -> {
                    plugin.getDatabaseManager().getBackpackUpgradeLevelByDbId(backpackDbId).thenAcceptAsync(level -> {
                        plugin.getDatabaseManager().getBackpackSkin(backpackDbId).thenAcceptAsync(skinId -> {
                            plugin.getDatabaseManager().getBackpackName(backpackDbId).thenAcceptAsync(customName -> {
                                plugin.getServer().getScheduler().runTask(plugin, () -> {
                                    ItemStack bpItem = plugin.createBackpackItemStack(finalTargetPlayer, backpackDbId, size, level, newInstanceUUID, skinId, customName);
                                    if (!finalTargetPlayer.getInventory().addItem(bpItem).isEmpty()) {
                                        finalTargetPlayer.getWorld().dropItemNaturally(finalTargetPlayer.getLocation(), bpItem);
                                        plugin.sendPluginMessage(sender, "backpack_item_give_fail_inventory_full", "player", finalTargetPlayer.getName());
                                    } else {
                                        plugin.sendPluginMessage(sender, "give_success", "player", finalTargetPlayer.getName(), "index", String.valueOf(finalBackpackIndex));
                                    }
                                });
                            });
                        });
                    });
                });
            });
        });
    }

    @Override
    public List<String> getSubcommandArguments(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> playerNames = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());
            return StringUtil.copyPartialMatches(args[0], playerNames, new ArrayList<>());
        }
        return Collections.emptyList();
    }
}