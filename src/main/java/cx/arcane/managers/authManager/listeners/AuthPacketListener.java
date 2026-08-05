package cx.arcane.managers.authManager.listeners;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.*;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.WorldBlockPosition;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerDisconnect;
import com.github.retrooper.packetevents.wrapper.handshaking.client.WrapperHandshakingClientHandshake;
import com.github.retrooper.packetevents.wrapper.login.client.WrapperLoginClientEncryptionResponse;
import com.github.retrooper.packetevents.wrapper.login.client.WrapperLoginClientLoginStart;
import com.github.retrooper.packetevents.wrapper.login.server.WrapperLoginServerDisconnect;
import com.github.retrooper.packetevents.wrapper.login.server.WrapperLoginServerEncryptionRequest;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDisconnect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerJoinGame;
import cx.arcane.Arcane;
import cx.arcane.managers.authManager.AuthManager;
import cx.arcane.managers.playerManager.AccountType;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.managers.playerManager.PlayerSession;
import cx.arcane.utils.*;
import net.kyori.adventure.text.Component;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.security.KeyPair;
import java.security.SecureRandom;

import io.netty.channel.Channel;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.geyser.api.GeyserApi;

public class AuthPacketListener implements PacketListener {

    public record EncryptionData(String username, byte[] token, UUID uuid) {}

    private static final String LOG_PREFIX = "[Auth]";

    private final KeyPair keyPair;
    private final Random random = new SecureRandom();

    private final Cache<String, EncryptionData> encryptionDataCache = Caffeine.newBuilder()
            .expireAfterWrite(2, TimeUnit.MINUTES)
            .build();

    private final Cache<InetAddress, Long> throttleCache;
    private final long throttleMs;

    public AuthPacketListener() {
        Log.info("{} Generating RSA key pair...", LOG_PREFIX);
        this.keyPair = CryptUtils.generateKeyPair().orElseThrow(() -> {
            Log.error("{} FATAL: Failed to generate RSA key pair. Auth cannot function.", LOG_PREFIX);
            return new IllegalStateException("Failed to generate server key pair");
        });
        Log.info("{} RSA key pair generated successfully.", LOG_PREFIX);

        long configured = Bukkit.getServer().getConnectionThrottle();
        this.throttleMs = configured > 0 ? Math.max(configured + 500L, 0L) : 0L;

        this.throttleCache = Caffeine.newBuilder()
                .expireAfterWrite(Math.max(this.throttleMs, 1000L), TimeUnit.MILLISECONDS)
                .build();

        if (this.throttleMs > 0) {
            Log.info("{} Connection throttle active — {}ms (bukkit.yml: {}ms).", LOG_PREFIX, this.throttleMs, configured);
        } else {
            Log.info("{} Connection throttle disabled (bukkit.yml value: {}).", LOG_PREFIX, configured);
        }
    }

    private String addr(User user) {
        return user.getAddress() != null ? user.getAddress().toString() : "unknown";
    }

    private void kickPlayer(String reason, User player) {
        switch(player.getConnectionState()) {
            case CONFIGURATION -> {
                var kickPacket = new WrapperConfigServerDisconnect(Component.text(Text.toSmallCaps(reason), Colors.HOT_PINK));
                PacketEvents.getAPI().getProtocolManager().sendPacket(player.getChannel(), kickPacket);
                break;
            }
            case LOGIN -> {
                var kickPacket = new WrapperLoginServerDisconnect(Component.text(Text.toSmallCaps(reason), Colors.HOT_PINK));
                PacketEvents.getAPI().getProtocolManager().sendPacket(player.getChannel(), kickPacket);
                break;
            }
            case PLAY -> {
                var kickPacket = new WrapperPlayServerDisconnect(Component.text(Text.toSmallCaps(reason), Colors.HOT_PINK));
                PacketEvents.getAPI().getProtocolManager().sendPacket(player.getChannel(), kickPacket);
                break;
            }
        }

        /*PlayerSession pSession = NetUtils.getSession(player);
        if (pSession != null) {
            NetUtils.invalidateSession(pSession.getAddress());
        }*/
    }

    private void kickPlayer(Component reason, User player) {
        var kickPacket = new WrapperLoginServerDisconnect(reason.color(Colors.HOT_PINK));
        try {
            PacketEvents.getAPI().getProtocolManager().sendPacket(player.getChannel(), kickPacket);
        } catch (Exception ex) {
            Log.warn("{} Kick packet failed ({}) — {}", LOG_PREFIX, addr(player), ex.getMessage());
        } finally {
            player.closeConnection();
        }
    }

    private boolean verifyNonce(WrapperLoginClientEncryptionResponse packet, byte[] expectedToken) {
        Optional<byte[]> encryptedToken = packet.getEncryptedVerifyToken();
        if (encryptedToken.isEmpty()) {
            Log.warn("{} verifyNonce — token absent from packet.", LOG_PREFIX);
            return false;
        }
        boolean valid = CryptUtils.verifyNonce(expectedToken, keyPair.getPrivate(), encryptedToken.get());
        if (!valid) Log.warn("{} verifyNonce — nonce mismatch.", LOG_PREFIX);
        return valid;
    }

    @Override
    public void onUserConnect(UserConnectEvent e) {
        if (e.getUser() == null) return;
    }

    @Override
    public void onPacketSend(PacketSendEvent e) {
        User user = e.getUser();
        if (user == null) return;

        UUID uuid = user.getUUID();
        if (uuid == null) return;

        if (!AuthManager.isAuthenticated(uuid)) {
            switch (e.getPacketType()) {
                case PacketType.Play.Server.SYSTEM_CHAT_MESSAGE:
                case PacketType.Play.Server.SET_TITLE_SUBTITLE:
                case PacketType.Play.Server.SET_TITLE_TEXT:
                case PacketType.Play.Server.SET_TITLE_TIMES:
                case PacketType.Play.Server.SOUND_EFFECT:
                case PacketType.Play.Server.ACTION_BAR:
                case PacketType.Play.Server.SET_EXPERIENCE:
                case PacketType.Play.Server.WINDOW_ITEMS:
                case PacketType.Play.Server.SET_SLOT:
                case PacketType.Play.Server.ENTITY_EQUIPMENT:
                case PacketType.Play.Server.COMBAT_EVENT:
                case PacketType.Play.Server.WINDOW_PROPERTY:
                case PacketType.Play.Server.ENTITY_EFFECT:
                case PacketType.Play.Server.ENTITY_STATUS:
                    e.setCancelled(true);
                    break;
                case PacketType.Play.Server.JOIN_GAME: {
                    WrapperPlayServerJoinGame joinPacket = new WrapperPlayServerJoinGame(e);
                    joinPacket.setLastDeathPosition(new WorldBlockPosition(WorldBlockPosition.OVERWORLD_DIMENSION, new Vector3i(0, 0, 0)));
                    e.markForReEncode(true);
                    break;
                }
                default:
                    break;
            }
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent e) {
        var type = e.getPacketType();

        if (type == PacketType.Handshaking.Client.HANDSHAKE) {
            WrapperHandshakingClientHandshake packet = new WrapperHandshakingClientHandshake(e);
            if (packet.getIntention() == WrapperHandshakingClientHandshake.ConnectionIntention.LOGIN) {
                InetSocketAddress socketAddr = e.getUser().getAddress();
                if (throttleMs > 0 && !socketAddr.getAddress().isLoopbackAddress()) {
                    InetAddress ip = socketAddr.getAddress();
                    long now = System.currentTimeMillis();
                    Long last = throttleCache.getIfPresent(ip);
                    throttleCache.put(ip, now);
                    if (last != null) {
                        long elapsed = now - last;
                        if (elapsed < throttleMs) {
                            Log.info("{} Connection State: {}", LOG_PREFIX, e.getUser().getConnectionState().toString());
                            Log.warn("{} Throttled ({}) — {}ms since last attempt (limit: {}ms).", LOG_PREFIX, socketAddr.toString(), elapsed, throttleMs);
                            kickPlayer("you're connecting too fast", e.getUser());
                            e.setCancelled(true);
                            return;
                        }
                    }
                }
            }
        }

        if (type.getWrapperClass() == null || !type.getWrapperClass().getName().contains("Login")) return;

        if (type == PacketType.Login.Client.LOGIN_START) {
            handleLoginStart(e);
        } else if (type == PacketType.Login.Client.ENCRYPTION_RESPONSE) {
            handleEncryptionResponse(e);
        }
    }

    @Override
    public void onUserDisconnect(UserDisconnectEvent e) {
        PlayerSession pSession = NetUtils.getSession(e.getUser());
        if (pSession != null) {
            NetUtils.invalidateSession(pSession.getAddress());
        }
    }

    private boolean validateLogon(String username, UUID uniqueId, AccountType accountType, User user) {
        String a = addr(user);

        /*String cappuIp = "136.158.67.231";
        if (!user.getAddress().getAddress().getHostAddress().equals(cappuIp)) {
            Log.info("{} Rejected ({}) — not cappu ip.", LOG_PREFIX, a);
            kickPlayer("only accepting cappu for now", user);
        }*/

        if (Arcane.getShuttingDown().get()) {
            Log.info("{} Rejected ({}) — shutting down.", LOG_PREFIX, a);
            kickPlayer("arcane is shutting down", user);
            return false;
        }

        if (username == null || username.isBlank()) {
            Log.warn("{} Rejected ({}) — blank username.", LOG_PREFIX, a);
            kickPlayer("invalid name provided", user);
            return false;
        }

        if (uniqueId == null) {
            Log.warn("{} Rejected ({}) — null UUID.", LOG_PREFIX, a);
            kickPlayer("invalid client information", user);
            return false;
        }

        if (!PlayerUtils.isValidName(username) && accountType != AccountType.BEDROCK) {
            Log.warn("{} Rejected ({}) — username '{}' failed validation.", LOG_PREFIX, a, username);
            kickPlayer("invalid username", user);
            return false;
        } else if (!PlayerUtils.isValidBedrockName(username) && accountType == AccountType.BEDROCK) {
            Log.warn("{} Rejected ({}) — Bedrock username '{}' failed validation.", LOG_PREFIX, a, username);
            kickPlayer("invalid username", user);
            return false;
        }

        PlayerData pExisting = PlayerManager.getByNameIgnoreCase(username);
        if (pExisting != null) {
            if (pExisting.getAccountType() != accountType) {
                Log.warn("{} Rejected ({}) — username '{}' tried to login as {}, should've been {}", LOG_PREFIX, a, username, accountType, pExisting.getAccountType());
                kickPlayer("account already registered as " + pExisting.getAccountType(), user);
                return false;
            }

            if (pExisting.isCracked() && !pExisting.getUsername().equals(username)) {
                Log.warn("{} Rejected ({}) — username '{}' should've logged in using {}", LOG_PREFIX, a, pExisting.getUsername());
                kickPlayer(Component.text().append(
                        Text.toSmallCapsComponent("you used the wrong capitalization").color(Colors.HOT_PINK),
                        Component.newline(),
                        Text.toSmallCapsComponent("for this cracked account").color(Colors.HOT_PINK),
                        Component.newline(),
                        Component.newline(),
                        Component.text(pExisting.getUsername()).color(Colors.WHITE),
                        Component.newline(),
                        Component.newline(),
                        Text.toSmallCapsComponent("if you own this account").color(Colors.HOT_PINK),
                        Component.newline(),
                        Text.toSmallCapsComponent("please use the exact name typed above").color(Colors.HOT_PINK)
                ).build(), user);
                return false;
            }
        }

        if (Bukkit.getServer().hasWhitelist()) {
            boolean whitelisted = Bukkit.getWhitelistedPlayers().stream()
                    .anyMatch(p -> Objects.requireNonNull(p.getName()).equalsIgnoreCase(username));
            if (!whitelisted) {
                Log.info("{} Rejected ({}) — '{}' not on whitelist.", LOG_PREFIX, a, username);
                kickPlayer("arcane is under maintenance", user);
                return false;
            }
        }

        if (NetUtils.hasSession(user.getAddress())) {
            Log.warn("{} Rejected ({}) — address already has active session.", LOG_PREFIX, a);
            kickPlayer("you're already online from this location", user);
            return false;
        }

        if (PlayerManager.isUsernameOnline(username)) {
            if (pExisting != null && pExisting.getPlayer() != null) {
                Player p = pExisting.getPlayer();
                Log.warn("Bukkit.getPlayer() - Online? {}, Connected? {}", p.isOnline(), p.isConnected());
            }

            Log.warn("{} Rejected ({}) — already online.", LOG_PREFIX, a);
            kickPlayer("account already online", user);
            return false;
        }

        for (Player otherPlayer : Bukkit.getOnlinePlayers()) {
            if (!otherPlayer.getName().equalsIgnoreCase(username)) continue;
            if (AuthManager.isAuthenticated(otherPlayer.getUniqueId())) continue;

            otherPlayer.getScheduler().run(Arcane.getPlugin(), t -> {
                Log.warn("{} Rejected ({}) — already online but unauthenticated.", LOG_PREFIX, a);
                otherPlayer.kick(Text.toSmallCapsComponent("account joined from another location").color(Colors.HOT_PINK));
            }, null);
        }

        return true;
    }
    private void handleLoginStart(PacketReceiveEvent e) {
        WrapperLoginClientLoginStart packet = new WrapperLoginClientLoginStart(e);
        e.setCancelled(true);

        String username = packet.getUsername();
        UUID uniqueId = packet.getPlayerUUID().orElse(null);
        User user = e.getUser();
        AccountType accountType = null;
        InetSocketAddress address = (InetSocketAddress) ((Channel) user.getChannel()).remoteAddress();

        // =========================
        // 1. BEDROCK CHECK
        // =========================
        for (var p : GeyserApi.api().onlineConnections()) {
            if (p.bedrockUsername().equals(username)) {
                accountType = AccountType.BEDROCK;
                String XUID = p.xuid();

                for (var x : FloodgateApi.getInstance().getPlayers()) {
                    if (x.getUsername().equals(username) && x.getXuid().equals(XUID)) {
                        uniqueId = x.getCorrectUniqueId();
                        username = "." + username;
                        break;
                    }
                }
                break;
            }
        }

        // =========================
        // 2. NOT BEDROCK → CHECK PREMIUM (ASYNC)
        // =========================
        if (accountType != AccountType.BEDROCK) {

            boolean looksPremium = uniqueId != null
                    && !PlayerUtils.isOfflineUUID(uniqueId, username)
                    && PlayerUtils.isPremiumUUID(uniqueId, username);

            if (looksPremium) {
                // 🔥 ASYNC CHECK HERE
                String finalUsername = username;
                UUID finalUuid = uniqueId;

                HTTP_CLIENT.sendAsync(
                        HttpRequest.newBuilder()
                                .uri(URI.create("https://playerdb.co/api/player/minecraft/" + uniqueId))
                                .timeout(Duration.ofSeconds(3))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                ).thenAccept(response -> {

                    boolean isPremium = false;

                    try {
                        if (response.statusCode() == 200 && response.body().contains("\"success\":true")) {
                            isPremium = true;
                        }
                    } catch (Exception ignored) {}

                    AccountType finalType;
                    UUID uuidToUse;

                    if (isPremium) {
                        finalType = AccountType.PREMIUM;
                        uuidToUse = finalUuid;
                    } else {
                        finalType = AccountType.CRACKED;
                        uuidToUse = PlayerUtils.generateOfflineUUID(finalUsername);
                    }

                    continueLogin(e, user, address, finalUsername, uuidToUse, finalType);

                });

                return; // IMPORTANT: stop here (async continues)
            }

            // Not premium → cracked
            uniqueId = PlayerUtils.generateOfflineUUID(username);
            accountType = AccountType.CRACKED;
        }

        // =========================
        // 3. CONTINUE (SYNC PATH)
        // =========================
        continueLogin(e, user, address, username, uniqueId, accountType);
    }

    private void continueLogin(PacketReceiveEvent e, User user, InetSocketAddress address,
                               String username, UUID uniqueId, AccountType accountType) {

        Log.info("{} LOGIN_START ({}) — '{}' uuid: {} type: {}",
                LOG_PREFIX, addr(user), username, uniqueId, accountType);

        if (!validateLogon(username, uniqueId, accountType, user)) return;

        NetUtils.createSession(address, user, username, uniqueId, accountType);

        if (accountType == AccountType.PREMIUM) {
            startEncryptionFlow(e, username, uniqueId);
        } else {
            sendFakeStartPacket(username, e.getChannel(), uniqueId);
        }
    }

    private void startEncryptionFlow(PacketReceiveEvent e, String username, UUID uuid) {
        String a = addr(e.getUser());

        if (encryptionDataCache.getIfPresent(a) != null) {
            Log.warn("{} ({}) — stale encryption session invalidated.", LOG_PREFIX, a);
        }
        encryptionDataCache.invalidate(a);

        byte[] token = CryptUtils.generateVerifyToken(random);
        encryptionDataCache.put(a, new EncryptionData(username, token, uuid));

        PacketEvents.getAPI().getProtocolManager().sendPacket(
                e.getChannel(),
                new WrapperLoginServerEncryptionRequest("", keyPair.getPublic(), token)
        );

        Log.info("{} ({}) — EncryptionRequest sent.", LOG_PREFIX, a);
    }

    private void handleEncryptionResponse(PacketReceiveEvent e) {
        e.setCancelled(true);

        WrapperLoginClientEncryptionResponse packet = new WrapperLoginClientEncryptionResponse(e);
        User user = e.getUser();
        String a = addr(user);

        Log.info("{} ENCRYPTION_RESPONSE ({})", LOG_PREFIX, a);

        EncryptionData data = encryptionDataCache.getIfPresent(a);
        if (data == null) {
            Log.warn("{} ({}) — no pending encryption data. Replay or timeout.", LOG_PREFIX, a);
            kickPlayer("Authentication Error", user);
            return;
        }

        if (!verifyNonce(packet, data.token())) {
            Log.warn("{} ({}) — nonce verification failed.", LOG_PREFIX, a);
            kickPlayer("Authentication Error", user);
            return;
        }

        Optional<SecretKey> loginKeyOpt = CryptUtils.decryptSharedKey(keyPair.getPrivate(), packet.getEncryptedSharedSecret());
        if (loginKeyOpt.isEmpty()) {
            Log.warn("{} ({}) — failed to decrypt shared secret.", LOG_PREFIX, a);
            kickPlayer("Authentication Error", user);
            return;
        }

        SecretKey loginKey = loginKeyOpt.get();

        if (!NetUtils.setEncryptionKey((Channel) user.getChannel(), loginKey)) {
            Log.warn("{} ({}) — failed to set channel encryption key.", LOG_PREFIX, a);
            kickPlayer("Authentication Error", user);
            return;
        }

        Log.info("{} ({}) — channel encrypted. Verifying session...", LOG_PREFIX, a);

        String serverId = CryptUtils.getServerIdHashString("", loginKey, keyPair.getPublic());
        InetAddress hostIp = user.getAddress().getAddress();

        verifySessionAsync(data.username(), serverId, hostIp)
                .thenAccept(valid -> {
                    if (!valid) {
                        Log.warn("{} ({}) — Mojang session invalid for '{}'.", LOG_PREFIX, a, data.username());
                        kickPlayer("Invalid Session. Please Restart Your Launcher.", user);
                        return;
                    }

                    Log.info("{} ({}) — Mojang session verified.", LOG_PREFIX, a);

                    sendFakeStartPacket(data.username(), e.getChannel(), data.uuid());

                    Log.info("{} ({}) — '{}' handed off to login pipeline.", LOG_PREFIX, a, data.username());
                });
    }

    private void sendFakeStartPacket(String username, Object channel, UUID uuid) {
        var startPacket = new WrapperLoginClientLoginStart(
                PacketEvents.getAPI().getServerManager().getVersion().toClientVersion(),
                username, null, uuid
        );
        PacketEvents.getAPI().getProtocolManager().receivePacketSilently(channel, startPacket);
    }

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();

    private CompletableFuture<Boolean> verifySessionAsync(String username, String serverHash, InetAddress hostIp) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                StringBuilder url = new StringBuilder("https://sessionserver.mojang.com/session/minecraft/hasJoined?username=")
                        .append(URLEncoder.encode(username, StandardCharsets.UTF_8))
                        .append("&serverId=")
                        .append(URLEncoder.encode(serverHash, StandardCharsets.UTF_8));

                if (!(hostIp instanceof Inet6Address) && !hostIp.isLoopbackAddress() && !hostIp.isSiteLocalAddress()) {
                    url.append("&ip=").append(URLEncoder.encode(hostIp.getHostAddress(), StandardCharsets.UTF_8));
                }

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url.toString()))
                        .timeout(Duration.ofSeconds(3))
                        .GET()
                        .build();

                HttpResponse<Void> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
                int code = response.statusCode();

                Log.info("{} Mojang hasJoined '{}' — HTTP {}", LOG_PREFIX, username, code);

                if (code == 200) return true;
                if (code != 204) Log.warn("{} Unexpected Mojang response {} for '{}'", LOG_PREFIX, code, username);
                return false;

            } catch (IOException ex) {
                Log.warn("{} Session verify IOException for '{}' — {}", LOG_PREFIX, username, ex.getMessage());
                return false;
            } catch (Exception ex) {
                Log.error("{} Session verify unexpected error for '{}' — {}", LOG_PREFIX, username, ex.getMessage());
                return false;
            }
        });
    }
}