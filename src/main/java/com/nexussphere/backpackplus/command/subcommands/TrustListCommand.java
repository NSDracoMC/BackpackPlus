package com.nexussphere.backpackplus.commands.subcommands;

import com.nexussphere.backpackplus.commands.SubCommand;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TrustListCommand extends SubCommand {
    @Override
    public String getName() { return "trustlist"; }
    @Override
    public String getDescription() { return "Xem danh sách người chơi được tin cậy."; }
    @Override
    public String getSyntax() { return "/backpack trustlist"; }
    @Override
    public String getPermission() { return "backpackplus.command.trustlist"; }

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

        plugin.getDatabaseManager().getTrustedPlayers(backpackDbId).thenAccept(trustedUuids -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.sendPluginMessage(player, "trustlist_header");
                if (trustedUuids.isEmpty()) {
                    plugin.sendPluginMessage(player, "trustlist_empty");
                } else {
                    String names = trustedUuids.stream()
                            .map(uuid -> Bukkit.getOfflinePlayer(uuid).getName())
                            .filter(name -> name != null)
                            .collect(Collectors.joining(", "));
                    player.sendMessage(ChatColor.GREEN + names);
                }
            });
        });
    }

    @Override
    public List<String> getSubcommandArguments(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}