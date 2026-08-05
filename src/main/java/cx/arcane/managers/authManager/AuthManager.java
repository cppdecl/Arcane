package cx.arcane.managers.authManager;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import cx.arcane.Arcane;
import cx.arcane.managers.authManager.listeners.AuthEventListener;
import cx.arcane.managers.authManager.listeners.AuthPacketListener;
import cx.arcane.managers.authManager.listeners.AuthPermsListener;
import cx.arcane.managers.playerManager.*;
import cx.arcane.managers.playerManager.listeners.PlayerListener;
import cx.arcane.managers.skinManager.SkinManager;
import cx.arcane.managers.teleportManager.TeleportManager;
import cx.arcane.managers.voteManager.VoteManager;
import cx.arcane.utils.*;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.chat.ChatType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.minecraft.network.Connection;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class AuthManager {

    @Getter
    @Setter
    private static final class AuthCache {
        private Location location;
        private boolean isDead;
    }

    public record EncryptionData(String username, byte[] token, UUID uuid) {}

    @Getter
    private static final ChatType chatType = ChatType.chatType(Key.key("auth"));

    private static final ConcurrentHashMap<UUID, AuthState> authSessions = new ConcurrentHashMap<>();

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private static final Cache<UUID, AuthCache> authCache = Caffeine.newBuilder()
            .expireAfterWrite(3, TimeUnit.MINUTES)
            .build();

    public static void onEnable() {
        Bukkit.getPluginManager().registerEvents(new AuthEventListener(), Arcane.getPlugin());
        PacketEvents.getAPI().getEventManager().registerListener(new AuthPacketListener(), PacketListenerPriority.MONITOR);
        AuthPermsListener.subscribe();

        startScheduler();
    }

    public static void onDisable() {
        authSessions.clear();
    }

    public static void onSave() {

    }

    /* ============================= */
    /*        FOLIA SCHEDULER        */
    /* ============================= */

    private static void startScheduler() {
        Bukkit.getAsyncScheduler().runAtFixedRate(
                Arcane.getPlugin(),
                task -> {
                    Instant now = Instant.now();

                    for (UUID uuid : authSessions.keySet()) {
                        Player player = Bukkit.getPlayer(uuid);
                        if (player == null) {
                            authSessions.remove(uuid);
                            continue;
                        }

                        AuthState state = authSessions.get(uuid);
                        if (state == null || state.isAuthenticated() || state.getStartedAt() == null) continue;

                        Duration elapsed = Duration.between(state.getStartedAt(), now);
                        Duration remaining = TIMEOUT.minus(elapsed);

                        if (remaining.isZero() || remaining.isNegative()) {
                            player.getScheduler().run(Arcane.getPlugin(), t -> {
                                Player live = Bukkit.getPlayer(uuid);
                                if (live == null || !live.isOnline()) return;
                                live.kick(Component.text(Text.toSmallCaps("You took too long to authenticate"), Colors.HOT_PINK));
                            }, null);
                        } else {
                            player.getScheduler().run(Arcane.getPlugin(), t -> {
                                Player live = Bukkit.getPlayer(uuid);
                                if (live == null || !live.isOnline()) return;
                                PlayerUtils.sendPacketActionBar(live, Component.text(formatDuration(remaining), Colors.HOT_PINK));
                            }, null);
                        }
                    }
                },
                1, 1, TimeUnit.SECONDS
        );
    }

    /* ============================= */
    /*          UTILITIES            */
    /* ============================= */

    private static String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        return "You have " + seconds + " seconds to authenticate.";
    }

    /* ============================= */
    /*       SESSION MANAGEMENT      */
    /* ============================= */

    public static void cacheStuff(UUID uniqueId, Location location) {
        AuthCache cache = new AuthCache();
        cache.setDead(false);
        cache.setLocation(location.clone());

        authCache.invalidate(uniqueId);
        authCache.put(uniqueId, cache);
    }

    public static AuthCache getCachedStuff(UUID uniqueId) {
        AuthCache cached = authCache.getIfPresent(uniqueId);
        if (cached == null) return null;

        return cached;
    }


    public static void restoreCachedStuffOnDisconnect(UUID uniqueId) {
        Log.info("[restoreCachedStuffOnDisconnect]");

        AuthCache cached = getCachedStuff(uniqueId);
        if (cached == null) return;

        Player p = Bukkit.getPlayer(uniqueId);
        if (p == null) return;
        p.setHealth(cached.isDead() ? 0 : 20);

        PlayerData pData = PlayerManager.getByUniqueId(p.getUniqueId());
        if (pData != null) {
            pData.setLocation(cached.getLocation().clone());
            Log.info("Set {}'s location again back to {}", pData.getUsername(), pData.getLocation());
        }
    }

    public static boolean isDeadOnCache(Player p) {
        AuthCache cached = getCachedStuff(p.getUniqueId());
        if (cached == null) return false;

        return cached.isDead();
    }

    public static AuthState newAuthSession(Player player) {

        Connection connection = NetUtils.getConnection(player);
        if (connection == null) {
            Log.error("[AuthEventListener]: Connection is null for {} ", player.getUniqueId());
            player.kick( Component.text(Text.toSmallCaps("an error has occurred"), Colors.HOT_PINK));
            return null;
        }

        Log.error("[Auth] {} is {}", player.getName(), connection.isEncrypted() ? "encrypted" : "not encrypted");

        AuthState state = new AuthState();
        state.setAuthenticated(connection.isEncrypted());
        state.setStartedAt(Instant.now());
        authSessions.put(player.getUniqueId(), state);

        AuthCache cached = authCache.getIfPresent(player.getUniqueId());
        if (cached != null) {
            cached.setDead(player.getHealth() == 0 ? true : false);
            if (cached.isDead()) {
                player.setHealth(20);
            }
        }

        return state;
    }

    public static AuthState getAuthSession(UUID uuid) {
        return authSessions.get(uuid);
    }

    public static boolean isAuthenticated(UUID uuid) {
        AuthState state = authSessions.get(uuid);
        return state != null && state.isAuthenticated();
    }

    public static void removeAuthSession(UUID uuid) {
        AuthState state = getAuthSession(uuid);
        if (state == null) return;

        if (!state.isAuthenticated()) {
            restoreCachedStuffOnDisconnect(uuid);
        }

        authSessions.remove(uuid);
    }

    public static void authenticate(Player p) {
        AuthState state = authSessions.get(p.getUniqueId());
        if (state != null) {
            state.setAuthenticated(true);

            AuthCache cached = authCache.getIfPresent(p.getUniqueId());
            if (cached == null) return;

            Log.info("[Auth] Teleport user back to his original location.");

            p.teleportAsync(cached.getLocation()).whenComplete((success, error) -> {
                if (error != null) {
                    Log.error("[AuthEventListener]: {} ", error.toString());
                    p.kick( Component.text(Text.toSmallCaps("an error has occurred"), Colors.HOT_PINK));
                    return;
                }

                if (!success) {
                    Log.error("[AuthEventListener]: Connection is null for {} ", error.toString());
                    p.kick( Component.text(Text.toSmallCaps("an error has occurred"), Colors.HOT_PINK));
                    return;
                }

                p.getScheduler().runDelayed(Arcane.getPlugin(), t -> {
                    if (cached.isDead()) {
                        p.setHealth(0);
                        authCache.invalidate(p.getUniqueId());
                    }
                }, null, 1);

                p.sendHealthUpdate();
                p.updateInventory();
                p.updateCommands();
                p.sendExperienceChange(p.getExp(), p.getLevel());

                PlayerListener.onJoin(p);
            });
        }
    }

    public static boolean shouldAutoLoginCracked(PlayerSession session, PlayerData data) {
        if (session == null || data == null) return false;
        if (!data.isCracked()) return false;

        if (data.getLastLoginAt() == null || data.getLastLoginAddress() == null) return false;

        boolean within7Days = data.getLastLoginAt().isAfter(Instant.now().minus(7, ChronoUnit.DAYS));
        boolean sameIp = Objects.equals(
                session.getAddress().getAddress(),
                data.getLastLoginAddress()
        );

        return within7Days && sameIp;
    }
}