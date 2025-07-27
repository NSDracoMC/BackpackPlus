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
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class WardrobeCommand extends SubCommand {
    @Override
    public String getName() {
        return "wardrobe";
    }

    @Override
    public String getDescription() {
        return "Mở tủ đồ skin ba lô của bạn.";
    }

    @Override
    public String getSyntax() {
        return "/backpack wardrobe";
    }

    @Override
    public String getPermission() {
        return "backpackplus.command.wardrobe";
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

        ItemStack backpackInHand = player.getInventory().getItemInMainHand();
        if (!plugin.getBackpackManager().isBackpackItem(backpackInHand)) {
            player.sendMessage(ChatColor.RED + "Bạn cần cầm ba lô trên tay để quản lý skin!");
            return;
        }

        FileConfiguration guiConfig = plugin.getCustomGuiConfig();
        ConfigurationSection wardrobeConfig = guiConfig.getConfigurationSection("wardrobe_gui");

        if (wardrobeConfig == null) {
            player.sendMessage(ChatColor.RED + "Lỗi: Cấu hình wardrobe_gui không được tìm thấy trong custom_gui.yml");
            return;
        }

        int size = wardrobeConfig.getInt("size", 54);
        String title = ChatColor.translateAlternateColorCodes('&', wardrobeConfig.getString("title", "Tủ Đồ Skin"));
        Inventory wardrobeGui = Bukkit.createInventory(null, size, title);

        plugin.getDatabaseManager().getPlayerSkins(player.getUniqueId()).thenAcceptAsync(ownedSkins -> {

            List<ItemStack> storageItems = new ArrayList<>();

            ItemStack defaultSkinItem = new ItemStack(Material.BARRIER);
            ItemMeta defaultMeta = defaultSkinItem.getItemMeta();
            defaultMeta.setDisplayName(ChatColor.WHITE + "Tháo Skin");
            defaultMeta.setLore(Collections.singletonList(ChatColor.YELLOW + "Nhấn để tháo skin hiện tại."));
            defaultMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "wardrobe_skin_id"), PersistentDataType.STRING, "default");
            defaultSkinItem.setItemMeta(defaultMeta);
            storageItems.add(defaultSkinItem);

            List<CompletableFuture<ItemStack>> futures = new ArrayList<>();

            for (Map.Entry<String, Integer> entry : ownedSkins.entrySet()) {
                String skinId = entry.getKey();
                int ownedCount = entry.getValue();

                CompletableFuture<ItemStack> future = plugin.getDatabaseManager().countSkinInUse(player.getUniqueId(), skinId)
                        .thenApply(inUseCount -> {
                            ConfigurationSection skinConfig = plugin.getBackpackSkins().get(skinId);
                            if (skinConfig == null) return null;

                            int availableCount = ownedCount - inUseCount;

                            ItemStack skinItem = new ItemStack(Material.matchMaterial(skinConfig.getString("material", "PAPER")));
                            ItemMeta meta = skinItem.getItemMeta();
                            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', skinConfig.getString("display_name")));
                            meta.setCustomModelData(skinConfig.getInt("custom_model_data"));

                            List<String> lore = new ArrayList<>();
                            lore.add(" ");
                            lore.add(ChatColor.translateAlternateColorCodes('&', "&fSở hữu: &a" + ownedCount));
                            lore.add(ChatColor.translateAlternateColorCodes('&', "&fĐang dùng: &c" + inUseCount));
                            lore.add(ChatColor.translateAlternateColorCodes('&', "&fCó thể dùng: &e" + availableCount));
                            lore.add(" ");

                            if (availableCount > 0) {
                                lore.add(ChatColor.GREEN + "Nhấn để trang bị.");
                            } else {
                                lore.add(ChatColor.RED + "Bạn đã dùng hết các bản sao của skin này.");
                            }

                            meta.setLore(lore);
                            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "wardrobe_skin_id"), PersistentDataType.STRING, skinId);
                            skinItem.setItemMeta(meta);
                            return skinItem;
                        });
                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
                futures.forEach(f -> {
                    try {
                        ItemStack item = f.get();
                        if (item != null) {
                            storageItems.add(item);
                        }
                    } catch (Exception ignored) {}
                });

                Bukkit.getScheduler().runTask(plugin, () -> {
                    populateGuiFromLayout(wardrobeGui, wardrobeConfig, storageItems);
                    player.openInventory(wardrobeGui);
                });
            });
        });
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