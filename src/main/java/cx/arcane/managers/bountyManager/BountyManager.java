package cx.arcane.managers.bountyManager;

import cx.arcane.Arcane;
import cx.arcane.managers.clanManager.ClanManager;
import cx.arcane.managers.dbManager.DBManager;
import cx.arcane.managers.ecoManager.EcoManager;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.utils.Colors;
import cx.arcane.utils.Log;
import cx.arcane.utils.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BountyManager {

    private BountyManager() {}

    private static final ConcurrentHashMap<UUID, Bounty> BOUNTIES = new ConcurrentHashMap<>();

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

    public static void createTables() {
        try (Connection con = DBManager.getConnection(); Statement st = con.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS acx_bounty (
                    Id VARCHAR(36) NOT NULL,
                    Reward BIGINT NOT NULL,
                    PRIMARY KEY (Id)
                ) ENGINE=InnoDB
            """);
        } catch (SQLException e) {
            Log.error("[BountyManager] Table creation failed");
            e.printStackTrace();
        }
    }

    public static void loadAll() {
        BOUNTIES.clear();

        try (Connection con = DBManager.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM acx_bounty");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                UUID id = UUID.fromString(rs.getString("Id"));
                long reward = rs.getLong("Reward");
                BOUNTIES.put(id, new Bounty(id, reward));
            }

            Log.info("[BountyManager] Loaded {} bounties.", BOUNTIES.size());
        } catch (Exception e) {
            Log.error("[BountyManager] loadAll() failed");
            e.printStackTrace();
        }
    }

    public static void saveAll() {
        long t = System.currentTimeMillis();

        String upsertSql = """
            INSERT INTO acx_bounty (Id, Reward)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE Reward = VALUES(Reward)
        """;

        try (Connection con = DBManager.getConnection()) {
            con.setAutoCommit(false);

            try (PreparedStatement ps = con.prepareStatement(upsertSql)) {
                for (Bounty bounty : BOUNTIES.values()) {
                    ps.setString(1, bounty.getUniqueId().toString());
                    ps.setLong(2, bounty.getReward());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            try (Statement st = con.createStatement()) {
                if (!BOUNTIES.isEmpty()) {
                    String ids = BOUNTIES.keySet().stream()
                            .map(uuid -> "'" + uuid + "'")
                            .collect(java.util.stream.Collectors.joining(","));
                    st.executeUpdate("DELETE FROM acx_bounty WHERE Id NOT IN (" + ids + ")");
                } else {
                    st.executeUpdate("DELETE FROM acx_bounty");
                }
            }

            con.commit();
            Log.info("[BountyManager] saveAll() completed in {}ms", System.currentTimeMillis() - t);
        } catch (Exception e) {
            Log.error("[BountyManager] saveAll() failed");
            e.printStackTrace();
        }
    }

    public static Bounty getBounty(@NotNull UUID uniqueId) {
        return BOUNTIES.get(uniqueId);
    }

    public static boolean hasBounty(@NotNull UUID uniqueId) {
        return BOUNTIES.containsKey(uniqueId);
    }

    public static void increaseBounty(@NotNull UUID uniqueId, long amount) {
        BOUNTIES.compute(uniqueId, (k, existing) -> {
            if (existing != null) {
                existing.increaseReward(amount);
                return existing;
            }
            return new Bounty(k, amount);
        });
    }

    public static List<Bounty> getBounties() {
        return BOUNTIES.values().stream().toList();
    }

    public static int getBountyPosition(@NotNull UUID uuid) {
        List<Bounty> sorted = BOUNTIES.values().stream()
                .sorted((a, b) -> Long.compare(b.getReward(), a.getReward()))
                .toList();
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getUniqueId().equals(uuid)) return i + 1;
        }
        return -1;
    }

    public static void handlePlaceBounty(@NotNull Player p, @NotNull UUID targetId, long reward) {
        p.playSound(p.getLocation(), Sound.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1, 1);

        Component txt = Component.text(p.getName(), Colors.HOT_PINK)
                .append(Component.text(" has placed a ", Colors.WHITE))
                .append(Component.text("$" + Text.formatShortBalance(reward), Colors.HOT_PINK))
                .append(Component.text(" bounty on ", Colors.WHITE))
                .append(Component.text(Objects.requireNonNull(PlayerManager.getName(targetId)) + "!", Colors.HOT_PINK));

        Component ac = Component.text()
                .append(Component.text("You placed a ", Colors.WHITE))
                .append(Component.text("$" + Text.formatShortBalance(reward), Colors.HOT_PINK))
                .append(Component.text(" bounty on ", Colors.WHITE))
                .append(Component.text(Objects.requireNonNull(PlayerManager.getName(targetId)), Colors.HOT_PINK))
                .build();
        p.sendActionBar(ac);

        PlayerManager.broadcast(txt);

        EcoManager.takeMoney(p.getUniqueId(), reward);
        increaseBounty(targetId, reward);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(p)) continue;
            online.playSound(online.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.MASTER, 1, 1);
            online.playSound(online.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 1, 1);
        }
    }

    public static void handleClaimBounty(@NotNull Player p, @NotNull UUID targetId) {
        Bounty b = getBounty(targetId);
        if (b == null || b.getReward() <= 0) return;

        if (ClanManager.isSameClan(p.getUniqueId(), targetId)) return;

        PlayerData killerData = PlayerManager.getByUniqueId(p.getUniqueId());
        PlayerData targetData = PlayerManager.getByUniqueId(targetId);
        if (killerData == null || targetData == null) return;

        if (killerData.getLastLoginAddress().equals(targetData.getLastLoginAddress())) return;

        Component txt = Component.text(p.getName(), Colors.HOT_PINK)
                .append(Component.text(" has claimed the ", Colors.WHITE))
                .append(Component.text("$" + Text.formatShortBalance(b.getReward()), Colors.HOT_PINK))
                .append(Component.text(" bounty on ",Colors.WHITE))
                .append(Component.text(Objects.requireNonNull(PlayerManager.getName(targetId)) + "!", Colors.HOT_PINK));

        Component ac = Component.text()
                .append(Component.text("You received ", Colors.WHITE))
                .append(Component.text("$" + Text.formatShortBalance(b.getReward()), Colors.HOT_PINK))
                .append(Component.text(" for killing ", Colors.WHITE))
                .append(Component.text(Objects.requireNonNull(PlayerManager.getName(targetId)), Colors.HOT_PINK))
                .build();

        p.sendActionBar(ac);

        EcoManager.giveMoney(p.getUniqueId(), b.getReward());
        PlayerManager.broadcast(txt, Sound.ENTITY_EXPERIENCE_ORB_PICKUP);

        BOUNTIES.remove(targetId);
    }
}