package com.nexussphere.backpackplus.manager;

import com.nexussphere.backpackplus.CrossServerBackpack;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class BackpackManager {
    private final CrossServerBackpack plugin;
    private final ConcurrentMap<UUID, ConcurrentMap<Integer, TrackedBackpackInstance>> openTrackedInstances;
    private final Set<UUID> playersViewingBackpack = ConcurrentHashMap.newKeySet();
    private Pattern titlePattern;
    private final NamespacedKey bpControlActionKey;
    private final Set<Integer> savingBackpacks = ConcurrentHashMap.newKeySet();

    public static class TrackedBackpackInstance {
        public final UUID ownerUUID;
        public final int backpackDbId;
        public final int totalSize;
        public final int itemsPerPage;
        public final int totalPages;
        public Inventory currentGuiInventory;
        public int currentPage;
        public Map<Integer, ItemStack> allItems;
        public final UUID sessionToken;

        public TrackedBackpackInstance(UUID ownerUUID, int backpackDbId, int totalSize, int itemsPerPageArgument, Map<Integer, ItemStack> initialItems) {
            this.ownerUUID = ownerUUID;
            this.backpackDbId = backpackDbId;
            this.totalSize = totalSize;
            this.itemsPerPage = (itemsPerPageArgument > 0) ? CrossServerBackpack.getInstance().getItemsPerPage() : 27;
            this.sessionToken = UUID.randomUUID();

            int calculatedTotalPages;
            if (this.totalSize == 0) {
                calculatedTotalPages = 0;
            } else {
                if (this.itemsPerPage <= 0) {
                    calculatedTotalPages = 1;
                } else {
                    calculatedTotalPages = (int) Math.ceil((double) this.totalSize / this.itemsPerPage);
                }
                if (calculatedTotalPages == 0 && this.totalSize > 0) {
                    calculatedTotalPages = 1;
                }
            }
            this.totalPages = calculatedTotalPages;
            this.currentPage = 0;
            this.allItems = new ConcurrentHashMap<>(initialItems != null ? initialItems : new HashMap<>());
        }
    }

    public BackpackManager(CrossServerBackpack plugin) {
        this.plugin = plugin;
        this.openTrackedInstances = new ConcurrentHashMap<>();
        this.bpControlActionKey = new NamespacedKey(plugin, "bp_control_action");
        compileTitlePattern();
    }

    public boolean isPlayerViewingBackpack(UUID playerUuid) {
        return playersViewingBackpack.contains(playerUuid);
    }

    public void addViewingPlayer(UUID playerUuid) {
        playersViewingBackpack.add(playerUuid);
    }

    public void removeViewingPlayer(UUID playerUuid) {
        playersViewingBackpack.remove(playerUuid);
    }

    public boolean isBackpackItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        PersistentDataContainer nbt = item.getItemMeta().getPersistentDataContainer();
        return nbt.has(plugin.backpackDbIdKey, PersistentDataType.INTEGER);
    }

    public void compileTitlePattern() {
        FileConfiguration guiCfg = plugin.getCustomGuiConfig();
        String configFormat = guiCfg.getString("backpack_gui.title_format", "&5&lBalo của {player} &d(ID: {backpack_id}) - Trang {current_page}/{max_pages}");
        String strippedFormat = ChatColor.stripColor(configFormat);
        String regexFormat = Pattern.quote(strippedFormat)
                .replace("\\{player\\}", "(?<player>.+?)")
                .replace("\\{backpack_id\\}", "(?<bpid>\\d+)")
                .replace("\\{current_page\\}", "\\d+")
                .replace("\\{max_pages\\}", "\\d+");
        this.titlePattern = Pattern.compile("^" + regexFormat + "$");
    }

    private void showLoadingGui(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Inventory loadingGui = Bukkit.createInventory(null, 27, ChatColor.translateAlternateColorCodes('&', "&8Đang tải dữ liệu ba lô..."));
            ItemStack filler = createControlItemFromKey("filler_pane_item");
            if (filler != null && filler.getType() != Material.AIR) {
                for (int i = 0; i < loadingGui.getSize(); i++) {
                    loadingGui.setItem(i, filler.clone());
                }
            }
            player.openInventory(loadingGui);
        });
    }

    public void openBackpackByDbId(Player opener, int backpackDbId, String openerNameForTitleContext) {
        if (savingBackpacks.contains(backpackDbId)) {
            plugin.sendPluginMessage(opener, "backpack_being_saved");
            return;
        }

        addViewingPlayer(opener.getUniqueId());
        showLoadingGui(opener);

        plugin.getDatabaseManager().getBackpackOwnerByDbId(backpackDbId).thenCompose(ownerUUID -> {
            if (ownerUUID == null) {
                plugin.getServer().getScheduler().runTask(plugin, (Runnable) opener::closeInventory);
                plugin.sendPluginMessage(opener, "backpack_not_found", "player", openerNameForTitleContext, "bpid", String.valueOf(backpackDbId));
                return CompletableFuture.completedFuture(null);
            }
            OfflinePlayer ownerPlayer = Bukkit.getOfflinePlayer(ownerUUID);
            String ownerName = ownerPlayer.getName() != null ? ownerPlayer.getName() : "Không rõ";
            return openBackpackLogic(opener, ownerUUID, ownerName, backpackDbId);
        }).exceptionally(ex -> {
            plugin.getServer().getScheduler().runTask(plugin, (Runnable) opener::closeInventory);
            plugin.getLogger().log(Level.SEVERE, "Lỗi mở item balo: " + ex.getMessage(), ex);
            plugin.sendPluginMessage(opener, "error_opening_backpack");
            return null;
        });
    }

    private CompletableFuture<Inventory> openBackpackLogic(Player openerToDisplayFor, UUID actualOwnerUUID, String actualOwnerName, int backpackDbId) {
        final int itemsPerPageActual = plugin.getItemsPerPage();
        return plugin.getDatabaseManager().getBackpackSizeByDbId(backpackDbId).thenCombine(
                plugin.getDatabaseManager().getBackpackContents(backpackDbId),
                (totalSize, initialItems) -> {
                    if (totalSize == -1) {
                        plugin.getServer().getScheduler().runTask(plugin, (Runnable) openerToDisplayFor::closeInventory);
                        plugin.sendPluginMessage(openerToDisplayFor, "backpack_not_found", "player", actualOwnerName, "bpid", String.valueOf(backpackDbId));
                        return null;
                    }

                    TrackedBackpackInstance instance = new TrackedBackpackInstance(actualOwnerUUID, backpackDbId, totalSize, itemsPerPageActual, initialItems);

                    openTrackedInstances
                            .computeIfAbsent(actualOwnerUUID, k -> new ConcurrentHashMap<>())
                            .put(backpackDbId, instance);

                    instance.currentPage = 0;
                    showBackpackPage(openerToDisplayFor, instance);
                    return instance.currentGuiInventory;
                }
        );
    }

    public void showBackpackPage(Player viewer, TrackedBackpackInstance backpackInstance) {
        OfflinePlayer ownerPlayer = Bukkit.getOfflinePlayer(backpackInstance.ownerUUID);
        String ownerName = ownerPlayer.getName() != null ? ownerPlayer.getName() : "Không rõ";

        FileConfiguration guiCfg = plugin.getCustomGuiConfig();
        String titleFormat = guiCfg.getString("backpack_gui.title_format", "&5&lBalo của {player} &d(ID: {backpack_id}) - Trang {current_page}/{max_pages}");
        String title = titleFormat
                .replace("{player}", ownerName)
                .replace("{backpack_id}", String.valueOf(backpackInstance.backpackDbId))
                .replace("{current_page}", String.valueOf(backpackInstance.currentPage + 1))
                .replace("{max_pages}", String.valueOf(backpackInstance.totalPages));
        title = ChatColor.translateAlternateColorCodes('&', title);

        int guiTotalSize = guiCfg.getInt("backpack_gui.size", 45);
        Inventory gui = Bukkit.createInventory(null, guiTotalSize, title);
        backpackInstance.currentGuiInventory = gui;

        populateBackpackPageGui(gui, backpackInstance);

        final Inventory finalGuiToOpen = gui;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            viewer.openInventory(finalGuiToOpen);
        });
        openTrackedInstances.computeIfAbsent(backpackInstance.ownerUUID, k -> new ConcurrentHashMap<>()).put(backpackInstance.backpackDbId, backpackInstance);
    }

    public void populateBackpackPageGui(Inventory gui, TrackedBackpackInstance bpInstance) {
        gui.clear();
        FileConfiguration guiCfg = plugin.getCustomGuiConfig();
        ItemStack fillerItem = createControlItemFromKey("filler_pane_item");

        ConfigurationSection layoutSection = guiCfg.getConfigurationSection("backpack_gui.layout");
        if (layoutSection != null) {
            int itemIndex = 0;
            for (int currentSlot = 0; currentSlot < gui.getSize(); currentSlot++) {
                ConfigurationSection slotConfig = findSlotConfig(layoutSection, currentSlot);

                if (slotConfig != null) {
                    String type = slotConfig.getString("type");

                    if ("storage".equals(type)) {
                        int absoluteSlotInAllItems = bpInstance.currentPage * bpInstance.itemsPerPage + itemIndex;
                        if (absoluteSlotInAllItems < bpInstance.totalSize) {
                            gui.setItem(currentSlot, bpInstance.allItems.get(absoluteSlotInAllItems));
                        }
                        itemIndex++;
                    } else if ("control_item".equals(type)) {
                        String itemKey = slotConfig.getString("item_key");
                        if (itemKey != null) {
                            if ("previous_page_button".equals(itemKey)) {
                                gui.setItem(currentSlot, (bpInstance.currentPage > 0) ? createControlItemFromKey(itemKey) : fillerItem.clone());
                            } else if ("next_page_button".equals(itemKey)) {
                                gui.setItem(currentSlot, (bpInstance.currentPage < bpInstance.totalPages - 1) ? createControlItemFromKey(itemKey) : fillerItem.clone());
                            } else if ("page_indicator_item".equals(itemKey)) {
                                gui.setItem(currentSlot, createControlItemFromKey(itemKey, "{current_page}", String.valueOf(bpInstance.currentPage + 1), "{max_pages}", String.valueOf(bpInstance.totalPages)));
                            } else {
                                gui.setItem(currentSlot, createControlItemFromKey(itemKey));
                            }
                        }
                    }
                } else {
                    gui.setItem(currentSlot, fillerItem.clone());
                }
            }
        }
    }

    public ConfigurationSection findSlotConfig(ConfigurationSection layoutSection, int slot) {
        for (String key : layoutSection.getKeys(false)) {
            String[] parts = key.split("-");
            int start = Integer.parseInt(parts[0]);
            int end = (parts.length > 1) ? Integer.parseInt(parts[1]) : start;
            if (slot >= start && slot <= end) {
                return layoutSection.getConfigurationSection(key);
            }
        }
        return null;
    }

    public ItemStack createControlItemFromKey(String itemKey, String... placeholders) {
        FileConfiguration guiCfg = plugin.getCustomGuiConfig();
        ConfigurationSection itemDef = guiCfg.getConfigurationSection("backpack_gui.control_items_definition." + itemKey);
        if (itemDef == null) {
            return new ItemStack(Material.AIR);
        }

        Material material = Material.matchMaterial(itemDef.getString("material", "STONE"));
        if (material == null) material = Material.STONE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            String name = itemDef.getString("name", " ");
            for (int i = 0; i < placeholders.length; i += 2) {
                if (i + 1 < placeholders.length) name = name.replace(placeholders[i], placeholders[i + 1]);
            }
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            List<String> lore = itemDef.getStringList("lore").stream()
                    .map(l -> ChatColor.translateAlternateColorCodes('&', l))
                    .collect(Collectors.toList());
            if (!lore.isEmpty()) meta.setLore(lore);
            int cmd = itemDef.getInt("custom_model_data", 0);
            if (cmd > 0) meta.setCustomModelData(cmd);

            String actionType = itemDef.getString("action", "");
            if (!actionType.isEmpty()) {
                PersistentDataContainer nbt = meta.getPersistentDataContainer();
                nbt.set(bpControlActionKey, PersistentDataType.STRING, actionType);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isControlItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer nbt = meta.getPersistentDataContainer();
        return nbt.has(bpControlActionKey, PersistentDataType.STRING);
    }

    public boolean isStorageSlot(int slotIndex) {
        FileConfiguration guiCfg = plugin.getCustomGuiConfig();
        ConfigurationSection layoutSection = guiCfg.getConfigurationSection("backpack_gui.layout");
        if (layoutSection == null) {
            return false;
        }

        for (String slotKey : layoutSection.getKeys(false)) {
            ConfigurationSection slotConfig = layoutSection.getConfigurationSection(slotKey);
            if (slotConfig != null && "storage".equals(slotConfig.getString("type"))) {
                String[] slotParts = slotKey.split("-");
                int startSlotGui = Integer.parseInt(slotParts[0]);
                int endSlotGui = (slotParts.length > 1) ? Integer.parseInt(slotParts[1]) : startSlotGui;
                if (slotIndex >= startSlotGui && slotIndex <= endSlotGui) {
                    return true;
                }
            }
        }
        return false;
    }

    public void switchPage(Player player, int backpackDbId, boolean next) {
        TrackedBackpackInstance instance = getTrackedInstanceByDbIdOnly(player.getUniqueId(), backpackDbId);
        if (instance == null) {
            player.closeInventory();
            return;
        }
        syncGuiToInternalMap(instance);

        int newPage = instance.currentPage;
        if (next && (instance.currentPage + 1) < instance.totalPages) {
            newPage++;
        } else if (!next && instance.currentPage > 0) {
            newPage--;
        } else {
            return;
        }
        instance.currentPage = newPage;
        populateBackpackPageGui(instance.currentGuiInventory, instance);
    }

    public void syncGuiToInternalMap(TrackedBackpackInstance inst) {
        if (inst == null || inst.currentGuiInventory == null) {
            return;
        }

        int itemsPerPage = plugin.getItemsPerPage();
        int startIndexInAllItems = inst.currentPage * itemsPerPage;

        int itemIndex = 0;
        for (int guiSlot = 0; guiSlot < inst.currentGuiInventory.getSize(); guiSlot++) {
            if (isStorageSlot(guiSlot)) {
                ItemStack itemInGui = inst.currentGuiInventory.getItem(guiSlot);
                int absoluteSlot = startIndexInAllItems + itemIndex;

                if (absoluteSlot < inst.totalSize) {
                    if (itemInGui == null || itemInGui.getType() == Material.AIR || isControlItem(itemInGui)) {
                        inst.allItems.remove(absoluteSlot);
                    } else {
                        inst.allItems.put(absoluteSlot, itemInGui.clone());
                    }
                }
                itemIndex++;
            }
        }
    }


    public TrackedBackpackInstance getTrackedInstanceByInventory(Inventory inv) {
        if (inv == null) return null;
        for (Map<Integer, TrackedBackpackInstance> playerBackpacks : openTrackedInstances.values()) {
            for (TrackedBackpackInstance instance : playerBackpacks.values()) {
                if (instance.currentGuiInventory != null && instance.currentGuiInventory.equals(inv)) {
                    return instance;
                }
            }
        }
        return null;
    }

    private TrackedBackpackInstance getTrackedInstanceByDbIdOnly(UUID pOwnerUUID, int bpDbId) {
        Map<Integer, TrackedBackpackInstance> ownerMap = openTrackedInstances.get(pOwnerUUID);
        if (ownerMap != null) return ownerMap.get(bpDbId);
        for (Map<Integer, TrackedBackpackInstance> map : openTrackedInstances.values()) {
            if (map.containsKey(bpDbId)) return map.get(bpDbId);
        }
        return null;
    }

    public void initiateSaveAndInvalidate(Player closer, TrackedBackpackInstance trackedInst) {
        if (trackedInst == null) return;

        removeViewingPlayer(closer.getUniqueId());
        syncGuiToInternalMap(trackedInst);

        final UUID ownerUuid = trackedInst.ownerUUID;
        final int backpackDbId = trackedInst.backpackDbId;
        final Map<Integer, ItemStack> itemsSnapshot = new HashMap<>(trackedInst.allItems);
        final UUID sessionToken = trackedInst.sessionToken;

        removeTrackedInstance(ownerUuid, backpackDbId);

        savingBackpacks.add(backpackDbId);

        Runnable saveTask = () -> {
            TrackedBackpackInstance currentSession = getTrackedInstanceByDbIdOnly(ownerUuid, backpackDbId);
            if (currentSession != null && !currentSession.sessionToken.equals(sessionToken)) {
                savingBackpacks.remove(backpackDbId);
                return;
            }

            List<ItemStack> removedItems = new ArrayList<>();
            itemsSnapshot.entrySet().removeIf(entry -> {
                if (isBackpackItem(entry.getValue())) {
                    removedItems.add(entry.getValue());
                    return true;
                }
                return false;
            });

            if (!removedItems.isEmpty()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    for (ItemStack removedItem : removedItems) {
                        if (!closer.getInventory().addItem(removedItem).isEmpty()) {
                            closer.getWorld().dropItemNaturally(closer.getLocation(), removedItem);
                        }
                    }
                    plugin.sendPluginMessage(closer, "cannot_put_backpack_into_itself");
                });
            }

            plugin.getDatabaseManager().saveBackpackContents(backpackDbId, itemsSnapshot)
                    .thenRun(() -> {
                        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.updateBackpackItemInInventory(ownerUuid, backpackDbId));
                        savingBackpacks.remove(backpackDbId);
                    })
                    .exceptionally(ex -> {
                        plugin.getLogger().log(Level.SEVERE, "Lỗi lưu DB ID " + backpackDbId, ex);
                        plugin.sendPluginMessage(closer, "error_saving_backpack");
                        savingBackpacks.remove(backpackDbId);
                        return null;
                    });
        };

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, saveTask);
    }

    private void removeTrackedInstance(UUID ownerUUID, int backpackDbId) {
        Map<Integer, TrackedBackpackInstance> playerOpenBackpacks = openTrackedInstances.get(ownerUUID);
        if (playerOpenBackpacks != null) {
            playerOpenBackpacks.remove(backpackDbId);
            if (playerOpenBackpacks.isEmpty()) {
                openTrackedInstances.remove(ownerUUID);
            }
        }
    }

    public void handleServerShutdown() {
        openTrackedInstances.forEach((ownerUUID, backpacks) -> {
            backpacks.forEach((backpackDbId, trackedInst) -> {
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(ownerUUID);
                String playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : ownerUUID.toString().substring(0, 8);
                plugin.getLogger().info("Đang lưu balo ID " + backpackDbId + " của " + playerName + " do server tắt.");
                if (trackedInst.currentGuiInventory != null) {
                    syncGuiToInternalMap(trackedInst);
                }
                try {
                    plugin.getDatabaseManager().saveBackpackContents(backpackDbId, trackedInst.allItems).join();
                    plugin.getLogger().info("Đã lưu (đồng bộ) balo ID " + backpackDbId + " cho " + playerName + ".");
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Lỗi khi lưu (đồng bộ) balo ID " + backpackDbId + " cho " + ownerUUID, e);
                }
            });
        });
        openTrackedInstances.clear();
        playersViewingBackpack.clear();
    }

    private int getBookTier(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return 0;
        }
        for (String line : item.getItemMeta().getLore()) {
            String strippedLine = ChatColor.stripColor(line);
            for (CrossServerBackpack.EnchantmentTier tier : plugin.getEnchantmentTiers()) {
                if (strippedLine.contains(tier.getUnicode())) {
                    return tier.getPriority();
                }
            }
        }
        return 0;
    }

    public void sortBackpack(TrackedBackpackInstance instance) {
        if (instance == null) return;

        List<ItemStack> items = new ArrayList<>(instance.allItems.values());
        items.removeIf(Objects::isNull);

        items.sort((item1, item2) -> {
            boolean isBook1 = item1.getType() == Material.ENCHANTED_BOOK;
            boolean isBook2 = item2.getType() == Material.ENCHANTED_BOOK;

            if (isBook1 || isBook2) {
                int tier1 = getBookTier(item1);
                int tier2 = getBookTier(item2);
                if (tier1 != tier2) {
                    return Integer.compare(tier2, tier1);
                }
            }

            String name1 = item1.hasItemMeta() && item1.getItemMeta().hasDisplayName() ? item1.getItemMeta().getDisplayName() : item1.getType().name();
            String name2 = item2.hasItemMeta() && item2.getItemMeta().hasDisplayName() ? item2.getItemMeta().getDisplayName() : item2.getType().name();
            int nameCompare = ChatColor.stripColor(name1).compareToIgnoreCase(ChatColor.stripColor(name2));
            if (nameCompare != 0) {
                return nameCompare;
            }

            return Integer.compare(item2.getAmount(), item1.getAmount());
        });

        List<ItemStack> mergedItems = new ArrayList<>();
        for (ItemStack sortedItem : items) {
            boolean wasMerged = false;
            if (sortedItem.getMaxStackSize() > 1) {
                for (ItemStack mergedItem : mergedItems) {
                    if (mergedItem.isSimilar(sortedItem)) {
                        int amountToAdd = Math.min(sortedItem.getAmount(), mergedItem.getMaxStackSize() - mergedItem.getAmount());
                        if (amountToAdd > 0) {
                            mergedItem.setAmount(mergedItem.getAmount() + amountToAdd);
                            sortedItem.setAmount(sortedItem.getAmount() - amountToAdd);
                        }
                        if (sortedItem.getAmount() <= 0) {
                            wasMerged = true;
                            break;
                        }
                    }
                }
            }
            if (!wasMerged && sortedItem.getAmount() > 0) {
                mergedItems.add(sortedItem);
            }
        }

        instance.allItems.clear();
        for (int i = 0; i < mergedItems.size(); i++) {
            if (i >= instance.totalSize) break;
            instance.allItems.put(i, mergedItems.get(i));
        }

        populateBackpackPageGui(instance.currentGuiInventory, instance);
    }

    public void quickDeposit(Player player, TrackedBackpackInstance instance) {
        if (instance == null) return;

        Set<Material> backpackMaterials = instance.allItems.values().stream()
                .filter(Objects::nonNull)
                .map(ItemStack::getType)
                .collect(Collectors.toSet());

        if (backpackMaterials.isEmpty()) {
            plugin.sendPluginMessage(player, "quick_deposit_fail");
            return;
        }

        int depositedCount = 0;
        ItemStack[] playerInventory = player.getInventory().getContents();

        for (int i = 9; i <= 35; i++) {
            ItemStack playerItem = playerInventory[i];
            if (playerItem == null || playerItem.getType() == Material.AIR || isBackpackItem(playerItem) || plugin.getBlacklistedMaterials().contains(playerItem.getType())) continue;

            if (backpackMaterials.contains(playerItem.getType())) {
                int amountLeft = playerItem.getAmount();

                for (Map.Entry<Integer, ItemStack> entry : instance.allItems.entrySet()) {
                    if (amountLeft > 0 && entry.getValue() != null && entry.getValue().isSimilar(playerItem)) {
                        int canAdd = entry.getValue().getMaxStackSize() - entry.getValue().getAmount();
                        if (canAdd > 0) {
                            int toAdd = Math.min(amountLeft, canAdd);
                            entry.getValue().setAmount(entry.getValue().getAmount() + toAdd);
                            amountLeft -= toAdd;
                        }
                    }
                }

                if (amountLeft > 0) {
                    for (int j = 0; j < instance.totalSize; j++) {
                        if (!instance.allItems.containsKey(j) || instance.allItems.get(j) == null) {
                            ItemStack newStack = playerItem.clone();
                            newStack.setAmount(amountLeft);
                            instance.allItems.put(j, newStack);
                            amountLeft = 0;
                            break;
                        }
                    }
                }

                if (amountLeft < playerItem.getAmount()) {
                    depositedCount++;
                    if (amountLeft > 0) {
                        playerItem.setAmount(amountLeft);
                    } else {
                        player.getInventory().setItem(i, null);
                    }
                }
            }
        }

        if (depositedCount > 0) {
            plugin.sendPluginMessage(player, "quick_deposit_success", "count", String.valueOf(depositedCount));
            populateBackpackPageGui(instance.currentGuiInventory, instance);
        } else {
            plugin.sendPluginMessage(player, "quick_deposit_fail");
        }
    }

    public void takeAllFromPage(Player player, TrackedBackpackInstance instance) {
        if (instance == null) return;

        syncGuiToInternalMap(instance);

        List<Integer> slotsOnPage = new ArrayList<>();
        int startIndex = instance.currentPage * instance.itemsPerPage;
        int endIndex = Math.min(startIndex + instance.itemsPerPage, instance.totalSize);

        for (int i = startIndex; i < endIndex; i++) {
            slotsOnPage.add(i);
        }

        int itemsTaken = 0;
        List<ItemStack> itemsToGive = new ArrayList<>();
        for (int slotIndex : slotsOnPage) {
            ItemStack item = instance.allItems.get(slotIndex);
            if (item != null && item.getType() != Material.AIR) {
                itemsToGive.add(item.clone());
                instance.allItems.remove(slotIndex);
                itemsTaken++;
            }
        }

        if (itemsTaken > 0) {
            HashMap<Integer, ItemStack> couldNotFit = player.getInventory().addItem(itemsToGive.toArray(new ItemStack[0]));
            for (ItemStack leftover : couldNotFit.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }

            plugin.sendPluginMessage(player, "take_all_from_page_success", "count", String.valueOf(itemsTaken));
            populateBackpackPageGui(instance.currentGuiInventory, instance);
        } else {
            plugin.sendPluginMessage(player, "take_all_from_page_empty");
        }
    }
}