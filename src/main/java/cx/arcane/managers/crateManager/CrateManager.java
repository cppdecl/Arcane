package cx.arcane.managers.crateManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import cx.arcane.Arcane;
import cx.arcane.managers.dbManager.DBManager;
import cx.arcane.utils.Log;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.sql.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class CrateManager {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, CrateData> crateData = new ConcurrentHashMap<>();
    private static final Map<UUID, KeyData> keyData = new ConcurrentHashMap<>();

    public static void onEnable() {
        createTables();
        loadAll();
        Bukkit.getPluginManager().registerEvents(new CrateListener(), Arcane.getPlugin());
        Bukkit.getPluginManager().registerEvents(new CrateHologramListener(), Arcane.getPlugin());

        Bukkit.getAsyncScheduler().runDelayed(Arcane.getPlugin(), t -> {
            for (CrateData crate : crateData.values()) {
                runAtCrate(crate, () -> {
                    CrateHologramManager.createHolograms(crate);
                    CrateParticleManager.startParticleTask();
                });
            }
        }, 1000, TimeUnit.MILLISECONDS);
    }

    public static void onDisable() {
        saveAll();
    }

    public static void onSave() {
        createTables();
        saveAll();
    }

    private static void createTables() {
        try (Connection con = DBManager.getConnection(); Statement st = con.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS acx_crates (
                    Id VARCHAR(64) NOT NULL,
                    Data JSON NOT NULL,
                    PRIMARY KEY (Id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS acx_keys (
                    PlayerId VARCHAR(36) NOT NULL,
                    Data JSON NOT NULL,
                    PRIMARY KEY (PlayerId)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
            """);
        } catch (SQLException e) {
            Log.error("[CrateManager] createTables() failed");
            e.printStackTrace();
        }
    }

    private static void loadAll() {
        crateData.clear();
        keyData.clear();

        try (Connection con = DBManager.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement("SELECT Data FROM acx_crates");
                 ResultSet rs = ps.executeQuery()) {
                int i = 0;
                while (rs.next()) {
                    CrateData crate = MAPPER.readValue(rs.getString("Data"), CrateData.class);
                    crateData.put(crate.getId().toLowerCase(), crate);
                    i++;
                }
                Log.info("[CrateManager] Loaded {} crates.", i);
            }

            try (PreparedStatement ps = con.prepareStatement("SELECT Data FROM acx_keys");
                 ResultSet rs = ps.executeQuery()) {
                int i = 0;
                while (rs.next()) {
                    KeyData kd = MAPPER.readValue(rs.getString("Data"), KeyData.class);
                    if (kd.getKeys() == null) kd.setKeys(new ConcurrentHashMap<>());
                    keyData.put(kd.getPlayerId(), kd);
                    i++;
                }
                Log.info("[CrateManager] Loaded {} key records.", i);
            }

        } catch (Exception e) {
            Log.error("[CrateManager] loadAll() failed");
            e.printStackTrace();
        }
    }

    public static void saveAll() {
        long t = System.currentTimeMillis();

        String crateSql = """
        INSERT INTO acx_crates (Id, Data) VALUES (?, ?)
        ON DUPLICATE KEY UPDATE Data = VALUES(Data)
    """;

        String keySql = """
        INSERT INTO acx_keys (PlayerId, Data) VALUES (?, ?)
        ON DUPLICATE KEY UPDATE Data = VALUES(Data)
    """;

        try (Connection con = DBManager.getConnection()) {
            con.setAutoCommit(false);

            try (PreparedStatement ps = con.prepareStatement(crateSql)) {
                for (CrateData crate : crateData.values()) {
                    ps.setString(1, crate.getId().toLowerCase());
                    ps.setString(2, MAPPER.writeValueAsString(crate));
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            try (Statement st = con.createStatement()) {
                if (!crateData.isEmpty()) {
                    String ids = crateData.keySet().stream()
                            .map(id -> "'" + id + "'")
                            .collect(java.util.stream.Collectors.joining(","));
                    st.executeUpdate("DELETE FROM acx_crates WHERE Id NOT IN (" + ids + ")");
                } else {
                    st.executeUpdate("DELETE FROM acx_crates");
                }
            }

            try (PreparedStatement ps = con.prepareStatement(keySql)) {
                for (KeyData kd : keyData.values()) {
                    ps.setString(1, kd.getPlayerId().toString());
                    ps.setString(2, MAPPER.writeValueAsString(kd));
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            try (Statement st = con.createStatement()) {
                if (!keyData.isEmpty()) {
                    String ids = keyData.keySet().stream()
                            .map(id -> "'" + id + "'")
                            .collect(java.util.stream.Collectors.joining(","));
                    st.executeUpdate("DELETE FROM acx_keys WHERE PlayerId NOT IN (" + ids + ")");
                } else {
                    st.executeUpdate("DELETE FROM acx_keys");
                }
            }

            con.commit();
            Log.info("[CrateManager] saveAll() completed in {}ms", System.currentTimeMillis() - t);

        } catch (Exception e) {
            Log.error("[CrateManager] saveAll() failed after {}ms", System.currentTimeMillis() - t);
            e.printStackTrace();
        }
    }

    public static Map<String, CrateData> getCrates() { return crateData; }
    public static Map<UUID, KeyData> getKeyData() { return keyData; }

    public static void giveKey(UUID playerId, String crateId, long amount) {
        if (amount <= 0) return;
        keyData.computeIfAbsent(playerId, id -> new KeyData(id, new ConcurrentHashMap<>()))
                .addKey(crateId.toLowerCase(), amount);
    }

    public static void takeKey(UUID playerId, String crateId, long amount) {
        if (amount <= 0) return;
        KeyData kd = keyData.get(playerId);
        if (kd == null) return;
        kd.takeKey(crateId.toLowerCase(), amount);
    }

    public static long getKeyCount(UUID playerId, String crateId) {
        KeyData kd = keyData.get(playerId);
        return kd == null ? 0L : kd.getKeyCount(crateId.toLowerCase());
    }

    public static boolean hasKey(UUID playerId, String crateId) {
        return getKeyCount(playerId, crateId) > 0;
    }

    public static void clearKeys(UUID playerId) { keyData.remove(playerId); }

    public static void addCrate(CrateData crate) {
        crateData.put(crate.getId().toLowerCase(), crate);
        runAtCrate(crate, () -> CrateHologramManager.createHolograms(crate));
    }

    public static void removeCrate(String crateId) {
        CrateData crate = crateData.remove(crateId.toLowerCase());
        if (crate != null) {
            runAtCrate(crate, () -> CrateHologramManager.removeHolograms(crate));
        }
    }

    public static void clearCrates() { crateData.clear(); }
    public static CrateData getCrateById(String crateId) { return crateData.get(crateId.toLowerCase()); }
    public static boolean hasCrate(String crateId) { return crateData.containsKey(crateId.toLowerCase()); }

    public static CrateData getCrateByLocation(Location loc) {
        if (loc == null) return null;
        for (CrateData crate : crateData.values()) {
            Location cl = crate.getLocation();
            if (cl == null) continue;
            if (loc.getWorld() == cl.getWorld()
                    && loc.getBlockX() == cl.getBlockX()
                    && loc.getBlockY() == cl.getBlockY()
                    && loc.getBlockZ() == cl.getBlockZ()) return crate;
        }
        return null;
    }

    public static boolean hasCrateAtLocation(Location loc) {
        return getCrateByLocation(loc) != null;
    }

    private static void runAtCrate(CrateData crate, Runnable task) {
        Location loc = crate.getLocation();
        if (loc == null) return;

        Bukkit.getRegionScheduler().execute(
                Arcane.getPlugin(),
                loc,
                task
        );
    }
}