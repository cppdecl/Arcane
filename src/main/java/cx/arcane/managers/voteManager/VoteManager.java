package cx.arcane.managers.voteManager;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import cx.arcane.Arcane;
import cx.arcane.managers.crateManager.CrateManager;
import cx.arcane.managers.dbManager.DBManager;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.utils.Colors;
import cx.arcane.utils.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VoteManager {
    private static final ConcurrentHashMap<UUID, List<VoteAction>> pendingVotes = new ConcurrentHashMap<>();
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final Random RANDOM = new Random();
    private static final LinkedHashMap<String, Integer> BONUS_KEYS = new LinkedHashMap<>();

    public static void onEnable() {
        createTable();
        VoteStatistics.onEnable(Arcane.getPlugin().getDataFolder());
        loadPendingVotes();
        VoteListener.startListener();
    }

    public static void onDisable() {
        VoteListener.stopListener();
        VoteStatistics.onDisable();
        savePendingVotes();
    }

    public static void onSave() {
        createTable();
        savePendingVotes();
    }

    private static void createTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS acx_votes (
                UniqueId VARCHAR(36) NOT NULL PRIMARY KEY,
                PendingVoteCount INT NOT NULL,
                PendingVotes JSON NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
        """;
        try (Connection con = DBManager.getConnection();
             Statement st = con.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {}
    }

    private static void loadPendingVotes() {
        try (Connection con = DBManager.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM acx_votes");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("UniqueId"));
                List<VoteAction> votes = MAPPER.readValue(rs.getString("PendingVotes"), new TypeReference<>() {});
                if (!votes.isEmpty()) pendingVotes.put(uuid, votes);
            }
        } catch (Exception e) {}
    }

    public static void savePendingVotes() {
        String insertSql = """
        INSERT INTO acx_votes (UniqueId, PendingVoteCount, PendingVotes)
        VALUES (?, ?, ?)
        ON DUPLICATE KEY UPDATE
            PendingVoteCount = VALUES(PendingVoteCount),
            PendingVotes = VALUES(PendingVotes)
    """;
        String deleteSql = "DELETE FROM acx_votes WHERE UniqueId = ?";

        try (Connection con = DBManager.getConnection();
             PreparedStatement insertPs = con.prepareStatement(insertSql);
             PreparedStatement deletePs = con.prepareStatement(deleteSql)) {

            con.setAutoCommit(false);

            for (var entry : pendingVotes.entrySet()) {
                UUID uuid = entry.getKey();
                List<VoteAction> votes = entry.getValue();

                if (votes.isEmpty()) {
                    deletePs.setString(1, uuid.toString());
                    deletePs.addBatch();
                } else {
                    insertPs.setString(1, uuid.toString());
                    insertPs.setInt(2, votes.size());
                    insertPs.setString(3, MAPPER.writeValueAsString(votes));
                    insertPs.addBatch();
                }
            }

            if (!pendingVotes.isEmpty()) {
                String ids = pendingVotes.keySet().stream()
                        .map(id -> "'" + id + "'")
                        .collect(java.util.stream.Collectors.joining(","));
                try (Statement st = con.createStatement()) {
                    st.executeUpdate("DELETE FROM acx_votes WHERE UniqueId NOT IN (" + ids + ")");
                }
            } else {
                try (Statement st = con.createStatement()) {
                    st.executeUpdate("DELETE FROM acx_votes");
                }
            }

            deletePs.executeBatch();
            insertPs.executeBatch();
            con.commit();

        } catch (Exception e) {}
    }

    public static List<VoteAction> getPendingVotesFor(UUID uniqueId) {
        List<VoteAction> votes = pendingVotes.get(uniqueId);
        return votes != null ? Collections.unmodifiableList(votes) : Collections.emptyList();
    }

    public static void onVote(VoteAction vote, PlayerData pData) {
        UUID uuid = vote.getUniqueId();

        if (pData.isOnline()) {
            Player p = pData.getPlayer();
            if (p == null) {
                queuePending(uuid, vote);
                return;
            }
            PlayerManager.broadcast(Sound.BLOCK_NOTE_BLOCK_BELL);
            p.getScheduler().run(Arcane.getPlugin(), task -> executeVote(vote, pData, p), () -> queuePending(uuid, vote));
        } else {
            queuePending(uuid, vote);
        }
    }

    private static void queuePending(UUID uuid, VoteAction vote) {
        pendingVotes.compute(uuid, (id, list) -> {
            if (list == null) list = new ArrayList<>();
            list.add(vote);
            return list;
        });
    }

    public static void executePendingVotes(UUID uniqueId) {
        PlayerData pData = PlayerManager.getByUniqueId(uniqueId);
        if (pData == null || !pData.isOnline()) return;

        Player p = pData.getPlayer();
        if (p == null) return;

        p.getScheduler().run(Arcane.getPlugin(), task -> {
            List<VoteAction> votes = pendingVotes.remove(uniqueId);
            if (votes == null) return;
            PlayerManager.broadcast(Sound.BLOCK_NOTE_BLOCK_BELL);
            for (VoteAction vote : votes) executeVote(vote, pData, p);
        }, null);
    }

    static {
        BONUS_KEYS.put("pink",    4);
        BONUS_KEYS.put("purple",  8);
        BONUS_KEYS.put("gizmo",   16);
        BONUS_KEYS.put("spawner",  20);
    }

    private static void executeVote(VoteAction vote, PlayerData pData, Player p) {
        VoteStatistics.totalVotesAllTime.incrementAndGet();
        VoteStatistics.totalVotesMonthly.incrementAndGet();
        VoteStatistics.totalVotesWeekly.incrementAndGet();
        VoteStatistics.totalVotesDaily.incrementAndGet();

        pData.getMeta().setTotalVotes(pData.getMeta().getTotalVotes() + 1);

        List<String> keys = new ArrayList<>();
        keys.add("vote");
        for (var entry : BONUS_KEYS.entrySet())
            if (RANDOM.nextInt(entry.getValue()) == 0) keys.add(entry.getKey());

        for (String key : keys) giveKey(p, key, 1);

        PlayerManager.broadcast(buildBroadcast(p.getName(), keys));

        long current = VoteStatistics.votePartyVotes.incrementAndGet();
        if (current >= VoteStatistics.votePartyRequired.get()) {
            VoteStatistics.votePartyVotes.set(0);
            startVoteParty();
        }
    }

    private static Component buildBroadcast(String name, List<String> keys) {
        List<String> labels = new ArrayList<>();
        for (String key : keys)
            labels.add(Character.toUpperCase(key.charAt(0)) + key.substring(1));

        ComponentBuilder<TextComponent, TextComponent.Builder> builder = Component.text()
                .append(Component.text(name, Colors.HOT_PINK))
                .append(Component.text(" voted and received ", Colors.GRAY));

        if (labels.size() == 1) {
            builder.append(Component.text("a ", Colors.GRAY));
            builder.append(Component.text(labels.get(0) + " Crate Key", Colors.HOT_PINK));
        } else {
            for (int i = 0; i < labels.size(); i++) {
                boolean isLast = i == labels.size() - 1;
                builder.append(Component.text(labels.get(i) + (isLast ? " Crate Key" : ""), Colors.HOT_PINK));
                if (i < labels.size() - 2)        builder.append(Component.text(", ", Colors.GRAY));
                else if (i == labels.size() - 2)  builder.append(Component.text(" and ", Colors.GRAY));
            }
        }

        return builder.append(Component.text().append(
                Component.text(" "),
                Component.text("(", Colors.BRIGHT_PINK),
                Component.text(Text.toSmallCaps("/vote"), Colors.BRIGHT_PINK),
                Component.text(")", Colors.BRIGHT_PINK)
        ).build()).build();
    }

    private static void giveKey(Player p, String crate, int amount) {
        CrateManager.giveKey(p.getUniqueId(), crate, amount);
        String label = Character.toUpperCase(crate.charAt(0)) + crate.substring(1) + " Crate Key";
    }

    private static void startVoteParty() {
        //Bukkit.broadcast(Component.text("Vote party started!"));
    }
}