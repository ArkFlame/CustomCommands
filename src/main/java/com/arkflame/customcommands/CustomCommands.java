package com.arkflame.customcommands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public class CustomCommands extends JavaPlugin {
    private boolean placeholderAPIEnabled = false;
    private Map<String, CustomCommand> commands = new HashMap<>();

    @Override
    public void onEnable() {
        // Create config if it doesn't exist
        saveDefaultConfig();
        
        // Check for PlaceholderAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderAPIEnabled = true;
            getLogger().info("PlaceholderAPI found! Placeholder support enabled.");
        } else {
            getLogger().info("PlaceholderAPI not found. Placeholders will not work.");
        }

        // Load commands from config
        loadCommands();
        
        // Register commands dynamically
        registerCommands();
        
        // Display startup message
        getLogger().info(ChatColor.GREEN + "CustomCommands has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info(ChatColor.RED + "CustomCommands has been disabled!");
    }
    
    public void loadCommands() {
        // Clear existing commands
        commands.clear();
        
        // Make sure config section exists
        if (!getConfig().isConfigurationSection("commands")) {
            getLogger().warning("No commands section found in config.yml, creating default commands.");
            createDefaultCommands();
            saveConfig();
        }
        
        // Get all commands from config
        Set<String> commandNames = getConfig().getConfigurationSection("commands").getKeys(false);
        
        for (String cmdName : commandNames) {
            String permission = getConfig().getString("commands." + cmdName + ".permission", "");
            String permissionMessage = getConfig().getString("commands." + cmdName + ".permission-message", 
                    "&cYou don't have permission to use this command.");
            List<String> messages = getConfig().getStringList("commands." + cmdName + ".messages");
            List<String> consoleMessages = getConfig().getStringList("commands." + cmdName + ".console-messages");
            List<String> playerCommands = getConfig().getStringList("commands." + cmdName + ".player-commands");
            List<String> consoleCommands = getConfig().getStringList("commands." + cmdName + ".console-commands");
            boolean usePermission = getConfig().getBoolean("commands." + cmdName + ".use-permission", false);
            
            // If no console-specific messages, use regular messages
            if (consoleMessages.isEmpty()) {
                consoleMessages = new ArrayList<>(messages);
            }
            
            CustomCommand cmd = new CustomCommand(
                cmdName,
                permission,
                permissionMessage,
                messages,
                consoleMessages,
                playerCommands,
                consoleCommands,
                usePermission
            );
            
            commands.put(cmdName, cmd);
        }
        
        getLogger().info("Loaded " + commands.size() + " custom commands.");
    }
    
    private void createDefaultCommands() {
        // Create the commands section
        if (!getConfig().isConfigurationSection("commands")) {
            getConfig().createSection("commands");
        }
        
        // Store command
        getConfig().set("commands.store.messages", 
                List.of("&8&l»&r &6Visit our store at: &e&nhttps://www.arkflame.com/store"));
        getConfig().set("commands.store.use-permission", false);
        getConfig().set("commands.store.permission", "customcommands.store");
        getConfig().set("commands.store.permission-message", "&cYou don't have permission to use this command.");
        getConfig().set("commands.store.player-commands", new ArrayList<>());
        getConfig().set("commands.store.console-commands", new ArrayList<>());
        
        // Map command
        getConfig().set("commands.map.messages", 
                List.of("&8&l»&r &6View our server map at: &e&nhttps://www.arkflame.com/map"));
        getConfig().set("commands.map.use-permission", false);
        getConfig().set("commands.map.permission", "customcommands.map");
        getConfig().set("commands.map.permission-message", "&cYou don't have permission to use this command.");
        getConfig().set("commands.map.player-commands", new ArrayList<>());
        getConfig().set("commands.map.console-commands", new ArrayList<>());
        
        // Discord command
        getConfig().set("commands.discord.messages", 
                List.of("&8&l»&r &6Join our Discord server at: &e&nhttps://discord.arkflame.com"));
        getConfig().set("commands.discord.use-permission", false);
        getConfig().set("commands.discord.permission", "customcommands.discord");
        getConfig().set("commands.discord.permission-message", "&cYou don't have permission to use this command.");
        getConfig().set("commands.discord.player-commands", new ArrayList<>());
        getConfig().set("commands.discord.console-commands", new ArrayList<>());
    }
    
    private void registerCommands() {
        for (Map.Entry<String, CustomCommand> entry : commands.entrySet()) {
            String cmdName = entry.getKey();
            CustomCommand customCommand = entry.getValue();
            
            // Register command with Bukkit
            try {
                getCommand(cmdName).setExecutor(new CommandHandler(customCommand));
            } catch (Exception e) {
                getLogger().warning("Failed to register command /" + cmdName + ". Make sure it's in plugin.yml!");
            }
        }
    }
    
    private class CommandHandler implements CommandExecutor {
        private final CustomCommand customCommand;
        
        public CommandHandler(CustomCommand customCommand) {
            this.customCommand = customCommand;
        }
        
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            // Check permission if required
            if (customCommand.isUsePermission() && !sender.hasPermission(customCommand.getPermission())) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', customCommand.getPermissionMessage()));
                return true;
            }
            
            // Handle messages differently based on sender type
            if (sender instanceof Player) {
                Player player = (Player) sender;
                
                // Send messages to player
                for (String message : customCommand.getMessages()) {
                    String processed = processPlaceholders(message, player);
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', processed));
                }
                
                // Execute player commands
                for (String command : customCommand.getPlayerCommands()) {
                    String processed = processPlaceholders(command, player);
                    Bukkit.dispatchCommand(player, processed);
                }
                
                // Execute console commands
                for (String command : customCommand.getConsoleCommands()) {
                    String processed = processPlaceholders(command, player);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processed);
                }
            } else {
                // Sender is console or other non-player
                for (String message : customCommand.getConsoleMessages()) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                }
            }
            
            return true;
        }
    }
    
    private String processPlaceholders(String text, Player player) {
        String processed = text;
        
        // Replace built-in placeholders
        processed = processed.replace("%player%", player.getName());
        processed = processed.replace("%displayname%", player.getDisplayName());
        processed = processed.replace("%world%", player.getWorld().getName());
        processed = processed.replace("%server%", "mc.arkflame.com");
        processed = processed.replace("%website%", "www.arkflame.com");
        
        // Use PlaceholderAPI if available
        if (placeholderAPIEnabled) {
            try {
                processed = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, processed);
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Error processing PlaceholderAPI placeholders", e);
            }
        }
        
        return processed;
    }
    
    // Command configuration class
    private static class CustomCommand {
        private final String name;
        private final String permission;
        private final String permissionMessage;
        private final List<String> messages;
        private final List<String> consoleMessages;
        private final List<String> playerCommands;
        private final List<String> consoleCommands;
        private final boolean usePermission;
        
        public CustomCommand(String name, String permission, String permissionMessage, 
                             List<String> messages, List<String> consoleMessages,
                             List<String> playerCommands, List<String> consoleCommands, 
                             boolean usePermission) {
            this.name = name;
            this.permission = permission;
            this.permissionMessage = permissionMessage;
            this.messages = messages;
            this.consoleMessages = consoleMessages;
            this.playerCommands = playerCommands;
            this.consoleCommands = consoleCommands;
            this.usePermission = usePermission;
        }
        
        public String getName() {
            return name;
        }
        
        public String getPermission() {
            return permission;
        }
        
        public String getPermissionMessage() {
            return permissionMessage;
        }
        
        public List<String> getMessages() {
            return messages;
        }
        
        public List<String> getConsoleMessages() {
            return consoleMessages;
        }
        
        public List<String> getPlayerCommands() {
            return playerCommands;
        }
        
        public List<String> getConsoleCommands() {
            return consoleCommands;
        }
        
        public boolean isUsePermission() {
            return usePermission;
        }
    }
}