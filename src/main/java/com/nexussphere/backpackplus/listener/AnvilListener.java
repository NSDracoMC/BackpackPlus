package com.nexussphere.backpackplus.listeners;

import com.nexussphere.backpackplus.CrossServerBackpack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;

public class AnvilListener implements Listener {

    private final CrossServerBackpack plugin;

    public AnvilListener(CrossServerBackpack plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack firstItem = event.getInventory().getItem(0);
        if (firstItem == null) {
            return;
        }

        if (plugin.getBackpackManager().isBackpackItem(firstItem)) {
            event.setResult(null);
            if (event.getView().getPlayer() instanceof Player) {
                Player player = (Player) event.getView().getPlayer();
                plugin.sendPluginMessage(player, "anvil_rename_blocked");
            }
        }
    }
}