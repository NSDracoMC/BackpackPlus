package com.nexussphere.backpackplus.listeners;

import com.nexussphere.backpackplus.CrossServerBackpack;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class ItemLossListener implements Listener {

    private final CrossServerBackpack plugin;

    public ItemLossListener(CrossServerBackpack plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemDespawn(ItemDespawnEvent event) {
        ItemStack itemStack = event.getEntity().getItemStack();
        if (itemStack != null && itemStack.hasItemMeta()) {
            if (itemStack.getItemMeta().getPersistentDataContainer().has(plugin.backpackDbIdKey, PersistentDataType.INTEGER)) {
                Integer backpackDbId = itemStack.getItemMeta().getPersistentDataContainer().get(plugin.backpackDbIdKey, PersistentDataType.INTEGER);
                if (backpackDbId != null) {
                    plugin.getDatabaseManager().markBackpackAsLost(backpackDbId);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Item)) {
            return;
        }

        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.LAVA || cause == EntityDamageEvent.DamageCause.FIRE || cause == EntityDamageEvent.DamageCause.FIRE_TICK) {
            Item itemEntity = (Item) event.getEntity();
            ItemStack itemStack = itemEntity.getItemStack();

            if (itemStack != null && itemStack.hasItemMeta()) {
                if (itemStack.getItemMeta().getPersistentDataContainer().has(plugin.backpackDbIdKey, PersistentDataType.INTEGER)) {
                    Integer backpackDbId = itemStack.getItemMeta().getPersistentDataContainer().get(plugin.backpackDbIdKey, PersistentDataType.INTEGER);
                    if (backpackDbId != null) {
                        plugin.getDatabaseManager().markBackpackAsLost(backpackDbId);
                        itemEntity.remove();
                        event.setCancelled(true);
                    }
                }
            }
        }
    }
}