package com.nexussphere.backpackplus.commands.subcommands;

import com.nexussphere.backpackplus.commands.SubCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class CreateCommand extends SubCommand {
    @Override
    public String getName() {
        return "create";
    }

    @Override
    public String getDescription() {
        return "Tạo một ba lô mới.";
    }

    @Override
    public String getSyntax() {
        return "/backpack create [tên_người_chơi] <số_trang>";
    }

    @Override
    public String getPermission() {
        return "backpackplus.create";
    }

    @Override
    public void perform(CommandSender sender, String[] args) {
        if (!sender.hasPermission(getPermission())) {
            plugin.sendPluginMessage(sender, "no_permission");
            return;
        }

        Player targetPlayer;
        int numPages;

        if(sender instanceof ConsoleCommandSender){
            if(args.length < 2){
                plugin.sendPluginMessage(sender, getSyntax());
                return;
            }
            targetPlayer = Bukkit.getPlayerExact(args[0]);
            try {
                numPages = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                plugin.sendPluginMessage(sender, getSyntax());
                return;
            }
        } else if (sender instanceof Player) {
            if (args.length >= 2) {
                targetPlayer = Bukkit.getPlayerExact(args[0]);
                try {
                    numPages = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    plugin.sendPluginMessage(sender, getSyntax());
                    return;
                }
            } else if(args.length == 1){
                targetPlayer = (Player) sender;
                try {
                    numPages = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    plugin.sendPluginMessage(sender, getSyntax());
                    return;
                }
            }
            else {
                plugin.sendPluginMessage(sender, getSyntax());
                return;
            }
        }
        else {
            return;
        }

        if (targetPlayer == null) {
            plugin.sendPluginMessage(sender, "player_not_found", "player", args[0]);
            return;
        }


        if (numPages <= 0 || numPages > plugin.getMaxPages()) {
            plugin.sendPluginMessage(sender, "invalid_page_count", "max_pages", String.valueOf(plugin.getMaxPages()));
            return;
        }

        final int totalSize = numPages * plugin.getItemsPerPage();
        final Player finalTargetPlayer = targetPlayer;
        final String newItemInstanceUUID = UUID.randomUUID().toString();

        plugin.getDatabaseManager().createBackpackEntry(finalTargetPlayer.getUniqueId(), totalSize, newItemInstanceUUID).thenAccept(bpDbId -> {
            if (bpDbId != -1) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    ItemStack bpItem = plugin.createBackpackItemStack(finalTargetPlayer, bpDbId, totalSize, 0, newItemInstanceUUID, "default", null);
                    if (!finalTargetPlayer.getInventory().addItem(bpItem).isEmpty()) {
                        finalTargetPlayer.getWorld().dropItemNaturally(finalTargetPlayer.getLocation(), bpItem);
                        plugin.sendPluginMessage(sender, "backpack_item_give_fail_inventory_full", "player", finalTargetPlayer.getName());
                    } else {
                        if (sender.getName().equals(finalTargetPlayer.getName())) {
                            plugin.sendPluginMessage(sender, "backpack_item_created_self");
                        } else {
                            plugin.sendPluginMessage(sender, "backpack_item_created", "player", finalTargetPlayer.getName());
                        }
                    }
                });
            } else {
                plugin.sendPluginMessage(sender, "error_creating_backpack");
            }
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
        if (args.length == 2) {
            return StringUtil.copyPartialMatches(args[1], Arrays.asList(String.valueOf(plugin.getMaxPages())), new ArrayList<>());
        }
        return Collections.emptyList();
    }
}