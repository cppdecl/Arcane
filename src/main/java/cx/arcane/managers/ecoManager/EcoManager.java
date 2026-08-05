package cx.arcane.managers.ecoManager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import cx.arcane.managers.dbManager.DBManager;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/*
 * EcoManager
 *
 * THREAD SAFETY:
 *  - ALL public methods in this class are SAFE to call from ANY thread.
 *  - This includes:
 *      - Folia region threads
 *      - async tasks
 *      - command executors
 *      - database workers
 *
 *  Bukkit API MUST NOT be used here.
 *
 * DESIGN:
 *  - EcoData objects are stored in a ConcurrentHashMap
 *  - All mutations use a global lock to ensure atomic operations
 *  - Transfers are atomic (no race conditions)
 */

public class EcoManager {

    // Global lock protecting all economy mutations
    private static final Object lock = new Object();

    // Player economy storage
    private static final ConcurrentHashMap<UUID, EcoData> ecoMap = new ConcurrentHashMap<>();

    private static final ObjectMapper mapper = new ObjectMapper();

    public static void onEnable() {
        createTable();
        loadAll();
    }

    public static void onDisable() {
        saveAll();
    }

    public static void onSave() {
        createTable();
        saveAll();
    }

    /*
     * Thread-safe
     * Can be called from ANY thread.
     */
    private static EcoData get(UUID uuid) {
        return ecoMap.computeIfAbsent(uuid, id -> new EcoData(id, 0));
    }

    /*
     * Get the player's position in the money leaderboard
     *
     * THREAD-SAFE: Can be called from ANY thread
     */
    public static int getMoneyPosition(UUID uuid) {
        synchronized (lock) {
            // Create a stable sorted snapshot
            List<EcoData> sorted = ecoMap.values().stream()
                    .sorted(Comparator.comparingLong(EcoData::getMoney).reversed())
                    .toList();

            for (int i = 0; i < sorted.size(); i++) {
                if (sorted.get(i).getUniqueId().equals(uuid)) return i + 1; // positions are 1-based
            }

            return -1; // not found
        }
    }

    /*
     * ATOMIC TRANSFER
     *
     * Thread-safe
     * Can be called from ANY thread.
     *
     * Returns:
     *  true  = success
     *  false = insufficient funds
     */
    public static boolean transferMoney(UUID from, UUID to, long amount) {
        synchronized (lock) {

            EcoData fromData = get(from);
            EcoData toData = get(to);

            if (fromData.getMoney() < amount) {
                return false;
            }

            fromData.takeMoney(amount);
            toData.giveMoney(amount);

            return true;
        }
    }

    /*
     * Remove money safely
     *
     * Thread-safe
     * ANY thread
     */
    public static boolean takeMoney(UUID uuid, long amount) {
        synchronized (lock) {

            EcoData data = get(uuid);

            if (data.getMoney() < amount) {
                return false;
            }

            data.takeMoney(amount);

            return true;
        }
    }

    /*
     * Give money safely
     *
     * Thread-safe
     * ANY thread
     */
    public static void giveMoney(UUID uuid, long amount) {
        synchronized (lock) {
            get(uuid).giveMoney(amount);
        }
    }

    /*
     * Set money safely
     *
     * Thread-safe
     * ANY thread
     */
    public static void setMoney(UUID uuid, long amount) {
        synchronized (lock) {
            get(uuid).setMoney(amount);
        }
    }

    /*
     * READ operations
     *
     * Thread-safe because reads are atomic
     * ANY thread
     */
    public static long getMoney(UUID uuid) {
        return get(uuid).getMoney();
    }


    /*
     * Leaderboards
     *
     * Thread-safe (uses lock for stable sorting snapshot)
     */
    public static List<EcoData> getTopMoney(int limit) {
        synchronized (lock) {
            return ecoMap.values().stream()
                    .sorted(Comparator.comparingLong(EcoData::getMoney).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
        }
    }

    /*
     * DATABASE SECTION
     *
     * These should ideally run on an async thread.
     */

    private static boolean createTable() {

        String sql = """
        CREATE TABLE IF NOT EXISTS acx_eco (
            UniqueId VARCHAR(36) PRIMARY KEY,
            EcoData JSON NOT NULL
        );
        """;

        try (Connection con = DBManager.getConnection();
             Statement st = con.createStatement()) {

            st.execute(sql);
            return true;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void loadAll() {

        synchronized (lock) {

            try (Connection conn = DBManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT * FROM acx_eco");
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {

                    UUID uuid = UUID.fromString(rs.getString("UniqueId"));
                    String json = rs.getString("EcoData");

                    EcoData data = mapper.readValue(json, EcoData.class);

                    ecoMap.put(uuid, data);
                }

            } catch (SQLException | JsonProcessingException e) {
                e.printStackTrace();
            }
        }
    }

    public static void saveAll() {

        synchronized (lock) {

            try (Connection conn = DBManager.getConnection()) {

                String sql = """
                INSERT INTO acx_eco (UniqueId, EcoData)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE EcoData = ?
                """;

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {

                    for (Map.Entry<UUID, EcoData> entry : ecoMap.entrySet()) {

                        String json = mapper.writeValueAsString(entry.getValue());

                        stmt.setString(1, entry.getKey().toString());
                        stmt.setString(2, json);
                        stmt.setString(3, json);

                        stmt.addBatch();
                    }

                    stmt.executeBatch();
                }

            } catch (SQLException | JsonProcessingException e) {
                e.printStackTrace();
            }
        }
    }
}