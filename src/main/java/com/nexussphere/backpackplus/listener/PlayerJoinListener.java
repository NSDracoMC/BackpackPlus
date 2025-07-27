package com.nexussphere.backpackplus.listeners;

import com.nexussphere.backpackplus.CrossServerBackpack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final CrossServerBackpack plugin;

    public PlayerJoinListener(CrossServerBackpack plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        plugin.getDatabaseManager().getLostBackpackCount(player.getUniqueId()).thenAccept(count -> {
            if (count > 0) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    plugin.sendPluginMessage(player, "lost_backpack_notification", "count", String.valueOf(count));
                }, 100L);
            }
        });
    }
}