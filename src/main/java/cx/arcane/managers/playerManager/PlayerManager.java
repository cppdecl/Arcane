package cx.arcane.managers.playerManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import cx.arcane.Arcane;
import cx.arcane.managers.dbManager.DBManager;
import cx.arcane.managers.ecoManager.EcoManager;
import cx.arcane.managers.geoManager.GeoData;
import cx.arcane.managers.geoManager.GeoManager;
import cx.arcane.managers.playerManager.listeners.SpawnPVPListener;
import cx.arcane.managers.playerManager.listeners.PlayerListener;
import cx.arcane.managers.playerManager.listeners.PlayerPacketListener;
import cx.arcane.managers.skinManager.CachedSkin;
import cx.arcane.managers.skinManager.SkinManager;
import cx.arcane.utils.*;
import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.skins.SkinData;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;


public class PlayerManager {
    private static boolean LOAD_SUCCESS = false;

    private static final ConcurrentHashMap<UUID, PlayerData> playersByUniqueId = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, PlayerData> playersByName = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, PlayerData> playersByNameLowercase = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, PlayerData> playersByDiscordId = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<InetAddress, Set<PlayerData>> playersByLoginIp = new ConcurrentHashMap<>();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static volatile List<PlayerData> overallLeaderboardCache = List.of();

    private record ScoredMetric(ToLongFunction<PlayerData> function, double weight) {}

    private static final List<ScoredMetric> METRICS = List.of(
            new ScoredMetric(d -> EcoManager.getMoney(d.getUniqueId()), 1.0),
            new ScoredMetric(d -> d.getMeta().getPlaytimeSeconds(), 0.7),
            new ScoredMetric(d -> d.getMeta().getTotalVotes(), 0.6),
            new ScoredMetric(d -> d.getMeta().getKills(), 0.4),
            new ScoredMetric(d -> d.getMeta().getTotalBought(), 0.3),
            new ScoredMetric(d -> d.getMeta().getTotalSold(), 0.2),
            new ScoredMetric(d -> d.getMeta().getAnchorsExploded(), 0.1),
            new ScoredMetric(d -> d.getMeta().getCrystalsExploded(), 0.1)
    );

    public static void onEnable() {
        createTable();
        alterTable();
        loadAll();

        PacketEvents.getAPI().getEventManager().registerListener(new PlayerPacketListener(), PacketListenerPriority.MONITOR);
        Bukkit.getPluginManager().registerEvents(new SpawnPVPListener(), Arcane.getPlugin());
        Bukkit.getPluginManager().registerEvents(new PlayerListener(), Arcane.getPlugin());

        Bukkit.getAsyncScheduler().runAtFixedRate(Arcane.getPlugin(), t -> {
            for (PlayerData data : getOnline()) {
                data.getPlayer().getScheduler().run(Arcane.getPlugin(), ts -> {
                    PlayerListener.onOnlinePlayerSecond(data.getPlayer());
                }, null);
            }
        }, 1, 1, java.util.concurrent.TimeUnit.SECONDS);

        Bukkit.getAsyncScheduler().runAtFixedRate(Arcane.getPlugin(), t -> {
            for (PlayerData data : getOnline()) {
                data.getPlayer().getScheduler().run(Arcane.getPlugin(), ts -> {
                    PlayerListener.onOnlinePlayerTick(data.getPlayer());
                }, null);
            }
        }, 50, 50, TimeUnit.MILLISECONDS);

        Bukkit.getAsyncScheduler().runAtFixedRate(Arcane.getPlugin(), t -> refreshLeaderboard(),
                30, 60 * 3, TimeUnit.SECONDS);
    }

    public static void onDisable() {
        Log.info("[PlayerManager] Saving " + playersByUniqueId.size() + " players...");
        saveAll();
        Log.info("[PlayerManager] Finished saving. ");
    }

    public static void onSave() {
        createTable();
        saveAll();
    }

    private static void alterTable() {
        String checkSql = """
            SELECT COUNT(*) 
            FROM INFORMATION_SCHEMA.COLUMNS 
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'acx_players'
              AND COLUMN_NAME = 'DiscordId'
        """;

        String alterSql = """
            ALTER TABLE acx_players
            ADD COLUMN DiscordId VARCHAR(512) NULL,
            ADD UNIQUE KEY uq_acx_players_discord (DiscordId),
            ADD INDEX idx_acx_players_discord (DiscordId)
        """;

        try (Connection con = DBManager.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(checkSql)) {

            if (rs.next() && rs.getInt(1) > 0) {
                return;
            }

            st.execute(alterSql);
            Log.info("[PlayerManager] DiscordId column added successfully.");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void createTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS acx_players (
                UniqueId VARCHAR(36) NOT NULL,
                Name VARCHAR(16) NOT NULL,
                Password VARCHAR(255) NOT NULL,
    
                RegisterAt TIMESTAMP NULL,
                RegisterAddress VARCHAR(45) NULL,
    
                LastLoginAt TIMESTAMP NULL,
                LastLoginAddress VARCHAR(45) NULL,
                LoginAddressHistory JSON NOT NULL,
    
                DiscordId VARCHAR(512) NULL,
                LastGeoData JSON NULL,
                Settings JSON NULL,
                Meta JSON NULL,
    
                PRIMARY KEY (UniqueId),
                UNIQUE KEY uq_acx_players_name (Name),
                INDEX idx_acx_players_name (Name),
                UNIQUE KEY uq_acx_players_discord (DiscordId),
                INDEX idx_acx_players_discord (DiscordId)
            )
            ENGINE=InnoDB
            DEFAULT CHARSET=utf8mb4
            COLLATE=utf8mb4_general_ci
        """;

        try (Connection con = DBManager.getConnection();
             Statement st = con.createStatement()) {
            st.execute(sql);

            con.createStatement().executeUpdate(
                    "ALTER TABLE acx_players " +
                            "ADD COLUMN IF NOT EXISTS World VARCHAR(255) NULL"
            );

            con.createStatement().executeUpdate(
                    "ALTER TABLE acx_players " +
                            "ADD COLUMN IF NOT EXISTS LocX DOUBLE NULL"
            );

            con.createStatement().executeUpdate(
                    "ALTER TABLE acx_players " +
                            "ADD COLUMN IF NOT EXISTS LocY DOUBLE NULL"
            );

            con.createStatement().executeUpdate(
                    "ALTER TABLE acx_players " +
                            "ADD COLUMN IF NOT EXISTS LocZ DOUBLE NULL"
            );

            con.createStatement().executeUpdate(
                    "ALTER TABLE acx_players " +
                            "ADD COLUMN IF NOT EXISTS Pitch FLOAT NULL"
            );

            con.createStatement().executeUpdate(
                    "ALTER TABLE acx_players " +
                            "ADD COLUMN IF NOT EXISTS Yaw FLOAT NULL"
            );

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void loadAll() {
        try (Connection con = DBManager.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM acx_players");
             ResultSet rs = ps.executeQuery()) {

            int i = 0;
            while (rs.next()) {
                i++;
                PlayerData data = new PlayerData();

                data.setUniqueId(UUID.fromString(rs.getString("UniqueId")));
                data.setUsername(rs.getString("Name"));
                data.setPassword(rs.getString("Password"));

                Timestamp registerAt = rs.getTimestamp("RegisterAt");
                if (registerAt != null) {
                    data.setRegisterAt(registerAt.toInstant());
                }

                data.setRegisterAddress(
                        Address.deserialize(rs.getString("RegisterAddress"))
                );

                Timestamp lastLoginAt = rs.getTimestamp("LastLoginAt");
                if (lastLoginAt != null) {
                    data.setLastLoginAt(lastLoginAt.toInstant());
                }

                data.setLastLoginAddress(
                        Address.deserialize(rs.getString("LastLoginAddress"))
                );

                data.setAddressHistory(
                        new ArrayList<>(Address.deserializeList(rs.getString("LoginAddressHistory")))
                );

                data.setDiscordId(rs.getString("DiscordId"));

                String geoJson = rs.getString("LastGeoData");
                if (geoJson != null && !geoJson.equals("null") && !geoJson.isEmpty())
                    data.setLastGeoData(MAPPER.readValue(geoJson, GeoData.class));

                String settingsJson = rs.getString("Settings");
                if (settingsJson != null && !settingsJson.isEmpty() && !settingsJson.equals("null")) {
                    data.setSettings(MAPPER.readValue(settingsJson, PlayerSettings.class));
                }

                String metaJson = rs.getString("Meta");
                if (metaJson != null && !metaJson.isEmpty() && !metaJson.equals("null")) {
                    data.setMeta(MAPPER.readValue(metaJson, PlayerMeta.class));
                }

                String world = rs.getString("World");
                if (world != null) {
                    double x = rs.getDouble("LocX");
                    double y = rs.getDouble("LocY");
                    double z = rs.getDouble("LocZ");
                    float yaw = rs.getFloat("Yaw");
                    float pitch = rs.getFloat("Pitch");
                    org.bukkit.World w = Bukkit.getWorld(world);
                    if (w != null) data.setLocation(new Location(w, x, y, z, yaw, pitch));
                }

                add(data);

                addIpIndex(playersByLoginIp, data.getLastLoginAddress(), data);
            }

            Log.info("[PlayerManager] Found " + i + " players!");
            LOAD_SUCCESS = true;

        } catch (Exception e) {
            Log.info("[PlayerManager] Failed to load players!");
            e.printStackTrace();
        }
    }

    public static void saveAll() {
        if (!LOAD_SUCCESS) {
            Log.info("[PlayerManager] saveAll() but LOAD_SUCCESS is false. Not saving players.");
            return;
        }

        String sql = """
        INSERT INTO acx_players (
            UniqueId, Name, Password,
            RegisterAt, RegisterAddress,
            LastLoginAt, LastLoginAddress, LoginAddressHistory,
            DiscordID, LastGeoData,
            Settings, Meta, World, LocX, LocY, LocZ, Yaw, Pitch
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
            Name = VALUES(Name),
            Password = VALUES(Password),
            RegisterAt = VALUES(RegisterAt),
            RegisterAddress = VALUES(RegisterAddress),
            LastLoginAt = VALUES(LastLoginAt),
            LastLoginAddress = VALUES(LastLoginAddress),
            LoginAddressHistory = VALUES(LoginAddressHistory),
            DiscordID = VALUES(DiscordID),
            LastGeoData = VALUES(LastGeoData),
            Settings = VALUES(Settings),
            Meta = VALUES(Meta),
           World = VALUES(World),
           LocX = VALUES(LocX),
           LocY = VALUES(LocY),
           LocZ = VALUES(LocZ),
           Yaw = VALUES(Yaw),
           Pitch = VALUES(Pitch)
    """;

        int total = playersByUniqueId.size();

        long tTotal = System.currentTimeMillis();

        try {
            long tConn = System.currentTimeMillis();
            Connection con = DBManager.getConnection();

            try (con; PreparedStatement ps = con.prepareStatement(sql)) {

                con.setAutoCommit(false);

                long tBuild = System.currentTimeMillis();

                for (PlayerData data : playersByUniqueId.values()) {
                    ps.setString(1, data.getUniqueId().toString());
                    ps.setString(2, data.getUsername());
                    ps.setString(3, data.getPassword());

                    if (data.getRegisterAt() != null) {
                        ps.setTimestamp(4, Timestamp.from(data.getRegisterAt()));
                    } else {
                        ps.setNull(4, Types.TIMESTAMP);
                    }

                    ps.setString(5, Address.serialize(data.getRegisterAddress()));

                    if (data.getLastLoginAt() != null) {
                        ps.setTimestamp(6, Timestamp.from(data.getLastLoginAt()));
                    } else {
                        ps.setNull(6, Types.TIMESTAMP);
                    }

                    ps.setString(7, Address.serialize(data.getLastLoginAddress()));
                    ps.setString(8, Address.serializeList(data.getAddressHistory()));
                    ps.setString(9, data.getDiscordId());

                    GeoData geo = data.getLastGeoData();
                    ps.setString(10, geo != null ? MAPPER.writeValueAsString(geo) : null);

                    ps.setString(11, data.getSettings() != null
                            ? MAPPER.writeValueAsString(data.getSettings())
                            : null);

                    ps.setString(12, data.getMeta() != null
                            ? MAPPER.writeValueAsString(data.getMeta())
                            : null);

                    Location loc = data.getLocation();
                    if (loc != null) {
                        ps.setString(13, loc.getWorld().getName());
                        ps.setDouble(14, loc.getX());
                        ps.setDouble(15, loc.getY());
                        ps.setDouble(16, loc.getZ());
                        ps.setFloat(17, loc.getYaw());
                        ps.setFloat(18, loc.getPitch());
                    } else {
                        ps.setNull(13, Types.VARCHAR);
                        ps.setNull(14, Types.DOUBLE);
                        ps.setNull(15, Types.DOUBLE);
                        ps.setNull(16, Types.DOUBLE);
                        ps.setNull(17, Types.FLOAT);
                        ps.setNull(18, Types.FLOAT);
                    }

                    ps.addBatch();
                }

                try {
                    long tExec = System.currentTimeMillis();
                    int[] results = ps.executeBatch();

                    long tCommit = System.currentTimeMillis();
                    con.commit();

                } catch (Exception e) {
                    Log.info("[PlayerManager] Batch execution failed after " + (System.currentTimeMillis() - tTotal) + "ms - rolling back.");
                    con.rollback();
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {
            Log.info("[PlayerManager] saveAll() failed after " + (System.currentTimeMillis() - tTotal) + "ms");
            e.printStackTrace();
        }
    }


    public static List<String> getUsernames(boolean onlineOnly) {
        if (onlineOnly) {
            return playersByName.values().stream()
                    .filter(data -> {
                        Player p = org.bukkit.Bukkit.getPlayer(data.getUniqueId());
                        return p != null && p.isOnline();
                    })
                    .map(PlayerData::getUsername)
                    .toList();
        } else {
            return playersByName.values().stream()
                    .map(PlayerData::getUsername)
                    .toList();
        }
    }

    public static List<String> getUsernames() {
        return getUsernames(false);
    }

    public static @NotNull String getName(UUID uniqueId) {
        PlayerData data = playersByUniqueId.get(uniqueId);
        if (data == null) return "N/A";
        return data.getUsername();
    }

    public static Collection<PlayerData> getOnline() {
        List<PlayerData> online = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData data = playersByUniqueId.get(p.getUniqueId());
            if (data != null && data.isOnline()) online.add(data);
        }
        return online;
    }

    public static Collection<Player> getOnlinePlayers() {
        List<Player> online = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData data = playersByUniqueId.get(p.getUniqueId());
            if (data != null && data.isOnline()) online.add(p);
        }
        return online;
    }

    public static Collection<PlayerData> getAll() {
        return playersByUniqueId.values();
    }

    public static boolean isUsernameOnline(String username) {
        PlayerData data = getByNameIgnoreCase(username);
        return (data != null && data.isOnline());
    }

    public static boolean debugAntiXray(Player p) {
        PlayerData pData = getByUniqueId(p.getUniqueId());
        if (pData == null) return false;

        PlayerMeta pMeta = pData.getMeta();
        if (pMeta == null) return false;

        boolean hasPerm = p.hasPermission("arcane.rank.management");
        if (!hasPerm) pMeta.setAntiXrayDebug(false);

        return pMeta.isAntiXrayDebug();
    }

    public static PlayerData getByUniqueId(UUID uuid) {
        return playersByUniqueId.get(uuid);
    }

    public static boolean isRegistered(UUID uuid) {
        return playersByUniqueId.containsKey(uuid);
    }

    public static PlayerData getByName(String name) {
        return playersByName.get(name);
    }

    public static PlayerData getByNameIgnoreCase(String name) {
        return playersByNameLowercase.get(name.toLowerCase(Locale.ROOT));
    }

    public static AccountType getAccountTypeByName(String name) {
        var a = playersByNameLowercase.get(name.toLowerCase(Locale.ROOT));
        return a == null ? AccountType.UNKNOWN : a.getAccountType();
    }

    public static boolean isUsernameRegistered(String name) {
        var a = playersByNameLowercase.get(name.toLowerCase(Locale.ROOT));
        return a == null ? false : true;
    }

    public static PlayerData newPlayer(Player p, String password) {
        PlayerData pData = new PlayerData();
        pData.setUsername(p.getName());
        pData.setUniqueId(p.getUniqueId());
        pData.setRegisterAt(Instant.now());
        pData.setLastLoginAt(Instant.now());
        pData.setRegisterAddress(p.getAddress().getAddress());
        pData.setLastLoginAddress(p.getAddress().getAddress());
        pData.addToAddressHistory(p.getAddress().getAddress());
        pData.setPassword(password);
        pData.setTimezone("UTC");
        pData.setLastGeoData(
                GeoManager.lookup(p.getAddress().getAddress())
        );

        PlayerManager.add(pData);
        addIpIndex(playersByLoginIp, pData.getLastLoginAddress(), pData);

        return pData;
    }

    public static PlayerData updatePlayer(Player p, boolean updateUsername) {
        PlayerData pData = getByUniqueId(p.getUniqueId());
        if (pData == null) return null;

        InetAddress lastLoginIp = pData.getLastLoginAddress();
        InetAddress newLoginIp = p.getAddress().getAddress();

        String lastIpStr = lastLoginIp.getHostAddress();
        String newIpStr = newLoginIp.getHostAddress();

        removeIpIndex(playersByLoginIp, lastLoginIp, pData);

        pData.setLastLoginAt(Instant.now());
        pData.setLastLoginAddress(newLoginIp);
        pData.addToAddressHistory(newLoginIp);

        addIpIndex(playersByLoginIp, newLoginIp, pData);

        if (updateUsername) {
            String oldUsername = pData.getUsername();
            String newUsername = p.getName();

            if (!oldUsername.equals(newUsername)) {

                playersByName.remove(oldUsername);
                playersByNameLowercase.remove(oldUsername.toLowerCase(Locale.ROOT));

                pData.setUsername(newUsername);

                // UsernameChangeEvent.fire(p, pData, oldUsername, newUsername);

                playersByName.put(newUsername, pData);
                playersByNameLowercase.put(newUsername.toLowerCase(Locale.ROOT), pData);
            }
        }

        if (!newLoginIp.equals(lastLoginIp)) {
            // DifferentLoginLocationEvent.fire(p, pData, lastIpStr, newIpStr);
        }

        pData.setLastGeoData(
                GeoManager.lookup(newLoginIp)
        );

        return pData;
    }

    public static void add(PlayerData data) {
        playersByUniqueId.put(data.getUniqueId(), data);
        playersByName.put(data.getUsername(), data);
        playersByNameLowercase.put(data.getUsername().toLowerCase(Locale.ROOT), data);

        if (data.getDiscordId() != null) {
            playersByDiscordId.put(data.getDiscordId(), data);
        }
    }

    public static Location getLocation(UUID uuid) {
        PlayerData pData = playersByUniqueId.get(uuid);
        if (pData == null) return null;
        return pData.getLocation();
    }

    public static boolean linkDiscord(UUID playerUUID, String discordId) {
        if (playersByDiscordId.containsKey(discordId)) {
            return false;
        }

        PlayerData pData = getByUniqueId(playerUUID);
        if (pData == null) return false;

        if (pData.getDiscordId() != null) {
            playersByDiscordId.remove(pData.getDiscordId());
        }

        pData.setDiscordId(discordId);
        playersByDiscordId.put(discordId, pData);

        /*Discord.log(ArcaneChannel.MANAGEMENT_LOGS_LINK, List.of(
                String.format("**%s** has been linked to <@%s> (`%s`).", pData.getUsername(), discordId, discordId),
                String.format("```%s```", pData.getUniqueId().toString())
        ));

        DiscordManager.syncRanks(playerUUID);*/

        return true;
    }

    public static void unlinkDiscord(UUID playerUUID, String by) {
        PlayerData data = getByUniqueId(playerUUID);
        if (data == null || data.getDiscordId() == null) return;

        String discordId = data.getDiscordId();

        playersByDiscordId.remove(data.getDiscordId());
        data.setDiscordId(null);

        /*Discord.log(ArcaneChannel.MANAGEMENT_LOGS_UNLINK, List.of(
                String.format("**%s** has been unlinked from <@%s> (`%s`) by **%s**.", data.getUsername(), discordId, discordId, by),
                String.format("```%s```", data.getUniqueId().toString())
        ));

        DiscordManager.syncRanks(playerUUID);*/
    }

    public static PlayerData getByDiscordId(String discordId) {
        return playersByDiscordId.get(discordId);
    }

    public static void clear(PlayerData data) {
        playersByName.clear();
        playersByUniqueId.clear();
        playersByNameLowercase.clear();
    }

    private static void addIpIndex(
            ConcurrentHashMap<InetAddress, Set<PlayerData>> map,
            InetAddress ip,
            PlayerData data
    ) {
        if (ip == null) return;
        map.computeIfAbsent(ip, k -> ConcurrentHashMap.newKeySet()).add(data);
    }

    private static void removeIpIndex(
            ConcurrentHashMap<InetAddress, Set<PlayerData>> map,
            InetAddress ip,
            PlayerData data
    ) {
        if (ip == null) return;
        Set<PlayerData> set = map.get(ip);
        if (set != null) {
            set.remove(data);
            if (set.isEmpty()) {
                map.remove(ip);
            }
        }
    }

    // only returns if player online
    public static @Nullable Player getPlayer(UUID uniqueId) {
        PlayerData pData = getByUniqueId(uniqueId);
        if (pData == null) return null;
        if (!pData.isOnline()) return null;

        return pData.getPlayer();
    }

    public static @Nullable PlayerMeta getMeta(UUID uniqueId) {
        PlayerData pData = getByUniqueId(uniqueId);
        if (pData == null) return null;

        return pData.getMeta();
    }

    public static List<PlayerData> getPlayersWithSameLoginIp(InetAddress ip) {
        return new ArrayList<>(playersByLoginIp.getOrDefault(ip, Set.of()));
    }

    public static Set<PlayerData> getPlayersWithRelatedIp(PlayerData target) {
        if (target == null) return Set.of();

        Set<PlayerData> related = new HashSet<>();

        InetAddress loginIp = target.getLastLoginAddress();

        if (loginIp != null) {
            related.addAll(getPlayersWithSameLoginIp(loginIp));
        }

        related.remove(target);

        return related;
    }

    public static List<PlayerData> getTopKills(int limit) {
        return playersByUniqueId.values().stream()
                .sorted(Comparator.<PlayerData, Long>comparing(d -> (long) d.getMeta().getKills()).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public static List<PlayerData> getTopVotes(int limit) {
        return playersByUniqueId.values().stream()
                .sorted(Comparator.<PlayerData, Long>comparing(d -> (long) d.getMeta().getTotalVotes()).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public static List<PlayerData> getTopDeaths(int limit) {
        return playersByUniqueId.values().stream()
                .sorted(Comparator.<PlayerData, Long>comparing(d -> (long) d.getMeta().getDeaths()).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public static int getKillsPosition(UUID uuid) {
        List<PlayerData> sorted = playersByUniqueId.values().stream()
                .sorted(Comparator.<PlayerData, Long>comparing(d -> d.getMeta().getKills()).reversed())
                .toList();
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getUniqueId().equals(uuid)) return i + 1;
        }
        return -1;
    }

    public static int getVotesPosition(UUID uuid) {
        List<PlayerData> sorted = playersByUniqueId.values().stream()
                .sorted(Comparator.<PlayerData, Long>comparing(d -> d.getMeta().getTotalVotes()).reversed())
                .toList();
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getUniqueId().equals(uuid)) return i + 1;
        }
        return -1;
    }

    public static int getDeathsPosition(UUID uuid) {
        List<PlayerData> sorted = playersByUniqueId.values().stream()
                .sorted(Comparator.<PlayerData, Long>comparing(d -> d.getMeta().getDeaths()).reversed())
                .toList();
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getUniqueId().equals(uuid)) return i + 1;
        }
        return -1;
    }

    public static int getKdrPosition(UUID uuid) {
        List<PlayerData> sorted = playersByUniqueId.values().stream()
                .sorted(Comparator.<PlayerData, Double>comparing(d -> {
                    long deaths = d.getMeta().getDeaths();
                    return deaths == 0 ? (double) d.getMeta().getKills() : (double) d.getMeta().getKills() / deaths;
                }).reversed())
                .toList();
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getUniqueId().equals(uuid)) return i + 1;
        }
        return -1;
    }

    public static int getKillstreakPosition(UUID uuid) {
        List<PlayerData> sorted = playersByUniqueId.values().stream()
                .sorted(Comparator.<PlayerData, Long>comparing(d -> d.getMeta().getKillstreak()).reversed())
                .toList();
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getUniqueId().equals(uuid)) return i + 1;
        }
        return -1;
    }

    public static List<PlayerData> getTopPlaytime(int limit) {
        return playersByUniqueId.values().stream()
                .sorted(Comparator.<PlayerData, Long>comparing(d -> d.getMeta().getPlaytimeSeconds()).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public static int getPlaytimePosition(UUID uuid) {
        List<PlayerData> sorted = playersByUniqueId.values().stream()
                .sorted(Comparator.<PlayerData, Long>comparing(d -> d.getMeta().getPlaytimeSeconds()).reversed())
                .toList();
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getUniqueId().equals(uuid)) return i + 1;
        }
        return -1;
    }

    public static void refreshLeaderboard() {
        long start = System.currentTimeMillis();

        List<PlayerData> all = new ArrayList<>(playersByUniqueId.values());
        if (all.isEmpty()) {
            overallLeaderboardCache = List.of();
            return;
        }

        Map<UUID, Double> scores = new HashMap<>();

        for (ScoredMetric scoredMetric : METRICS) {
            ToLongFunction<PlayerData> metric = scoredMetric.function();
            double weight = scoredMetric.weight();

            List<PlayerData> sorted = all.stream()
                    .sorted(Comparator.comparingLong(metric).reversed())
                    .toList();

            // Only evaluate the Top 10 of each metric
            for (int i = 0; i < Math.min(10, sorted.size()); i++) {
                PlayerData d = sorted.get(i);
                if (metric.applyAsLong(d) == 0) continue;

                // Base points: 10 for 1st, 1 for 10th. Multiply by category weight.
                double points = (10.0 - i) * weight;
                scores.merge(d.getUniqueId(), points, Double::sum);
            }
        }

        // Sort the players by their total weighted points
        overallLeaderboardCache = scores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .map(e -> playersByUniqueId.get(e.getKey()))
                .filter(Objects::nonNull)
                .toList();

        Log.info("[PlayerManager] Overall leaderboard refreshed in " + (System.currentTimeMillis() - start) + "ms");

        updateTopNpcAndHologram(1);
        updateTopNpcAndHologram(2);
        updateTopNpcAndHologram(3);
    }

    private static void updateTopNpcAndHologram(int pos) {
        PlayerData top = getTopPlayerByPosition(pos);
        if (top == null) return;

        String key = "top" + pos;
        Log.info("[PlayerManager] Top {} - {}", pos, top.getUsername());

        Hologram holo = FancyHologramsPlugin.get().getHologramManager().getHologram(key).orElse(null);
        if (holo != null) {
            TextHologramData holoData = (TextHologramData) holo.getData();
            holoData.getText().set(1, "&r&f" + top.getUsername());
            holo.refreshForViewersInWorld();
        }

        Npc npc = FancyNpcsPlugin.get().getNpcManager().getNpc(key);
        if (npc == null) return;

        CachedSkin skin = SkinManager.getCachedSkin(top.getUniqueId());
        if (skin != null) {
            SkinData skinData = new SkinData(top.getUsername(), SkinData.SkinVariant.AUTO, skin.getSkinData(), skin.getSkinSignature());
            npc.getData().setMirrorSkin(false);
            npc.getData().setSkinData(skinData);
        } else {
            npc.getData().setSkin("ZaBIe");
        }

        npc.removeForAll();
        npc.create();
        npc.spawnForAll();
    }

    public static List<PlayerData> getTopPlayers(int limit) {
        List<PlayerData> cached = overallLeaderboardCache;
        return cached.size() <= limit ? cached : cached.subList(0, limit);
    }

    public static PlayerData getTopPlayerByPosition(int pos) {
        List<PlayerData> cached = overallLeaderboardCache;
        if (pos < 1 || pos > cached.size()) return null;
        return cached.get(pos - 1);
    }

    public static int getOverallPosition(UUID uuid) {
        List<PlayerData> cached = overallLeaderboardCache;
        for (int i = 0; i < cached.size(); i++) {
            if (cached.get(i).getUniqueId().equals(uuid)) return i + 1;
        }
        return -1;
    }

    public static PlayerSession getSession(UUID uuid) {
        return NetUtils.getSession(uuid);
    }

    public static void broadcast(String message) {
        broadcast(Component.text(message));
    }

    public static void broadcast(Component message) {
        for (PlayerData data : getOnline()) {
            if (!data.getSettings().isShowSystemMessages()) continue;

            data.getPlayer().sendMessage(message);
        }
    }

    public static void broadcast(String message, Sound sound) {
        broadcast(Component.text(message), sound);
    }

    public static void broadcast(Component message, Sound sound) {
        broadcast(message, sound, 1.0);
    }

    public static void broadcast(Component message, Sound sound, double pitch) {
        for (PlayerData data : getOnline()) {
            if (!data.getSettings().isShowSystemMessages()) continue;

            Player p = data.getPlayer();
            p.sendMessage(message);
            p.playSound(p.getLocation(), sound, 1.0f, 1.0f);
        }
    }

    public static void broadcast(Sound sound) {
        broadcast(sound, 1.0);
    }

    public static void broadcast(Sound sound, double pitch) {
        for (PlayerData data : getOnline()) {
            if (!data.getSettings().isShowSystemMessages()) continue;

            Player p = data.getPlayer();
            p.playSound(p.getLocation(), sound, 1.0f, 1.0f);
        }
    }

    public static List<PlayerData> getTopMostBought(int limit) {
        return playersByUniqueId.values().stream()
                .sorted(Comparator.<PlayerData, Long>comparing(d -> (long) d.getMeta().getTotalBought()).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public static int getMostBoughtPosition(UUID uuid) {
        List<PlayerData> sorted = playersByUniqueId.values().stream()
                .sorted(Comparator.<PlayerData, Long>comparing(d -> d.getMeta().getTotalBought()).reversed())
                .toList();
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getUniqueId().equals(uuid)) return i + 1;
        }
        return -1;
    }

    public static List<PlayerData> getTopMostSold(int limit) {
        return playersByUniqueId.values().stream()
                .sorted(Comparator.<PlayerData, Long>comparing(d -> (long) d.getMeta().getTotalSold()).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public static int getMostSoldPosition(UUID uuid) {
        List<PlayerData> sorted = playersByUniqueId.values().stream()
                .sorted(Comparator.<PlayerData, Long>comparing(d -> d.getMeta().getTotalSold()).reversed())
                .toList();
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getUniqueId().equals(uuid)) return i + 1;
        }
        return -1;
    }

    public static void checkOps(CommandSender s) {
        if (!Bukkit.getOperators().isEmpty()) {
            if (!(s instanceof ConsoleCommandSender || s instanceof  Player && s.hasPermission("arcane.rank.management"))) return;
            s.sendMessage(Component.text("Player Operators (" + Bukkit.getOperators().size() + "):", Colors.HOT_PINK));
            for (OfflinePlayer op : Bukkit.getOperators()) {
                s.sendMessage(Component.text("- " + op.getName(), Colors.DARK_PINK));
            }
        }
    }

    public static List<PlayerData> getTopMostCrystals(int limit) {
        return playersByUniqueId.values().stream()
                .sorted(Comparator.<PlayerData, Long>comparing(d -> (long) d.getMeta().getCrystalsExploded()).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public static int getMostCrystalsPosition(UUID uuid) {
        List<PlayerData> sorted = playersByUniqueId.values().stream()
                .sorted(Comparator.<PlayerData, Long>comparing(d -> d.getMeta().getCrystalsExploded()).reversed())
                .toList();
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getUniqueId().equals(uuid)) return i + 1;
        }
        return -1;
    }

    public static List<PlayerData> getTopMostAnchors(int limit) {
        return playersByUniqueId.values().stream()
                .sorted(Comparator.<PlayerData, Long>comparing(d -> (long) d.getMeta().getAnchorsExploded()).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public static int getMostAnchorsPosition(UUID uuid) {
        List<PlayerData> sorted = playersByUniqueId.values().stream()
                .sorted(Comparator.<PlayerData, Long>comparing(d -> d.getMeta().getAnchorsExploded()).reversed())
                .toList();
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getUniqueId().equals(uuid)) return i + 1;
        }
        return -1;
    }

       /* =========================
       State queries
       ========================= */

    public static boolean isVanished(Player player) {
        PlayerData data = getByUniqueId(player.getUniqueId());
        return data != null && data.getMeta().isVanish();
    }

    public static boolean isVanished(UUID uuid) {
        PlayerData data = getByUniqueId(uuid);
        return data != null && data.getMeta().isVanish();
    }

    public static boolean canVanish(Player player) {
        return player.hasPermission("arcane.rank.management");
    }

    /* =========================
       State mutation
       ========================= */

    public static void setVanished(Player player, boolean vanished) {
        PlayerData data = getByUniqueId(player.getUniqueId());
        if (data == null) return;

        // SAFETY: cannot enable vanish without permission
        if (vanished && !canVanish(player)) {
            return;
        }

        if (data.getMeta().isVanish() == vanished) return;

        data.getMeta().setVanish(vanished);
        updateVisibilityFor(player);
    }

    /* =========================
       Visibility logic (ONLY PLACE)
       ========================= */

    public static boolean canSee(Player viewer, Player target) {
        if (target == null) return false;
        if (viewer == null) return false;

        if (viewer.equals(target)) return true;

        if (isVanished(target)) {
            return canVanish(viewer);
        } else {
            return true;
        }
    }

    private final static BossBar vanishBar = BossBar.bossBar(Text.toSmallCapsComponent("YOU ARE VANISHED!").color(Colors.HOT_PINK), BossBar.MAX_PROGRESS, BossBar.Color.RED, BossBar.Overlay.PROGRESS);

    /** Updates how everyone sees this player | OTHERS -> PLAYER */
    public static void updateVisibilityFor(Player target) {
        boolean vanished = isVanished(target);

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(target)) continue;

            if (vanished) {
                if (canVanish(viewer)) {
                    viewer.showPlayer(Arcane.getPlugin(), target);
                } else {
                    viewer.hidePlayer(Arcane.getPlugin(), target);
                }
            } else {
                viewer.showPlayer(Arcane.getPlugin(), target);
            }
        }

        if (vanished) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false, true));
            target.showBossBar(vanishBar);
        } else {
            target.removePotionEffect(PotionEffectType.NIGHT_VISION);
            target.hideBossBar(vanishBar);
        }
    }

    /** Updates what player sees | PLAYER -> OTHERS*/
    public static void updateVisibilityForJoiner(Player joiner) {
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (joiner.equals(target)) continue;

            if (isVanished(target)
                    && !canVanish(joiner)) {
                joiner.hidePlayer(Arcane.getPlugin(), target);
            } else {
                joiner.showPlayer(Arcane.getPlugin(), target);
            }
        }
    }
}
