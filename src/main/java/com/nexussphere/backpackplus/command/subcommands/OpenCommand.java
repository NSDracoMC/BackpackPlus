package com.nexussphere.backpackplus.commands.subcommands;

import com.nexussphere.backpackplus.commands.SubCommand;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class OpenCommand extends SubCommand {
    @Override
    public String getName() { return "open"; }
    @Override
    public String getDescription() { return "Mở ba lô của người chơi khác."; }
    @Override
    public String getSyntax() { return "/backpack open <tên_người_chơi> <thứ_tự_balo>"; }
    @Override
    public String getPermission() { return "backpackplus.admin.open"; }

    @Override
    public void perform(CommandSender sender, String[] args) {
        if (!sender.hasPermission(getPermission())) {
            plugin.sendPluginMessage(sender, "no_permission");
            return;
        }

        if (!(sender instanceof Player opener)) {
            sender.sendMessage(ChatColor.RED + "Lệnh này chỉ có thể được sử dụng bởi người chơi có quyền.");
            return;
        }
        if (args.length < 2) {
            plugin.sendPluginMessage(opener, "usage_open");
            return;
        }
        String targetPlayerName = args[0];
        int backpackIndexNumber;
        try {
            backpackIndexNumber = Integer.parseInt(args[1]);
            if (backpackIndexNumber <= 0) {
                plugin.sendPluginMessage(opener, "invalid_backpack_index");
                return;
            }
        } catch (NumberFormatException e) {
            plugin.sendPluginMessage(opener, "invalid_backpack_index");
            return;
        }
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName);
        if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline()) {
            plugin.sendPluginMessage(opener, "player_not_found", "player", targetPlayerName);
            return;
        }
        UUID targetUUID = targetPlayer.getUniqueId();
        String targetDisplayName = targetPlayer.getName() != null ? targetPlayer.getName() : targetPlayerName;
        int dbQueryIndex = backpackIndexNumber - 1;
        plugin.getDatabaseManager().getNthBackpackDbIdForPlayer(targetUUID, dbQueryIndex).thenAccept(backpackDbId -> {
            if (backpackDbId != -1) {
                plugin.getBackpackManager().openBackpackByDbId(opener, backpackDbId, targetDisplayName);
            } else {
                plugin.sendPluginMessage(opener, "player_no_backpacks_at_index", "player", targetDisplayName, "index", String.valueOf(backpackIndexNumber));
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
        return Collections.emptyList();
    }
}