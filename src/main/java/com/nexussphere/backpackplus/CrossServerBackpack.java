package com.nexussphere.backpackplus;

import com.nexussphere.backpackplus.commands.BackpackCommand;
import com.nexussphere.backpackplus.database.DatabaseManager;
import com.nexussphere.backpackplus.listeners.*;
import com.nexussphere.backpackplus.manager.BackpackManager;
import net.milkbowl.vault.economy.Economy;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;

public final class CrossServerBackpack extends JavaPlugin {
    private static CrossServerBackpack instance;
    private DatabaseManager databaseManager;
    private BackpackManager backpackManager;

    private int maxPages;
    private int itemsPerPage;
    private int maxUpgrades;
    private double recoverCost;
    private int buyCost;
    private double buyupCost;
    private double renameCost;

    public NamespacedKey backpackDbIdKey;
    public NamespacedKey backpackOwnerUuidKey;
    public NamespacedKey backpackSizeKey;
    public NamespacedKey backpackUpgradeLevelKey;
    public NamespacedKey backpackInstanceKey;
    public NamespacedKey backpackSkinIdKey;
    public NamespacedKey backpackCustomNameKey;

    private FileConfiguration messagesConfig;
    private FileConfiguration customGuiConfig;
    private FileConfiguration skinsConfig;

    private Map<String, ConfigurationSection> backpackSkins = new HashMap<>();
    private Set<Material> blacklistedMaterials = new HashSet<>();

    private final List<EnchantmentTier> enchantmentTiers = new ArrayList<>();

    private Economy economy = null;
    private PlayerPointsAPI playerPointsAPI = null;

    public static class EnchantmentTier {
        private final String unicode;
        private final int priority;

        public EnchantmentTier(String unicode, int priority) {
            this.unicode = unicode;
            this.priority = priority;
        }

        public String getUnicode() { return unicode; }
        public int getPriority() { return priority; }
    }

    @Override
    public void onEnable() {
        instance = this;
        this.backpackDbIdKey = new NamespacedKey(this, "backpack_db_id");
        this.backpackOwnerUuidKey = new NamespacedKey(this, "backpack_owner_uuid");
        this.backpackSizeKey = new NamespacedKey(this, "backpack_size");
        this.backpackUpgradeLevelKey = new NamespacedKey(this, "backpack_upgrade_level");
        this.backpackInstanceKey = new NamespacedKey(this, "backpack_instance_uuid");
        this.backpackSkinIdKey = new NamespacedKey(this, "backpack_skin_id");
        this.backpackCustomNameKey = new NamespacedKey(this, "backpack_custom_name");

        saveDefaultConfig();
        reloadAllConfigs();

        if (!setupEconomy()) {
            getLogger().warning("Không tìm thấy Vault hoặc plugin kinh tế tương thích! Các tính năng thu phí bằng tiền sẽ bị vô hiệu hóa.");
        }
        if (!setupPlayerPoints()) {
            getLogger().warning("Không tìm thấy PlayerPoints! Các tính năng thu phí bằng điểm sẽ bị vô hiệu hóa.");
        }

        try {
            databaseManager = new DatabaseManager(this);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Lỗi DatabaseManager!", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        backpackManager = new BackpackManager(this);

        PluginCommand mainCommand = getCommand("backpack");
        if (mainCommand != null) {
            BackpackCommand backpackExecutor = new BackpackCommand(this);
            mainCommand.setExecutor(backpackExecutor);
            mainCommand.setTabCompleter(backpackExecutor);
        }

        getServer().getPluginManager().registerEvents(new InventoryListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerChatListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new ItemLossListener(this), this);
        getServer().getPluginManager().registerEvents(new ItemDropListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new AnvilListener(this), this);
        getServer().getPluginManager().registerEvents(new PreventStackingListener(this), this);

        getLogger().info(this.getDescription().getName() + " đã kích hoạt!");
    }

    @Override
    public void onDisable() {
        getLogger().info(this.getDescription().getName() + " đang tắt...");
        if (backpackManager != null) {
            backpackManager.handleServerShutdown();
        }
        if (databaseManager != null) {
            databaseManager.closeDataSource();
        }
        getLogger().info(this.getDescription().getName() + " đã tắt.");
    }

    public ItemStack createBackpackItemStack(OfflinePlayer owner, int dbId, int size, int level, String instanceUuid, String skinId, String customName) {
        String appliedSkinId = (skinId != null && backpackSkins.containsKey(skinId)) ? skinId : "default";
        ConfigurationSection skinConfig = backpackSkins.get(appliedSkinId);

        String materialName = skinConfig.getString("material", "PAPER").toUpperCase();
        Material itemMaterial = Material.matchMaterial(materialName);
        if (itemMaterial == null) itemMaterial = Material.PAPER;

        ItemStack bpItem = new ItemStack(itemMaterial);
        ItemMeta meta = bpItem.getItemMeta();

        if (meta != null) {
            int numPages = (int) Math.ceil((double) size / itemsPerPage);
            if (customName != null && !customName.isEmpty()) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', customName));
            } else {
                String displayNameFormat = getConfig().getString("backpack.item.name_format", "&d&lBalo Cá Nhân ({num_pages} trang)");
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayNameFormat.replace("{num_pages}", String.valueOf(numPages))));
            }

            List<String> loreFormat = getConfig().getStringList("backpack.item.lore_format");
            List<String> lore = new ArrayList<>();
            for (String line : loreFormat) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line
                        .replace("{owner}", owner.getName() != null ? owner.getName() : "Không rõ")
                        .replace("{num_pages}", String.valueOf(numPages))
                        .replace("{total_slots}", String.valueOf(size))
                        .replace("{used_slots}", "0")
                        .replace("{backpack_db_id}", String.valueOf(dbId))
                        .replace("{upgrade_level}", String.valueOf(level))
                        .replace("{max_upgrades}", String.valueOf(maxUpgrades))
                ));
            }
            meta.setLore(lore);

            int customModelData = skinConfig.getInt("custom_model_data", 0);
            if (customModelData > 0) meta.setCustomModelData(customModelData);

            PersistentDataContainer nbt = meta.getPersistentDataContainer();
            nbt.set(backpackDbIdKey, PersistentDataType.INTEGER, dbId);
            nbt.set(backpackOwnerUuidKey, PersistentDataType.STRING, owner.getUniqueId().toString());
            nbt.set(backpackSizeKey, PersistentDataType.INTEGER, size);
            nbt.set(backpackUpgradeLevelKey, PersistentDataType.INTEGER, level);
            nbt.set(backpackInstanceKey, PersistentDataType.STRING, instanceUuid);
            nbt.set(backpackSkinIdKey, PersistentDataType.STRING, appliedSkinId);
            if (customName != null) {
                nbt.set(backpackCustomNameKey, PersistentDataType.STRING, customName);
            }
            bpItem.setItemMeta(meta);
        }
        return bpItem;
    }

    public void updateBackpackItem(ItemStack backpackItem, Player owner, int dbId, int newSize, int newLevel) {
        if (backpackItem == null || backpackItem.getType() == Material.AIR) return;
        ItemMeta meta = backpackItem.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer nbt = meta.getPersistentDataContainer();
        nbt.set(backpackSizeKey, PersistentDataType.INTEGER, newSize);
        nbt.set(backpackUpgradeLevelKey, PersistentDataType.INTEGER, newLevel);

        String skinId = nbt.get(backpackSkinIdKey, PersistentDataType.STRING);
        ConfigurationSection skinConfig = backpackSkins.get(skinId != null ? skinId : "default");
        if (skinConfig != null) {
            int customModelData = skinConfig.getInt("custom_model_data", 0);
            if (customModelData > 0) meta.setCustomModelData(customModelData);
        }

        String customName = nbt.get(backpackCustomNameKey, PersistentDataType.STRING);
        if (customName == null || customName.isEmpty()) {
            int numPages = (int) Math.ceil((double) newSize / itemsPerPage);
            String displayNameFormat = getConfig().getString("backpack.item.name_format", "&d&lBalo Cá Nhân ({num_pages} trang)");
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayNameFormat.replace("{num_pages}", String.valueOf(numPages))));
        }

        backpackItem.setItemMeta(meta);

        updateLoreWithUsedSlots(backpackItem, owner, dbId, newSize, newLevel);
    }

    public void updateBackpackItemOwner(ItemStack backpackItem, OfflinePlayer newOwner) {
        if (backpackItem == null || backpackItem.getType() == Material.AIR || !backpackItem.hasItemMeta()) return;
        ItemMeta meta = backpackItem.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer nbt = meta.getPersistentDataContainer();
        nbt.set(backpackOwnerUuidKey, PersistentDataType.STRING, newOwner.getUniqueId().toString());

        int dbId = nbt.getOrDefault(backpackDbIdKey, PersistentDataType.INTEGER, 0);
        int size = nbt.getOrDefault(backpackSizeKey, PersistentDataType.INTEGER, 0);
        int level = nbt.getOrDefault(backpackUpgradeLevelKey, PersistentDataType.INTEGER, 0);

        backpackItem.setItemMeta(meta);

        updateLoreWithUsedSlots(backpackItem, newOwner, dbId, size, level);
    }

    public void updateLoreWithUsedSlots(ItemStack backpackItem, OfflinePlayer owner, int dbId, int size, int level) {
        if (backpackItem == null || !backpackItem.hasItemMeta()) return;

        updateLoreSynchronously(backpackItem, owner, dbId, size, level, "?");

        getDatabaseManager().getBackpackContents(dbId).thenAcceptAsync(items -> {
            long usedSlots = items.values().stream().filter(item -> item != null && item.getType() != Material.AIR).count();
            getServer().getScheduler().runTask(this, () -> {
                updateLoreSynchronously(backpackItem, owner, dbId, size, level, String.valueOf(usedSlots));
            });
        });
    }

    private void updateLoreSynchronously(ItemStack backpackItem, OfflinePlayer owner, int dbId, int size, int level, String usedSlotsPlaceholder) {
        if (backpackItem == null || !backpackItem.hasItemMeta()) return;
        ItemMeta meta = backpackItem.getItemMeta();
        if (meta == null) return;

        List<String> loreFormat = getConfig().getStringList("backpack.item.lore_format");
        List<String> newLore = new ArrayList<>();
        int numPages = (int) Math.ceil((double) size / getItemsPerPage());

        for (String line : loreFormat) {
            newLore.add(ChatColor.translateAlternateColorCodes('&', line
                    .replace("{owner}", owner.getName() != null ? owner.getName() : "Không rõ")
                    .replace("{num_pages}", String.valueOf(numPages))
                    .replace("{total_slots}", String.valueOf(size))
                    .replace("{used_slots}", usedSlotsPlaceholder)
                    .replace("{backpack_db_id}", String.valueOf(dbId))
                    .replace("{upgrade_level}", String.valueOf(level))
                    .replace("{max_upgrades}", String.valueOf(getMaxUpgrades()))
            ));
        }
        meta.setLore(newLore);
        backpackItem.setItemMeta(meta);
    }

    public void updateBackpackItemInInventory(UUID ownerUUID, int backpackDbId) {
        Player player = Bukkit.getPlayer(ownerUUID);
        if (player == null || !player.isOnline()) return;

        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack itemStack = player.getInventory().getItem(i);
            if (itemStack != null && itemStack.hasItemMeta()) {
                ItemMeta meta = itemStack.getItemMeta();
                if(meta == null) continue;

                if (meta.getPersistentDataContainer().has(backpackDbIdKey, PersistentDataType.INTEGER)) {
                    Integer id = meta.getPersistentDataContainer().get(backpackDbIdKey, PersistentDataType.INTEGER);
                    if (id != null && id == backpackDbId) {
                        int size = meta.getPersistentDataContainer().getOrDefault(backpackSizeKey, PersistentDataType.INTEGER, 0);
                        int level = meta.getPersistentDataContainer().getOrDefault(backpackUpgradeLevelKey, PersistentDataType.INTEGER, 0);
                        updateLoreWithUsedSlots(itemStack, player, backpackDbId, size, level);
                    }
                }
            }
        }
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    private boolean setupPlayerPoints() {
        Plugin playerPointsPlugin = getServer().getPluginManager().getPlugin("PlayerPoints");
        if (playerPointsPlugin == null) {
            return false;
        }
        playerPointsAPI = PlayerPoints.class.cast(playerPointsPlugin).getAPI();
        return playerPointsAPI != null;
    }

    public static CrossServerBackpack getInstance() { return instance; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public BackpackManager getBackpackManager() { return backpackManager; }
    public Economy getEconomy() { return economy; }
    public PlayerPointsAPI getPlayerPointsAPI() { return playerPointsAPI; }
    public int getMaxPages() { return maxPages; }
    public int getItemsPerPage() { return itemsPerPage; }
    public int getMaxUpgrades() { return maxUpgrades; }
    public double getRecoverCost() { return recoverCost; }
    public int getBuyCost() { return buyCost; }
    public double getBuyupCost() { return buyupCost; }
    public double getRenameCost() { return renameCost; }
    public Map<String, ConfigurationSection> getBackpackSkins() { return backpackSkins; }
    public Set<Material> getBlacklistedMaterials() { return blacklistedMaterials; }
    public List<EnchantmentTier> getEnchantmentTiers() { return enchantmentTiers; }

    public void reloadAllConfigs() {
        updateConfigFile("config.yml");
        updateConfigFile("messages.yml");
        updateConfigFile("custom_gui.yml");
        updateConfigFile("skins.yml");

        reloadConfig();
        this.messagesConfig = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
        this.customGuiConfig = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "custom_gui.yml"));
        this.skinsConfig = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "skins.yml"));

        FileConfiguration mainCfg = getConfig();
        maxPages = mainCfg.getInt("backpack.max_pages", 1000);
        maxUpgrades = mainCfg.getInt("backpack.max_upgrades", 3);
        recoverCost = mainCfg.getDouble("backpack.recover_cost", 1000.0);
        buyCost = mainCfg.getInt("backpack.buy_cost", 500);
        buyupCost = mainCfg.getDouble("backpack.buyup_cost", 250);
        renameCost = mainCfg.getDouble("backpack.rename_cost", 10000);

        loadSkinsData();
        loadBlacklistedItems();
        loadSortingConfig();

        itemsPerPage = getCustomGuiConfig().getInt("backpack_gui.items_per_page", 27);

        if (backpackManager != null) {
            backpackManager.compileTitlePattern();
        }
    }

    private void updateConfigFile(String fileName) {
        File configFile = new File(getDataFolder(), fileName);
        if (!configFile.exists()) {
            saveResource(fileName, false);
            return;
        }

        java.io.InputStream defaultConfigStream = getResource(fileName);
        if (defaultConfigStream == null) {
            getLogger().warning("Không tìm thấy tệp mặc định " + fileName + " trong JAR.");
            return;
        }
        YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultConfigStream, StandardCharsets.UTF_8));

        YamlConfiguration currentConfig = YamlConfiguration.loadConfiguration(configFile);

        boolean modified = false;
        for (String key : defaultConfig.getKeys(true)) {
            if (!currentConfig.isSet(key)) {
                currentConfig.set(key, defaultConfig.get(key));
                modified = true;
            }
        }

        if (modified) {
            try {
                currentConfig.save(configFile);
                getLogger().info(fileName + " đã được cập nhật với các giá trị mới.");
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Không thể lưu tệp đã cập nhật " + fileName, e);
            }
        }
    }

    private void loadSortingConfig() {
        enchantmentTiers.clear();
        List<Map<?, ?>> tiersList = getConfig().getMapList("sorting.enchantment_tiers");
        for (Map<?, ?> tierMap : tiersList) {
            if (tierMap.containsKey("unicode") && tierMap.containsKey("priority")) {
                String unicode = (String) tierMap.get("unicode");
                int priority = (int) tierMap.get("priority");
                enchantmentTiers.add(new EnchantmentTier(unicode, priority));
            }
        }
        enchantmentTiers.sort(Comparator.comparingInt(EnchantmentTier::getPriority).reversed());
    }

    private void loadSkinsData() {
        backpackSkins.clear();
        ConfigurationSection skinsSection = skinsConfig.getConfigurationSection("skins");
        if (skinsSection != null) {
            for (String key : skinsSection.getKeys(false)) {
                backpackSkins.put(key, skinsSection.getConfigurationSection(key));
            }
        }
    }

    public void loadBlacklistedItems() {
        blacklistedMaterials.clear();
        List<String> materialNames = getConfig().getStringList("backpack.blacklisted-items");
        for (String name : materialNames) {
            try {
                Material material = Material.valueOf(name.toUpperCase());
                blacklistedMaterials.add(material);
            } catch (IllegalArgumentException e) {
                getLogger().warning("Vật liệu không hợp lệ trong danh sách đen: '" + name + "'");
            }
        }
    }

    public FileConfiguration getCustomGuiConfig() {
        if (this.customGuiConfig == null) {
            this.customGuiConfig = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "custom_gui.yml"));
        }
        return this.customGuiConfig;
    }

    public String getFormattedMessage(String path, String... replacements) {
        String message = this.messagesConfig.getString(path, "&cMissing message: " + path);
        String prefix = this.messagesConfig.getString("prefix", "&8[&dBP+&8] &r");
        message = prefix + message;
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace("{" + replacements[i] + "}", replacements[i + 1]);
            }
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public void sendPluginMessage(CommandSender sender, String path, String... replacements) {
        sender.sendMessage(getFormattedMessage(path, replacements));
    }
}