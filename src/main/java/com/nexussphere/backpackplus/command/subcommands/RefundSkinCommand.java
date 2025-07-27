package com.nexussphere.backpackplus.commands.subcommands;

import com.nexussphere.backpackplus.commands.SubCommand;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class RefundSkinCommand extends SubCommand {
    @Override
    public String getName() {
        return "refundskin";
    }

    @Override
    public String getDescription() {
        return "Thu hồi skin và hoàn tiền cho người chơi.";
    }

    @Override
    public String getSyntax() {
        return "/backpack refundskin <tên_người_chơi> <skin_id>";
    }

    @Override
    public String getPermission() {
        return "backpackplus.admin.refundskin";
    }

    @Override
    public void perform(CommandSender sender, String[] args) {
        if (!sender.hasPermission(getPermission())) {
            plugin.sendPluginMessage(sender, "no_permission");
            return;
        }
        if (args.length < 2) {
            plugin.sendPluginMessage(sender, "usage_refundskin");
            return;
        }

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(args[0]);
        if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline()) {
            plugin.sendPluginMessage(sender, "player_not_found", "player", args[0]);
            return;
        }

        String skinId = args[1].toLowerCase();
        ConfigurationSection skinConfig = plugin.getBackpackSkins().get(skinId);
        if (skinConfig == null) {
            // "skin_not_found" có thể là một key message tốt hơn ở đây
            plugin.sendPluginMessage(sender, "skin_not_owned", "player", targetPlayer.getName(), "skin_id", skinId);
            return;
        }

        plugin.getDatabaseManager().getPlayerSkins(targetPlayer.getUniqueId()).thenAccept(ownedSkins -> {
            if (!ownedSkins.containsKey(skinId)) {
                plugin.sendPluginMessage(sender, "skin_not_owned", "player", targetPlayer.getName(), "skin_id", skinId);
                return;
            }

            plugin.getDatabaseManager().removePlayerSkin(targetPlayer.getUniqueId(), skinId).thenAccept(success -> {
                if (success) {
                    PlayerPointsAPI ppAPI = plugin.getPlayerPointsAPI();
                    if (ppAPI != null) {
                        int cost = skinConfig.getInt("price", 0);
                        if (cost > 0) {
                            ppAPI.give(targetPlayer.getUniqueId(), cost);
                        }
                        plugin.sendPluginMessage(sender, "refund_success",
                                "player", targetPlayer.getName(),
                                "skin_id", skinId,
                                "cost", String.valueOf(cost));
                    }
                } else {
                    plugin.sendPluginMessage(sender, "refund_error");
                }
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
        if (args.length == 2) {
            return StringUtil.copyPartialMatches(args[1], plugin.getBackpackSkins().keySet(), new ArrayList<>());
        }
        return Collections.emptyList();
    }
}