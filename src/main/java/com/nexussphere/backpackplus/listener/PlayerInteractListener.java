package com.nexussphere.backpackplus.listeners;

import com.nexussphere.backpackplus.CrossServerBackpack;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class PlayerInteractListener implements Listener {
    private final CrossServerBackpack plugin;

    public PlayerInteractListener(CrossServerBackpack plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        Action action = event.getAction();
        EquipmentSlot hand = event.getHand();

        if (hand != EquipmentSlot.HAND || item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer nbt = meta.getPersistentDataContainer();

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        if (nbt.has(plugin.backpackDbIdKey, PersistentDataType.INTEGER)) {
            event.setCancelled(true);
            if (plugin.getBackpackManager().isPlayerViewingBackpack(player.getUniqueId())) {
                plugin.sendPluginMessage(player, "close_other_backpack_first");
                return;
            }
            Integer backpackDbId = nbt.get(plugin.backpackDbIdKey, PersistentDataType.INTEGER);
            if (backpackDbId == null) return;
            revalidateAndOpenBackpack(player, item, backpackDbId, hand);
        }
    }

    private void revalidateAndOpenBackpack(Player player, ItemStack item, int dbId, EquipmentSlot hand) {
        String itemUuid = item.getItemMeta().getPersistentDataContainer().get(plugin.backpackInstanceKey, PersistentDataType.STRING);
        plugin.getDatabaseManager().getItemInstanceUUID(dbId).thenAcceptAsync(dbUuid -> {
            if (dbUuid == null || !dbUuid.equals(itemUuid)) {
                player.getInventory().setItem(hand, null);
                plugin.sendPluginMessage(player, "backpack_outdated_copy");
                return;
            }
            plugin.getBackpackManager().openBackpackByDbId(player, dbId, player.getName());
        });
    }
}