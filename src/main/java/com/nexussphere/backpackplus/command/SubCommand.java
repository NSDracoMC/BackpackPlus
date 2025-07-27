package com.nexussphere.backpackplus.commands;

import com.nexussphere.backpackplus.CrossServerBackpack;
import org.bukkit.command.CommandSender;
import java.util.List;

public abstract class SubCommand {

    protected final CrossServerBackpack plugin = CrossServerBackpack.getInstance();

    public abstract String getName();
    public abstract String getDescription();
    public abstract String getSyntax();
    public abstract String getPermission();
    public abstract void perform(CommandSender sender, String[] args);
    public abstract List<String> getSubcommandArguments(CommandSender sender, String[] args);
}