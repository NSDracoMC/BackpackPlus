package com.nexussphere.backpackplus.database;

import com.nexussphere.backpackplus.CrossServerBackpack;
import com.nexussphere.backpackplus.util.ItemSerializer;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class DatabaseManager {
    private final CrossServerBackpack plugin;
    private HikariDataSource dataSource;
    private final String PLAYER_BACKPACKS_TABLE = "bp_player_backpacks";
    private final String BACKPACK_ITEMS_TABLE = "bp_backpack_items";
    private final String PERMISSIONS_TABLE = "bp_backpack_permissions";
    private static final String PLAYER_SKINS_TABLE = "bp_player_skins";
    private static final String SAVED_ITEMS_TABLE = "bp_saved_items";
    private static final String SERVER_SETTINGS_UUID = "00000000-0000-0000-0000-000000000000";

    public DatabaseManager(CrossServerBackpack plugin) {
        this.plugin = plugin;
        connect();
        initializeDatabase();
    }

    private void connect() {
        ConfigurationSection dbConfig = plugin.getConfig().getConfigurationSection("database");
        if (dbConfig == null) {
            plugin.getLogger().log(Level.SEVERE, "Cấu hình database bị thiếu trong config.yml!");
            throw new IllegalStateException("Cấu hình database bị thiếu.");
        }

        HikariConfig config = new HikariConfig();
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s",
                dbConfig.getString("host", "localhost"),
                dbConfig.getInt("port", 3306),
                dbConfig.getString("database", "backpackplus_db")
        );
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(dbConfig.getString("username", "user_database"));
        config.setPassword(dbConfig.getString("password", "password_database"));
        config.setMaximumPoolSize(dbConfig.getInt("maxPoolSize", 10));
        config.setMinimumIdle(dbConfig.getInt("minIdle", 5));
        config.setConnectionTimeout(dbConfig.getLong("connectionTimeout", 30000));
        config.setIdleTimeout(dbConfig.getLong("idleTimeout", 600000));
        config.setMaxLifetime(dbConfig.getLong("maxLifetime", 1800000));

        ConfigurationSection propertiesSection = dbConfig.getConfigurationSection("properties");
        if (propertiesSection != null) {
            for (String key : propertiesSection.getKeys(false)) {
                config.addDataSourceProperty(key, propertiesSection.get(key));
            }
        } else {
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        }

        try {
            this.dataSource = new HikariDataSource(config);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Không thể khởi tạo HikariCP connection pool: " + e.getMessage(), e);
            throw new RuntimeException("Không thể khởi tạo HikariCP.", e);
        }
    }

    public void closeDataSource() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("DataSource chưa được khởi tạo.");
        }
        return dataSource.getConnection();
    }

    private void initializeDatabase() {
        String sqlPlayerBackpacks = "CREATE TABLE IF NOT EXISTS " + PLAYER_BACKPACKS_TABLE + " (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "player_uuid VARCHAR(36) NOT NULL, " +
                "backpack_id_name VARCHAR(50) NOT NULL, " +
                "size INT NOT NULL, " +
                "upgrade_level INT NOT NULL DEFAULT 0, " +
                "item_instance_uuid VARCHAR(36) NOT NULL, " +
                "creation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "lost_timestamp TIMESTAMP NULL DEFAULT NULL, " +
                "skin_id VARCHAR(50) NULL DEFAULT NULL, " +
                "custom_name VARCHAR(255) NULL DEFAULT NULL, " +
                "UNIQUE KEY player_bp_unique (player_uuid, backpack_id_name)" +
                ");";

        String sqlBackpackItems = "CREATE TABLE IF NOT EXISTS " + BACKPACK_ITEMS_TABLE + " (" +
                "item_entry_id INT AUTO_INCREMENT PRIMARY KEY, " +
                "backpack_db_id INT NOT NULL, " +
                "slot_index INT NOT NULL, " +
                "item_data LONGTEXT NOT NULL, " +
                "FOREIGN KEY (backpack_db_id) REFERENCES " + PLAYER_BACKPACKS_TABLE + "(id) ON DELETE CASCADE, " +
                "UNIQUE KEY bp_slot_unique (backpack_db_id, slot_index)" +
                ");";

        String sqlPermissions = "CREATE TABLE IF NOT EXISTS " + PERMISSIONS_TABLE + " (" +
                "permission_id INT AUTO_INCREMENT PRIMARY KEY, " +
                "backpack_db_id INT NOT NULL, " +
                "trusted_uuid VARCHAR(36) NOT NULL, " +
                "FOREIGN KEY (backpack_db_id) REFERENCES " + PLAYER_BACKPACKS_TABLE + "(id) ON DELETE CASCADE, " +
                "UNIQUE KEY bp_permission_unique (backpack_db_id, trusted_uuid)" +
                ");";

        String createPlayerSkinsTable = "CREATE TABLE IF NOT EXISTS " + PLAYER_SKINS_TABLE + " ("
                + "id INT AUTO_INCREMENT PRIMARY KEY,"
                + "player_uuid VARCHAR(36) NOT NULL,"
                + "skin_id VARCHAR(50) NOT NULL,"
                + "quantity INT NOT NULL DEFAULT 1,"
                + "UNIQUE(player_uuid, skin_id)"
                + ");";

        String createSavedItemsTable = "CREATE TABLE IF NOT EXISTS " + SAVED_ITEMS_TABLE + " ("
                + "id INT AUTO_INCREMENT PRIMARY KEY,"
                + "owner_uuid VARCHAR(36) NOT NULL,"
                + "item_name VARCHAR(100) NOT NULL,"
                + "item_data LONGTEXT NOT NULL,"
                + "UNIQUE KEY owner_item_unique (owner_uuid, item_name)"
                + ");";

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sqlPlayerBackpacks);
            stmt.execute(sqlBackpackItems);
            stmt.execute(sqlPermissions);
            stmt.execute(createPlayerSkinsTable);
            stmt.execute(createSavedItemsTable);

            DatabaseMetaData metaData = conn.getMetaData();
            if (!metaData.getColumns(null, null, PLAYER_BACKPACKS_TABLE, "upgrade_level").next()) {
                stmt.executeUpdate("ALTER TABLE " + PLAYER_BACKPACKS_TABLE + " ADD COLUMN upgrade_level INT NOT NULL DEFAULT 0;");
            }
            if (!metaData.getColumns(null, null, PLAYER_BACKPACKS_TABLE, "lost_timestamp").next()) {
                stmt.executeUpdate("ALTER TABLE " + PLAYER_BACKPACKS_TABLE + " ADD COLUMN lost_timestamp TIMESTAMP NULL DEFAULT NULL;");
            }
            if (!metaData.getColumns(null, null, PLAYER_BACKPACKS_TABLE, "item_instance_uuid").next()) {
                stmt.executeUpdate("ALTER TABLE " + PLAYER_BACKPACKS_TABLE + " ADD COLUMN item_instance_uuid VARCHAR(36) NOT NULL;");
            }
            if (!metaData.getColumns(null, null, PLAYER_BACKPACKS_TABLE, "skin_id").next()) {
                stmt.executeUpdate("ALTER TABLE " + PLAYER_BACKPACKS_TABLE + " ADD COLUMN skin_id VARCHAR(50) NULL DEFAULT NULL;");
            }
            if (!metaData.getColumns(null, null, PLAYER_BACKPACKS_TABLE, "custom_name").next()) {
                stmt.executeUpdate("ALTER TABLE " + PLAYER_BACKPACKS_TABLE + " ADD COLUMN custom_name VARCHAR(255) NULL DEFAULT NULL;");
            }
            if (!metaData.getColumns(null, null, PLAYER_SKINS_TABLE, "quantity").next()) {
                stmt.executeUpdate("ALTER TABLE " + PLAYER_SKINS_TABLE + " ADD COLUMN quantity INT NOT NULL DEFAULT 1;");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Lỗi khi khởi tạo bảng trong database: ", e);
        }
    }

    public CompletableFuture<Boolean> removePlayerSkin(UUID playerUuid, String skinId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection()) {
                String updateSql = "UPDATE " + PLAYER_SKINS_TABLE + " SET quantity = quantity - 1 WHERE player_uuid = ? AND skin_id = ? AND quantity > 0";
                try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                    pstmt.setString(1, playerUuid.toString());
                    pstmt.setString(2, skinId);
                    int affectedRows = pstmt.executeUpdate();
                    if (affectedRows == 0) return false;
                }

                String deleteSql = "DELETE FROM " + PLAYER_SKINS_TABLE + " WHERE player_uuid = ? AND skin_id = ? AND quantity <= 0";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteSql)) {
                    pstmt.setString(1, playerUuid.toString());
                    pstmt.setString(2, skinId);
                    pstmt.executeUpdate();
                }
                return true;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Lỗi khi xóa skin " + skinId + " của người chơi " + playerUuid, e);
                return false;
            }
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<Void> updateBackpackName(int backpackDbId, String customName) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE " + PLAYER_BACKPACKS_TABLE + " SET custom_name = ? WHERE id = ?;";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, customName);
                pstmt.setInt(2, backpackDbId);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Lỗi khi cập nhật tên cho balo DB ID " + backpackDbId, e);
            }
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<String> getBackpackName(int backpackDbId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT custom_name FROM " + PLAYER_BACKPACKS_TABLE + " WHERE id = ?;";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, backpackDbId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return rs.getString("custom_name");
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Lỗi khi lấy tên balo cho DB ID " + backpackDbId, e);
            }
            return null;
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<Void> updateBackpackSkin(int backpackDbId, String skinId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE " + PLAYER_BACKPACKS_TABLE + " SET skin_id = ? WHERE id = ?;";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, skinId);
                pstmt.setInt(2, backpackDbId);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Lỗi khi cập nhật skin cho balo DB ID " + backpackDbId, e);
            }
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<String> getBackpackSkin(int backpackDbId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT skin_id FROM " + PLAYER_BACKPACKS_TABLE + " WHERE id = ?;";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, backpackDbId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return rs.getString("skin_id");
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Lỗi khi lấy skin cho balo DB ID " + backpackDbId, e);
            }
            return null;
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<Void> addPlayerSkin(UUID playerUuid, String skinId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO " + PLAYER_SKINS_TABLE + " (player_uuid, skin_id, quantity) VALUES (?, ?, 1) "
                    + "ON DUPLICATE KEY UPDATE quantity = quantity + 1;";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, playerUuid.toString());
                pstmt.setString(2, skinId);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Lỗi khi thêm/cập nhật skin " + skinId + " cho người chơi " + playerUuid, e);
            }
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<Map<String, Integer>> getPlayerSkins(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Integer> ownedSkins = new HashMap<>();
            String sql = "SELECT skin_id, quantity FROM " + PLAYER_SKINS_TABLE + " WHERE player_uuid = ?;";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, playerUuid.toString());
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    ownedSkins.put(rs.getString("skin_id"), rs.getInt("quantity"));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Lỗi khi lấy danh sách skin của người chơi " + playerUuid, e);
            }
            return ownedSkins;
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<Integer> countSkinInUse(UUID playerUuid, String skinId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM " + PLAYER_BACKPACKS_TABLE + " WHERE player_uuid = ? AND skin_id = ?;";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, playerUuid.toString());
                pstmt.setString(2, skinId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt(1);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Lỗi khi đếm skin " + skinId + " đang sử dụng của " + playerUuid, e);
            }
            return 0;
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<Integer> createBackpackEntry(UUID playerUUID, int totalCalculatedSize, String instanceUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String generatedName = UUID.randomUUID().toString();
            String sql = "INSERT INTO " + PLAYER_BACKPACKS_TABLE + " (player_uuid, backpack_id_name, size, item_instance_uuid) VALUES (?, ?, ?, ?);";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, playerUUID.toString());
                pstmt.setString(2, generatedName);
                pstmt.setInt(3, totalCalculatedSize);
                pstmt.setString(4, instanceUuid);
                int affectedRows = pstmt.executeUpdate();
                if (affectedRows > 0) {
                    try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            return generatedKeys.getInt(1);
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Lỗi khi tạo backpack entry trong database cho " + playerUUID, e);
            }
            return -1;
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<Integer> getNthBackpackDbIdForPlayer(UUID playerUUID, int zeroBasedIndex) {
        if (zeroBasedIndex < 0) {
            return CompletableFuture.completedFuture(-1);
        }
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT id FROM " + PLAYER_BACKPACKS_TABLE + " WHERE player_uuid = ? ORDER BY creation_date ASC, id ASC LIMIT 1 OFFSET ?;";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, playerUUID.toString());
                pstmt.setInt(2, zeroBasedIndex);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("id");
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Lỗi khi lấy balo thứ " + (zeroBasedIndex + 1) + " cho người chơi " + playerUUID + ": " + e.getMessage());
            }
            return -1;
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<Map<Integer, ItemStack>> getBackpackContents(int backpackDbId) {
        return CompletableFuture.supplyAsync(() -> {
            Map<Integer, ItemStack> items = new HashMap<>();
            String sql = "SELECT slot_index, item_data FROM " + BACKPACK_ITEMS_TABLE + " WHERE backpack_db_id = ?;";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, backpackDbId);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    try {
                        ItemStack item = ItemSerializer.itemStackFromBase64(rs.getString("item_data"));
                        if (item != null) {
                            items.put(rs.getInt("slot_index"), item);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "Lỗi khi deserialize item từ DB cho balo " + backpackDbId + ", slot " + rs.getInt("slot_index"), e);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Lỗi khi lấy nội dung balo cho DB ID " + backpackDbId + ": " + e.getMessage());
            }
            return items;
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<Void> saveBackpackContents(int backpackDbId, Map<Integer, ItemStack> items) {
        return CompletableFuture.runAsync(() -> {
            String insertSql = "INSERT INTO " + BACKPACK_ITEMS_TABLE + " (backpack_db_id, slot_index, item_data) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE item_data = VALUES(item_data);";

            List<Integer> slotsToKeep = new ArrayList<>();

            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);

                try (PreparedStatement insertPstmt = conn.prepareStatement(insertSql)) {
                    for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
                        if (entry.getValue() != null && entry.getValue().getType() != Material.AIR) {
                            String itemData = ItemSerializer.itemStackToBase64(entry.getValue());
                            if (itemData != null && !itemData.isEmpty()) {
                                insertPstmt.setInt(1, backpackDbId);
                                insertPstmt.setInt(2, entry.getKey());
                                insertPstmt.setString(3, itemData);
                                insertPstmt.addBatch();
                                slotsToKeep.add(entry.getKey());
                            }
                        }
                    }
                    insertPstmt.executeBatch();
                }

                if (!slotsToKeep.isEmpty()) {
                    String deleteSql = "DELETE FROM " + BACKPACK_ITEMS_TABLE + " WHERE backpack_db_id = ? AND slot_index NOT IN ("
                            + slotsToKeep.stream().map(String::valueOf).collect(Collectors.joining(",")) + ");";
                    try (PreparedStatement deletePstmt = conn.prepareStatement(deleteSql)) {
                        deletePstmt.setInt(1, backpackDbId);
                        deletePstmt.executeUpdate();
                    }
                } else {
                    String deleteAllSql = "DELETE FROM " + BACKPACK_ITEMS_TABLE + " WHERE backpack_db_id = ?;";
                    try (PreparedStatement deleteAllPstmt = conn.prepareStatement(deleteAllSql)) {
                        deleteAllPstmt.setInt(1, backpackDbId);
                        deleteAllPstmt.executeUpdate();
                    }
                }

                conn.commit();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Lỗi khi lưu nội dung balo DB ID " + backpackDbId, e);
            }
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<Boolean> updateBackpackSizeAndLevel(int backpackDbId, int newSize, int newLevel) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE " + PLAYER_BACKPACKS_TABLE + " SET size = ?, upgrade_level = ? WHERE id = ?;";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, newSize);
                pstmt.setInt(2, newLevel);
                pstmt.setInt(3, backpackDbId);
                int affectedRows = pstmt.executeUpdate();
                return affectedRows > 0;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Lỗi khi cập nhật kích thước và cấp độ cho balo DB ID " + backpackDbId, e);
                return false;
            }
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<Integer> getBackpackSizeByDbId(int backpackDbId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT size FROM " + PLAYER_BACKPACKS_TABLE + " WHERE id = ?;";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, backpackDbId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("size");
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Lỗi khi lấy kích thước balo cho DB ID " + backpackDbId + ": " + e.getMessage());
            }
            return -1;
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<Integer> getBackpackUpgradeLevelByDbId(int backpackDbId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT upgrade_level FROM " + PLAYER_BACKPACKS_TABLE + " WHERE id = ?;";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, backpackDbId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("upgrade_level");
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Lỗi khi lấy cấp độ nâng cấp cho balo DB ID " + backpackDbId + ": " + e.getMessage());
            }
            return 0;
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<UUID> getBackpackOwnerByDbId(int backpackDbId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT player_uuid FROM " + PLAYER_BACKPACKS_TABLE + " WHERE id = ?;";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, backpackDbId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return UUID.fromString(rs.getString("player_uuid"));
                }
            } catch (SQLException | IllegalArgumentException e) {
                plugin.getLogger().log(Level.WARNING, "Lỗi khi lấy chủ sở hữu balo cho DB ID " + backpackDbId + ": " + e.getMessage());
            }
            return null;
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<Boolean> addTrustedPlayer(int backpackDbId, UUID trustedUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT IGNORE INTO " + PERMISSIONS_TABLE + " (backpack_db_id, trusted_uuid) VALUES (?, ?);";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, backpackDbId);
                pstmt.setString(2, trustedUuid.toString());
                int affectedRows = pstmt.executeUpdate();
                return affectedRows > 0;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Lỗi khi thêm người chơi tin cậy cho balo " + backpackDbId, e);
                return false;
            }
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<Boolean> removeTrustedPlayer(int backpackDbId, UUID trustedUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + PERMISSIONS_TABLE + " WHERE backpack_db_id = ? AND trusted_uuid = ?;";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, backpackDbId);
                pstmt.setString(2, trustedUuid.toString());
                int affectedRows = pstmt.executeUpdate();
                return affectedRows > 0;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Lỗi khi xóa người chơi tin cậy cho balo " + backpackDbId, e);
                return false;
            }
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<List<UUID>> getTrustedPlayers(int backpackDbId) {
        return CompletableFuture.supplyAsync(() -> {
            List<UUID> trustedUuids = new ArrayList<>();
            String sql = "SELECT trusted_uuid FROM " + PERMISSIONS_TABLE + " WHERE backpack_db_id = ?;";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, backpackDbId);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    try {
                        trustedUuids.add(UUID.fromString(rs.getString("trusted_uuid")));
                    } catch (IllegalArgumentException ignored) {}
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Lỗi khi lấy danh sách tin cậy cho balo " + backpackDbId, e);
            }
            return trustedUuids;
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<Boolean> isPlayerTrusted(int backpackDbId, UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM " + PERMISSIONS_TABLE + " WHERE backpack_db_id = ? AND trusted_uuid = ?;";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, backpackDbId);
                pstmt.setString(2, playerUuid.toString());
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Lỗi khi kiểm tra người chơi tin cậy cho balo " + backpackDbId, e);
            }
            return false;
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<Void> markBackpackAsLost(int backpackDbId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE " + PLAYER_BACKPACKS_TABLE + " SET lost_timestamp = NOW() WHERE id = ?;";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, backpackDbId);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Lỗi khi đánh dấu balo bị mất: " + backpackDbId, e);
            }
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<Void> markBackpackAsRecovered(int backpackDbId, String newInstanceUuid) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE " + PLAYER_BACKPACKS_TABLE + " SET lost_timestamp = NULL, item_instance_uuid = ? WHERE id = ?;";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, newInstanceUuid);
                pstmt.setInt(2, backpackDbId);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Lỗi khi đánh dấu balo đã khôi phục: " + backpackDbId, e);
            }
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<Integer> getMostRecentlyLostBackpack(UUID playerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT id FROM " + PLAYER_BACKPACKS_TABLE + " WHERE player_uuid = ? AND lost_timestamp IS NOT NULL ORDER BY lost_timestamp DESC LIMIT 1;";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, playerUUID.toString());
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("id");
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Lỗi khi lấy balo bị mất gần nhất cho người chơi " + playerUUID, e);
            }
            return -1;
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<String> getItemInstanceUUID(int backpackDbId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT item_instance_uuid FROM " + PLAYER_BACKPACKS_TABLE + " WHERE id = ?;";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, backpackDbId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return rs.getString("item_instance_uuid");
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Lỗi khi lấy item instance UUID cho balo " + backpackDbId, e);
            }
            return null;
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<Boolean> updateBackpackOwner(int backpackDbId, UUID newOwnerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE " + PLAYER_BACKPACKS_TABLE + " SET player_uuid = ? WHERE id = ?;";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, newOwnerUuid.toString());
                pstmt.setInt(2, backpackDbId);
                int affectedRows = pstmt.executeUpdate();
                return affectedRows > 0;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Lỗi khi cập nhật chủ sở hữu cho balo DB ID " + backpackDbId, e);
                return false;
            }
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<Boolean> deleteBackpackById(int backpackDbId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + PLAYER_BACKPACKS_TABLE + " WHERE id = ?;";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, backpackDbId);
                int affectedRows = pstmt.executeUpdate();
                return affectedRows > 0;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Lỗi khi xóa balo DB ID " + backpackDbId, e);
                return false;
            }
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<Void> updateItemInstanceUUID(int backpackDbId, String newInstanceUuid) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE " + PLAYER_BACKPACKS_TABLE + " SET item_instance_uuid = ? WHERE id = ?;";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, newInstanceUuid);
                pstmt.setInt(2, backpackDbId);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Lỗi khi cập nhật item instance UUID cho balo " + backpackDbId, e);
            }
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<Integer> getLostBackpackCount(UUID playerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM " + PLAYER_BACKPACKS_TABLE + " WHERE player_uuid = ? AND lost_timestamp IS NOT NULL;";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, playerUUID.toString());
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt(1);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Lỗi khi đếm số balo bị mất cho người chơi " + playerUUID, e);
            }
            return 0;
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<Boolean> isBackpackLost(int backpackDbId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT lost_timestamp FROM " + PLAYER_BACKPACKS_TABLE + " WHERE id = ?;";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, backpackDbId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return rs.getTimestamp("lost_timestamp") != null;
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Lỗi khi kiểm tra trạng thái mất của balo " + backpackDbId, e);
            }
            return false;
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<Void> clearLostStatus(int backpackDbId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE " + PLAYER_BACKPACKS_TABLE + " SET lost_timestamp = NULL WHERE id = ?;";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, backpackDbId);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Không thể xóa trạng thái mất cho balo: " + backpackDbId, e);
            }
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<Boolean> saveItem(UUID ownerUuid, String itemName, ItemStack item) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO " + SAVED_ITEMS_TABLE + " (owner_uuid, item_name, item_data) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE item_data = VALUES(item_data);";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, ownerUuid.toString());
                pstmt.setString(2, itemName.toLowerCase());
                pstmt.setString(3, ItemSerializer.itemStackToBase64(item));
                pstmt.executeUpdate();
                return true;
            } catch (SQLException | IllegalStateException e) {
                plugin.getLogger().log(Level.SEVERE, "Lỗi khi lưu vật phẩm '" + itemName + "' cho " + ownerUuid, e);
                return false;
            }
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<ItemStack> loadItem(UUID ownerUuid, String itemName) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT item_data FROM " + SAVED_ITEMS_TABLE + " WHERE owner_uuid = ? AND item_name = ?;";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, ownerUuid.toString());
                pstmt.setString(2, itemName.toLowerCase());
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return ItemSerializer.itemStackFromBase64(rs.getString("item_data"));
                }
            } catch (SQLException | IllegalStateException e) {
                plugin.getLogger().log(Level.SEVERE, "Lỗi khi tải vật phẩm '" + itemName + "' của " + ownerUuid, e);
            }
            return null;
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<Boolean> deleteItem(UUID ownerUuid, String itemName) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + SAVED_ITEMS_TABLE + " WHERE owner_uuid = ? AND item_name = ?;";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, ownerUuid.toString());
                pstmt.setString(2, itemName.toLowerCase());
                return pstmt.executeUpdate() > 0;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Lỗi khi xóa vật phẩm '" + itemName + "' của " + ownerUuid, e);
                return false;
            }
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<List<String>> listItems(UUID ownerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> itemNames = new ArrayList<>();
            String sql = "SELECT item_name FROM " + SAVED_ITEMS_TABLE + " WHERE owner_uuid = ? ORDER BY item_name ASC;";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, ownerUuid.toString());
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    itemNames.add(rs.getString("item_name"));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Lỗi khi liệt kê vật phẩm của " + ownerUuid, e);
            }
            return itemNames;
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<Void> saveCasinoSetting(String key, String value) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO " + SAVED_ITEMS_TABLE + " (owner_uuid, item_name, item_data) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE item_data = VALUES(item_data);";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, SERVER_SETTINGS_UUID);
                pstmt.setString(2, key.toLowerCase());
                pstmt.setString(3, value);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Lỗi khi lưu cài đặt casino: " + key, e);
            }
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<String> loadCasinoSetting(String key) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT item_data FROM " + SAVED_ITEMS_TABLE + " WHERE owner_uuid = ? AND item_name = ?;";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, SERVER_SETTINGS_UUID);
                pstmt.setString(2, key.toLowerCase());
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return rs.getString("item_data");
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Lỗi khi tải cài đặt casino: " + key, e);
            }
            return null;
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }
}