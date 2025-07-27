package com.nexussphere.backpackplus.commands.subcommands;

import com.nexussphere.backpackplus.commands.SubCommand;
import org.bukkit.command.CommandSender;
import java.util.Collections;
import java.util.List;

public class ReloadCommand extends SubCommand {
    @Override
    public String getName() { return "reload"; }
    @Override
    public String getDescription() { return "Tải lại cấu hình của plugin."; }
    @Override
    public String getSyntax() { return "/backpack reload"; }
    @Override
    public String getPermission() { return "backpackplus.reload"; }

    @Override
    public void perform(CommandSender sender, String[] args) {
        plugin.reloadAllConfigs();
        plugin.sendPluginMessage(sender, "reload_success");
    }

    @Override
    public List<String> getSubcommandArguments(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}