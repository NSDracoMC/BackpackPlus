package com.nexussphere.backpackplus.commands.subcommands;

import com.nexussphere.backpackplus.commands.SubCommand;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TrustCommand extends SubCommand {
    @Override
    public String getName() { return "trust"; }
    @Override
    public String getDescription() { return "Cho phép người khác sử dụng ba lô của bạn."; }
    @Override
    public String getSyntax() { return "/backpack trust <tên_người_chơi>"; }
    @Override
    public String getPermission() { return "backpackplus.command.trust"; }

    @Override
    public void perform(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Lệnh này chỉ dành cho người chơi.");
            return;
        }

        if (!player.hasPermission(getPermission())) {
            plugin.sendPluginMessage(player, "no_permission");
            return;
        }

        if (args.length < 1) {
            plugin.sendPluginMessage(player, "usage_trust");
            return;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand.getType() == Material.AIR || !itemInHand.hasItemMeta()) {
            plugin.sendPluginMessage(player, "trust_no_backpack_in_hand");
            return;
        }

        PersistentDataContainer nbt = itemInHand.getItemMeta().getPersistentDataContainer();
        Integer backpackDbId = nbt.get(plugin.backpackDbIdKey, PersistentDataType.INTEGER);
        String ownerUuidString = nbt.get(plugin.backpackOwnerUuidKey, PersistentDataType.STRING);

        if (backpackDbId == null || ownerUuidString == null) {
            plugin.sendPluginMessage(player, "trust_no_backpack_in_hand");
            return;
        }

        if (!player.getUniqueId().toString().equals(ownerUuidString)) {
            plugin.sendPluginMessage(player, "trust_not_your_backpack");
            return;
        }

        OfflinePlayer trustedPlayer = Bukkit.getOfflinePlayer(args[0]);
        if (!trustedPlayer.hasPlayedBefore() && !trustedPlayer.isOnline()) {
            plugin.sendPluginMessage(player, "player_not_found", "player", args[0]);
            return;
        }

        if(player.getUniqueId().equals(trustedPlayer.getUniqueId())) {
            plugin.sendPluginMessage(player, "trust_cannot_trust_self");
            return;
        }

        plugin.getDatabaseManager().addTrustedPlayer(backpackDbId, trustedPlayer.getUniqueId()).thenAccept(success -> {
            if (success) {
                plugin.sendPluginMessage(player, "trust_success", "player", trustedPlayer.getName());
            } else {
                plugin.sendPluginMessage(player, "trust_player_already_trusted", "player", trustedPlayer.getName());
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