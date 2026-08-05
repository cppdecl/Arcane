package cx.arcane.managers.teleportManager;

import cx.arcane.Arcane;
import cx.arcane.utils.Colors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportManager {

    private static final ConcurrentHashMap<UUID, TeleportTask> activeTeleports = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Location> lastLocationBeforeTeleport = new ConcurrentHashMap<>();

    public static void onEnable() {
        Bukkit.getPluginManager().registerEvents(new TeleportListener(), Arcane.getPlugin());
    }

    public static void onDisable() {

    }

    public static void onSave() {

    }

    public static void saveLastLocation(Player player) {
        lastLocationBeforeTeleport.put(player.getUniqueId(), player.getLocation().clone());
    }

    public static Location getLastLocation(Player player) {
        Location loc = lastLocationBeforeTeleport.get(player.getUniqueId());
        return loc == null ? null : loc.clone();
    }

    public static boolean hasLastLocation(Player player) {
        return lastLocationBeforeTeleport.containsKey(player.getUniqueId());
    }

    public static void clearLastLocation(Player player) {
        lastLocationBeforeTeleport.remove(player.getUniqueId());
    }

    public static TeleportBuilder teleport(Player p, Location targetLoc) {
        return new TeleportBuilder(p, targetLoc, null);
    }

    public static TeleportBuilder teleport(Player p, Player targetPlayer) {
        return new TeleportBuilder(p, null, targetPlayer);
    }

    public static class TeleportBuilder {
        private final Player player;
        private final Location staticTargetLoc;
        private final Player dynamicTargetPlayer;

        private String locationName = null;
        private long delayMs = 5000;

        private SoundConfig startSound = null;
        private SoundConfig countdownSound = new SoundConfig(Sound.UI_BUTTON_CLICK, 3f);
        private SoundConfig cancelSound = new SoundConfig(Sound.ENTITY_VILLAGER_NO, 1f);
        private SoundConfig endSound = new SoundConfig(Sound.ENTITY_ENDERMAN_TELEPORT, 1f);

        private Runnable startCountdownTask = null;
        private Runnable cancelTeleportTask = null;
        private Runnable teleportDoneTask = null;

        TeleportBuilder(Player player, Location targetLoc, Player targetPlayer) {
            this.player = player;
            this.staticTargetLoc = targetLoc;
            this.dynamicTargetPlayer = targetPlayer;

            this.delayMs = 5000; //PermissionManager.getPermissionLong(player, "arcane.delay.tpa", 5000);
        }

        public TeleportBuilder name(String name) {
            this.locationName = name;
            return this;
        }

        public TeleportBuilder delay(long ms) {
            this.delayMs = ms;
            return this;
        }


        public TeleportBuilder onCountdown(Runnable task) {
            this.startCountdownTask = task;
            return this;
        }

        public TeleportBuilder onCancelled(Runnable task) {
            this.cancelTeleportTask = task;
            return this;
        }

        public TeleportBuilder onTeleport(Runnable task) {
            this.teleportDoneTask = task;
            return this;
        }

        public TeleportBuilder teleportStartSound(Sound sound, float pitch) {
            this.startSound = new SoundConfig(sound, pitch);
            return this;
        }

        public TeleportBuilder teleportStartSound(Sound sound) {
            return teleportStartSound(sound, 1.0F);
        }

        public TeleportBuilder teleportCountdownSound(Sound sound, float pitch) {
            this.countdownSound = new SoundConfig(sound, pitch);
            return this;
        }

        public TeleportBuilder teleportCancelSound(Sound sound, float pitch) {
            this.cancelSound = new SoundConfig(sound, pitch);
            return this;
        }

        public TeleportBuilder teleportEndSound(Sound sound, float pitch) {
            this.endSound = new SoundConfig(sound, pitch);
            return this;
        }

        public TeleportBuilder teleportEndSound(Sound sound) {
            return teleportEndSound(sound, 1.0F);
        }

        public void start() {
            TeleportTask task = new TeleportTask(
                    player,
                    staticTargetLoc,
                    dynamicTargetPlayer,
                    locationName,
                    delayMs,
                    startSound,
                    countdownSound,
                    cancelSound,
                    endSound,
                    startCountdownTask,
                    cancelTeleportTask,
                    teleportDoneTask
            );
            activeTeleports.put(UUID.randomUUID(), task);
            task.start();
        }
    }

    private record SoundConfig(Sound sound, float pitch) {}

    private static class TeleportTask {

        private Runnable startCountdownTask = null;
        private Runnable cancelTeleportTask = null;
        private Runnable teleportDoneTask = null;

        private final Player player;
        private final Location staticTargetLoc;
        private final Player dynamicTargetPlayer;
        private final String customLocationName;
        private final Location startLoc;

        private final long delayMs;
        private final SoundConfig startSound;
        private final SoundConfig countdownSound;
        private final SoundConfig cancelSound;
        private final SoundConfig endSound;

        private int secondsLeft;
        private boolean cancelled = false;

        TeleportTask(Player player,
                     Location staticTargetLoc,
                     Player dynamicTargetPlayer,
                     String locationName,
                     long delayMs,
                     SoundConfig startSound,
                     SoundConfig countdownSound,
                     SoundConfig cancelSound,
                     SoundConfig endSound,
                     Runnable startCountdownTask,
                     Runnable cancelTeleportTask,
                     Runnable teleportDoneTask) {

            this.player = player;
            this.staticTargetLoc = staticTargetLoc;
            this.dynamicTargetPlayer = dynamicTargetPlayer;
            this.customLocationName = locationName;
            this.delayMs = delayMs;

            this.startSound = startSound;
            this.countdownSound = countdownSound;
            this.cancelSound = cancelSound;
            this.endSound = endSound;

            this.startLoc = player.getLocation().clone();
            this.secondsLeft = (int) (delayMs / 1000);

            this.cancelTeleportTask = cancelTeleportTask;
            this.startCountdownTask = startCountdownTask;
            this.teleportDoneTask = teleportDoneTask;
        }

        void start() {
            if (startCountdownTask != null) startCountdownTask.run();
            if (startSound != null) playSound(startSound);
            runCountdown();
        }

        private void runCountdown() {
            if (!player.isOnline() || cancelled) {
                cancel();
                return;
            }

            Location currentLoc = player.getLocation();
            if (hasMoved(currentLoc)) {
                Component msg = Component.text("Teleporting cancelled because you moved.", Colors.DARK_PINK);
                player.sendMessage(msg);
                player.sendActionBar(msg);
                if (cancelSound != null) playSound(cancelSound);

                if (cancelTeleportTask != null) cancelTeleportTask.run();

                cancel();
                return;
            }

            if (secondsLeft <= 0) {
                Location finalTarget = null;

                if (dynamicTargetPlayer != null && dynamicTargetPlayer.isConnected()) {
                    finalTarget = dynamicTargetPlayer.getLocation();
                } else if (staticTargetLoc != null) {
                    finalTarget = staticTargetLoc;
                }

                if (finalTarget == null) {
                    Component msg = Component.text("Teleporting failed because the location is invalid.", Colors.DARK_PINK);
                    player.sendMessage(msg);

                    if (cancelTeleportTask != null) cancelTeleportTask.run();

                    cancel();
                    return;
                }

                player.teleportAsync(finalTarget, PlayerTeleportEvent.TeleportCause.COMMAND)
                        .thenRun(() -> {
                            if (endSound != null) playSound(endSound);

                            TeleportManager.saveLastLocation(player);

                            TextComponent finishMessage;

                            String nameForMessage;
                            if (customLocationName != null) {
                                nameForMessage = customLocationName;
                            } else if (dynamicTargetPlayer != null) {
                                nameForMessage = dynamicTargetPlayer.getName();
                            } else {
                                nameForMessage = "";
                            }

                            if (nameForMessage.isEmpty()) {
                                finishMessage = Component.text("You teleported!", Colors.GRAY);
                            } else {
                                finishMessage =
                                        Component.text("You teleported to ", Colors.GRAY)
                                                .append(Component.text(nameForMessage, Colors.HOT_PINK))
                                                .append(Component.text("!", Colors.GRAY));
                            }

                            player.sendMessage(finishMessage);
                            player.sendActionBar(finishMessage);

                            if (teleportDoneTask != null) teleportDoneTask.run();
                        });
                cancel();
                return;
            }

            Component bar = Component.text("Teleporting in ", Colors.GRAY)
                    .append(Component.text(secondsLeft + "s", Colors.HOT_PINK));
            player.sendActionBar(bar);

            if (countdownSound != null) playSound(countdownSound);

            secondsLeft--;

            player.getScheduler().runDelayed(Arcane.getPlugin(),
                    task -> runCountdown(), new Runnable() {
                        @Override
                        public void run() {
                            cancel();
                        }
                    }, 20L);
        }

        private boolean hasMoved(Location current) {
            return Math.abs(current.getX() - startLoc.getX()) > 0.5
                    || Math.abs(current.getY() - startLoc.getY()) > 0.5
                    || Math.abs(current.getZ() - startLoc.getZ()) > 0.5;
        }

        private void playSound(SoundConfig cfg) {
            player.playSound(player.getLocation(), cfg.sound, 1f, cfg.pitch);
        }

        void cancel() {
            cancelled = true;
        }
    }
}
