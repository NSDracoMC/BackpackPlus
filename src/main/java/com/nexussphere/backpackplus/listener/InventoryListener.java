package com.nexussphere.backpackplus.listeners;

import com.nexussphere.backpackplus.CrossServerBackpack;
import com.nexussphere.backpackplus.manager.BackpackManager;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class InventoryListener implements Listener {
    private final CrossServerBackpack plugin;
    private final NamespacedKey bpControlActionKey;

    public InventoryListener(CrossServerBackpack plugin) {
        this.plugin = plugin;
        this.bpControlActionKey = new NamespacedKey(plugin, "bp_control_action");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        plugin.getBackpackManager().removeViewingPlayer(player.getUniqueId());
        BackpackManager.TrackedBackpackInstance trackedInst = plugin.getBackpackManager().getTrackedInstanceByInventory(event.getInventory());
        if (trackedInst != null) {
            plugin.getBackpackManager().initiateSaveAndInvalidate(player, trackedInst);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String viewTitle = event.getView().getTitle();
        FileConfiguration guiConfig = plugin.getCustomGuiConfig();

        String shopTitle = ChatColor.translateAlternateColorCodes('&', guiConfig.getString("skin_shop_gui.title", "Cửa Hàng Skin Ba Lô"));
        String wardrobeTitle = ChatColor.translateAlternateColorCodes('&', guiConfig.getString("wardrobe_gui.title", "Tủ Đồ Skin"));

        if (viewTitle.equals(shopTitle) || viewTitle.equals(wardrobeTitle) || viewTitle.equals(ChatColor.translateAlternateColorCodes('&', "&8Đang tải dữ liệu ba lô..."))) {
            event.setCancelled(true);
            if (viewTitle.equals(shopTitle)) onSkinShopClick(event);
            if (viewTitle.equals(wardrobeTitle)) onWardrobeClick(event);
            return;
        }

        BackpackManager bpManager = plugin.getBackpackManager();
        BackpackManager.TrackedBackpackInstance openBpInst = bpManager.getTrackedInstanceByInventory(event.getView().getTopInventory());
        if (openBpInst == null) return;

        if (event.getClickedInventory() != null && event.getClickedInventory().equals(event.getView().getTopInventory())) {
            if (!bpManager.isStorageSlot(event.getSlot())) {
                event.setCancelled(true);
                if (bpManager.isControlItem(event.getCurrentItem())) handleControlItemClick(player, event.getCurrentItem(), openBpInst);
                return;
            }
        }

        if (isAttemptingToPlaceForbiddenItem(event)) {
            event.setCancelled(true);
            plugin.sendPluginMessage(player, "cannot_store_blacklisted_item");
            player.updateInventory();
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.getOpenInventory().getTopInventory().equals(openBpInst.currentGuiInventory)) {
                bpManager.syncGuiToInternalMap(openBpInst);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        BackpackManager bpManager = plugin.getBackpackManager();
        BackpackManager.TrackedBackpackInstance openBpInfo = bpManager.getTrackedInstanceByInventory(event.getView().getTopInventory());
        if (openBpInfo == null) return;

        if (isForbiddenItem(event.getOldCursor())) {
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < event.getView().getTopInventory().getSize() && bpManager.isStorageSlot(rawSlot)) {
                    event.setResult(Event.Result.DENY);
                    plugin.sendPluginMessage(player, "cannot_store_blacklisted_item");
                    return;
                }
            }
        }
        if (event.getRawSlots().stream().anyMatch(s -> s < event.getView().getTopInventory().getSize() && !bpManager.isStorageSlot(s))) {
            event.setResult(Event.Result.DENY);
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> bpManager.syncGuiToInternalMap(openBpInfo));
    }

    private boolean isForbiddenItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        return plugin.getBackpackManager().isBackpackItem(item) || plugin.getBlacklistedMaterials().contains(item.getType());
    }

    private boolean isAttemptingToPlaceForbiddenItem(InventoryClickEvent event) {
        InventoryAction action = event.getAction();
        if (event.getRawSlot() < event.getView().getTopInventory().getSize()) {
            ItemStack cursorItem = event.getCursor();
            if (action == InventoryAction.PLACE_ALL || action == InventoryAction.PLACE_ONE || action == InventoryAction.PLACE_SOME || action == InventoryAction.SWAP_WITH_CURSOR) {
                return isForbiddenItem(cursorItem);
            }
            if (action == InventoryAction.HOTBAR_SWAP) {
                return isForbiddenItem(event.getWhoClicked().getInventory().getItem(event.getHotbarButton()));
            }
        } else {
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                return isForbiddenItem(event.getCurrentItem());
            }
        }
        return false;
    }

    private void handleControlItemClick(Player p, ItemStack item, BackpackManager.TrackedBackpackInstance instance) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        String action = meta.getPersistentDataContainer().get(bpControlActionKey, PersistentDataType.STRING);
        if (action == null) return;

        plugin.getBackpackManager().syncGuiToInternalMap(instance);
        switch (action) {
            case "prev_page" -> plugin.getBackpackManager().switchPage(p, instance.backpackDbId, false);
            case "next_page" -> plugin.getBackpackManager().switchPage(p, instance.backpackDbId, true);
            case "sort" -> {
                plugin.getBackpackManager().sortBackpack(instance);
                plugin.sendPluginMessage(p, "backpack_sorted");
            }
            case "quick_deposit" -> plugin.getBackpackManager().quickDeposit(p, instance);
            case "take_all_from_page" -> plugin.getBackpackManager().takeAllFromPage(p, instance);
        }
    }

    public void onSkinShopClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType().isAir() || clickedItem.getItemMeta() == null) return;
        String skinId = clickedItem.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "shop_skin_id"), PersistentDataType.STRING);
        if (skinId == null) return;

        ConfigurationSection skinConfig = plugin.getBackpackSkins().get(skinId);
        int price = skinConfig.getInt("price");
        PlayerPointsAPI ppAPI = plugin.getPlayerPointsAPI();
        if (ppAPI.look(player.getUniqueId()) < price) {
            player.sendMessage(ChatColor.RED + "Bạn không đủ điểm!");
            return;
        }
        ppAPI.take(player.getUniqueId(), price);
        plugin.getDatabaseManager().addPlayerSkin(player.getUniqueId(), skinId).thenRun(() -> {
            player.sendMessage(ChatColor.GREEN + "Bạn đã mua thành công 1 bản sao của skin " + ChatColor.translateAlternateColorCodes('&', skinConfig.getString("display_name")) + "!");
            player.sendMessage(ChatColor.GREEN + "Sử dụng lệnh /backpack wardrobe để trang bị.");
            player.closeInventory();
        });
    }

    public void onWardrobeClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType().isAir() || clickedItem.getItemMeta() == null) return;

        String skinId = clickedItem.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "wardrobe_skin_id"), PersistentDataType.STRING);
        if (skinId == null) return;

        ItemStack backpackInHand = player.getInventory().getItemInMainHand();
        if (!plugin.getBackpackManager().isBackpackItem(backpackInHand)) {
            player.sendMessage(ChatColor.RED + "Bạn cần cầm ba lô trên tay!");
            player.closeInventory();
            return;
        }

        ItemMeta backpackMeta = backpackInHand.getItemMeta();
        if (backpackMeta == null) return;

        Integer backpackDbId = backpackMeta.getPersistentDataContainer().get(plugin.backpackDbIdKey, PersistentDataType.INTEGER);
        if (backpackDbId == null) return;

        equipSkin(player, backpackInHand, backpackDbId, skinId);
    }

    private void equipSkin(Player player, ItemStack backpackItem, int backpackDbId, String newSkinId) {
        ItemMeta meta = backpackItem.getItemMeta();
        if (meta == null) return;

        String oldSkinId = meta.getPersistentDataContainer().get(plugin.backpackSkinIdKey, PersistentDataType.STRING);
        if (newSkinId.equals(oldSkinId)) {
            player.sendMessage(ChatColor.YELLOW + "Ba lô này đã được trang bị skin này rồi.");
            player.closeInventory();
            return;
        }

        if (!newSkinId.equals("default")) {
            plugin.getDatabaseManager().getPlayerSkins(player.getUniqueId()).thenCombineAsync(
                    plugin.getDatabaseManager().countSkinInUse(player.getUniqueId(), newSkinId),
                    (ownedSkins, inUseCount) -> {
                        int ownedCount = ownedSkins.getOrDefault(newSkinId, 0);
                        int availableCount = ownedCount - inUseCount;
                        if (availableCount > 0) {
                            return true;
                        } else {
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                player.sendMessage(ChatColor.RED + "Bạn không có bản sao nào của skin này để trang bị.");
                                player.closeInventory();
                            });
                            return false;
                        }
                    }
            ).thenAccept(canEquip -> {
                if (canEquip) {
                    performSkinUpdate(player, backpackItem, backpackDbId, newSkinId);
                }
            });
        } else {
            performSkinUpdate(player, backpackItem, backpackDbId, "default");
        }
    }

    private void performSkinUpdate(Player player, ItemStack backpackItem, int backpackDbId, String skinId) {
        String finalSkinId = skinId.equals("default") ? null : skinId;

        plugin.getDatabaseManager().updateBackpackSkin(backpackDbId, finalSkinId).thenRun(() -> {
            ItemMeta meta = backpackItem.getItemMeta();
            if (meta != null) {
                ConfigurationSection skinConfig = plugin.getBackpackSkins().get(skinId);
                if (skinConfig != null) {
                    Material material = Material.matchMaterial(skinConfig.getString("material", "PAPER").toUpperCase());
                    if (material != null) backpackItem.setType(material);
                    meta.setCustomModelData(skinConfig.getInt("custom_model_data"));
                } else {
                    ConfigurationSection defaultConfig = plugin.getBackpackSkins().get("default");
                    if(defaultConfig != null) {
                        Material material = Material.matchMaterial(defaultConfig.getString("material", "PAPER").toUpperCase());
                        if (material != null) backpackItem.setType(material);
                        meta.setCustomModelData(defaultConfig.getInt("custom_model_data"));
                    }
                }
                meta.getPersistentDataContainer().set(plugin.backpackSkinIdKey, PersistentDataType.STRING, skinId);
                backpackItem.setItemMeta(meta);

                int size = meta.getPersistentDataContainer().getOrDefault(plugin.backpackSizeKey, PersistentDataType.INTEGER, 0);
                int level = meta.getPersistentDataContainer().getOrDefault(plugin.backpackUpgradeLevelKey, PersistentDataType.INTEGER, 0);
                plugin.updateLoreWithUsedSlots(backpackItem, player, backpackDbId, size, level);

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ChatColor.GREEN + "Đã cập nhật skin thành công!");
                    player.closeInventory();
                });
            }
        });
    }
}