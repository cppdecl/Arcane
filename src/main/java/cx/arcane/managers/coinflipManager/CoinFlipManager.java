package cx.arcane.managers.coinflipManager;

import cx.arcane.Arcane;
import cx.arcane.managers.dbManager.DBManager;
import cx.arcane.managers.ecoManager.EcoManager;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.utils.Colors;
import cx.arcane.utils.Log;
import cx.arcane.utils.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class CoinFlipManager {
    private static final ConcurrentHashMap<UUID, CoinFlipWager> BY_PLAYER  = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, CoinFlipWager> BY_WAGER   = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, ReentrantLock> WAGER_LOCKS = new ConcurrentHashMap<>();

    public static void onEnable() {
        createTable();
        loadWagers();
    }

    public static void onDisable() {
        saveWagers();
    }

    public static void onSave() {
        createTable();
        saveWagers();
    }

    private static ReentrantLock lockFor(UUID wagerId) {
        return WAGER_LOCKS.computeIfAbsent(wagerId, id -> new ReentrantLock());
    }

    private static void releaseLock(UUID wagerId, ReentrantLock lock) {
        lock.unlock();
        if (!lock.hasQueuedThreads() && !lock.isLocked()) {
            WAGER_LOCKS.remove(wagerId, lock);
        }
    }

    private static void createTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS acx_coinflip (
                WagerId VARCHAR(36) NOT NULL PRIMARY KEY,
                UniqueId VARCHAR(36) NOT NULL UNIQUE,
                Wager BIGINT NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
        """;
        try (Connection con = DBManager.getConnection();
             Statement st = con.createStatement()) {
            st.execute(sql);
        } catch (SQLException ignored) {}
    }

    private static void loadWagers() {
        try (Connection con = DBManager.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT WagerId, UniqueId, Wager FROM acx_coinflip");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                CoinFlipWager wager = new CoinFlipWager(
                        UUID.fromString(rs.getString("WagerId")),
                        UUID.fromString(rs.getString("UniqueId")),
                        rs.getLong("Wager")
                );
                BY_PLAYER.put(wager.ownerId(), wager);
                BY_WAGER.put(wager.wagerId(), wager);
            }
        } catch (SQLException ignored) {}
    }

    public static void saveWagers() {
        String upsertSql = """
            INSERT INTO acx_coinflip (WagerId, UniqueId, Wager)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE UniqueId = VALUES(UniqueId), Wager = VALUES(Wager)
        """;

        try (Connection con = DBManager.getConnection();
             PreparedStatement upsertPs = con.prepareStatement(upsertSql)) {

            con.setAutoCommit(false);

            for (CoinFlipWager wager : BY_PLAYER.values()) {
                upsertPs.setString(1, wager.wagerId().toString());
                upsertPs.setString(2, wager.ownerId().toString());
                upsertPs.setLong(3, wager.amount());
                upsertPs.addBatch();
            }

            if (!BY_PLAYER.isEmpty()) {
                String ids = BY_PLAYER.values().stream()
                        .map(w -> "'" + w.wagerId() + "'")
                        .collect(Collectors.joining(","));
                try (Statement st = con.createStatement()) {
                    st.executeUpdate("DELETE FROM acx_coinflip WHERE WagerId NOT IN (" + ids + ")");
                }
            } else {
                try (Statement st = con.createStatement()) {
                    st.executeUpdate("DELETE FROM acx_coinflip");
                }
            }

            upsertPs.executeBatch();
            con.commit();

        } catch (SQLException ignored) {}
    }

    public static Collection<CoinFlipWager> getActiveWagers() {
        return Collections.unmodifiableCollection(BY_PLAYER.values());
    }

    public static boolean playerHasWager(UUID uniqueId) {
        return BY_PLAYER.containsKey(uniqueId);
    }

    public static CoinFlipWager getPlayerWager(UUID uniqueId) {
        return BY_PLAYER.get(uniqueId);
    }

    public static CoinFlipWager getWager(UUID wagerId) {
        return BY_WAGER.get(wagerId);
    }

    private static void error(Player p, String message) {
        Component msg = Component.text(message, Colors.DARK_PINK);
        p.sendMessage(msg);
        p.sendActionBar(msg);
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
    }

    private static void success(Player p, String message, Sound sound) {
        Component msg = Component.text(message, Colors.HOT_PINK);
        p.sendMessage(msg);
        p.sendActionBar(msg);
        p.playSound(p.getLocation(), sound, 1f, 1f);
    }

    public static boolean createWager(Player p, long amount) {
        if (BY_PLAYER.containsKey(p.getUniqueId())) {
            error(p, "You still have an active wager!");
            return false;
        }

        if (!Text.isValidAmount(amount)) {
            error(p, "That amount is invalid.");
            return false;
        }

        if (EcoManager.getMoney(p.getUniqueId()) < amount) {
            error(p, "You can't afford to wager that amount.");
            return false;
        }

        Log.info("[CFManager] Player {} has {} and wants to wager {}",  p.getUniqueId(), EcoManager.getMoney(p.getUniqueId()), amount);

        EcoManager.takeMoney(p.getUniqueId(), amount);

        CoinFlipWager wager = CoinFlipWager.of(p.getUniqueId(), amount);
        BY_PLAYER.put(p.getUniqueId(), wager);
        BY_WAGER.put(wager.wagerId(), wager);

        PlayerManager.broadcast(
                Component.text().append(
                        Component.text(p.getName(), Colors.HOT_PINK),
                        Component.text(" wagered ", Colors.WHITE),
                        Component.text(Text.formatShortBalanceWithSign("$", wager.amount()), Colors.HOT_PINK),
                        Component.text(" via ", Colors.WHITE),
                        Component.text("coinflip. ", Colors.HOT_PINK),
                        Component.text("(", Colors.BRIGHT_PINK),
                        Component.text(Text.toSmallCaps("/cf"), Colors.BRIGHT_PINK),
                        Component.text(")", Colors.BRIGHT_PINK)
                ).build(), Sound.BLOCK_NOTE_BLOCK_HAT, 2.0
        );

        return true;
    }

    public static boolean cancelWager(Player p) {
        CoinFlipWager wager = BY_PLAYER.get(p.getUniqueId());
        if (wager == null) {
            error(p, "You don't have an active wager.");
            return false;
        }

        ReentrantLock lock = lockFor(wager.wagerId());
        lock.lock();
        try {
            if (!BY_WAGER.containsKey(wager.wagerId())) {
                error(p, "Your wager was just accepted by someone!");
                return false;
            }
            BY_WAGER.remove(wager.wagerId());
            BY_PLAYER.remove(p.getUniqueId());
        } finally {
            releaseLock(wager.wagerId(), lock);
        }

        EcoManager.giveMoney(p.getUniqueId(), wager.amount());
        p.sendMessage(Component.text().append(
                Component.text("Your ", Colors.WHITE),
                Component.text("wager", Colors.HOT_PINK),
                Component.text(" has been cancelled.", Colors.WHITE)
        ).build());

        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 3);
        return true;
    }

    public static boolean acceptWager(Player acceptor, UUID ownerId) {
        if (acceptor.getUniqueId().equals(ownerId)) {
            error(acceptor, "You can't wager with yourself!");
            return false;
        }

        CoinFlipWager wager = BY_PLAYER.get(ownerId);
        if (wager == null) {
            error(acceptor, "That player has no active wager.");
            return false;
        }

        if (EcoManager.getMoney(acceptor.getUniqueId()) < wager.amount()) {
            error(acceptor, "You can't afford to wager that amount.");
            return false;
        }

        ReentrantLock lock = lockFor(wager.wagerId());
        lock.lock();
        try {
            if (!BY_WAGER.containsKey(wager.wagerId())) {
                error(acceptor, "That wager was just taken or cancelled.");
                return false;
            }

            BY_WAGER.remove(wager.wagerId());
            BY_PLAYER.remove(ownerId);

            boolean acceptorWins = ThreadLocalRandom.current().nextBoolean();
            UUID winnerId = acceptorWins ? acceptor.getUniqueId() : ownerId;
            UUID loserId  = acceptorWins ? ownerId : acceptor.getUniqueId();

            PlayerData winnerData = PlayerManager.getByUniqueId(winnerId);
            PlayerData loserData  = PlayerManager.getByUniqueId(loserId);

            String winnerName = winnerData != null ? winnerData.getUsername() : winnerId.toString();
            String loserName  = loserData  != null ? loserData.getUsername()  : loserId.toString();

            long reward = wager.amount() * 2;

            PlayerManager.broadcast(
                    Component.text().append(
                            Component.text(winnerName, Colors.HOT_PINK),
                            Component.text(" won ", Colors.WHITE),
                            Component.text(Text.formatShortBalanceWithSign("$", wager.amount()), Colors.HOT_PINK),
                            Component.text(" against ", Colors.WHITE),
                            Component.text(loserName, Colors.HOT_PINK),
                            Component.text(" in a coinflip. ", Colors.WHITE),
                            Component.text("(", Colors.BRIGHT_PINK),
                            Component.text(Text.toSmallCaps("/cf"), Colors.BRIGHT_PINK),
                            Component.text(")", Colors.BRIGHT_PINK)
                    ).build(), Sound.BLOCK_NOTE_BLOCK_BASS
            );

            EcoManager.takeMoney(acceptor.getUniqueId(), wager.amount());

            Runnable giveWinner = () -> {
                EcoManager.giveMoney(winnerId, reward);
                if (winnerData != null && winnerData.isOnline()) {
                    Player winner = winnerData.getPlayer();
                    winner.playSound(winner.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);
                }
            };

            Runnable notifyLoser = () -> {
                if (loserData != null && loserData.isOnline()) {
                    Player loser = loserData.getPlayer();
                    loser.playSound(loser.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                }
            };

            if (winnerData != null && winnerData.isOnline()) {
                winnerData.getPlayer().getScheduler().run(Arcane.getPlugin(), t -> giveWinner.run(),
                        () -> Arcane.getPlugin().getServer().getGlobalRegionScheduler().run(Arcane.getPlugin(), t -> giveWinner.run()));
            } else {
                Arcane.getPlugin().getServer().getGlobalRegionScheduler().run(Arcane.getPlugin(), t -> giveWinner.run());
            }

            if (loserData != null && loserData.isOnline()) {
                loserData.getPlayer().getScheduler().run(Arcane.getPlugin(), t -> notifyLoser.run(),
                        () -> Arcane.getPlugin().getServer().getGlobalRegionScheduler().run(Arcane.getPlugin(), t -> notifyLoser.run()));
            } else {
                Arcane.getPlugin().getServer().getGlobalRegionScheduler().run(Arcane.getPlugin(), t -> notifyLoser.run());
            }

        } finally {
            releaseLock(wager.wagerId(), lock);
        }

        return true;
    }
}