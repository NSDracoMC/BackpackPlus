package com.nexussphere.backpackplus.listeners;

import com.nexussphere.backpackplus.CrossServerBackpack;
import org.bukkit.event.Listener;

@SuppressWarnings("deprecation")
public class PlayerChatListener implements Listener {

    private final CrossServerBackpack plugin;

    public PlayerChatListener(CrossServerBackpack plugin) {
        this.plugin = plugin;
    }
}