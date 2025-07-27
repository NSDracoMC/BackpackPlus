package com.nexussphere.backpackplus.commands.subcommands;

import com.nexussphere.backpackplus.commands.SubCommand;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class RevokeCommand extends SubCommand {
    @Override
    public String getName() {
        return "revoke";
    }

    @Override
    public String getDescription() {
        return "Vô hiệu hóa các bản sao cũ của một ba lô cụ thể.";
    }

    @Override
    public String getSyntax() {
        return "/backpack revoke <tên_người_chơi> <thứ_tự_balo>";
    }

    @Override
    public String getPermission() {
        return "backpackplus.admin.revoke";
    }

    @Override
    public void perform(CommandSender sender, String[] args) {
        if (!sender.hasPermission(getPermission())) {
            plugin.sendPluginMessage(sender, "no_permission");
            return;
        }

        if (args.length < 2) {
            plugin.sendPluginMessage(sender, "usage_revoke");
            return;
        }

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(args[0]);
        if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline()) {
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

        plugin.getDatabaseManager().getNthBackpackDbIdForPlayer(targetPlayer.getUniqueId(), backpackIndex - 1)
                .thenAccept(backpackDbId -> {
                    if (backpackDbId == -1) {
                        plugin.sendPluginMessage(sender, "player_no_backpacks_at_index", "player", targetPlayer.getName(), "index", String.valueOf(backpackIndex));
                        return;
                    }

                    String newInstanceUUID = UUID.randomUUID().toString();
                    plugin.getDatabaseManager().updateItemInstanceUUID(backpackDbId, newInstanceUUID).thenRun(() -> {
                        plugin.sendPluginMessage(sender, "revoke_success",
                                "player", targetPlayer.getName(),
                                "index", String.valueOf(backpackIndex));
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