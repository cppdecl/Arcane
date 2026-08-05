package cx.arcane.managers.pvpManager;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import cx.arcane.Arcane;
import cx.arcane.managers.clanManager.ClanManager;
import cx.arcane.managers.clanManager.clanInfo.ClanData;
import cx.arcane.managers.clanManager.clanInfo.ClanMember;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.managers.playerManager.PlayerMeta;
import cx.arcane.managers.playerManager.listeners.SpawnPVPListener;
import cx.arcane.utils.*;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class PVPManager {

    // -------------------------------------------------------------------------
    // Types
    // -------------------------------------------------------------------------

    public enum ExplosiveType { BED, RESPAWN_ANCHOR, CRYSTAL, TNT }

    /**
     * Tracks who placed/triggered a bed or respawn anchor and the ItemStack that
     * represents it — used for death message attribution and friendly-fire checks.
     */
    public record ExplosiveSource(Player owner, ExplosiveType type, ItemStack stack) {}

    /**
     * Lightweight coordinate key for fake-block visualiser tracking.
     * Avoids the overhead of creating full {@link Location} objects.
     */
    public record BlockPos(int x, int y, int z) {}

    /**
     * Snapshot of who hit an EnderCrystal and which ItemStack represents the crystal.
     * Populated the moment a player damages the crystal entity, and consumed when the
     * resulting explosion damages another player.
     *
     * @param attacker The player who struck the crystal.
     * @param weapon   A cloned END_CRYSTAL ItemStack, custom-named if the entity had a nametag.
     */
    public record CrystalAttack(Player attacker, ItemStack weapon) {}

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /** Beds and respawn anchors tracked by their block location. Entries auto-expire after 40 ticks. */
    private static final Map<Location, ExplosiveSource> explosiveSources  = new ConcurrentHashMap<>();

    /**
     * Maps EnderCrystal entity IDs to the player who most recently struck them.
     * Updated whenever a player hits a crystal; consumed when the crystal explosion damages a player.
     * Cleaned up via EntityRemoveFromWorldEvent.
     */
    private static final Map<Integer, CrystalAttack>  crystalAttacks     = new ConcurrentHashMap<>();

    /**
     * Maps AbstractArrow entity IDs to the bow ItemStack used to fire them.
     * Populated in EntityShootBowEvent; consumed in the damage handler; cleaned up on entity removal.
     */
    private static final Map<Integer, ItemStack>      arrowWeapons       = new ConcurrentHashMap<>();

    /**
     * Maps ExplosiveMinecart entity IDs to the player who most recently hit them.
     * Populated on EntityDamageByEntityEvent (minecart + player); consumed when the explosion damages a player.
     * Cleaned up via EntityRemoveFromWorldEvent.
     */
    private static final Map<Integer, Player>         minecartAttackers  = new ConcurrentHashMap<>();

    /** Active combat sessions keyed by the session owner's UUID. */
    private static final Map<UUID, PVPSession>        sessions           = new ConcurrentHashMap<>();

    /** Fake glass-block positions currently shown to each player as the safe-zone boundary. */
    private static final Map<UUID, Set<BlockPos>>     visualizedBlocks   = new ConcurrentHashMap<>();

    /** Last known block-level position of each player, used to skip re-computation when stationary. */
    private static final Map<UUID, Location>          lastLocations      = new ConcurrentHashMap<>();

    private static ScheduledTask tickWorker;
    private static ScheduledTask visualizerTickWorker;

    private static final int      visualizerRange = 10;
    private static final Material visualizerBlock = Material.RED_STAINED_GLASS;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /** Registers the PVP listener and starts background workers. Call from plugin onEnable. */
    public static void onEnable() {
        Bukkit.getPluginManager().registerEvents(new PVPListener(), Arcane.getPlugin());
        startTickWorker();
        startVisualizerWorker();
    }

    /** Notifies all in-combat players, clears all sessions, and stops background workers. Call from plugin onDisable. */
    public static void onDisable() {
        stopTickWorker();
        stopVisualizerWorker();

        for (Map.Entry<UUID, PVPSession> entry : sessions.entrySet()) {
            PVPSession session = entry.getValue();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.sendMessage(Component.text("Your combat session has ended due to server shutdown.", Colors.GRAY));
                player.sendActionBar(Component.text(""));
            }
            UUID attackerId = session.getLastAttackerId();
            if (attackerId != null) {
                Player attacker = Bukkit.getPlayer(attackerId);
                if (attacker != null) {
                    attacker.sendMessage(Component.text("Your opponent's combat session ended due to server shutdown.", Colors.GRAY));
                    attacker.sendActionBar(Component.text(""));
                }
            }
        }

        sessions.clear();
    }

    public static void onSave() {

    }

// -------------------------------------------------------------------------
// Explosive tracking  (beds / respawn anchors)
// -------------------------------------------------------------------------

    /**
     * Registers a bed or respawn anchor at the given location as owned by {@code owner}.
     */
    public static void trackExplosive(Location loc, Player owner, ExplosiveType type, ItemStack stack) {

     /*   Log.info("========== TRACK EXPLOSIVE ==========");
        Log.info("Owner: " + (owner != null ? owner.getName() : "NULL"));
        Log.info("Type: " + type);
        Log.info("Location: " + loc);

        if (stack == null) {
            Log.info("Stack: NULL");
        } else {
            Log.info("Stack: " + stack.getType());
            Log.info("Stack amount: " + stack.getAmount());
            Log.info("Has meta: " + stack.hasItemMeta());
        }*/

        explosiveSources.put(loc, new ExplosiveSource(owner, type, stack));

      /*  Log.info("Explosive stored successfully at: " + loc);*/
    }

    /** Immediately removes the explosive record at the given location, if present. */
    public static void untrackExplosive(Location loc) {

     /*   Log.info("========== UNTRACK EXPLOSIVE ==========");
        Log.info("Removing explosive at: " + loc);*/

        boolean removed = explosiveSources.remove(loc) != null;

     /*   Log.info("Removed: " + removed);*/
    }

    /**
     * Lookup explosive source for a block location.
     */
    public static ExplosiveSource getExplosive(Location loc) {

        /*Log.info("========== GET EXPLOSIVE ==========");
        Log.info("Lookup location: " + loc);*/

        ExplosiveSource source = explosiveSources.get(loc);

        if (source == null) {
            /*Log.info("RESULT: NULL (no explosive found)");*/
        } else {
          /* Log.info("RESULT: FOUND");
            Log.info("Owner: " + (source.owner() != null ? source.owner().getName() : "NULL"));
            Log.info("Type: " + source.type());
            Log.info("Stack: " + (source.stack() != null ? source.stack().getType() : "NULL"));*/
        }

        return source;
    }


// -------------------------------------------------------------------------
// Crystal attack tracking
// -------------------------------------------------------------------------

    /**
     * Registers crystal attacker
     */
    public static void trackCrystalAttack(int entityId, Player attacker, ItemStack weapon) {

        /*Log.info("========== TRACK CRYSTAL ==========");
        Log.info("Entity ID: " + entityId);
        Log.info("Attacker: " + (attacker != null ? attacker.getName() : "NULL"));*/

        if (weapon == null) {
       /*     Log.info("Weapon: NULL");*/
        } else {
          /*  Log.info("Weapon: " + weapon.getType());
            Log.info("Weapon meta: " + weapon.hasItemMeta());*/
        }

        crystalAttacks.put(entityId, new CrystalAttack(attacker, weapon));

       /* Log.info("Crystal attack stored for entity " + entityId);*/
    }

    /** Removes crystal tracking */
    public static void untrackCrystalAttack(int entityId) {

    /*    Log.info("========== UNTRACK CRYSTAL ==========");
        Log.info("Entity ID: " + entityId);*/

        CrystalAttack removed = crystalAttacks.remove(entityId);

   /*     Log.info("Removed: " + (removed != null));*/
    }

    /** Lookup crystal attacker */
    public static CrystalAttack getCrystalAttack(int entityId) {

      /*  Log.info("========== GET CRYSTAL ==========");
        Log.info("Entity ID: " + entityId);*/

        CrystalAttack attack = crystalAttacks.get(entityId);

       /* if (attack == null) {
            Log.info("RESULT: NULL");
        } else {
            Log.info("Attacker: " + (attack.attacker() != null ? attack.attacker().getName() : "NULL"));
        }*/

        return attack;
    }


// -------------------------------------------------------------------------
// Arrow weapon tracking
// -------------------------------------------------------------------------

    /**
     * Stores bow used to fire arrow
     */
    public static void trackArrow(int arrowEntityId, ItemStack bow) {

     /*   Log.info("========== TRACK ARROW ==========");
        Log.info("Arrow ID: " + arrowEntityId);

        if (bow == null) {
            Log.info("Bow: NULL");
        } else {
            Log.info("Bow: " + bow.getType());
            Log.info("Has meta: " + bow.hasItemMeta());
        }*/

        arrowWeapons.put(arrowEntityId, bow);

        /*Log.info("Arrow weapon stored");*/
    }

    /** Removes arrow mapping */
    public static void untrackArrow(int arrowEntityId) {

      /*  Log.info("========== UNTRACK ARROW ==========");
        Log.info("Arrow ID: " + arrowEntityId);*/

        boolean removed = arrowWeapons.remove(arrowEntityId) != null;

      /*  Log.info("Removed: " + removed);*/
    }

    /** Lookup bow */
    public static ItemStack getArrowWeapon(int arrowEntityId) {

       /* Log.info("========== GET ARROW ==========");
        Log.info("Arrow ID: " + arrowEntityId);*/

        ItemStack bow = arrowWeapons.get(arrowEntityId);

       /* Log.info("Result: " + (bow != null ? bow.getType() : "NULL"));*/

        return bow;
    }


// -------------------------------------------------------------------------
// TNT Minecart attacker tracking
// -------------------------------------------------------------------------

    /**
     * Stores minecart attacker
     */
    public static void trackMinecartAttacker(int entityId, Player attacker) {

       /* Log.info("========== TRACK MINECART ==========");
        Log.info("Entity ID: " + entityId);*
        Log.info("Attacker: " + (attacker != null ? attacker.getName() : "NULL"));*/

        minecartAttackers.put(entityId, attacker);

       /*Log.info("Minecart attacker stored");*/
    }

    /** Remove minecart attacker */
    public static void untrackMinecartAttacker(int entityId) {

        /*Log.info("========== UNTRACK MINECART ==========");
        Log.info("Entity ID: " + entityId);*/

        Player removed = minecartAttackers.remove(entityId);

        /*Log.info("Removed: " + (removed != null ? removed.getName() : "NULL"));*/
    }

    /** Lookup minecart attacker */
    public static Player getMinecartAttacker(int entityId) {

       /* Log.info("========== GET MINECART ==========");
        Log.info("Entity ID: " + entityId);*/

        Player attacker = minecartAttackers.get(entityId);

      /*  Log.info("Result: " + (attacker != null ? attacker.getName() : "NULL"));*/

        return attacker;
    }

    // -------------------------------------------------------------------------
    // Clan helpers
    // -------------------------------------------------------------------------

    /**
     * Returns true when {@code attacker} and {@code victim} are in the same clan AND
     * that clan has friendly fire disabled. Used to cancel PvP damage between clan members.
     *
     * @param attacker Player dealing damage.
     * @param victim   Player receiving damage.
     * @return True if the damage should be blocked as friendly fire.
     */
    public static boolean isFriendlyFire(Player attacker, Player victim) {
        if (!ClanManager.hasClan(attacker.getUniqueId()) || !ClanManager.hasClan(victim.getUniqueId())) return false;
        if (!ClanManager.isSameClan(attacker.getUniqueId(), victim.getUniqueId())) return false;
        ClanData clan = ClanManager.getPlayerClan(victim.getUniqueId());
        return clan != null && !clan.isFriendlyFireEnabled();
    }

    /**
     * Increments kill/death counters on both the victim's and killer's {@link ClanData}
     * and their individual {@link ClanMember} records.
     *
     * @param victim The player who died.
     * @param killer The player credited with the kill, or null for environmental death.
     */
    public static void processClanDeath(Player victim, Player killer) {
        if (ClanManager.hasClan(victim.getUniqueId())) {
            ClanData cData = ClanManager.getPlayerClan(victim.getUniqueId());
            if (cData != null) {
                cData.setDeaths(cData.getDeaths() + 1);
                ClanMember cMember = cData.getMember(victim.getUniqueId());
                if (cMember != null) cMember.setDeaths(cMember.getDeaths() + 1);
            }
        }

        if (killer != null && ClanManager.hasClan(killer.getUniqueId())) {
            ClanData cData = ClanManager.getPlayerClan(killer.getUniqueId());
            if (cData != null) {
                cData.setKills(cData.getKills() + 1);
                ClanMember cMember = cData.getMember(killer.getUniqueId());
                if (cMember != null) cMember.setKills(cMember.getKills() + 1);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Combat sessions
    // -------------------------------------------------------------------------

    /**
     * Convenience overload — no weapon or explosive type recorded.
     *
     * @param victim   Player receiving damage.
     * @param attacker Player dealing damage.
     * @param damage   Final damage value from the event.
     */
    public static void onDamage(Player victim, Player attacker, double damage) {
        onDamage(victim, attacker, damage, null, null);
    }

    /**
     * Convenience overload — records explosive type but no weapon ItemStack.
     *
     * @param victim       Player receiving damage.
     * @param attacker     Player dealing damage.
     * @param damage       Final damage value from the event.
     * @param weaponType   Explosive category, or null for melee/projectile.
     */
    public static void onDamage(Player victim, Player attacker, double damage, ExplosiveType weaponType) {
        onDamage(victim, attacker, damage, null, weaponType);
    }

    /**
     * Core damage handler. Creates or refreshes combat sessions for both participants,
     * records highest-damage attacker, stores the weapon snapshot, and sends first-hit
     * notifications if this is the opening hit of a session.
     *
     * <p>Thread safety: sessions map is a {@link ConcurrentHashMap}; individual session
     * mutation here happens on the region thread via event handling or the entity scheduler.</p>
     *
     * @param victim       Player receiving damage.
     * @param attacker     Player dealing damage.
     * @param damage       Final damage value (post-armour, post-enchantment).
     * @param weapon       Pre-cloned ItemStack of the weapon used, or null if unavailable.
     *                     Stored as-is — caller must clone before passing.
     * @param weaponType   Explosive category for non-melee sources, or null.
     */
    public static void onDamage(Player victim, Player attacker, double damage, ItemStack weapon, ExplosiveType weaponType) {

        /*Log.info("========== PVP DAMAGE EVENT ==========");
        Log.info("Victim: " + victim.getName());
        Log.info("Attacker: " + (attacker != null ? attacker.getName() : "NULL"));
        Log.info("Damage: " + damage);
        Log.info("WeaponType (ExplosiveType): " + weaponType);*/

        if (weapon == null) {
            /*Log.info("Weapon: NULL");*/
        } else {
            /*Log.info("Weapon: " + weapon.getType());
            Log.info("Weapon amount: " + weapon.getAmount());
            Log.info("Weapon meta present: " + weapon.hasItemMeta());*/
        }

        if (attacker == null || attacker.equals(victim)) {
            /*Log.info("ABORT: attacker is null or same as victim");*/
            return;
        }

        PVPSession victimSession = sessions.computeIfAbsent(
                victim.getUniqueId(),
                uuid -> {
                    /*Log.info("Creating NEW victim session for " + victim.getName());*/
                    return new PVPSession(uuid, 8000);
                }
        );

        PVPSession attackerSession = sessions.computeIfAbsent(
                attacker.getUniqueId(),
                uuid -> {
                    /*Log.info("Creating NEW attacker session for " + attacker.getName());*/
                    return new PVPSession(uuid, 8000);
                }
        );

        /*Log.info("VictimSession exists: " + (victimSession != null));
        Log.info("AttackerSession exists: " + (attackerSession != null));*/

        // -------------------------
        // Attacker tracking
        // -------------------------
        /*Log.info("Adding attacker relationship (victim <- attacker, attacker <- victim)");*/
        victimSession.addAttacker(attacker.getUniqueId());
        attackerSession.addAttacker(victim.getUniqueId());
        victimSession.setLastAttackerId(attacker.getUniqueId());

        // -------------------------
        // Weapon tracking (CRITICAL DEBUG POINT)
        // -------------------------
        if (weapon != null && !weapon.getType().isAir()) {

            /*Log.info("Candidate weapon detected for session storage");*/

            ItemStack previous = victimSession.getAttackerWeapon();

            if (previous == null) {
                /*Log.info("No previous weapon stored -> setting new weapon");*/
            } else {
                /*Log.info("Previous weapon exists: " + previous.getType());
                Log.info("Overwriting weapon with: " + weapon.getType());*/
            }

            victimSession.setAttackerWeapon(weapon);

        } else {
            /*Log.info("Weapon NOT stored (null or air)");*/
        }

        // -------------------------
        // Reset cooldowns
        // -------------------------
        /*Log.info("Resetting cooldowns");*/
        victimSession.resetCooldown();
        attackerSession.resetCooldown();

        // -------------------------
        // First hit logic
        // -------------------------
        /*Log.info("Victim firstHit = " + victimSession.isFirstHit());
        Log.info("Attacker firstHit = " + attackerSession.isFirstHit());*/

        if (victimSession.isFirstHit()) {
            /*Log.info("Sending FIRST HIT message to victim");*/

            victim.sendMessage(Component.text("You've been hit by ", Colors.GRAY)
                    .append(Component.text(attacker.getName(), Colors.HOT_PINK))
                    .append(Component.text(", do not log out!", Colors.GRAY)));
        }

        if (attackerSession.isFirstHit()) {
            /*Log.info("Sending FIRST HIT message to attacker");*/

            attacker.sendMessage(Component.text("You engaged with ", Colors.GRAY)
                    .append(Component.text(victim.getName(), Colors.HOT_PINK))
                    .append(Component.text(", do not log out!", Colors.GRAY)));
        }

        victimSession.setFirstHit(false);
        attackerSession.setFirstHit(false);

        /*Log.info("FirstHit flags reset (victim + attacker)");*/
        /*Log.info("========== END PVP DAMAGE EVENT ==========");*/
    }

    /**
     * Ends the combat session for the given player, notifies them, clears their visualiser,
     * and removes the session from the active map.
     *
     * @param playerId UUID of the player whose session should be ended.
     */
    public static void endSession(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.sendMessage(Component.text("You are no longer in combat", Colors.HOT_PINK));
            player.sendActionBar(Component.text(""));
            clearVisualizer(player);
        }
        sessions.remove(playerId);
    }

    /** Forcibly clears all active combat sessions without notifying players. */
    public static void endCombat() {
        sessions.clear();
    }

    /**
     * @param playerId UUID to check.
     * @return True if a session exists for this player and its cooldown has not expired.
     */
    public static boolean isInCombat(UUID playerId) {
        PVPSession session = sessions.get(playerId);
        return session != null && session.isInCombat();
    }

    /**
     * @param playerId UUID to look up.
     * @return The active {@link PVPSession} for this player, or null if not in combat.
     */
    public static PVPSession getSession(UUID playerId) {
        return sessions.get(playerId);
    }

    /**
     * Called when a player disconnects. If they are in combat, kills them (health set to 0),
     * notifies their last attacker, and removes both sessions.
     *
     * @param player The player who quit.
     */
    public static void handleLogout(Player player) {
        UUID quitterId = player.getUniqueId();
        PVPSession quitterSession = sessions.get(quitterId);
        if (quitterSession == null || !quitterSession.isInCombat()) return;

        player.setHealth(0.0);

        UUID attackerId = quitterSession.getLastAttackerId();
        if (attackerId != null) {
            sessions.remove(attackerId);
            Player attacker = Bukkit.getPlayer(attackerId);
            if (attacker != null) {
                clearVisualizer(attacker);
                attacker.sendMessage(Component.text("Your opponent ", Colors.GRAY)
                        .append(Component.text(player.getName(), Colors.HOT_PINK))
                        .append(Component.text(" has logged out during combat.", Colors.GRAY)));
            }
        }

        sessions.remove(quitterId);
    }

    /**
     * Called on PlayerDeathEvent. Processes clan stats, Elo changes, clears visualisers,
     * and removes sessions for both the victim and their last attacker.
     *
     * @param victim The player who died.
     * @param killer The credited killer, or null for environmental death.
     */
    public static void handleDeath(Player victim, Player killer) {
        processClanDeath(victim, killer);
        processEloDeath(victim, killer);

        UUID victimId = victim.getUniqueId();
        PVPSession session = sessions.get(victimId);
        if (session != null && session.isInCombat()) {
            UUID attackerId = session.getLastAttackerId();
            if (attackerId != null) {
                sessions.remove(attackerId);
                Player attacker = Bukkit.getPlayer(attackerId);
                if (attacker != null) {
                    clearVisualizer(attacker);
                    attacker.sendMessage(Component.text("You are no longer in combat.", Colors.GRAY));
                }
            }
            clearVisualizer(victim);
            sessions.remove(victimId);
        }
    }

    /**
     * Applies Elo changes to both participants using the EloUtils protocol,
     * then sends title/message feedback to each player showing the delta.
     *
     * @param victim The player who died.
     * @param killer The credited killer, or null (skips Elo processing).
     */
    private static void processEloDeath(Player victim, Player killer) {
        if (killer == null || killer.equals(victim)) return;

        PlayerData pKillerData = PlayerManager.getByUniqueId(killer.getUniqueId());
        if (pKillerData == null) return;
        PlayerMeta pKillerMeta = pKillerData.getMeta();
        if (pKillerMeta == null) return;

        PlayerData pVictimData = PlayerManager.getByUniqueId(victim.getUniqueId());
        if (pVictimData == null) return;
        PlayerMeta pVictimMeta = pVictimData.getMeta();
        if (pVictimMeta == null) return;

        long currentKillerElo = pKillerMeta.getElo();
        long currentVictimElo = pVictimMeta.getElo();

        EloUtils protocol = new EloUtils()
                .winnerRating(currentKillerElo)
                .loserRating(currentVictimElo)
                .result(EloUtils.MatchConclusionType.WIN)
                .minimumRating(100)
                .kFactor(28)
                .calculate();

        long winnerNew = protocol.winnerResult();
        long loserNew  = protocol.loserResult();

        pKillerMeta.setElo(winnerNew);
        pVictimMeta.setElo(loserNew);

        long winnerGained = winnerNew - currentKillerElo;
        long loserLost    = currentVictimElo - loserNew;

        killer.showTitle(Title.title(
                Component.text("+" + winnerGained + " Elo", Colors.HOT_PINK),
                Component.text("Killed ", Colors.GRAY).append(Component.text(victim.getName(), Colors.WHITE)),
                Title.Times.times(
                        java.time.Duration.ofMillis(TimeUtils.ticksToMs(5)),
                        java.time.Duration.ofMillis(TimeUtils.ticksToMs(35)),
                        java.time.Duration.ofMillis(TimeUtils.ticksToMs(5))
                )
        ));

        victim.showTitle(Title.title(
                Component.text("-" + loserLost + " Elo", Colors.DARK_PINK),
                Component.text("Killed by ", Colors.GRAY).append(Component.text(killer.getName(), Colors.WHITE)),
                Title.Times.times(
                        java.time.Duration.ofMillis(TimeUtils.ticksToMs(5)),
                        java.time.Duration.ofMillis(TimeUtils.ticksToMs(35)),
                        java.time.Duration.ofMillis(TimeUtils.ticksToMs(5))
                )
        ));

        long killerDiff = winnerNew - currentKillerElo;
        long victimDiff = loserNew  - currentVictimElo;

        TextColor killerColor = killerDiff > 0 ? TextColor.color(0x00ff00) : killerDiff < 0 ? TextColor.color(0xff0000) : TextColor.color(0xffa500);
        TextColor victimColor = victimDiff > 0 ? TextColor.color(0x00ff00) : victimDiff < 0 ? TextColor.color(0xff0000) : TextColor.color(0xffa500);

        killer.sendMessage(Component.text("Killer ", Colors.GRAY)
                .append(Component.text(killer.getName(), Colors.HOT_PINK))
                .append(Component.text(" ", Colors.GRAY))
                .append(Component.text(currentKillerElo))
                .append(Component.text(" -> ", Colors.GRAY))
                .append(Component.text(winnerNew + " ", killerColor))
                .append(Component.text((killerDiff >= 0 ? "+" : "") + winnerGained, killerDiff == 0 ? Colors.GRAY : killerColor)));

        killer.sendMessage(Component.text("Victim ", Colors.GRAY)
                .append(Component.text(victim.getName(), Colors.HOT_PINK))
                .append(Component.text(" ", Colors.GRAY))
                .append(Component.text(currentVictimElo))
                .append(Component.text(" -> ", Colors.GRAY))
                .append(Component.text(loserNew + " ", victimColor))
                .append(Component.text((victimDiff >= 0 ? "+" : "") + victimDiff, victimDiff == 0 ? Colors.GRAY : victimColor)));
    }

    /**
     * Cancels the given command event and notifies the player they cannot use commands
     * while in combat.
     *
     * @param player The in-combat player.
     * @param e      The command event to cancel.
     */
    public static void cancelCommandIfInCombat(Player player, PlayerCommandPreprocessEvent e) {
        player.sendMessage(Component.text("You can't use commands while in combat!", Colors.DARK_PINK));
        e.setCancelled(true);
    }

    // -------------------------------------------------------------------------
    // Tick worker
    // -------------------------------------------------------------------------

    /**
     * Starts the async 1 Hz tick worker that checks whether each combat session's cooldown
     * has expired. For expired sessions it ends combat; for active sessions it updates the
     * player's action bar with a remaining-seconds countdown.
     * No-ops if the worker is already running.
     */
    public static void startTickWorker() {
        if (tickWorker != null && !tickWorker.isCancelled()) return;

        tickWorker = Bukkit.getAsyncScheduler().runAtFixedRate(Arcane.getPlugin(), t -> {
            long now = System.currentTimeMillis();

            for (PVPSession session : sessions.values()) {
                Player player = Bukkit.getPlayer(session.getPlayerId());
                if (player == null) continue;
                if (PlayerManager.getByUniqueId(player.getUniqueId()) == null) continue;

                player.getScheduler().run(Arcane.getPlugin(), st -> {
                    long remaining = session.getCooldownMs() - (now - session.getLastDamageTime());

                    if (remaining <= 0) {
                        clearVisualizer(player);
                        sessions.remove(session.getPlayerId());
                        player.sendMessage(Component.text("You are no longer in combat", Colors.HOT_PINK));
                        player.sendActionBar(Component.text(""));
                        return;
                    }

                    int seconds = (int) Math.ceil(remaining / 1000.0);
                    player.sendActionBar(Component.text("In combat for ", Colors.GRAY)
                            .append(Component.text(seconds + "s", Colors.HOT_PINK)));
                }, null);
            }
        }, 1000, 1000, TimeUnit.MILLISECONDS);
    }

    /** Cancels the combat tick worker if running. */
    public static void stopTickWorker() {
        if (tickWorker != null) {
            tickWorker.cancel();
            tickWorker = null;
        }
    }

    // -------------------------------------------------------------------------
    // Visualizer worker
    // -------------------------------------------------------------------------

    /**
     * Starts the async 5 Hz visualiser worker that sends fake red-glass boundary blocks
     * to each in-combat player so they can see the safe-zone edge. No-ops if already running.
     */
    public static void startVisualizerWorker() {
        if (visualizerTickWorker != null && !visualizerTickWorker.isCancelled()) return;

        visualizerTickWorker = Bukkit.getAsyncScheduler().runAtFixedRate(Arcane.getPlugin(), t -> {
            for (PlayerData pData : PlayerManager.getOnline()) {
                if (!isInCombat(pData.getUniqueId())) continue;
                Player p = pData.getPlayer();
                p.getScheduler().run(Arcane.getPlugin(), task -> handleVisualizerPlayer(p), null);
            }
        }, 1000, 200, TimeUnit.MILLISECONDS);
    }

    /** Cancels the visualiser worker if running. */
    public static void stopVisualizerWorker() {
        if (visualizerTickWorker != null) {
            visualizerTickWorker.cancel();
            visualizerTickWorker = null;
        }
    }

    /**
     * Computes the new set of boundary blocks around {@code p}, diffs against the previous
     * set, sends fake-block packets for new positions, and restores real blocks at old positions.
     * Skips computation entirely if the player hasn't moved since the last call.
     *
     * @param p The in-combat player to update the visualiser for.
     */
    private static void handleVisualizerPlayer(Player p) {
        UUID uuid = p.getUniqueId();
        Location now  = p.getLocation();
        Location last = lastLocations.get(uuid);

        if (last != null
                && last.getBlockX() == now.getBlockX()
                && last.getBlockY() == now.getBlockY()
                && last.getBlockZ() == now.getBlockZ()) return;

        lastLocations.put(uuid, now);

        Set<BlockPos> newBlocks = getNearbyBoundary(p);
        Set<BlockPos> oldBlocks = visualizedBlocks.getOrDefault(uuid, new HashSet<>());

        for (BlockPos pos : newBlocks) {
            if (!oldBlocks.contains(pos)) sendFakeBlock(p, pos, visualizerBlock);
        }

        for (BlockPos pos : oldBlocks) {
            if (!newBlocks.contains(pos)) restoreBlock(p, pos);
        }

        visualizedBlocks.put(uuid, newBlocks);
    }

    /**
     * Returns all replaceble block positions within {@link #visualizerRange} of the player
     * that sit on the safe-zone boundary (i.e., are safe-zone blocks adjacent to non-safe-zone blocks).
     *
     * @param p The player to compute the boundary around.
     * @return Set of {@link BlockPos} coordinates that should receive a fake glass block.
     */
    private static Set<BlockPos> getNearbyBoundary(Player p) {
        Location loc = p.getLocation();
        int px = loc.getBlockX(), py = loc.getBlockY(), pz = loc.getBlockZ();
        int height = 4;
        Set<BlockPos> result = new HashSet<>();

        for (int x = -visualizerRange; x <= visualizerRange; x++) {
            for (int z = -visualizerRange; z <= visualizerRange; z++) {
                if (x * x + z * z > visualizerRange * visualizerRange) continue;
                int bx = px + x, bz = pz + z;
                if (isSafeZone(bx, py, bz)) continue;
                addIfSafe(p, result, bx + 1, py, bz, height);
                addIfSafe(p, result, bx - 1, py, bz, height);
                addIfSafe(p, result, bx, py, bz + 1, height);
                addIfSafe(p, result, bx, py, bz - 1, height);
            }
        }

        return result;
    }

    /**
     * Adds the column of replaceable blocks at (x, y±height, z) to {@code result}
     * if that column is inside the safe zone.
     */
    private static void addIfSafe(Player p, Set<BlockPos> result, int x, int y, int z, int height) {
        if (!isSafeZone(x, y, z)) return;
        for (int dy = -height; dy <= height; dy++) {
            int by = y + dy;
            Block block = p.getWorld().getBlockAt(x, by, z);
            if (canReplace(block)) result.add(new BlockPos(x, by, z));
        }
    }

    /**
     * @return True if the block can be visually replaced by a fake glass block without
     *         blocking player movement (air, carpets, and non-solid blocks).
     */
    private static boolean canReplace(Block block) {
        Material type = block.getType();
        if (type.isAir()) return true;
        if (type.name().endsWith("_CARPET")) return true;
        return !type.isSolid();
    }

    /**
     * @param x World X coordinate.
     * @param y World Y coordinate (unused in safe-zone check but kept for API symmetry).
     * @param z World Z coordinate.
     * @return True if the given XZ position is inside the spawn safe zone.
     */
    public static boolean isSafeZone(long x, long y, long z) {
        return SpawnPVPListener.isInSafeZone(x, z);
    }

    /**
     * Restores all fake glass blocks shown to {@code p} and clears their visualiser state.
     * Scheduled on the player's entity scheduler to ensure correct threading.
     *
     * @param p The player whose visualiser should be cleared.
     */
    public static void clearVisualizer(Player p) {
        UUID uuid = p.getUniqueId();
        p.getScheduler().run(Arcane.getPlugin(), task -> {
            Set<BlockPos> blocks = visualizedBlocks.remove(uuid);
            if (blocks != null) {
                for (BlockPos pos : blocks) restoreBlock(p, pos);
            }
            lastLocations.remove(uuid);
        }, null);
    }

    /**
     * Sends a fake block-change packet to {@code player} replacing the block at {@code pos}
     * with the given material. Only affects the client — no world state is modified.
     *
     * @param player Target player.
     * @param pos    Block coordinate.
     * @param mat    Material to display.
     */
    private static void sendFakeBlock(Player player, BlockPos pos, Material mat) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerBlockChange(
                new Vector3i(pos.x(), pos.y(), pos.z()),
                SpigotConversionUtil.fromBukkitBlockData(mat.createBlockData())
        ));
    }

    /**
     * Sends a block-change packet that restores the real server-side block at {@code pos}
     * to the client, undoing any previous fake-block packet.
     *
     * @param player Target player.
     * @param pos    Block coordinate to restore.
     */
    private static void restoreBlock(Player player, BlockPos pos) {
        Block real = player.getWorld().getBlockAt(pos.x(), pos.y(), pos.z());
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerBlockChange(
                new Vector3i(pos.x(), pos.y(), pos.z()),
                SpigotConversionUtil.fromBukkitBlockData(real.getBlockData())
        ));
    }
}