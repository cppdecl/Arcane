package cx.arcane.utils;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.User;
import cx.arcane.managers.playerManager.AccountType;
import cx.arcane.managers.playerManager.PlayerSession;
import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.CryptException;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;

import javax.crypto.SecretKey;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for accessing low-level Minecraft network connections.
 *
 * Supports both standard Paper/Spigot and Folia environments.
 */
public final class NetUtils {
    private static final ConcurrentHashMap<InetSocketAddress, PlayerSession> SESSION_MAPPING = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, PlayerSession> UUID_TO_SESSION_MAPPING = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<User, PlayerSession> USER_TO_SESSION_MAPPING = new ConcurrentHashMap<>();

    public static Collection<PlayerSession> getSessions() {
        return SESSION_MAPPING.values();
    }

    public static void createSession(InetSocketAddress inetSocketAddr, User user, String username, UUID uuid, AccountType accountType) {
        if (inetSocketAddr == null) {
            Log.error("[NetUtils]: Tried to cache a mapping with an invalid InetSocketAddress!");
            return;
        }

        if (uuid == null) {
            Log.error("[NetUtils]: Tried to cache a mapping with an invalid UUID!");
            return;
        }

        PlayerSession session = new PlayerSession();
        session.setAddress(inetSocketAddr);
        session.setUsername(username);
        session.setUniqueId(uuid);
        session.setAccountType(accountType);
        session.setStartedAt(Instant.now());

        SESSION_MAPPING.put(inetSocketAddr, session);
        UUID_TO_SESSION_MAPPING.put(uuid, session);
        USER_TO_SESSION_MAPPING.put(user, session);

        Log.error("[NetUtils]: Created session for " + username + " (" + uuid + " - " + accountType + ") at " + inetSocketAddr);
    }

    public static PlayerSession getSession(InetSocketAddress inetSocketAddress) {
        return SESSION_MAPPING.get(inetSocketAddress);
    }

    public static PlayerSession getSession(User user) {
        return USER_TO_SESSION_MAPPING.get(user);
    }

    public static PlayerSession getSession(UUID uniqueId) {
        return UUID_TO_SESSION_MAPPING.get(uniqueId);
    }

    public static void invalidateSession(InetSocketAddress inetSocketAddr) {
        PlayerSession session = SESSION_MAPPING.remove(inetSocketAddr);
        if (session != null) {
            Log.error("[NetUtils]: Invalidated session for " + session.getUsername() + " (" + session.getUniqueId() + ") at " + inetSocketAddr);
            UUID_TO_SESSION_MAPPING.remove(session.getUniqueId());
        }
    }

    public static boolean hasSession(InetSocketAddress inetSocketAddr) {
        Log.info("[NetUtils]: Checking session for " + inetSocketAddr + " - " + SESSION_MAPPING.containsKey(inetSocketAddr));
        return SESSION_MAPPING.containsKey(inetSocketAddr);
    }

    public static boolean isBedrock(UUID uniqueId) {
        return FloodgateApi.getInstance().isFloodgatePlayer(uniqueId);
    }

    private static volatile Field LISTENER_CONNECTION_FIELD;
    private static volatile Field FOLIA_CONNECTIONS_FIELD;
    private static volatile Object FOLIA_SERVER_INSTANCE;

    private NetUtils() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * Returns all active network connections.
     *
     * On Paper/Spigot, this delegates to the server connection manager.
     * On Folia, this uses reflection to access RegionizedServer connections.
     *
     * @return collection of active {@link Connection} instances
     * @throws RuntimeException if reflection fails on Folia
     */
    @SuppressWarnings("unchecked")
    public static Collection<Connection> getActiveConnections() {
        if (!ServerUtils.isFolia()) {
            return MinecraftServer.getServer()
                    .getConnection()
                    .getConnections();
        }

        try {
            ensureFoliaInitialized();
            return (Collection<Connection>)
                    FOLIA_CONNECTIONS_FIELD.get(FOLIA_SERVER_INSTANCE);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to access Folia connection collection", e);
        }
    }

    /**
     * Returns the number of active network connections.
     *
     * @return active connection count
     */
    public static int getActiveConnectionCount() {
        return getActiveConnections().size();
    }

    /**
     * Returns a channel matching a socket address.
     *
     * @param address the socket address i.e /136.158.67.182:42331
     * @return matching {@link Channel}, or null if not found
     */
    public static Channel getChannel(InetSocketAddress address) {
        Objects.requireNonNull(address, "address");

        for (Connection connection : getActiveConnections()) {
            if (connection.getRemoteAddress() instanceof InetSocketAddress socketAddress) {
                if (address.equals(socketAddress)) {
                    return connection.channel;
                }
            }
        }
        return null;
    }

    /**
     * Returns a channel matching a player.
     * NOTE: This will only return something after PlayerLoginEvent
     *
     * @param player the player object
     * @return matching {@link Channel}, or null if not found
     */
    public static Channel getChannel(Player player) {
       return (Channel) PacketEvents.getAPI().getPlayerManager().getChannel(player);
    }

    /**
     * Finds an active connection by IP address.
     *
     * @param address the remote InetAddress
     * @return matching {@link Connection}, or null if not found
     */
    public static Connection getConnection(InetSocketAddress address) {
        Objects.requireNonNull(address, "address");

        for (Connection connection : getActiveConnections()) {
            if (connection.getRemoteAddress() instanceof InetSocketAddress socketAddress) {
                if (address.equals(socketAddress)) {
                    return connection;
                }
            }
        }
        return null;
    }

    /**
     * Finds an active connection by Player class.
     *
     * @param player the player
     * @return matching {@link Connection}, or null if not found
     */
    public static Connection getConnection(Player player) {

        Channel channel = (Channel) PacketEvents.getAPI().getPlayerManager().getChannel(player);

        Objects.requireNonNull(channel, "channel");

        for (Connection connection : getActiveConnections()) {
            Channel currentChannel = connection.channel;
            if (channel.equals(currentChannel)) {
                return connection;
            }
        }
        return null;
    }

    /**
     * Finds an active connection by netty channel.
     *
     * @param channel the active netty channel
     * @return matching {@link Connection}, or null if not found
     */
    public static Connection getConnection(Channel channel) {
        Objects.requireNonNull(channel, "channel");

        for (Connection connection : getActiveConnections()) {

            Channel currentChannel = connection.channel;
            if (channel.equals(currentChannel)) {
                return connection;
            }
        }
        return null;
    }

    public static boolean setEncryptionKey(Channel channel, SecretKey secret) {
        Connection connection = getConnection(channel);
        if (connection == null) return false;
        return setEncryptionKey(connection, secret);
    }

    public static boolean setEncryptionKey(Connection connection, SecretKey secret) {
        try {
            connection.setEncryptionKey(secret);
        } catch (CryptException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /* =========================
       Internal Helper Methods
       ========================= */

    private static Field resolveConnectionField() throws ReflectiveOperationException {
        Class<?> clazz = ServerGamePacketListenerImpl.class;

        while (clazz != null) {
            for (Field field : clazz.getDeclaredFields()) {
                if (Connection.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return field;
                }
            }
            clazz = clazz.getSuperclass();
        }

        throw new NoSuchFieldException("Could not locate Connection field in ServerGamePacketListenerImpl");
    }

    private static void ensureFoliaInitialized() throws ReflectiveOperationException {
        if (FOLIA_CONNECTIONS_FIELD != null && FOLIA_SERVER_INSTANCE != null) {
            return;
        }

        synchronized (NetUtils.class) {
            if (FOLIA_CONNECTIONS_FIELD != null && FOLIA_SERVER_INSTANCE != null) {
                return;
            }

            ClassLoader classLoader = Bukkit.getServer().getClass().getClassLoader();
            Class<?> regionizedServerClass =
                    classLoader.loadClass("io.papermc.paper.threadedregions.RegionizedServer");

            Field connectionsField = regionizedServerClass.getDeclaredField("connections");
            connectionsField.setAccessible(true);

            Method getInstanceMethod = regionizedServerClass.getDeclaredMethod("getInstance");
            getInstanceMethod.setAccessible(true);

            FOLIA_SERVER_INSTANCE = getInstanceMethod.invoke(null);
            FOLIA_CONNECTIONS_FIELD = connectionsField;
        }
    }
}