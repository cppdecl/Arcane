package cx.arcane.managers.authManager.listeners;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.github.retrooper.packetevents.PacketEvents;
import cx.arcane.managers.authManager.AuthManager;
import cx.arcane.managers.authManager.AuthState;
import cx.arcane.managers.chatManager.ChatManager;
import cx.arcane.managers.playerManager.*;
import cx.arcane.managers.playerManager.listeners.PlayerListener;
import cx.arcane.managers.skinManager.SkinManager;
import cx.arcane.managers.teleportManager.TeleportListener;
import cx.arcane.utils.*;
import io.netty.channel.Channel;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import io.papermc.paper.event.player.PlayerPickEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.minecraft.network.Connection;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@SuppressWarnings("UnstableApiUsage")
public class AuthEventListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent e) {

        Log.info("[AsyncPlayerPreLoginEvent]: Event UUID is {}", e.getPlayerProfile().getId());

        PlayerSession pSession = NetUtils.getSession(e.getConnection().getClientAddress());
        if (pSession == null) {
            Log.error("[AsyncPlayerPreLoginEvent]: Player kicked as no cached session mapping for his socket address -> {} ", e.getConnection().getClientAddress());
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.text(Text.toSmallCaps("an error has occurred"), Colors.HOT_PINK));
            return;
        }

        UUID actualUUID = pSession.getUniqueId();

        Log.info("[AsyncPlayerPreLoginEvent]: Mapping UUID is {}", actualUUID);

        PlayerProfile profile = Bukkit.createProfileExact(actualUUID, e.getName());
        PlayerProfile skinProfile = SkinManager.handlePreLogin(e, profile);
        e.setPlayerProfile(Objects.requireNonNullElse(skinProfile, profile));

        if (AuthManager.isAuthenticated(actualUUID)) {
            Log.error("[AsyncPlayerPreLoginEvent]: Attempted to log-in into an already authenticated player -> {} ", e.getConnection().getClientAddress());
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.text(Text.toSmallCaps("you're already online"), Colors.HOT_PINK));
            return;
        }
    }

    @EventHandler
    public void onAsyncPlayerSpawnLocation(AsyncPlayerSpawnLocationEvent e) {
        PlayerConfigurationConnection pConfCon = e.getConnection();
        PlayerProfile pProfile = pConfCon.getProfile();

        UUID playerId = pProfile.getId();
        if (playerId == null) return;

        PlayerSession pSession = NetUtils.getSession(playerId);
        if (pSession == null) return;

        PlayerData pData = PlayerManager.getByNameIgnoreCase(pProfile.getName());
        Location lastLocation = PlayerManager.getLocation(playerId);
        AccountType type = pSession.getAccountType();

        boolean inSpawnWorld = pData != null && pData.getLocation().getWorld().getName().equals("spawn");
        Location spawnPoint = LocationUtils.getPoint("spawn");
        Location authSpawn  = LocationUtils.getPoint("authspawn");

        if (type == AccountType.PREMIUM || type == AccountType.BEDROCK) {
            if (inSpawnWorld && spawnPoint != null) e.setSpawnLocation(spawnPoint);
            else if (lastLocation != null)          e.setSpawnLocation(lastLocation);
            return;
        }

        if (type == AccountType.CRACKED) {
            boolean autoLogin = pData != null && AuthManager.shouldAutoLoginCracked(pSession, pData);

            if (autoLogin) {
                if (inSpawnWorld && spawnPoint != null) e.setSpawnLocation(spawnPoint);
                else if (lastLocation != null)          e.setSpawnLocation(lastLocation);
                return;
            }

            AuthManager.cacheStuff(playerId, lastLocation != null ? lastLocation : e.getSpawnLocation());
            if (authSpawn != null) e.setSpawnLocation(authSpawn);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        if (AuthManager.isDeadOnCache(e.getPlayer())) {
            e.setShowDeathMessages(false);
            e.setShouldDropExperience(false);
            e.setShouldPlayDeathSound(false);
            e.getPlayer().decrementStatistic(Statistic.DEATHS, 1);
        }
        else {
            TeleportListener.onDeath(e);
            PlayerListener.onPlayerKillPlayer(e.getPlayer().getKiller(), e.getPlayer());
        }
    }


    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent e) {
        e.joinMessage(null);

        Player p = e.getPlayer();
        p.setInvisible(true);

        PlayerUtils.sendPacketPotionEffect(p, PotionEffectType.BLINDNESS, 60, 0);

        AuthState aState = AuthManager.newAuthSession(e.getPlayer());
        if (aState == null) {
            Log.error("[AuthEventListener]: Auth state is null for {} ", p.getUniqueId());
            p.kick( Component.text(Text.toSmallCaps("an error has occurred"), Colors.HOT_PINK));
            return;
        }

        Log.error("[AuthEventListener] UUID of {} is {}", p.getName(), p.getUniqueId());

        Connection connection = NetUtils.getConnection(p);
        if (connection == null) {
            Log.error("[AuthEventListener]: Auth state is null for {} ", p.getUniqueId());
            p.kick( Component.text(Text.toSmallCaps("an error has occurred"), Colors.HOT_PINK));
            return;
        }

        PlayerData pExisting = PlayerManager.getByNameIgnoreCase(p.getName());
        PlayerSession pSession = NetUtils.getSession(p.getUniqueId());

        if (pExisting != null) {
            if (pExisting.isPremium() || pExisting.isBedrock()) {
                p.setInvisible(false);
                PlayerUtils.sendPacketRemovePotionEffect(p, PotionEffectType.BLINDNESS);
                aState.setAuthenticated(true);

                p.sendMessage(Component.text("Welcome back, " + p.getName() + "!", Colors.HOT_PINK));
                PlayerManager.updatePlayer(p, true);
                PlayerListener.onJoin(p);
            }
            else if (pExisting.isCracked() && AuthManager.shouldAutoLoginCracked(pSession, pExisting)) {
                p.setInvisible(false);
                PlayerUtils.sendPacketRemovePotionEffect(p, PotionEffectType.BLINDNESS);
                aState.setAuthenticated(true);

                p.sendMessage(Component.text("Welcome back, " + p.getName() + "!", Colors.HOT_PINK));
                PlayerManager.updatePlayer(p, false);
                PlayerListener.onJoin(p);
            }
        } else {
            if (pSession.getAccountType() == AccountType.BEDROCK) {
                p.setInvisible(false);
                PlayerUtils.sendPacketRemovePotionEffect(p, PotionEffectType.BLINDNESS);
                aState.setAuthenticated(true);

                p.sendMessage(Component.text("Welcome, " + p.getName() + "!", Colors.HOT_PINK));
                PlayerData pData = PlayerManager.newPlayer(p, " -- Bedrock Account -- ");
                PlayerListener.onJoin(p);
            }
            else if (pSession.getAccountType() == AccountType.PREMIUM) {
                p.setInvisible(false);
                PlayerUtils.sendPacketRemovePotionEffect(p, PotionEffectType.BLINDNESS);
                aState.setAuthenticated(true);

                p.sendMessage(Component.text("Welcome, " + p.getName() + "!", Colors.HOT_PINK));
                PlayerData pData = PlayerManager.newPlayer(p, " -- Premium Account -- ");
                PlayerListener.onJoin(p);
            }
        }

        if (aState.isAuthenticated()) return;

        PlayerUtils.sendPacketTitle(p, Title.title(
                Component.text("ᴀᴜᴛʜᴇɴᴛɪᴄᴀᴛɪᴏɴ", Colors.HOT_PINK),
                Component.text("arcane.cx", Colors.WHITE),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(86400), Duration.ofMillis(500))
        ));

        if (PlayerManager.isRegistered(p.getUniqueId())) {
            PlayerUtils.sendPacketMessage(p, Component.text()
                    .append(Component.text("Use ", Colors.GRAY))
                    .append(Component.text("/login", Colors.HOT_PINK))
                    .append(Component.space())
                    .append(Component.text("<password>", Colors.LIGHT_PINK))
                    .append(Component.text(" to use your account.", Colors.GRAY)).build());
        } else {
            PlayerUtils.sendPacketMessage(p, Component.text()
                    .append(Component.text("Use ", Colors.GRAY))
                    .append(Component.text("/register", Colors.HOT_PINK))
                    .append(Component.space())
                    .append(Component.text("<password>", Colors.LIGHT_PINK))
                    .append(Component.text(" to create an account.", Colors.GRAY)).build());
        }

        aState.setStartedAt(Instant.now());
    }

    @EventHandler // NO OTHER PlayerQuitEvent CAN EXIST APART FROM CrystalTracker & This
    public void onPlayerQuit(PlayerQuitEvent e) {
        e.quitMessage(null);

        AuthState aState = AuthManager.getAuthSession(e.getPlayer().getUniqueId());
        boolean wasAuthenticated = aState.isAuthenticated();
        AuthManager.removeAuthSession(e.getPlayer().getUniqueId());

        if (wasAuthenticated) {
            PlayerData pData = PlayerManager.getByUniqueId(e.getPlayer().getUniqueId());
            pData.setLocation(e.getPlayer().getLocation());
            PlayerListener.onQuit(e.getPlayer());

            ChatManager.clearSpamState(e.getPlayer().getUniqueId());
        }

        Log.info("{} auth session removed", e.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEffectRemove(EntityPotionEffectEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!AuthManager.isAuthenticated(e.getEntity().getUniqueId())) {
            e.setCancelled(true);
            return;
        }

        PlayerListener.onEffect(e, p);
    }


    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent e) {
        if (!AuthManager.isAuthenticated(e.getPlayer().getUniqueId())) {
            e.message(null);
            return;
        }

        e.message(null);

        PlayerData pData = PlayerManager.getByUniqueId(e.getPlayer().getUniqueId());
        Player p = e.getPlayer();

        Component txt = Component.text().append(
                Component.text(p.getName(), Colors.HOT_PINK),
                Component.text(" has achieved ", Colors.GRAY),
                e.getAdvancement().displayName().color(Colors.HOT_PINK)
        ).build();

        /*PlayerManager.broadcast(txt);*/
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent e) {
        if (!AuthManager.isAuthenticated(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncChatEvent e) {
        if (!AuthManager.isAuthenticated(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (!AuthManager.isAuthenticated(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent e) {
        if (!AuthManager.isAuthenticated(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent e) {
        if (!AuthManager.isAuthenticated(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent e) {
        if (!AuthManager.isAuthenticated(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPickEntity(PlayerPickEntityEvent e) {
        if (!AuthManager.isAuthenticated(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (!AuthManager.isAuthenticated(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!AuthManager.isAuthenticated(e.getWhoClicked().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryInteract(InventoryInteractEvent e) {
        if (!AuthManager.isAuthenticated(e.getWhoClicked().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!AuthManager.isAuthenticated(e.getWhoClicked().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent e) {
        if (e.getEntity().getType() == EntityType.PLAYER && !AuthManager.isAuthenticated(e.getEntity().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHitPlayer(EntityDamageEvent e) {
        if (e.getEntity().getType() == EntityType.PLAYER && !AuthManager.isAuthenticated(e.getEntity().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArrowPickup(PlayerPickupArrowEvent  e) {
        if (!AuthManager.isAuthenticated(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        if (!AuthManager.isAuthenticated(e.getPlayer().getUniqueId())) {
            String cmd = e.getMessage().split(" ")[0].toLowerCase(Locale.ROOT);
            if (!(cmd.equals("/login") || cmd.equals("/register"))) {
                e.setCancelled(true);
            }
        }
    }
}
