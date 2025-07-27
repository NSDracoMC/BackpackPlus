package com.nexussphere.backpackplus.listeners;

import com.nexussphere.backpackplus.CrossServerBackpack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Iterator;

public class PlayerDeathListener implements Listener {

    private final CrossServerBackpack plugin;

    public PlayerDeathListener(CrossServerBackpack plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (event.getDrops().isEmpty()) return;

        Iterator<ItemStack> iterator = event.getDrops().iterator();
        while (iterator.hasNext()) {
            ItemStack item = iterator.next();
            if (item != null && item.hasItemMeta()) {
                if (item.getItemMeta().getPersistentDataContainer().has(plugin.backpackDbIdKey, PersistentDataType.INTEGER)) {
                    Integer backpackDbId = item.getItemMeta().getPersistentDataContainer().get(plugin.backpackDbIdKey, PersistentDataType.INTEGER);
                    if (backpackDbId != null) {
                        plugin.getDatabaseManager().markBackpackAsLost(backpackDbId);
                        iterator.remove();
                    }
                }
            }
        }
    }
}