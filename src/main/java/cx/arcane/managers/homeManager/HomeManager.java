package cx.arcane.managers.homeManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import cx.arcane.managers.dbManager.DBManager;
import cx.arcane.utils.Log;
import org.bukkit.Location;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class HomeManager {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_HOMES = 5;

    private static final Map<String, HomeData> HOMES = new ConcurrentHashMap<>();

    private static String key(UUID owner, String id) {
        return owner.toString() + ":" + id.toLowerCase();
    }

    public static void onEnable() {
        createTables();
        loadAll();
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
                CREATE TABLE IF NOT EXISTS acx_homes (
                    OwnerUUID VARCHAR(36) NOT NULL,
                    HomeId VARCHAR(64) NOT NULL,
                    Data JSON NOT NULL,
                    PRIMARY KEY (OwnerUUID, HomeId)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
            """);
        } catch (SQLException e) {
            Log.error("[HomeManager] createTables() failed");
            e.printStackTrace();
        }
    }

    private static void loadAll() {
        HOMES.clear();

        try (Connection con = DBManager.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT Data FROM acx_homes");
             ResultSet rs = ps.executeQuery()) {

            int i = 0;
            while (rs.next()) {
                HomeData hd = MAPPER.readValue(rs.getString("Data"), HomeData.class);
                HOMES.put(key(hd.getOwnerId(), hd.getId()), hd);
                i++;
            }
            Log.info("[HomeManager] Loaded {} homes.", i);

        } catch (Exception e) {
            Log.error("[HomeManager] loadAll() failed");
            e.printStackTrace();
        }
    }

    public static void saveAll() {
        long t = System.currentTimeMillis();

        String sql = """
            INSERT INTO acx_homes (OwnerUUID, HomeId, Data) VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE Data = VALUES(Data)
        """;

        try (Connection con = DBManager.getConnection()) {
            con.setAutoCommit(false);

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                for (HomeData hd : HOMES.values()) {
                    ps.setString(1, hd.getOwnerId().toString());
                    ps.setString(2, hd.getId().toLowerCase());
                    ps.setString(3, MAPPER.writeValueAsString(hd));
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            try (Statement st = con.createStatement()) {
                if (!HOMES.isEmpty()) {
                    String pairs = HOMES.values().stream()
                            .map(hd -> "('" + hd.getOwnerId() + "','" + hd.getId().toLowerCase() + "')")
                            .collect(Collectors.joining(","));
                    st.executeUpdate("DELETE FROM acx_homes WHERE (OwnerUUID, HomeId) NOT IN (" + pairs + ")");
                } else {
                    st.executeUpdate("DELETE FROM acx_homes");
                }
            }

            con.commit();
            Log.info("[HomeManager] saveAll() completed in {}ms", System.currentTimeMillis() - t);

        } catch (Exception e) {
            Log.error("[HomeManager] saveAll() failed after {}ms", System.currentTimeMillis() - t);
            e.printStackTrace();
        }
    }

    public static HomeData getHome(UUID owner, String id) {
        return HOMES.get(key(owner, id));
    }

    public static boolean setHome(UUID owner, String id, Location location) {
        if (!canCreateHome(owner) && !HOMES.containsKey(key(owner, id))) return false;

        HomeData data = new HomeData();
        data.setId(id);
        data.setOwnerId(owner);
        data.setLocation(location);
        data.setCreatedAt(System.currentTimeMillis());
        HOMES.put(key(owner, id), data);

        return true;
    }

    public static boolean deleteHome(UUID owner, String id) {
        return HOMES.remove(key(owner, id)) != null;
    }

    public static Collection<HomeData> getHomes(UUID owner) {
        return HOMES.values().stream()
                .filter(hd -> hd.getOwnerId().equals(owner))
                .sorted(Comparator.comparingLong(HomeData::getCreatedAt))
                .toList();
    }

    public static boolean canCreateHome(UUID owner) {
        long count = HOMES.values().stream()
                .filter(hd -> hd.getOwnerId().equals(owner))
                .count();
        return count < MAX_HOMES;
    }
}