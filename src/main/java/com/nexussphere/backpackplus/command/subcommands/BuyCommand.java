package com.nexussphere.backpackplus.commands.subcommands;

import com.nexussphere.backpackplus.commands.SubCommand;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
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

public class BuyCommand extends SubCommand {
    @Override
    public String getName() {
        return "buy";
    }

    @Override
    public String getDescription() {
        return "Mua ba lô bằng điểm.";
    }

    @Override
    public String getSyntax() {
        return "/backpack buy [tên_người_chơi] <số_trang>";
    }

    @Override
    public String getPermission() {
        return "backpackplus.command.buy";
    }

    @Override
    public void perform(CommandSender sender, String[] args) {
        Player targetPlayer;
        int numPages;
        boolean isAdminAction = false;

        if (sender instanceof ConsoleCommandSender) {
            if (args.length < 2) {
                plugin.sendPluginMessage(sender, "usage_buy_admin");
                return;
            }
            targetPlayer = Bukkit.getPlayerExact(args[0]);
            try {
                numPages = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                plugin.sendPluginMessage(sender, "usage_buy_admin");
                return;
            }
            isAdminAction = true;
        } else if (sender instanceof Player) {
            Player player = (Player) sender;
            if (args.length >= 2 && player.hasPermission("backpackplus.admin.buy")) {
                targetPlayer = Bukkit.getPlayerExact(args[0]);
                try {
                    numPages = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    plugin.sendPluginMessage(sender, "usage_buy_admin");
                    return;
                }
                isAdminAction = true;
            } else if (args.length >= 1 && player.hasPermission(getPermission())) {
                targetPlayer = player;
                try {
                    numPages = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    plugin.sendPluginMessage(sender, "usage_buy");
                    return;
                }
                isAdminAction = false;
            } else {
                plugin.sendPluginMessage(sender, "no_permission");
                return;
            }
        } else {
            return;
        }


        if (targetPlayer == null) {
            plugin.sendPluginMessage(sender, "player_not_found", "player", args.length > 0 ? args[0] : sender.getName());
            return;
        }

        if (numPages <= 0 || numPages > plugin.getMaxPages()) {
            plugin.sendPluginMessage(sender, "invalid_page_count", "max_pages", String.valueOf(plugin.getMaxPages()));
            return;
        }

        PlayerPointsAPI ppAPI = plugin.getPlayerPointsAPI();
        if (ppAPI == null) {
            plugin.sendPluginMessage(sender, "playerpoints_not_found");
            return;
        }
        int cost = plugin.getBuyCost() * numPages;
        if (ppAPI.look(targetPlayer.getUniqueId()) < cost) {
            String msgKey = isAdminAction && !sender.getName().equals(targetPlayer.getName()) ? "buy_not_enough_points_other" : "buy_not_enough_points";
            plugin.sendPluginMessage(sender, msgKey, "player", targetPlayer.getName(), "cost", String.valueOf(cost));
            return;
        }
        ppAPI.take(targetPlayer.getUniqueId(), cost);

        final int totalSize = numPages * plugin.getItemsPerPage();
        final String newItemInstanceUUID = UUID.randomUUID().toString();
        final int finalCost = cost;
        final Player finalTargetPlayer = targetPlayer;
        final boolean finalIsAdminAction = isAdminAction;

        plugin.getDatabaseManager().createBackpackEntry(targetPlayer.getUniqueId(), totalSize, newItemInstanceUUID).thenAccept(bpDbId -> {
            if (bpDbId != -1) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    ItemStack bpItem = plugin.createBackpackItemStack(finalTargetPlayer, bpDbId, totalSize, 0, newItemInstanceUUID, "default", null);
                    if (!finalTargetPlayer.getInventory().addItem(bpItem).isEmpty()) {
                        finalTargetPlayer.getWorld().dropItemNaturally(finalTargetPlayer.getLocation(), bpItem);
                        plugin.sendPluginMessage(sender, "backpack_item_give_fail_inventory_full", "player", finalTargetPlayer.getName());
                    }
                    if (finalIsAdminAction && !sender.getName().equals(finalTargetPlayer.getName())) {
                        plugin.sendPluginMessage(sender, "buy_success_admin", "player", finalTargetPlayer.getName(), "cost", String.valueOf(finalCost));
                        plugin.sendPluginMessage(finalTargetPlayer, "buy_success_target", "admin", sender.getName(), "cost", String.valueOf(finalCost));
                    } else {
                        plugin.sendPluginMessage(finalTargetPlayer, "buy_success", "cost", String.valueOf(finalCost));
                    }
                });
            } else {
                plugin.sendPluginMessage(sender, "error_creating_backpack");
                ppAPI.give(finalTargetPlayer.getUniqueId(), finalCost);
            }
        });
    }

    @Override
    public List<String> getSubcommandArguments(CommandSender sender, String[] args) {
        if (sender.hasPermission("backpackplus.admin.buy")) {
            if (args.length == 1) {
                return StringUtil.copyPartialMatches(args[0], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), new ArrayList<>());
            }
        }
        return Collections.emptyList();
    }
}