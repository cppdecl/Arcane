package cx.arcane.managers.antiXrayManager;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.LightData;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import cx.arcane.Arcane;
import cx.arcane.managers.antiXrayManager.listeners.AntiCheatTestListener;
import cx.arcane.managers.antiXrayManager.listeners.bukkit.EntityListener;
import cx.arcane.managers.antiXrayManager.listeners.bukkit.PlayerListener;
import cx.arcane.managers.antiXrayManager.listeners.packet.ChunkPacketListener;
import cx.arcane.managers.antiXrayManager.listeners.packet.EntityPacketListener;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.utils.ChunkUtils;
import cx.arcane.utils.Colors;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AntiXrayManager {

    public static final int UPDATE_CHUNK_RADIUS = 3;
    public static final int UPDATE_RADIUS_HALF = UPDATE_CHUNK_RADIUS / 2;
    public static final com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState HIDE_BLOCK_AIR = StateTypes.AIR.createBlockState();
    public static final com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState HIDE_BLOCK_DEEPSLATE = StateTypes.DEEPSLATE.createBlockState();


    private static final ConcurrentHashMap<UUID, PlayerData> playerDataMap = new ConcurrentHashMap<>();

    private static final BlockFace[] FACES = {
            BlockFace.UP, BlockFace.DOWN,
            BlockFace.NORTH, BlockFace.SOUTH,
            BlockFace.EAST, BlockFace.WEST
    };

    private record BlockPointer(int x, int y, int z) {}

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public static void onEnable() {
        Bukkit.getPluginManager().registerEvents(new PlayerListener(), Arcane.getPlugin());
        Bukkit.getPluginManager().registerEvents(new EntityListener(), Arcane.getPlugin());

        AntiCheatTestListener testListener = new AntiCheatTestListener();
        Bukkit.getPluginManager().registerEvents(testListener, Arcane.getPlugin());
        PacketEvents.getAPI().getEventManager().registerListener(testListener, PacketListenerPriority.MONITOR);

        PacketEvents.getAPI().getEventManager().registerListener(new ChunkPacketListener(), PacketListenerPriority.LOWEST);
        PacketEvents.getAPI().getEventManager().registerListener(new EntityPacketListener(), PacketListenerPriority.LOWEST);
    }

    public static void onDisable() {}

    public static void onSave() {

    }

    public static void debug(String reason) {}

    // -------------------------------------------------------------------------
    // Player data
    // -------------------------------------------------------------------------

    private static PlayerData getOrCreate(Player p) {
        return playerDataMap.computeIfAbsent(p.getUniqueId(), k -> new PlayerData());
    }

    public static void remove(Player p) {
        playerDataMap.remove(p.getUniqueId());
    }

    public static void resetPlayer(Player p) {
        p.getScheduler().runDelayed(Arcane.getPlugin(), t -> {
            PlayerData data = getOrCreate(p);
            data.resetChunks();

            boolean isDeepslate = p.getLocation().getBlockY() <= 0;
            data.setDeepslateLevel(isDeepslate);

            int chunkX = p.getLocation().getBlockX() >> 4;
            int chunkY = p.getLocation().getBlockY() >> 4;
            int chunkZ = p.getLocation().getBlockZ() >> 4;
            data.setLastChunk(chunkX, chunkY, chunkZ);

            if (isDeepslate) {
                for (int dx = -UPDATE_RADIUS_HALF; dx <= UPDATE_RADIUS_HALF; dx++)
                    for (int dz = -UPDATE_RADIUS_HALF; dz <= UPDATE_RADIUS_HALF; dz++)
                        data.markRevealed(chunkX + dx, chunkZ + dz);
            }

            resendChunks(p, isDeepslate);

            if (isDeepslate)
                revealConnectedAirChunksAsync(p);
        }, null, 1);
    }

    // -------------------------------------------------------------------------
    // Entity visibility
    // -------------------------------------------------------------------------

    public static void addEntity(Player p, int entityId, boolean hidden) {
        PlayerData data = getOrCreate(p);
        if (hidden) data.hiddenEntities.add(entityId);
        else        data.shownEntities.add(entityId);
    }

    public static void removeEntity(Player p, int entityId) {
        PlayerData data = getOrCreate(p);
        data.hiddenEntities.remove(entityId);
        data.shownEntities.remove(entityId);
    }

    public static boolean isEntityTracked(Player p, int entityId) {
        PlayerData data = getOrCreate(p);
        return data.hiddenEntities.contains(entityId) || data.shownEntities.contains(entityId);
    }

    public static boolean isEntityHidden(Player p, int entityId) {
        PlayerData data = getOrCreate(p);
        return data.hiddenEntities.contains(entityId) && !data.shownEntities.contains(entityId);
    }

    public static void hideEntity(Player p, int entityId) {
        PlayerData data = getOrCreate(p);
        data.hiddenEntities.add(entityId);
        data.shownEntities.remove(entityId);

        Entity entity = SpigotConversionUtil.getEntityById(p.getWorld(), entityId);
        if (entity == null || !entity.isValid()) return;

        if (entity instanceof Player other) {
            if (!PlayerManager.canSee(p, other)) return;
        }

        PacketEvents.getAPI().getPlayerManager().getUser(p)
                .sendPacketSilently(new WrapperPlayServerDestroyEntities(new int[]{entityId}));
    }

    public static void unhideEntitySilent(Player p, int entityId) {
        PlayerData data = getOrCreate(p);
        data.hiddenEntities.remove(entityId);
        data.shownEntities.add(entityId);
    }

    public static void unhideEntity(Player p, int entityId) {
        PlayerData data = getOrCreate(p);
        data.hiddenEntities.remove(entityId);
        data.shownEntities.add(entityId);

        Entity entity = SpigotConversionUtil.getEntityById(p.getWorld(), entityId);
        if (entity == null || !entity.isValid()) return;

        sendSpawnPacket(p, entity);
    }

    public static void unhideAllEntities(Player p) {
        PlayerData data = getOrCreate(p);
        Set<Integer> toUnhide = new HashSet<>(data.hiddenEntities);
        if (toUnhide.isEmpty()) return;

        User user = PacketEvents.getAPI().getPlayerManager().getUser(p);
        for (int entityId : toUnhide) {
            data.hiddenEntities.remove(entityId);
            data.shownEntities.add(entityId);

            Entity entity = SpigotConversionUtil.getEntityById(p.getWorld(), entityId);
            if (entity == null || !entity.isValid()) continue;
            sendSpawnPacket(user, entity);
        }
    }

    public static void hideAllEntities(Player p) {
        World world = p.getWorld();
        for (Entity e : world.getEntities()) {
            if (e.getLocation().getBlockY() <= 0 && p.isChunkSent(e.getChunk()))
                hideEntity(p, e.getEntityId());
        }
    }

    private static void sendSpawnPacket(Player p, Entity entity) {

        if (entity instanceof Player other) {
            if (!PlayerManager.canSee(p, other)) return;
        }

        sendSpawnPacket(PacketEvents.getAPI().getPlayerManager().getUser(p), entity);
    }

    private static void sendSpawnPacket(User user, Entity entity) {
        if (user == null) return;

        Location loc = entity.getLocation();
        user.sendPacketSilently(new WrapperPlayServerSpawnEntity(
                entity.getEntityId(),
                Optional.of(entity.getUniqueId()),
                SpigotConversionUtil.fromBukkitEntityType(entity.getType()),
                new Vector3d(loc.getX(), loc.getY(), loc.getZ()),
                loc.getPitch(), loc.getYaw(), loc.getYaw(),
                0,
                Optional.of(new Vector3d(
                        entity.getVelocity().getX(),
                        entity.getVelocity().getY(),
                        entity.getVelocity().getZ()))
        ));
    }

    // -------------------------------------------------------------------------
    // Entity visibility updates
    // -------------------------------------------------------------------------

    public static void updatePlayersSeeingEntity(Entity entity, Location loc, int cx, int cz, boolean isBelowDeepslate) {
        for (Player observer : loc.getWorld().getPlayers()) {
            int viewDistance = observer.getViewDistance();
            double range = viewDistance * 16 * 1.5;

            if (observer.getLocation().distanceSquared(loc) > range * range) continue;

            updateEntityVisibilityForPlayer(observer, entity, cx, cz, isBelowDeepslate);
        }
    }

    public static void updateEntityVisibilityForPlayer(Player observer, Entity target, int cx, int cz, boolean isTargetBelowDeepslate) {
        if (observer.equals(target)) return;

        boolean revealed = isChunkRevealed(observer, cx, cz);
        boolean hidden   = isEntityHidden(observer, target.getEntityId());

        if (isTargetBelowDeepslate) {
            if (!revealed && !hidden) hideEntity(observer, target.getEntityId());
            else if (revealed && hidden) unhideEntity(observer, target.getEntityId());
        } else {
            if (hidden) unhideEntity(observer, target.getEntityId());
        }
    }

    // -------------------------------------------------------------------------
    // Player state accessors
    // -------------------------------------------------------------------------

    public static boolean isDeepslateLevel(Player p)          { return getOrCreate(p).isDeepslateLevel(); }
    public static boolean isDeepslateLevel(Location location) { return location.getBlockY() <= 0; }

    public static void setDeepslateLevel(Player p, boolean val) { getOrCreate(p).setDeepslateLevel(val); }

    public static int getLastChunkX(Player p) { return getOrCreate(p).getLastChunkX(); }
    public static int getLastChunkY(Player p) { return getOrCreate(p).getLastChunkY(); }
    public static int getLastChunkZ(Player p) { return getOrCreate(p).getLastChunkZ(); }

    public static void updateLastChunk(Player p, int x, int y, int z) { getOrCreate(p).setLastChunk(x, y, z); }

    public static boolean isChunkRevealed(Player p, int cx, int cz) { return getOrCreate(p).isRevealed(cx, cz); }

    public static void revealChunk(Player p, int cx, int cz) {
        debug("Revealed chunk " + cx + ", " + cz + " for player " + p.getName());
        getOrCreate(p).markRevealed(cx, cz);
    }

    public static boolean isChunkReachable(Player p, Location loc) {
        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;
        return Math.abs(cx - getLastChunkX(p)) <= UPDATE_RADIUS_HALF
                && Math.abs(cz - getLastChunkZ(p)) <= UPDATE_RADIUS_HALF;
    }

    // -------------------------------------------------------------------------
    // Chunk sending
    // -------------------------------------------------------------------------

    public static void resendChunk(Player player, Chunk chunk, boolean showUnderground) {
        resendChunk(player, chunk, showUnderground, 0);
    }

    public static void resendChunk(Player player, Chunk chunk, boolean showUnderground, int delayTicks) {
        if (!player.isOnline()) return;

        World world = player.getWorld();
        User user = PacketEvents.getAPI().getPlayerManager().getUser(player);

        player.getScheduler().execute(Arcane.getPlugin(), () -> {
            if (!world.isChunkLoaded(chunk.getX(), chunk.getZ())) return;

            if (showUnderground) {
                ChunkUtils.refreshChunkForPlayer(player, chunk.getX(), chunk.getZ());
                return;
            }

            BlockState[] states = chunk.getTileEntities(true);
            Column col = buildColumn(player, chunk.getChunkSnapshot(), states);
            user.sendPacketSilently(new WrapperPlayServerChunkData(col, getFullBrightLightData(), true));
        }, null, delayTicks);
    }

    public static void resendChunks(Player player, boolean showUnderground) {
        if (!player.isOnline()) return;

        World world = player.getWorld();
        User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        Chunk center = player.getChunk();

        player.getScheduler().execute(Arcane.getPlugin(), () -> {
            for (int dx = -UPDATE_RADIUS_HALF; dx <= UPDATE_RADIUS_HALF; dx++) {
                for (int dz = -UPDATE_RADIUS_HALF; dz <= UPDATE_RADIUS_HALF; dz++) {
                    int cx = center.getX() + dx;
                    int cz = center.getZ() + dz;
                    if (!world.isChunkLoaded(cx, cz)) continue;

                    if (showUnderground) {
                        revealChunk(player, cx, cz);
                        ChunkUtils.refreshChunkForPlayer(player, cx, cz);
                        continue;
                    }

                    Chunk chunk = world.getChunkAt(cx, cz);
                    BlockState[] states = chunk.getTileEntities(true);
                    Column col = buildColumn(player, chunk.getChunkSnapshot(), states);
                    user.sendPacketSilently(new WrapperPlayServerChunkData(col, getFullBrightLightData(), true));
                }
            }
        }, null, 0L);
    }

    public static void handleChunkShift(Player player, int oldX, int oldZ, int newX, int newZ) {
        revealNearbyChunks(player);
    }

    // -------------------------------------------------------------------------
    // Packet handlers
    // -------------------------------------------------------------------------

    public static void handleSendChunk(PacketSendEvent e, WrapperPlayServerChunkData packet) {
        Player p = e.getPlayer();
        if (!isValidWorldPlayer(p)) return;

        int minChunkY = p.getWorld().getMinHeight() / 16;
        Column col = packet.getColumn();
        BaseChunk[] chunks = col.getChunks();
        boolean hide = !isChunkRevealed(p, col.getX(), col.getZ());
        boolean reEncoded = false;

        for (int i = 0; i < chunks.length; i++) {
            BaseChunk subChunk = chunks[i];
            int chunkY = minChunkY + i;

            for (int y = 0; y < 16; y++) {
                int blockY = chunkY * 16 + y;
                if (!hide || blockY >= 0) continue;
                for (int x = 0; x < 16; x++)
                    for (int z = 0; z < 16; z++)
                        subChunk.set(x, y, z, PlayerManager.debugAntiXray(p) ? HIDE_BLOCK_DEEPSLATE : HIDE_BLOCK_DEEPSLATE);
                reEncoded = true;
            }
        }

        if (reEncoded) e.markForReEncode(true);
    }

    public static void handleBlockChange(PacketSendEvent e, WrapperPlayServerBlockChange packet) {
        Player p = e.getPlayer();
        if (!isValidWorldPlayer(p)) return;

        int by = packet.getBlockPosition().getY();
        if (by >= 0) return;

        int cx = packet.getBlockPosition().getX() >> 4;
        int cz = packet.getBlockPosition().getZ() >> 4;

        if (!isChunkRevealed(p, cx, cz)) {
            e.setCancelled(true);
            debug("Cancelled Block Change at " + packet.getBlockPosition() + " for player " + p.getName());
        }
    }

    public static void handleMultiBlockChange(PacketSendEvent e, WrapperPlayServerMultiBlockChange packet) {
        Player p = e.getPlayer();
        if (!isValidWorldPlayer(p)) return;

        for (WrapperPlayServerMultiBlockChange.EncodedBlock record : packet.getBlocks()) {
            int by = record.getY();
            if (by >= 0) continue;

            int cx = record.getX() >> 4;
            int cz = record.getZ() >> 4;

            if (!isChunkRevealed(p, cx, cz)) {
                e.setCancelled(true);
                debug("Cancelled Multi Block Change at " + record.getX() + "," + by + "," + record.getZ() + " for player " + p.getName());
                return;
            }
        }
    }

    public static void handleSoundEffect(PacketSendEvent e, WrapperPlayServerSoundEffect packet) {
        Player p = e.getPlayer();
        if (!isValidWorldPlayer(p)) return;

        int by = (int) packet.getEffectPosition().getY() / 8;
        if (by >= 0) return;

        int cx = ((int) packet.getEffectPosition().getX() / 8) >> 4;
        int cz = ((int) packet.getEffectPosition().getZ() / 8) >> 4;

        if (!isChunkRevealed(p, cx, cz)) {
            e.setCancelled(true);
            debug("Cancelled Sound Effect for player " + p.getName());
        }
    }

    private static boolean isValidWorldPlayer(Player p) {
        return p != null
                && p.isOnline()
                && p.getLocation() != null
                && p.getLocation().getWorld() != null
                && p.getLocation().getWorld().getName().equals("world");
    }

    // -------------------------------------------------------------------------
    // Chunk building
    // -------------------------------------------------------------------------

    public static Column buildColumn(Player p, ChunkSnapshot snapshot, BlockState[] states) {
        World world = Bukkit.getWorld(snapshot.getWorldName());
        int minChunkY = world.getMinHeight() / 16;
        int maxChunkY = (world.getMaxHeight() - 1) / 16;

        List<BaseChunk> chunks = new ArrayList<>();
        for (int chunkY = minChunkY; chunkY <= maxChunkY; chunkY++) {
            Chunk_v1_18 baseChunk = (Chunk_v1_18) BaseChunk.create();
            boolean allUnderground = (chunkY * 16 + 15) < 0;
            boolean allAbove      = (chunkY * 16) >= 0;

            if (allUnderground) {
                for (int x = 0; x < 16; x++)
                    for (int y = 0; y < 16; y++)
                        for (int z = 0; z < 16; z++)
                            baseChunk.set(x, y, z, PlayerManager.debugAntiXray(p) ? HIDE_BLOCK_AIR : HIDE_BLOCK_DEEPSLATE);
            } else if (allAbove) {
                for (int x = 0; x < 16; x++)
                    for (int y = 0; y < 16; y++)
                        for (int z = 0; z < 16; z++) {
                            int blockY = chunkY * 16 + y;
                            BlockData bd = snapshot.getBlockData(x, blockY, z);
                            baseChunk.set(x, y, z, SpigotConversionUtil.fromBukkitBlockData(bd));
                        }
            } else {
                for (int y = 0; y < 16; y++) {
                    int blockY = chunkY * 16 + y;
                    if (blockY < 0) {
                        for (int x = 0; x < 16; x++)
                            for (int z = 0; z < 16; z++)
                                baseChunk.set(x, y, z, PlayerManager.debugAntiXray(p) ? HIDE_BLOCK_AIR : HIDE_BLOCK_DEEPSLATE);
                    } else {
                        for (int x = 0; x < 16; x++)
                            for (int z = 0; z < 16; z++) {
                                BlockData bd = snapshot.getBlockData(x, blockY, z);
                                baseChunk.set(x, y, z, SpigotConversionUtil.fromBukkitBlockData(bd));
                            }
                    }
                }
            }

            buildBiomeData(baseChunk);
            chunks.add(baseChunk);
        }

        return new Column(snapshot.getX(), snapshot.getZ(), true, chunks.toArray(new BaseChunk[0]), null);
    }

    public static void buildBiomeData(Chunk_v1_18 baseChunk) {
        for (int j = 0; j < 4; j++)
            for (int k = 0; k < 4; k++)
                for (int l = 0; l < 4; l++) {
                    int id = baseChunk.getBiomeData().palette.stateToId(1);
                    baseChunk.getBiomeData().storage.set(k << 2 | l << 2 | j, id);
                }
    }

    public static LightData getFullBrightLightData() {
        BitSet blockLightMask = new BitSet();
        BitSet skyLightMask   = new BitSet();
        for (int i = 0; i < 18; i++) {
            blockLightMask.set(i);
            skyLightMask.set(i);
        }

        byte[][] skyLight   = new byte[18][2048];
        byte[][] blockLight = new byte[18][2048];
        for (int i = 0; i < 18; i++) {
            Arrays.fill(skyLight[i],   (byte) 0xFF);
            Arrays.fill(blockLight[i], (byte) 0xFF);
        }

        return new LightData(true, blockLightMask, skyLightMask, new BitSet(), new BitSet(), 18, 18, skyLight, blockLight);
    }

    public static void revealNearbyChunks(Player player) {
        PlayerData data = getOrCreate(player);
        World world = player.getWorld();
        int cx = player.getLocation().getBlockX() >> 4;
        int cz = player.getLocation().getBlockZ() >> 4;

        for (int dx = -UPDATE_RADIUS_HALF; dx <= UPDATE_RADIUS_HALF; dx++) {
            for (int dz = -UPDATE_RADIUS_HALF; dz <= UPDATE_RADIUS_HALF; dz++) {
                int chunkX = cx + dx;
                int chunkZ = cz + dz;
                if (data.isRevealed(chunkX, chunkZ)) continue;
                data.markRevealed(chunkX, chunkZ);
                if (world.isChunkLoaded(chunkX, chunkZ))
                    resendChunk(player, world.getChunkAt(chunkX, chunkZ), true);
            }
        }
    }

    public static void revealConnectedAirChunksAsyncManually(Player player) {
        Location loc = player.getLocation();
        revealConnectedAirChunksAsync(player, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), true, true);
    }

    public static void revealConnectedAirChunksAsync(Player player) {
        Location loc = player.getLocation();
        revealConnectedAirChunksAsync(player, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), false, false);
    }

    public static void revealConnectedAirChunksAsync(Player player, int startX, int startY, int startZ, boolean allowAboveGround, boolean debug) {
        if (!allowAboveGround && startY >= 0) {
            if (debug) player.sendMessage(Component.text("[AntiXray] ", Colors.HOT_PINK)
                    .append(Component.text("Aborted — startY >= 0 (" + startY + ") and allowAboveGround=false", Colors.GRAY)));
            return;
        }

        Location playerLoc = player.getLocation();
        World world = player.getWorld();
        int viewDistance = Math.min(player.getViewDistance(), 8);

        Bukkit.getRegionScheduler().run(Arcane.getPlugin(), playerLoc, regionTask -> {
            if (!player.isOnline()) return;

            int seedX = startX;
            int seedY = startY;
            int seedZ = startZ;

            if (seedY >= 0) {
                int minY = world.getMinHeight();
                outer:
                for (int sy = -1; sy >= minY; sy--) {
                    int cx = seedX >> 4, cz = seedZ >> 4;
                    if (!world.isChunkLoaded(cx, cz)) break;
                    if (world.getBlockAt(seedX, sy, seedZ).getType().isAir()) {
                        seedY = sy;
                        break outer;
                    }
                }

                if (seedY >= 0) {
                    if (debug) player.sendMessage(Component.text("[AntiXray] ", Colors.HOT_PINK)
                            .append(Component.text("Aborted — no underground air found below " + startX + ", " + startZ, Colors.GRAY)));
                    return;
                }
            }

            if (debug) player.sendMessage(Component.text("[AntiXray] ", Colors.HOT_PINK)
                    .append(Component.text("Called at " + startX + ", " + startY + ", " + startZ + " | seed=" + seedX + "," + seedY + "," + seedZ + " | allowAboveGround=" + allowAboveGround, Colors.GRAY)));

            int centerCX = seedX >> 4;
            int centerCZ = seedZ >> 4;

            Map<Long, ChunkSnapshot> snapshots = new HashMap<>();
            for (int x = -viewDistance; x <= viewDistance; x++) {
                for (int z = -viewDistance; z <= viewDistance; z++) {
                    int cx = centerCX + x;
                    int cz = centerCZ + z;
                    if (world.isChunkLoaded(cx, cz))
                        snapshots.put(packChunk(cx, cz), world.getChunkAt(cx, cz).getChunkSnapshot());
                }
            }

            if (debug) player.sendMessage(Component.text("[AntiXray] ", Colors.HOT_PINK)
                    .append(Component.text("Snapshots loaded: " + snapshots.size() + " (radius " + viewDistance + ")", Colors.GRAY)));

            int finalSeedX = seedX, finalSeedY = seedY, finalSeedZ = seedZ;

            Bukkit.getAsyncScheduler().runNow(Arcane.getPlugin(), asyncTask -> {
                Set<Long>           chunksToReveal = new HashSet<>();
                Queue<BlockPointer> queue          = new ArrayDeque<>();
                Set<Long>           visited        = new HashSet<>();

                queue.add(new BlockPointer(finalSeedX, finalSeedY, finalSeedZ));
                visited.add(pack(finalSeedX, finalSeedY, finalSeedZ));

                while (!queue.isEmpty()) {
                    BlockPointer cur = queue.poll();
                    chunksToReveal.add(packChunk(cur.x() >> 4, cur.z() >> 4));
                    if (chunksToReveal.size() > 150) {
                        if (debug) player.sendMessage(Component.text("[AntiXray] ", Colors.HOT_PINK)
                                .append(Component.text("Hit 150 chunk cap, stopping early.", Colors.GRAY)));
                        break;
                    }

                    for (BlockFace face : FACES) {
                        int nx = cur.x() + face.getModX();
                        int ny = cur.y() + face.getModY();
                        int nz = cur.z() + face.getModZ();

                        if (ny >= 0 || ny < world.getMinHeight()) continue;
                        if (!visited.add(pack(nx, ny, nz))) continue;

                        ChunkSnapshot snap = snapshots.get(packChunk(nx >> 4, nz >> 4));
                        if (snap != null && snap.getBlockType(nx & 15, ny, nz & 15).isAir())
                            queue.add(new BlockPointer(nx, ny, nz));
                    }
                }

                if (debug) player.sendMessage(Component.text("[AntiXray] ", Colors.HOT_PINK)
                        .append(Component.text("Flood-fill done — visited: " + visited.size() + ", to reveal: " + chunksToReveal.size(), Colors.GRAY)));

                if (chunksToReveal.isEmpty()) {
                    if (debug) player.sendMessage(Component.text("[AntiXray] ", Colors.HOT_PINK)
                            .append(Component.text("Nothing to reveal — no unrevealed air chunks found.", Colors.GRAY)));
                    return;
                }

                Bukkit.getRegionScheduler().run(Arcane.getPlugin(), playerLoc, syncTask -> {
                    if (!player.isOnline()) return;
                    int revealed = 0;
                    for (long key : chunksToReveal) {
                        int cx = (int) (key >> 32);
                        int cz = (int) key;
                        revealChunk(player, cx, cz);
                        if (world.isChunkLoaded(cx, cz)) {
                            resendChunk(player, world.getChunkAt(cx, cz), true, 0);
                            revealed++;
                        }
                    }
                    if (debug) player.sendMessage(Component.text("[AntiXray] ", Colors.HOT_PINK)
                            .append(Component.text("Resent " + revealed + "/" + chunksToReveal.size() + " chunks.", Colors.GRAY)));

                    updateEntitiesForRevealedChunks(player, chunksToReveal);
                });
            });
        });
    }

    private static void updateEntitiesForRevealedChunks(Player player, Set<Long> revealedChunkKeys) {
        World world = player.getWorld();
        for (long key : revealedChunkKeys) {
            int cx = (int) (key >> 32);
            int cz = (int) key;
            if (!world.isChunkLoaded(cx, cz)) continue;
            Chunk chunk = world.getChunkAt(cx, cz);
            for (Entity entity : chunk.getEntities()) {
                if (entity.equals(player)) continue;
                Location eloc = entity.getLocation();
                if (eloc.getBlockY() >= 0) continue;
                updateEntityVisibilityForPlayer(player, entity, cx, cz, true);
            }
        }
    }

    // -------------------------------------------------------------------------
    // View-based reveal check
    // -------------------------------------------------------------------------

    private static final float  VIEW_CHECK_FOV   = 110f;
    private static final int    VIEW_RAY_GRID    = 5;
    private static final int    VIEW_MAX_STEPS   = 80;
    private static final double VIEW_STEP_SIZE   = 0.5;
    private static final float  VIEW_FOV_H       = VIEW_CHECK_FOV / 2f;
    private static final float  VIEW_FOV_V       = (float) Math.toDegrees(
            Math.atan(Math.tan(Math.toRadians(VIEW_CHECK_FOV / 2f)) / (16.0 / 9.0)));

    private static final int[][] RAY_ORDER = buildSpiralOrder(VIEW_RAY_GRID);

    private static int[][] buildSpiralOrder(int grid) {
        int half = grid / 2;
        List<int[]> order = new ArrayList<>();
        for (int radius = 0; radius <= half; radius++) {
            if (radius == 0) { order.add(new int[]{0, 0}); continue; }
            for (int gx = -radius; gx <= radius; gx++) order.add(new int[]{gx, -radius});
            for (int gy = -radius + 1; gy <= radius; gy++) order.add(new int[]{radius, gy});
            for (int gx = radius - 1; gx >= -radius; gx--) order.add(new int[]{gx, radius});
            for (int gy = radius - 1; gy >= -radius + 1; gy--) order.add(new int[]{-radius, gy});
        }
        return order.toArray(new int[0][]);
    }

    public static void startViewCheck(Player player) {
        if (!player.isOnline()) return;
        PlayerData data = getOrCreate(player);
        data.cancelViewCheckTask();

        ScheduledTask task = player.getScheduler().runAtFixedRate(Arcane.getPlugin(), t -> {
            if (!player.isOnline()) { data.cancelViewCheckTask(); return; }

            Location eye = player.getEyeLocation();
            World world  = eye.getWorld();
            if (world == null || !world.getName().equals("world")) return;

            int centerCX = eye.getBlockX() >> 4;
            int centerCZ = eye.getBlockZ() >> 4;

            boolean snapshotsDirty = data.lastSnapshotCX != centerCX || data.lastSnapshotCZ != centerCZ;
            if (snapshotsDirty) {
                int snapshotRadius = (int) Math.ceil((VIEW_MAX_STEPS * VIEW_STEP_SIZE) / 16.0) + 1;
                Map<Long, ChunkSnapshot> fresh = new HashMap<>();
                for (int dx = -snapshotRadius; dx <= snapshotRadius; dx++)
                    for (int dz = -snapshotRadius; dz <= snapshotRadius; dz++) {
                        int cx = centerCX + dx, cz = centerCZ + dz;
                        if (world.isChunkLoaded(cx, cz))
                            fresh.put(packChunk(cx, cz), world.getChunkAt(cx, cz).getChunkSnapshot());
                    }
                data.cachedSnapshots = fresh;
                data.lastSnapshotCX  = centerCX;
                data.lastSnapshotCZ  = centerCZ;
            }

            Map<Long, ChunkSnapshot> snapshots = data.cachedSnapshots;
            if (snapshots.isEmpty()) return;

            double eyeX = eye.getX(), eyeY = eye.getY(), eyeZ = eye.getZ();
            float pitch = eye.getPitch(), yaw = eye.getYaw();

            int[] hit = findViewRevealPoint(player, world, snapshots, eyeX, eyeY, eyeZ, pitch, yaw);
            if (hit == null) return;

            Location hitLoc = eye;
            Bukkit.getRegionScheduler().run(Arcane.getPlugin(), hitLoc, syncTask -> {
                if (player.isOnline())
                    revealConnectedAirChunksAsync(player, hit[0], hit[1], hit[2], true, false);
            });

        }, null, 1L, 10L);

        if (task != null) data.setViewCheckTask(task);
    }

    public static void stopViewCheck(Player player) {
        PlayerData data = playerDataMap.get(player.getUniqueId());
        if (data != null) data.cancelViewCheckTask();
    }

    @Nullable
    private static int[] findViewRevealPoint(
            Player player, World world,
            Map<Long, ChunkSnapshot> snapshots,
            double eyeX, double eyeY, double eyeZ,
            float pitch, float yaw) {

        double radYaw   = Math.toRadians(yaw);
        double radPitch = Math.toRadians(pitch);
        double sinYaw   = Math.sin(radYaw),  cosYaw   = Math.cos(radYaw);
        double sinPitch = Math.sin(radPitch), cosPitch = Math.cos(radPitch);

        double fwdX = -sinYaw * cosPitch, fwdY = -sinPitch,       fwdZ =  cosYaw * cosPitch;
        double rgtX =  cosYaw,            rgtY =  0,               rgtZ =  sinYaw;
        double upX  =  sinYaw * sinPitch, upY  =  cosPitch,        upZ  = -cosYaw * sinPitch;

        int half = VIEW_RAY_GRID / 2;
        int minY = world.getMinHeight();

        Set<Long> checkedChunks = new HashSet<>();

        for (int[] cell : RAY_ORDER) {
            int gx = cell[0], gy = cell[1];

            float hAngle = half == 0 ? 0f : (gx / (float) half) * VIEW_FOV_H;
            float vAngle = half == 0 ? 0f : (gy / (float) half) * VIEW_FOV_V;
            double hT = Math.tan(Math.toRadians(hAngle));
            double vT = Math.tan(Math.toRadians(vAngle));

            double dx = fwdX + rgtX * hT + upX * vT;
            double dy = fwdY + rgtY * hT + upY * vT;
            double dz = fwdZ + rgtZ * hT + upZ * vT;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= len; dy /= len; dz /= len;

            int prevBX = Integer.MIN_VALUE, prevBY = Integer.MIN_VALUE, prevBZ = Integer.MIN_VALUE;

            for (int step = 0; step < VIEW_MAX_STEPS; step++) {
                double t  = step * VIEW_STEP_SIZE;
                int bx = (int) Math.floor(eyeX + dx * t);
                int by = (int) Math.floor(eyeY + dy * t);
                int bz = (int) Math.floor(eyeZ + dz * t);

                if (bx == prevBX && by == prevBY && bz == prevBZ) continue;
                prevBX = bx; prevBY = by; prevBZ = bz;

                if (by < minY || by >= world.getMaxHeight()) break;

                int cx = bx >> 4, cz = bz >> 4;

                if (by < 0 ) {
                    long chunkKey = packChunk(cx, cz);
                    if (!checkedChunks.add(chunkKey)) break;
                    if (!isChunkRevealed(player, cx, cz)) return new int[]{bx, by, bz};
                    break;
                }

                ChunkSnapshot snap = snapshots.get(packChunk(cx, cz));
                if (snap == null) break;

                Material mat = snap.getBlockType(bx & 15, by, bz & 15);
                if (!isTransparent(mat)) {
                    break;
                }
            }
        }

        return null;
    }

    private static boolean isTransparent(Material mat) {
        return mat == Material.AIR
                || mat == Material.CAVE_AIR
                || mat == Material.VOID_AIR
                || !mat.isOccluding();
    }

    // -------------------------------------------------------------------------
    // Packing utilities
    // -------------------------------------------------------------------------

    private static long pack(int x, int y, int z) {
        return (((long) x & 0x3FFFFFF) << 38)
                | (((long) z & 0x3FFFFFF) << 12)
                | ((long) y & 0xFFF);
    }

    private static long packChunk(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    // -------------------------------------------------------------------------
    // PlayerData
    // -------------------------------------------------------------------------

    private static class PlayerData {
        private volatile boolean deepslateLevel = false;
        private volatile int lastChunkX;
        private volatile int lastChunkY;
        private volatile int lastChunkZ;

        private final Set<Long>    revealedChunks = ConcurrentHashMap.newKeySet();
        private final Set<Integer> hiddenEntities = ConcurrentHashMap.newKeySet();
        private final Set<Integer> shownEntities  = ConcurrentHashMap.newKeySet();

        boolean isDeepslateLevel()           { return deepslateLevel; }
        void setDeepslateLevel(boolean val)  { deepslateLevel = val; }

        int getLastChunkX() { return lastChunkX; }
        int getLastChunkY() { return lastChunkY; }
        int getLastChunkZ() { return lastChunkZ; }

        void setLastChunk(int cx, int cy, int cz) {
            lastChunkX = cx;
            lastChunkY = cy;
            lastChunkZ = cz;
        }

        void markRevealed(int cx, int cz)       { revealedChunks.add(toLong(cx, cz)); }
        boolean isRevealed(int cx, int cz)      { return revealedChunks.contains(toLong(cx, cz)); }
        void resetChunks()                       { revealedChunks.clear(); }

        private long toLong(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

        private volatile ScheduledTask viewCheckTask;

        void setViewCheckTask(ScheduledTask task) { viewCheckTask = task; }
        void cancelViewCheckTask() {
            ScheduledTask t = viewCheckTask;
            if (t != null && !t.isCancelled()) t.cancel();
            viewCheckTask = null;
        }

        volatile int lastSnapshotCX = Integer.MIN_VALUE;
        volatile int lastSnapshotCZ = Integer.MIN_VALUE;
        volatile Map<Long, ChunkSnapshot> cachedSnapshots = new HashMap<>();
    }
}