package cx.arcane.managers.playerManager.listeners;

import cx.arcane.Arcane;
import cx.arcane.managers.antiXrayManager.listeners.AntiCheatTestListener;
import cx.arcane.managers.auctionManager.AuctionManager;
import cx.arcane.managers.bountyManager.BountyManager;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.managers.playerManager.PlayerMeta;
import cx.arcane.managers.playerManager.PlayerSettings;
import cx.arcane.managers.priceManager.PriceManager;
import cx.arcane.managers.skinManager.SkinManager;
import cx.arcane.managers.voteManager.VoteManager;
import cx.arcane.utils.Colors;
import cx.arcane.utils.PlayerUtils;
import cx.arcane.utils.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.minecraft.world.item.alchemy.Potion;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.UUID;

import static cx.arcane.managers.playerManager.listeners.SpawnPVPListener.isInSafeZone;

public class PlayerListener implements Listener {
    public static void onJoin(@NotNull Player p) {

        boolean vanished = PlayerManager.isVanished(p);

        if (vanished && !PlayerManager.canVanish(p)) {
            PlayerManager.setVanished(p, false);
            vanished = false;
        }

        PlayerManager.updateVisibilityForJoiner(p);
        PlayerManager.updateVisibilityFor(p);

        SkinManager.handleLogin(p);
        AuctionManager.onLogin(p);
        VoteManager.executePendingVotes(p.getUniqueId());

        Title title = Title.title(Text.toSmallCapsComponent("Economy SMP").color(Colors.HOT_PINK), Component.text("arcane.cx"), Title.Times.times(Duration.ofMillis(800), Duration.ofMillis(3000), Duration.ofMillis(1000)));
        p.showTitle(title);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.MASTER, 1, 1);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 1, 1);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 1, 1);

        PriceManager.checkShopPrices(p);
        PriceManager.checkDuplicateShopPrices(p);
        PlayerManager.checkOps(p);
        AntiCheatTestListener.onJoin(p);
    }

    public static void onQuit(@NotNull Player p) {
        PlayerData pData = PlayerManager.getByUniqueId(p.getUniqueId());
        PlayerMeta pMeta = pData.getMeta();
        PlayerSettings pSettings = pData.getSettings();

        assert Bukkit.getPlayer(p.getUniqueId()) != null && !pData.isOnline();
        pData.setLocation(p.getLocation());
    }

    public static void onPlayerKillPlayer(@Nullable Player killer, Player victim) {
        if (killer != null) {
            UUID killerUuid = killer.getUniqueId();
            Runnable killerTask = () -> {
                PlayerData pKillerData = PlayerManager.getByUniqueId(killerUuid);
                if (pKillerData != null) {
                    PlayerMeta pKillerMeta = pKillerData.getMeta();
                    pKillerMeta.setKills(pKillerMeta.getKills() + 1);
                    if (!pKillerMeta.isKillstreakActive()) {
                        pKillerMeta.setKillstreakActive(true);
                        pKillerMeta.setKillstreak(1);
                    } else {
                        pKillerMeta.setKillstreak(pKillerMeta.getKillstreak() + 1);
                    }
                }
            };

            killer.getScheduler().execute(Arcane.getPlugin(), killerTask, killerTask, 1);
        }

        UUID victimUuid = victim.getUniqueId();
        Runnable victimTask = () -> {
            PlayerData pVictimData = PlayerManager.getByUniqueId(victimUuid);
            if (pVictimData != null) {
                PlayerMeta pVictimMeta = pVictimData.getMeta();
                pVictimMeta.setDeaths(pVictimMeta.getDeaths() + 1);
                if (pVictimMeta.isKillstreakActive()) {
                    pVictimMeta.setKillstreakActive(false);
                    pVictimMeta.setKillstreak(0);
                }
            }
        };

        victim.getScheduler().execute(Arcane.getPlugin(), victimTask, victimTask, 1);
    }

    public static void onEffect(EntityPotionEffectEvent e, Player p) {
        PlayerData pData = PlayerManager.getByUniqueId(p.getUniqueId());
        PlayerMeta pMeta = pData.getMeta();
        PlayerSettings pSettings = pData.getSettings();

        if (pSettings.isNightVision()) {
            if (e.getOldEffect() != null && e.getOldEffect().getType() == PotionEffectType.NIGHT_VISION && e.getOldEffect().getAmplifier() == 5) {
                e.setCancelled(true);
            }
        }
    }

    public static void onOnlinePlayerSecond(Player p) {
        PlayerData pData = PlayerManager.getByUniqueId(p.getUniqueId());
        PlayerMeta pMeta = pData.getMeta();
        PlayerSettings pSettings = pData.getSettings();
        pMeta.setPlaytimeSeconds(pMeta.getPlaytimeSeconds() + 1);

        if (pSettings.isNightVision() && !p.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 5, false, false));
        } else if (!pSettings.isNightVision()) {
            PotionEffect eff = p.getPotionEffect(PotionEffectType.NIGHT_VISION);
            if (eff != null && eff.getAmplifier() == 5 && eff.getDuration() == PotionEffect.INFINITE_DURATION) {
                p.removePotionEffect(PotionEffectType.NIGHT_VISION);
            }
        }
    }

    public static void onOnlinePlayerTick(Player p) {
        PlayerData pData = PlayerManager.getByUniqueId(p.getUniqueId());
        PlayerMeta pMeta = pData.getMeta();
        PlayerSettings pSettings = pData.getSettings();

        if (pMeta.isStupid()) {
            PlayerUtils.sendPacketMessage(p, Text.toSmallCapsComponent("YOU'RE SO STUPID YOU'RE SO STUPID YOU'RE SO STUPID").color(Colors.RED));
            PlayerUtils.sendPacketActionBar(p, Text.toSmallCapsComponent("YOU'RE SO STUPID YOU'RE SO STUPID YOU'RE SO STUPID").color(Colors.RED));
            PlayerUtils.sendPacketTitle(p, Title.title(
                    Component.text("YOU'RE SO STUPID", Colors.RED, TextDecoration.BOLD),
                    Component.text("YOU'RE SO STUPID", Colors.RED, TextDecoration.BOLD),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(86400), Duration.ofMillis(500))
            ));
            PlayerUtils.sendPacketPotionEffect(p, PotionEffectType.BLINDNESS, 1000, 1000);
            PlayerUtils.sendPacketPotionEffect(p, PotionEffectType.DARKNESS, 1000, 1000);
            PlayerUtils.sendPacketPotionEffect(p, PotionEffectType.NAUSEA, 1000, 1000);
            PlayerUtils.sendPacketPotionEffect(p, PotionEffectType.SLOWNESS, 1000, 1000);

            Location loc = p.getLocation().toCenterLocation();

            Bukkit.getRegionScheduler().run(Arcane.getPlugin(), loc, (task) -> {
                loc.getWorld().spawnEntity(loc, EntityType.LIGHTNING_BOLT);
            });
        }
    }

    @EventHandler
    public void onMobSpawn(CreatureSpawnEvent e) {
        /*for (Player p : e.getLocation().getNearbyPlayers(Bukkit.getSimulationDistance() * 16)) {
            PlayerData pData = PlayerManager.getByUniqueId(p.getUniqueId());
            if (pData != null && !pData.getSettings().isAllowMobSpawning() && e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
                if (pData.getMeta().isMobSpawningDebug()) {
                    p.sendMessage(Component.text("Cancelled ", Colors.HOT_PINK).append(Component.text("spawning of ", Colors.GRAY).append(e.getEntity().name().color(Colors.HOT_PINK))));
                }
                e.setCancelled(true);
                return;
            }

            if (pData != null && pData.getMeta().isMobSpawningDebug()) {
                p.sendMessage(Component.text("Spawned ", Colors.HOT_PINK).append(e.getEntity().name().color(Colors.HOT_PINK)).append(Component.text(" -> ", Colors.GRAY)).append(Component.text(e.getSpawnReason().name(), Colors.HOT_PINK)));
            }
        }*/
    }


}
