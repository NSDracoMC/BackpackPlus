package com.nexussphere.backpackplus.commands.subcommands;

import com.nexussphere.backpackplus.commands.SubCommand;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SkinsCommand extends SubCommand {
    @Override
    public String getName() {
        return "skins";
    }

    @Override
    public String getDescription() {
        return "Mở cửa hàng skin ba lô.";
    }

    @Override
    public String getSyntax() {
        return "/backpack skins";
    }

    @Override
    public String getPermission() {
        return "backpackplus.command.skins";
    }

    @Override
    public void perform(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Lệnh này chỉ dành cho người chơi.");
            return;
        }

        if (!player.hasPermission(getPermission())) {
            plugin.sendPluginMessage(player, "no_permission");
            return;
        }

        FileConfiguration guiConfig = plugin.getCustomGuiConfig();
        ConfigurationSection shopConfig = guiConfig.getConfigurationSection("skin_shop_gui");

        if (shopConfig == null) {
            player.sendMessage(ChatColor.RED + "Lỗi: Cấu hình skin_shop_gui không được tìm thấy trong custom_gui.yml");
            return;
        }

        int size = shopConfig.getInt("size", 54);
        String title = ChatColor.translateAlternateColorCodes('&', shopConfig.getString("title", "Cửa Hàng Skin"));
        Inventory skinsGui = Bukkit.createInventory(null, size, title);

        List<ItemStack> skinItems = new ArrayList<>();
        for (Map.Entry<String, ConfigurationSection> entry : plugin.getBackpackSkins().entrySet()) {
            String skinId = entry.getKey();
            ConfigurationSection skinConfig = entry.getValue();

            ItemStack skinItem = new ItemStack(Material.matchMaterial(skinConfig.getString("material", "PAPER")));
            ItemMeta meta = skinItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', skinConfig.getString("display_name")));
                meta.setCustomModelData(skinConfig.getInt("custom_model_data"));

                List<String> lore = new ArrayList<>();
                lore.add(" ");
                lore.add(ChatColor.translateAlternateColorCodes('&', "&fGiá: &e" + skinConfig.getInt("price") + " điểm"));
                lore.add(" ");
                lore.add(ChatColor.GREEN + "Nhấn để mua!");
                meta.setLore(lore);

                meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "shop_skin_id"), PersistentDataType.STRING, skinId);
                skinItem.setItemMeta(meta);
            }
            skinItems.add(skinItem);
        }

        populateGuiFromLayout(skinsGui, shopConfig, skinItems);
        player.openInventory(skinsGui);
    }

    private void populateGuiFromLayout(Inventory gui, ConfigurationSection guiConfig, List<ItemStack> storageItems) {
        ConfigurationSection layoutSection = guiConfig.getConfigurationSection("layout");
        if (layoutSection == null) return;

        int storageItemIndex = 0;

        for (int i = 0; i < gui.getSize(); i++) {
            ConfigurationSection slotConfig = findSlotConfig(layoutSection, i);
            if (slotConfig != null) {
                String type = slotConfig.getString("type");
                if ("storage".equals(type)) {
                    if (storageItemIndex < storageItems.size()) {
                        gui.setItem(i, storageItems.get(storageItemIndex));
                        storageItemIndex++;
                    }
                } else if ("control_item".equals(type)) {
                    String itemKey = slotConfig.getString("item_key");
                    gui.setItem(i, createControlItem(guiConfig, itemKey));
                }
            }
        }
    }

    private ConfigurationSection findSlotConfig(ConfigurationSection layoutSection, int slot) {
        for (String key : layoutSection.getKeys(false)) {
            String[] parts = key.split("-");
            try {
                int start = Integer.parseInt(parts[0]);
                int end = (parts.length > 1) ? Integer.parseInt(parts[1]) : start;
                if (slot >= start && slot <= end) {
                    return layoutSection.getConfigurationSection(key);
                }
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private ItemStack createControlItem(ConfigurationSection guiConfig, String itemKey) {
        ConfigurationSection itemDef = guiConfig.getConfigurationSection("control_items_definition." + itemKey);
        if (itemDef == null) {
            return new ItemStack(Material.AIR);
        }

        Material material = Material.matchMaterial(itemDef.getString("material", "STONE"));
        if (material == null) material = Material.STONE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            String name = itemDef.getString("name", " ");
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            List<String> lore = itemDef.getStringList("lore").stream()
                    .map(l -> ChatColor.translateAlternateColorCodes('&', l))
                    .collect(Collectors.toList());
            if (!lore.isEmpty()) meta.setLore(lore);
            int cmd = itemDef.getInt("custom_model_data", 0);
            if (cmd > 0) meta.setCustomModelData(cmd);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public List<String> getSubcommandArguments(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}