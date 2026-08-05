package cx.arcane.managers.clanManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import cx.arcane.Arcane;
import cx.arcane.managers.dbManager.DBManager;
import cx.arcane.managers.clanManager.clanInfo.ClanData;
import cx.arcane.managers.clanManager.clanInfo.ClanMember;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClanManager {
    private static final Object lock = new Object();

    private static ConcurrentHashMap<UUID, ClanData> clansById = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<String, ClanData> clansByTag = new ConcurrentHashMap<>();

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public static void onEnable() {
        createTable();
        loadAll();
    }

    public static void onDisable() {
        createTable();
        saveAll();
    }

    public static void onSave() {
        createTable();
        saveAll();
    }

    private static void loadAll() {
        try (Connection con = DBManager.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM acx_clans");
             ResultSet rs = ps.executeQuery()) {

            int i = 0;

            while (rs.next()) {
                String clanJson = rs.getString("ClanData");

                if (clanJson == null || clanJson.isBlank()) {
                    continue;
                }

                ClanData clan = MAPPER.readValue(clanJson, ClanData.class);
                add(clan);
                i++;
            }

            Arcane.getPlugin().getLogger().info("[ClanManager] Found " + i + " clans!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void saveAll() {
        String upsertSql = """
        INSERT INTO acx_clans (UniqueId, Tag, CreatedAt, ClanData)
        VALUES (?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
            Tag = VALUES(Tag),
            CreatedAt = VALUES(CreatedAt),
            ClanData = VALUES(ClanData)
        """;

        synchronized (lock) {
            try (Connection con = DBManager.getConnection()) {
                con.setAutoCommit(false);

                try {
                    // Remove rows that no longer exist in memory
                    purgeMissingRows(con);

                    // Upsert current clans
                    try (PreparedStatement ps = con.prepareStatement(upsertSql)) {
                        for (ClanData clan : clansById.values()) {
                            ps.setString(1, clan.getUniqueId().toString());
                            ps.setString(2, clan.getTag());

                            if (clan.getCreatedAt() != null) {
                                ps.setTimestamp(3, Timestamp.from(clan.getCreatedAt()));
                            } else {
                                ps.setNull(3, Types.TIMESTAMP);
                            }

                            ps.setString(4, MAPPER.writeValueAsString(clan));
                            ps.addBatch();
                        }

                        ps.executeBatch();
                    }

                    con.commit();
                } catch (Exception e) {
                    con.rollback();
                    throw e;
                } finally {
                    con.setAutoCommit(true);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void purgeMissingRows(Connection con) throws SQLException {
        if (clansById.isEmpty()) {
            try (Statement st = con.createStatement()) {
                st.executeUpdate("DELETE FROM acx_clans");
            }
            return;
        }

        String placeholders = String.join(",", Collections.nCopies(clansById.size(), "?"));
        String sql = "DELETE FROM acx_clans WHERE UniqueId NOT IN (" + placeholders + ")";

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            for (UUID uuid : clansById.keySet()) {
                ps.setString(i++, uuid.toString());
            }
            ps.executeUpdate();
        }
    }

    private static void createTable() {
        String sql = """
        CREATE TABLE IF NOT EXISTS acx_clans (
           UniqueId VARCHAR(36) NOT NULL,
           Tag VARCHAR(36) NOT NULL,
           CreatedAt TIMESTAMP NULL,
           ClanData JSON NOT NULL,
           PRIMARY KEY (UniqueId),
           UNIQUE KEY (Tag)
       )
       ENGINE=InnoDB
       DEFAULT CHARSET=utf8mb4
       COLLATE=utf8mb4_general_ci;
        """;

        try (Connection con = DBManager.getConnection();
             Statement st = con.createStatement()) {

            st.execute(sql);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void add(ClanData clan) {
        clansById.put(clan.getUniqueId(), clan);
        clansByTag.put(clan.getTag().toUpperCase(Locale.ROOT), clan);
    }

    public static Collection<ClanData> getAll() {
        return clansById.values();
    }

    public static ClanData getByUniqueId(UUID uuid) {
        return clansById.get(uuid);
    }

    public static ClanData getByName(String name) {
        return name == null ? null : clansByTag.get(name.toUpperCase(Locale.ROOT));
    }

    public static boolean isNameTaken(String name) {
        return getByName(name.toUpperCase(Locale.ROOT)) != null;
    }

    public static ClanData getPlayerClan(UUID playerId) {
        for (ClanData clan : clansById.values()) {
            if (clan.isMember(playerId)) {
                return clan;
            }
        }
        return null;
    }

    public static boolean hasClan(UUID playerId) {
        return getPlayerClan(playerId) != null;
    }

    public static ClanData newClan(String clanTag, UUID creatorId) {
        ClanData cData = new ClanData();
        cData.setUniqueId(UUID.randomUUID());
        cData.setCreatedAt(Instant.now());
        cData.setCreatedById(creatorId);
        cData.setTag(clanTag.toUpperCase(Locale.ROOT));
        cData.setHome(null);

        ClanMember creatorMember = new ClanMember();
        creatorMember.setUniqueId(creatorId);
        creatorMember.setRank("Leader");
        creatorMember.setJoinedAt(Instant.now());
        cData.addMember(creatorMember);

        add(cData);

        return cData;
    }

    public static boolean deleteClan(UUID clanUUID) {
        if (clanUUID == null) return false;

        ClanData removed;
        synchronized (lock) {
            removed = clansById.remove(clanUUID);
            if (removed != null) {
                clansByTag.remove(removed.getTag().toUpperCase(Locale.ROOT));
            }
        }

        if (removed == null) return false;

        removeInvitesForClan(clanUUID);
        deleteClanRow(clanUUID);

        return true;
    }

    private static void deleteClanRow(UUID clanUUID) {
        String sql = "DELETE FROM acx_clans WHERE UniqueId = ?";

        try (Connection con = DBManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, clanUUID.toString());
            ps.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void removeInvitesForClan(UUID clanUUID) {
        invitesByTarget.forEach((playerId, clanInvites) -> {
            clanInvites.entrySet().removeIf(entry ->
                    entry.getValue() != null && clanUUID.equals(entry.getValue().getClanId()));

            if (clanInvites.isEmpty()) {
                invitesByTarget.remove(playerId);
            }
        });
    }

    /**
     * Check if two players are in the SAME clan
     */
    public static boolean isSameClan(UUID playerA, UUID playerB) {
        if (playerA == null || playerB == null) return false;

        ClanData clanA = getPlayerClan(playerA);
        if (clanA == null) return false;

        return clanA.isMember(playerB);
    }


    // =======================
    // Invite system START
    // =======================

    // targetPlayer -> (clanId -> invite)
    private static final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, ClanInvite>> invitesByTarget
            = new ConcurrentHashMap<>();

    private static final long INVITE_DURATION_SECONDS = 60;

    /**
     * Removes expired invites and cleans empty maps
     */
    private static void cleanupExpiredInvites() {
        invitesByTarget.forEach((playerId, clanInvites) -> {
            clanInvites.entrySet().removeIf(e -> e.getValue().isExpired());

            if (clanInvites.isEmpty()) {
                invitesByTarget.remove(playerId);
            }
        });
    }

    /**
     * Get all invites received by a player
     */
    public static Collection<ClanInvite> getPlayerReceivedInvites(UUID playerId) {
        cleanupExpiredInvites();

        Map<UUID, ClanInvite> invites = invitesByTarget.get(playerId);
        return invites == null ? List.of() : invites.values();
    }

    /**
     * Get all invites sent by a clan
     */
    public static Collection<ClanInvite> getClanSentInvites(UUID clanUUID) {
        cleanupExpiredInvites();

        return invitesByTarget.values()
                .stream()
                .flatMap(map -> map.values().stream())
                .filter(invite -> invite.getClanId().equals(clanUUID))
                .toList();
    }

    /**
     * Invite a player to a clan
     */
    public static boolean inviteToClan(UUID clanUUID, UUID inviterId, UUID targetPlayer) {
        cleanupExpiredInvites();

        ClanData clan = getByUniqueId(clanUUID);
        if (clan == null) return false;

        // Player already in a clan
        if (hasClan(targetPlayer)) return false;

        invitesByTarget.putIfAbsent(targetPlayer, new ConcurrentHashMap<>());
        ConcurrentHashMap<UUID, ClanInvite> playerInvites = invitesByTarget.get(targetPlayer);

        // Already invited by this clan
        if (playerInvites.containsKey(clanUUID)) return false;

        ClanInvite invite = new ClanInvite(
                clanUUID,
                inviterId,
                targetPlayer,
                Instant.now().plusSeconds(INVITE_DURATION_SECONDS)
        );

        playerInvites.put(clanUUID, invite);
        return true;
    }

    /**
     * Accept an invite from a specific clan
     */
    public static boolean acceptInvite(UUID playerId, UUID clanUUID) {
        cleanupExpiredInvites();

        Map<UUID, ClanInvite> playerInvites = invitesByTarget.get(playerId);
        if (playerInvites == null) return false;

        ClanInvite invite = playerInvites.remove(clanUUID);
        if (invite == null || invite.isExpired()) return false;

        if (playerInvites.isEmpty()) {
            invitesByTarget.remove(playerId);
        }

        ClanData clan = getByUniqueId(clanUUID);
        if (clan == null) return false;

        ClanMember member = new ClanMember();
        member.setUniqueId(playerId);
        member.setRank("Member");
        member.setJoinedAt(Instant.now());

        clan.addMember(member);
        return true;
    }

    /**
     * Deny an invite from a specific clan
     */
    public static void denyInvite(UUID playerId, UUID clanUUID) {
        Map<UUID, ClanInvite> playerInvites = invitesByTarget.get(playerId);
        if (playerInvites == null) return;

        playerInvites.remove(clanUUID);

        if (playerInvites.isEmpty()) {
            invitesByTarget.remove(playerId);
        }
    }

    /**
     * Check if player has ANY valid invite
     */
    public static boolean hasValidInvite(UUID playerId) {
        cleanupExpiredInvites();
        return invitesByTarget.containsKey(playerId);
    }

    /**
     * Check if player has a valid invite from a specific clan
     */
    public static boolean hasValidInvite(UUID playerId, UUID clanUUID) {
        cleanupExpiredInvites();

        Map<UUID, ClanInvite> invites = invitesByTarget.get(playerId);
        return invites != null && invites.containsKey(clanUUID);
    }

    // =======================
    // Invite system END
    // =======================

    @NotNull
    public static List<ClanData> getTopClansByKills(int limit) {
        synchronized (lock) {
            return clansById.values().stream()
                    .sorted((a, b) -> Long.compare(b.getKills(), a.getKills()))
                    .limit(limit)
                    .toList();
        }
    }

    public static ClanData getClanAtPosition(int position) {
        if (position <= 0) return null;

        synchronized (lock) {
            return clansById.values().stream()
                    .sorted((a, b) -> Long.compare(b.getKills(), a.getKills()))
                    .skip(position - 1)
                    .findFirst()
                    .orElse(null);
        }
    }

    public static int getClanPosition(UUID clanUUID) {
        synchronized (lock) {
            List<ClanData> sortedList = clansById.values().stream()
                    .sorted((a, b) -> Long.compare(b.getKills(), a.getKills()))
                    .toList();

            for (int i = 0; i < sortedList.size(); i++) {
                if (sortedList.get(i).getUniqueId().equals(clanUUID)) {
                    return i + 1; // 1-based position
                }
            }

            return -1; // Not found
        }
    }

    public static int getClanPosition(String clanName) {
        ClanData clan = getByName(clanName);
        if (clan == null) return -1;

        return getClanPosition(clan.getUniqueId());
    }

    public static ClanData getTopClan() {
        return getClanAtPosition(1);
    }
}