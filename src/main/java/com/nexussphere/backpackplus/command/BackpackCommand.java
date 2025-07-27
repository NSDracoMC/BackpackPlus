package com.nexussphere.backpackplus.commands;

import com.nexussphere.backpackplus.CrossServerBackpack;
import com.nexussphere.backpackplus.commands.subcommands.*;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BackpackCommand implements CommandExecutor, TabCompleter {

    private final CrossServerBackpack plugin;
    private final List<SubCommand> subCommands = new ArrayList<>();

    public BackpackCommand(CrossServerBackpack plugin) {
        this.plugin = plugin;
        subCommands.add(new CreateCommand());
        subCommands.add(new OpenCommand());
        subCommands.add(new UpgradeCommand());
        subCommands.add(new ReloadCommand());
        subCommands.add(new RecoverCommand());
        subCommands.add(new TrustCommand());
        subCommands.add(new UntrustCommand());
        subCommands.add(new TrustListCommand());
        subCommands.add(new ForceLostCommand());
        subCommands.add(new GiveCommand());
        subCommands.add(new DeleteCommand());
        subCommands.add(new TransferCommand());
        subCommands.add(new BuyCommand());
        subCommands.add(new BuyUpCommand());
        subCommands.add(new SkinsCommand());
        subCommands.add(new WardrobeCommand());
        subCommands.add(new RenameCommand());
        subCommands.add(new RevokeCommand());
        subCommands.add(new RefundSkinCommand());
    }

    public List<SubCommand> getSubCommands() {
        return subCommands;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        String subCommandName = args[0].toLowerCase();

        for (SubCommand subCommand : subCommands) {
            if (subCommand.getName().equalsIgnoreCase(subCommandName)) {
                if (sender.hasPermission(subCommand.getPermission())) {
                    subCommand.perform(sender, Arrays.copyOfRange(args, 1, args.length));
                } else {
                    plugin.sendPluginMessage(sender, "no_permission");
                }
                return true;
            }
        }

        sendHelpMessage(sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = subCommands.stream()
                    .filter(sub -> sender.hasPermission(sub.getPermission()))
                    .map(SubCommand::getName)
                    .collect(Collectors.toList());
            return StringUtil.copyPartialMatches(args[0], completions, new ArrayList<>());
        }

        if (args.length > 1) {
            String subCommandName = args[0].toLowerCase();
            for (SubCommand subCommand : subCommands) {
                if (subCommand.getName().equalsIgnoreCase(subCommandName)) {
                    if (sender.hasPermission(subCommand.getPermission())) {
                        return subCommand.getSubcommandArguments(sender, Arrays.copyOfRange(args, 1, args.length));
                    }
                }
            }
        }
        return new ArrayList<>();
    }

    private void sendHelpMessage(CommandSender sender) {
        String prefix = plugin.getFormattedMessage("prefix");
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + "&eCác lệnh có sẵn:"));
        for (SubCommand subCommand : subCommands) {
            if (sender.hasPermission(subCommand.getPermission())) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e" + subCommand.getSyntax() + " &7- " + subCommand.getDescription()));
            }
        }
    }
}