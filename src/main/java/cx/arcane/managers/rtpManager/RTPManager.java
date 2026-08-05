package cx.arcane.managers.rtpManager;

import com.github.retrooper.packetevents.util.ColorUtil;
import cx.arcane.Arcane;
import cx.arcane.managers.permissionManager.PermissionManager;
import cx.arcane.managers.teleportManager.TeleportManager;
import cx.arcane.utils.Colors;
import cx.arcane.utils.Log;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

public class RTPManager {

    public static void start(World world, Player player) {
        new RTPSession(player, world).start();
    }

    private static class RTPSession {

        private final Player player;
        private final World world;

        private Location foundLocation;
        private boolean countdownDone = false;
        private boolean cancelled = false;

        private int secondsLeft = 5;

        RTPSession(Player player, World world) {
            this.player = player;
            this.world = world;
        }

        public void start() {
            startSearch();
            startCountdown();
        }

        // ========================
        // 🔍 SEARCH (ASYNC SAFE)
        // ========================

        private void startSearch() {
            findSafeLocation(world, 15).thenAccept(loc -> {

                if (cancelled) return;

                this.foundLocation = loc;

                tryTeleport();

            });
        }

        // ========================
        // ⏳ COUNTDOWN
        // ========================

        private void startCountdown() {
            player.playSound(player.getLocation(), Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);
            tick();
        }

        private void tick() {

            if (cancelled || !player.isOnline()) return;

            if (secondsLeft <= 0) {
                countdownDone = true;
                tryTeleport();
                return;
            }

            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 3.0f);
            player.sendActionBar(Component.text().append(
                    Component.text("RTP in ", Colors.GRAY),
                    Component.text(secondsLeft + "s", Colors.HOT_PINK)
            ).build());

            secondsLeft--;

            player.getScheduler().runDelayed(Arcane.getPlugin(),
                    task -> tick(),
                    this::cancel,
                    20L
            );
        }

        // ========================
        // 🚀 TELEPORT WHEN READY
        // ========================

        private void tryTeleport() {

            if (!countdownDone) return;
            if (foundLocation == null) return;

            Location loc = foundLocation.clone().add(0.5, 1, 0.5);

            player.getScheduler().execute(Arcane.getPlugin(), () -> {

                player.teleportAsync(loc).thenRun(() -> {
                    player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
                    player.sendMessage(Component.text("You teleported to a random location!", Colors.HOT_PINK));
                    player.sendActionBar(Component.text("RTP Success!", Colors.HOT_PINK));
                });

            }, null, 0);
        }

        private void cancel() {
            cancelled = true;
        }
    }

    // ========================
    // FIND LOCATION (SAME CORE)
    // ========================

    private static CompletableFuture<Location> findSafeLocation(World world, int attempts) {
        CompletableFuture<Location> future = new CompletableFuture<>();
        tryFind(world, attempts, future);
        return future;
    }

    private static void tryFind(World world, int attempts, CompletableFuture<Location> future) {

        if (attempts <= 0) {
            future.complete(null);
            return;
        }

        int[] pos = generateRandomPos(world);
        Location loc = new Location(world, pos[0], 0, pos[1]);

        world.getChunkAtAsync(loc).thenAccept(chunk -> {

            ChunkSnapshot snapshot = chunk.getChunkSnapshot();

            int lx = loc.getBlockX() & 15;
            int lz = loc.getBlockZ() & 15;

            int highestY = snapshot.getHighestBlockYAt(lx, lz);

            if (world.getEnvironment() == World.Environment.NETHER) {

                boolean found = false;

                for (int y = 0; y < 127; y++) {

                    BlockData block = snapshot.getBlockData(lx, y, lz);
                    BlockData above1 = snapshot.getBlockData(lx, y + 1, lz);
                    BlockData above2 = snapshot.getBlockData(lx, y + 2, lz);

                    if (block.getMaterial().isSolid()
                            && above1.getMaterial() == Material.AIR
                            && above2.getMaterial() == Material.AIR) {

                        loc.setY(y);
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    tryFind(world, attempts - 1, future);
                    return;
                }

            } else {
                loc.setY(highestY);
            }

            Bukkit.getRegionScheduler().execute(Arcane.getPlugin(), loc, () -> {
                if (isValid(loc)) {
                    future.complete(loc);
                } else {
                    tryFind(world, attempts - 1, future);
                }
            });

        }).exceptionally(ex -> {
            tryFind(world, attempts - 1, future);
            return null;
        });
    }

    private static boolean isValid(Location loc) {
        Block feet = loc.getBlock();
        Block body = feet.getRelative(BlockFace.UP);
        Block head = body.getRelative(BlockFace.UP);

        if (feet.getType().key().asMinimalString().toLowerCase(Locale.ROOT).contains("leaves"))
            return false;

        return !feet.isLiquid()
                && !body.isLiquid()
                && !head.isLiquid()
                && !feet.getType().isAir();
    }

    private static int[] generateRandomPos(World world) {
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        double min = 5;
        double max = (world.getWorldBorder().getSize() / 2.0) / 5;

        int cx = world.getWorldBorder().getCenter().getBlockX();
        int cz = world.getWorldBorder().getCenter().getBlockZ();

        double range = max - min;
        double t = 1.0 - Math.pow(rand.nextDouble(), 0.35);
        int offset = (int) (min + t * range);

        int x = cx + (rand.nextBoolean() ? 1 : -1) * offset;
        int z = cz + (rand.nextBoolean() ? 1 : -1) * offset;

        return new int[]{x, z};
    }
}