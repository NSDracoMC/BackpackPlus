package com.nexussphere.backpackplus.listeners;

import com.nexussphere.backpackplus.CrossServerBackpack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class ItemDropListener implements Listener {

    private final CrossServerBackpack plugin;

    public ItemDropListener(CrossServerBackpack plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack droppedItem = event.getItemDrop().getItemStack();
        Player player = event.getPlayer();

        if (droppedItem.hasItemMeta() && droppedItem.getItemMeta().getPersistentDataContainer().has(plugin.backpackDbIdKey, PersistentDataType.INTEGER)) {
            event.setCancelled(true);
            plugin.sendPluginMessage(player, "cannot_drop_backpack");
        }
    }
}