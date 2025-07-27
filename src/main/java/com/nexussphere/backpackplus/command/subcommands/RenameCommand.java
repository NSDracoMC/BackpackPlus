package com.nexussphere.backpackplus.commands.subcommands;

import com.nexussphere.backpackplus.commands.SubCommand;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import java.util.Collections;
import java.util.List;

public class RenameCommand extends SubCommand {
    @Override
    public String getName() { return "rename"; }
    @Override
    public String getDescription() { return "Đổi tên ba lô đang cầm trên tay."; }
    @Override
    public String getSyntax() { return "/backpack rename <tên_mới>"; }
    @Override
    public String getPermission() { return "backpackplus.command.rename"; }

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
            plugin.sendPluginMessage(player, "usage_rename");
            return;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand.getType() == Material.AIR || !itemInHand.hasItemMeta()) {
            plugin.sendPluginMessage(player, "rename_no_backpack_in_hand");
            return;
        }

        ItemMeta meta = itemInHand.getItemMeta();
        if (meta == null) {
            plugin.sendPluginMessage(player, "rename_no_backpack_in_hand");
            return;
        }

        PersistentDataContainer nbt = meta.getPersistentDataContainer();
        Integer backpackDbId = nbt.get(plugin.backpackDbIdKey, PersistentDataType.INTEGER);
        String ownerUuidString = nbt.get(plugin.backpackOwnerUuidKey, PersistentDataType.STRING);

        if (backpackDbId == null || ownerUuidString == null) {
            plugin.sendPluginMessage(player, "rename_no_backpack_in_hand");
            return;
        }

        if (!player.getUniqueId().toString().equals(ownerUuidString)) {
            plugin.sendPluginMessage(player, "rename_not_your_backpack");
            return;
        }

        double renameCost = plugin.getRenameCost();
        Economy economy = plugin.getEconomy();
        if (economy != null && renameCost > 0) {
            if (economy.getBalance(player) < renameCost) {
                plugin.sendPluginMessage(player, "rename_not_enough_money", "cost", String.valueOf(renameCost));
                return;
            }
            economy.withdrawPlayer(player, renameCost);
        }

        String newName = String.join(" ", args);
        String coloredName = ChatColor.translateAlternateColorCodes('&', newName);

        plugin.getDatabaseManager().updateBackpackName(backpackDbId, coloredName).thenRun(() -> {
            meta.setDisplayName(coloredName);
            nbt.set(plugin.backpackCustomNameKey, PersistentDataType.STRING, coloredName);
            itemInHand.setItemMeta(meta);
            plugin.sendPluginMessage(player, "rename_success", "name", coloredName);
        });
    }

    @Override
    public List<String> getSubcommandArguments(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}