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
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TransferCommand extends SubCommand {
    @Override
    public String getName() { return "transfer"; }
    @Override
    public String getDescription() { return "Chuyển quyền sở hữu ba lô cho người khác."; }
    @Override
    public String getSyntax() { return "/backpack transfer <tên_người_chơi>"; }
    @Override
    public String getPermission() { return "backpackplus.command.transfer"; }

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
            plugin.sendPluginMessage(player, "usage_transfer");
            return;
        }

        ItemStack backpackItem = player.getInventory().getItemInMainHand();
        if (backpackItem.getType() == Material.AIR || !backpackItem.hasItemMeta()) {
            plugin.sendPluginMessage(player, "transfer_no_backpack_in_hand");
            return;
        }

        ItemMeta meta = backpackItem.getItemMeta();
        PersistentDataContainer nbt = meta.getPersistentDataContainer();
        Integer backpackDbId = nbt.get(plugin.backpackDbIdKey, PersistentDataType.INTEGER);
        String ownerUuidString = nbt.get(plugin.backpackOwnerUuidKey, PersistentDataType.STRING);

        if (backpackDbId == null || ownerUuidString == null) {
            plugin.sendPluginMessage(player, "transfer_no_backpack_in_hand");
            return;
        }

        if (!player.getUniqueId().toString().equals(ownerUuidString)) {
            plugin.sendPluginMessage(player, "trust_not_your_backpack");
            return;
        }

        Player newOwner = Bukkit.getPlayerExact(args[0]);
        if (newOwner == null) {
            plugin.sendPluginMessage(player, "player_not_found", "player", args[0]);
            return;
        }

        if (player.getUniqueId().equals(newOwner.getUniqueId())) {
            plugin.sendPluginMessage(player, "transfer_cannot_transfer_to_self");
            return;
        }

        final int finalBackpackDbId = backpackDbId;
        final Player finalNewOwner = newOwner;
        final ItemStack finalBackpackItem = backpackItem;

        plugin.getDatabaseManager().updateBackpackOwner(finalBackpackDbId, finalNewOwner.getUniqueId()).thenAccept(success -> {
            if (success) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    plugin.updateBackpackItemOwner(finalBackpackItem, finalNewOwner);
                    plugin.sendPluginMessage(player, "transfer_success_sender", "player", finalNewOwner.getName());
                    plugin.sendPluginMessage(finalNewOwner, "transfer_success_receiver", "player", player.getName());
                });
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