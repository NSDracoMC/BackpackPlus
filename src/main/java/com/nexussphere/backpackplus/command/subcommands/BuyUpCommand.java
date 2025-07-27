package com.nexussphere.backpackplus.commands.subcommands;

import com.nexussphere.backpackplus.commands.SubCommand;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class BuyUpCommand extends SubCommand {
    @Override
    public String getName() {
        return "buyup";
    }

    @Override
    public String getDescription() {
        return "Nâng cấp ba lô bằng điểm.";
    }

    @Override
    public String getSyntax() {
        return "/backpack buyup [tên_người_chơi]";
    }

    @Override
    public String getPermission() {
        return "backpackplus.command.buyup";
    }

    @Override
    public void perform(CommandSender sender, String[] args) {
        Player targetPlayer;
        boolean isAdminAction = false;

        if (sender instanceof ConsoleCommandSender) {
            if (args.length < 1) {
                sender.sendMessage("Sử dụng: /backpack buyup <tên_người_chơi>");
                return;
            }
            targetPlayer = Bukkit.getPlayerExact(args[0]);
            isAdminAction = true;
        } else if (sender instanceof Player) {
            Player player = (Player) sender;
            if (args.length >= 1 && player.hasPermission("backpackplus.admin.buyup")) {
                targetPlayer = Bukkit.getPlayerExact(args[0]);
                isAdminAction = true;
            } else if (args.length == 0 && player.hasPermission(getPermission())) {
                targetPlayer = player;
            } else {
                plugin.sendPluginMessage(player, "no_permission");
                return;
            }
        } else {
            return;
        }


        if (targetPlayer == null) {
            plugin.sendPluginMessage(sender, "player_not_found", "player", args.length > 0 ? args[0] : "null");
            return;
        }

        PlayerPointsAPI ppAPI = plugin.getPlayerPointsAPI();
        if (ppAPI == null) {
            plugin.sendPluginMessage(sender, "playerpoints_not_found");
            return;
        }

        int cost = (int) plugin.getBuyupCost();
        if (ppAPI.look(targetPlayer.getUniqueId()) < cost) {
            String msgKey = isAdminAction && !sender.getName().equals(targetPlayer.getName()) ? "buyup_not_enough_points_other" : "buyup_not_enough_points";
            plugin.sendPluginMessage(sender, msgKey, "player", targetPlayer.getName(), "cost", String.valueOf(cost));
            return;
        }

        ppAPI.take(targetPlayer.getUniqueId(), cost);

        plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), "backpack upgrade " + targetPlayer.getName());

        if (isAdminAction && !sender.getName().equals(targetPlayer.getName())) {
            plugin.sendPluginMessage(sender, "buyup_success_admin", "player", targetPlayer.getName(), "cost", String.valueOf(cost));
            plugin.sendPluginMessage(targetPlayer, "buyup_success_target", "admin", sender.getName(), "cost", String.valueOf(cost));
        } else {
            plugin.sendPluginMessage(targetPlayer, "buyup_success", "cost", String.valueOf(cost));
        }
    }

    @Override
    public List<String> getSubcommandArguments(CommandSender sender, String[] args) {
        if (args.length == 1 && sender.hasPermission("backpackplus.admin.buyup")) {
            return StringUtil.copyPartialMatches(args[0], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), new ArrayList<>());
        }
        return Collections.emptyList();
    }
}