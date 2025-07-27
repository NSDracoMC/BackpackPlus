package com.nexussphere.backpackplus.listeners;

import com.nexussphere.backpackplus.CrossServerBackpack;
import com.nexussphere.backpackplus.manager.BackpackManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class PreventStackingListener implements Listener {

    private final CrossServerBackpack plugin;
    private final BackpackManager backpackManager;

    public PreventStackingListener(CrossServerBackpack plugin) {
        this.plugin = plugin;
        this.backpackManager = plugin.getBackpackManager();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        ItemStack cursorItem = event.getCursor();
        ItemStack currentItem = event.getCurrentItem();

        boolean isCursorBackpack = backpackManager.isBackpackItem(cursorItem);
        boolean isCurrentBackpack = backpackManager.isBackpackItem(currentItem);

        if (isCursorBackpack && isCurrentBackpack) {
            event.setCancelled(true);
            return;
        }

        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR && isCurrentBackpack) {
            event.setCancelled(true);
            return;
        }

        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && isCurrentBackpack) {
            event.setCancelled(true);
            plugin.sendPluginMessage((Player) event.getWhoClicked(), "cannot_shift_click_backpack");
        }
    }
}