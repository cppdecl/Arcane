package cx.arcane.managers.playerManager.listeners;

import cx.arcane.Arcane;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.utils.Colors;
import cx.arcane.utils.Log;
import cx.arcane.utils.Text;
import io.canvasmc.canvas.event.EntityTeleportAsyncEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SpawnPVPListener implements Listener {

    private static final int SAFE_RADIUS = 49;
    private static final int CENTER_X = -478;
    private static final int CENTER_Z = 491;

    private static final double EFF_SAFE = SAFE_RADIUS + 0.5;
    private static final double INV_SAFE = 1.0 / EFF_SAFE;

    private static final int BASE_Y = 213;
    private static final int Y_RANGE = 2;

    private static final String META_PEARL_ZONE = "arcane_pearl_zone";
    private static final String META_PEARL_SHOOTER = "arcane_pearl_shooter";

    private enum PvpZone { NONE, SAFE, SWORD, CRYSTAL }

    private static final Set<Material> BLOCKS_SWORD = Set.of(
            Material.COBWEB
    );

    private static final Set<Material> BLOCKS_CRYSTAL = Set.of(
            Material.OBSIDIAN,
            Material.COBWEB,
            Material.ENDER_CHEST,
            Material.SHULKER_BOX,
            Material.WHITE_SHULKER_BOX,
            Material.ORANGE_SHULKER_BOX,
            Material.MAGENTA_SHULKER_BOX,
            Material.LIGHT_BLUE_SHULKER_BOX,
            Material.YELLOW_SHULKER_BOX,
            Material.LIME_SHULKER_BOX,
            Material.PINK_SHULKER_BOX,
            Material.GRAY_SHULKER_BOX,
            Material.LIGHT_GRAY_SHULKER_BOX,
            Material.CYAN_SHULKER_BOX,
            Material.PURPLE_SHULKER_BOX,
            Material.BLUE_SHULKER_BOX,
            Material.BROWN_SHULKER_BOX,
            Material.GREEN_SHULKER_BOX,
            Material.RED_SHULKER_BOX,
            Material.BLACK_SHULKER_BOX,
            Material.RESPAWN_ANCHOR,
            Material.GLOWSTONE,
            Material.RAIL,
            Material.ACTIVATOR_RAIL,
            Material.DETECTOR_RAIL,
            Material.POWERED_RAIL
    );

    private static final Set<EntityType> CLEAR_ENTITIES_SWORD = Set.of();

    private static final Set<EntityType> CLEAR_ENTITIES_CRYSTAL = Set.of(
            EntityType.END_CRYSTAL,
            EntityType.MINECART,
            EntityType.CHEST_MINECART,
            EntityType.TNT_MINECART,
            EntityType.HOPPER_MINECART,
            EntityType.FURNACE_MINECART,
            EntityType.TNT
    );

    private static final Set<Material> THROWABLE_POTIONS = Set.of(
            Material.SPLASH_POTION,
            Material.LINGERING_POTION
    );

    public SpawnPVPListener() {
        Bukkit.getAsyncScheduler().runAtFixedRate(Arcane.getPlugin(), t -> clearPvpArea(), 30, 3600, TimeUnit.SECONDS);
    }

    private static void deny(Player p, String msg) {
        // p.sendMessage(Text.toSmallCapsComponent(msg).color(Colors.HOT_PINK));
    }

    private static void broadcast(String msg) {
        // Component c = Text.toSmallCapsComponent(msg).color(Colors.HOT_PINK);
        // Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(c));
    }

    private static boolean isStupid(Player p) {
        PlayerData data = PlayerManager.getByUniqueId(p.getUniqueId());
        return data != null && data.getMeta().isStupid();
    }

    public static boolean isInSafeZone(long x, long z) {
        if (x == CENTER_X) return true;
        double dx = Math.abs(x - CENTER_X) * INV_SAFE;
        double dz = Math.abs(z - CENTER_Z) * INV_SAFE;
        return dx * dx + dz * dz < 1;
    }

    private static boolean isInPvpZone(int x, int z) {
        PvpZone zone = getZone(x, z);
        return zone == PvpZone.SWORD || zone == PvpZone.CRYSTAL;
    }

    private static PvpZone getZone(int x, int z) {
        if (isInSafeZone(x, z)) return PvpZone.SAFE;

        World world = Bukkit.getWorld("spawn");
        if (world == null) return PvpZone.NONE;

        double dxSafe = Math.abs(x - CENTER_X) * INV_SAFE;
        double dzSafe = Math.abs(z - CENTER_Z) * INV_SAFE;
        boolean outsideSafe = dxSafe * dxSafe + dzSafe * dzSafe >= 1;

        double borderSize = world.getWorldBorder().getSize() / 2.0;
        double borderCX = world.getWorldBorder().getCenter().getX();
        double borderCZ = world.getWorldBorder().getCenter().getZ();
        boolean insideBorder = Math.abs(x - borderCX) < borderSize && Math.abs(z - borderCZ) < borderSize;

        if (!outsideSafe || !insideBorder) return PvpZone.NONE;

        return x >= CENTER_X ? PvpZone.SWORD : PvpZone.CRYSTAL;
    }

    private static Set<Material> getAllowedBlocks(PvpZone zone) {
        return zone == PvpZone.SWORD ? BLOCKS_SWORD : BLOCKS_CRYSTAL;
    }

    private static Set<EntityType> getClearEntities(PvpZone zone) {
        return zone == PvpZone.SWORD ? CLEAR_ENTITIES_SWORD : CLEAR_ENTITIES_CRYSTAL;
    }

    private static void extinguishSafeZone(World world) {
        int r = SAFE_RADIUS + 2;
        Set<Long> scheduledChunks = ConcurrentHashMap.newKeySet();

        for (int x = CENTER_X - r; x <= CENTER_X + r; x++) {
            for (int z = CENTER_Z - r; z <= CENTER_Z + r; z++) {
                if (!isInSafeZone(x, z)) continue;

                int cx = x >> 4;
                int cz = z >> 4;
                long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
                if (!scheduledChunks.add(key)) continue;

                final int fcx = cx;
                final int fcz = cz;

                Bukkit.getRegionScheduler().run(Arcane.getPlugin(), world, fcx, fcz, rt -> {
                    int bx0 = fcx << 4;
                    int bz0 = fcz << 4;

                    for (int bx = bx0; bx < bx0 + 16; bx++) {
                        for (int bz = bz0; bz < bz0 + 16; bz++) {
                            if (!isInSafeZone(bx, bz)) continue;
                            for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                                Block block = world.getBlockAt(bx, y, bz);
                                if (block.getType() == Material.FIRE || block.getType() == Material.SOUL_FIRE) {
                                    block.setType(Material.AIR);
                                }
                            }
                        }
                    }
                });
            }
        }
    }

    public static void clearPvpArea() {
        World world = Bukkit.getWorld("spawn");
        if (world == null) return;

        long start = System.currentTimeMillis();
        AtomicInteger blocksRemoved = new AtomicInteger(0);
        AtomicInteger entitiesRemoved = new AtomicInteger(0);

        double borderSize = world.getWorldBorder().getSize() / 2.0;
        double borderCX = world.getWorldBorder().getCenter().getX();
        double borderCZ = world.getWorldBorder().getCenter().getZ();

        int startX = (int) (borderCX - borderSize);
        int startZ = (int) (borderCZ - borderSize);
        int endX = (int) (borderCX + borderSize);
        int endZ = (int) (borderCZ + borderSize);

        Set<Long> scheduledChunks = ConcurrentHashMap.newKeySet();

        extinguishSafeZone(world);

        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                if (!isInPvpZone(x, z)) continue;

                int cx = x >> 4;
                int cz = z >> 4;
                long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
                if (!scheduledChunks.add(key)) continue;

                final int fcx = cx;
                final int fcz = cz;

                Bukkit.getRegionScheduler().run(Arcane.getPlugin(), world, fcx, fcz, rt -> {
                    int bx0 = fcx << 4;
                    int bz0 = fcz << 4;

                    for (int bx = bx0; bx < bx0 + 16; bx++) {
                        for (int bz = bz0; bz < bz0 + 16; bz++) {
                            PvpZone blockZone = getZone(bx, bz);
                            if (blockZone != PvpZone.SWORD && blockZone != PvpZone.CRYSTAL) continue;
                            Set<Material> allowed = getAllowedBlocks(blockZone);
                            for (int y = BASE_Y; y < BASE_Y + Y_RANGE; y++) {
                                Block block = world.getBlockAt(bx, y, bz);
                                if (allowed.contains(block.getType())) {
                                    block.setType(Material.AIR);
                                    blocksRemoved.incrementAndGet();
                                }
                            }
                        }
                    }

                    for (Entity entity : world.getChunkAt(fcx, fcz).getEntities()) {
                        int ex = entity.getLocation().getBlockX();
                        int ez = entity.getLocation().getBlockZ();
                        PvpZone entityZone = getZone(ex, ez);
                        if (entityZone != PvpZone.SWORD && entityZone != PvpZone.CRYSTAL) continue;
                        if (!getClearEntities(entityZone).contains(entity.getType())) continue;
                        entity.remove();
                        entitiesRemoved.incrementAndGet();
                    }
                });
            }
        }

        Bukkit.getAsyncScheduler().runDelayed(Arcane.getPlugin(), t -> {
            int blocks = blocksRemoved.get();
            int entities = entitiesRemoved.get();
            long elapsed = System.currentTimeMillis() - start;
            Log.info("[SpawnPVP] clearPvpArea() — {} blocks, {} entities removed in {}ms.", blocks, entities, elapsed);
            if (blocks > 0 || entities > 0) {
                broadcast("[pvp zone] cleared " + blocks + " blocks and " + entities + " entities in " + elapsed + "ms");
            }
        }, 3, TimeUnit.SECONDS);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent e) {
        if (!e.getTo().getWorld().getName().equalsIgnoreCase("spawn")) return;
        if (e.getFrom().getBlockX() == e.getTo().getBlockX() && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;

        PvpZone from = getZone(e.getFrom().getBlockX(), e.getFrom().getBlockZ());
        PvpZone to = getZone(e.getTo().getBlockX(), e.getTo().getBlockZ());

        if (from == to) return;

        switch (to) {
            case SWORD   -> e.getPlayer().sendActionBar(Component.text(Text.toSmallCaps("entered sword zone")).color(Colors.RED));
            case CRYSTAL -> e.getPlayer().sendActionBar(Component.text(Text.toSmallCaps("entered crystal zone")).color(Colors.RED));
            case SAFE    -> e.getPlayer().sendActionBar(Component.text(Text.toSmallCaps("now in safe zone")).color(Colors.HOT_PINK));
            default -> {}
        }
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onPlayerBlockPlace(BlockPlaceEvent e) {
        if (!e.getBlock().getWorld().getName().equalsIgnoreCase("spawn")) return;

        if (e.getPlayer().hasPermission("arcane.rank.management")) {
            e.setCancelled(false);
            return;
        }

        int bx = e.getBlock().getX();
        int by = e.getBlock().getY();
        int bz = e.getBlock().getZ();
        int px = e.getPlayer().getLocation().getBlockX();
        int pz = e.getPlayer().getLocation().getBlockZ();

        PvpZone blockZone = getZone(bx, bz);
        PvpZone playerZone = getZone(px, pz);

        if (blockZone != PvpZone.SWORD && blockZone != PvpZone.CRYSTAL || playerZone != blockZone) {
            e.setCancelled(true);
            deny(e.getPlayer(), "you can only place blocks while in the pvp zone");
            return;
        }

        if (by < BASE_Y || by >= BASE_Y + Y_RANGE) {
            e.setCancelled(true);
            deny(e.getPlayer(), "you can only place blocks between y " + BASE_Y + " and y " + (BASE_Y + Y_RANGE - 1));
            return;
        }

        if (!getAllowedBlocks(blockZone).contains(e.getBlock().getType())) {
            e.setCancelled(true);
            deny(e.getPlayer(), "that block is not allowed in this zone");
            return;
        }

        e.setCancelled(false);
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onPlayerBlockBreak(BlockBreakEvent e) {
        if (!e.getBlock().getWorld().getName().equalsIgnoreCase("spawn")) return;

        if (e.getPlayer().hasPermission("arcane.rank.management")) {
            e.setCancelled(false);
            return;
        }

        int bx = e.getBlock().getX();
        int by = e.getBlock().getY();
        int bz = e.getBlock().getZ();

        PvpZone blockZone = getZone(bx, bz);

        if (blockZone != PvpZone.SWORD && blockZone != PvpZone.CRYSTAL) {
            e.setCancelled(true);
            deny(e.getPlayer(), "you can only break blocks inside the pvp zone");
            return;
        }

        if (by < BASE_Y || by >= BASE_Y + Y_RANGE) {
            e.setCancelled(true);
            deny(e.getPlayer(), "you can only break blocks between y " + BASE_Y + " and y " + (BASE_Y + Y_RANGE - 1));
            return;
        }

        if (!getAllowedBlocks(blockZone).contains(e.getBlock().getType())) {
            e.setCancelled(true);
            deny(e.getPlayer(), "that block is not allowed to be broken in this zone");
            return;
        }

        e.setCancelled(false);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBlockIgnite(BlockIgniteEvent e) {
        if (!e.getBlock().getWorld().getName().equalsIgnoreCase("spawn")) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (!e.getEntity().getWorld().getName().equalsIgnoreCase("spawn")) return;
        int size = e.blockList().size();
        e.blockList().clear();
        if (size > 0) broadcast("[pvp zone] blocked " + e.getEntity().getType().name().toLowerCase().replace("_", " ") + " explosion from destroying " + size + " blocks");
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (!e.getBlock().getWorld().getName().equalsIgnoreCase("spawn")) return;
        int size = e.blockList().size();
        e.blockList().clear();
        if (size > 0) broadcast("[pvp zone] blocked block explosion from destroying " + size + " blocks");
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onWindChargePrime(ExplosionPrimeEvent e) {
        if (!e.getEntity().getWorld().getName().equalsIgnoreCase("spawn")) return;
        if (!(e.getEntity() instanceof AbstractWindCharge wc)) return;
        if (!(wc.getShooter() instanceof Player shooter)) return;

        PvpZone shooterZone = getZone(shooter.getLocation().getBlockX(), shooter.getLocation().getBlockZ());

        if (shooterZone == PvpZone.SAFE) {
            e.setCancelled(true);
            deny(shooter, "wind charges don't explode in the safe zone");
            return;
        }

        if (shooterZone != PvpZone.SWORD && shooterZone != PvpZone.CRYSTAL) {
            e.setCancelled(true);
        }
    }

    // EntityDamageByEntityEvent extends EntityDamageEvent, so wind charge hits fire both.
    // We skip cancellation here for wind charge damagers — onEntityDamageByEntity owns that logic.
    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent e) {
        if (!e.getEntity().getWorld().getName().equalsIgnoreCase("spawn")) return;
        if (!(e.getEntity() instanceof Player p)) return;
        if (e instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof AbstractWindCharge) return;

        int px = p.getLocation().getBlockX();
        int pz = p.getLocation().getBlockZ();

        if (isInPvpZone(px, pz)) return;
        if (isStupid(p)) return;

        e.setCancelled(true);
        deny(p, "you are protected from " + e.getCause().name().toLowerCase().replace("_", " ") + " damage in spawn");
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
        if (!e.getEntity().getWorld().getName().equalsIgnoreCase("spawn")) return;
        if (!(e.getEntity() instanceof Player victim)) return;

        PvpZone victimZone = getZone(victim.getLocation().getBlockX(), victim.getLocation().getBlockZ());
        boolean victimIsStupid = isStupid(victim);

        if (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            if (victimZone != PvpZone.SWORD && victimZone != PvpZone.CRYSTAL) {
                if (!victimIsStupid) {
                    e.setCancelled(true);
                    deny(victim, "you are protected from explosions in the safe zone");
                    return;
                }
            }

            Entity damager = e.getDamager();
            int dx = damager.getLocation().getBlockX();
            int dz = damager.getLocation().getBlockZ();
            PvpZone damagerZone = getZone(dx, dz);

            if (damagerZone != victimZone && !victimIsStupid) {
                e.setCancelled(true);
                deny(victim, "you are protected from cross-zone explosions");
                return;
            }

            e.setCancelled(false);
            return;
        }

        if (e.getDamager() instanceof AbstractWindCharge wc) {
            Player attacker = wc.getShooter() instanceof Player p ? p : null;

            if (attacker == null) {
                e.setCancelled(true);
                return;
            }

            if (attacker.getUniqueId().equals(victim.getUniqueId())) return;

            boolean attackerIsStupid = isStupid(attacker);
            PvpZone attackerZone = getZone(attacker.getLocation().getBlockX(), attacker.getLocation().getBlockZ());

            if (attackerZone == PvpZone.SAFE || victimZone == PvpZone.SAFE) {
                if (victimIsStupid && !attackerIsStupid) {
                    e.setCancelled(false);
                    return;
                }
                e.setCancelled(true);
                deny(attacker, "wind bursts can't affect other players in or from the safe zone");
                deny(victim, attacker.getName() + " tried to wind burst you");
                return;
            }

            if (attackerZone != victimZone || (attackerZone != PvpZone.SWORD && attackerZone != PvpZone.CRYSTAL)) {
                e.setCancelled(true);
                deny(attacker, "you can't wind burst players across zones");
                deny(victim, attacker.getName() + " tried to wind burst you across zones");
                return;
            }

            e.setCancelled(false);
            return;
        }

        if (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            Player attacker = e.getDamager() instanceof Player p ? p : null;

            if (attacker == null) {
                e.setCancelled(true);
                return;
            }

            if (attacker.getUniqueId().equals(victim.getUniqueId())) return;

            boolean attackerIsStupid = isStupid(attacker);
            PvpZone attackerZone = getZone(attacker.getLocation().getBlockX(), attacker.getLocation().getBlockZ());

            if (attackerZone == victimZone && (attackerZone == PvpZone.SWORD || attackerZone == PvpZone.CRYSTAL)) {
                e.setCancelled(false);
            } else if (victimIsStupid && !attackerIsStupid) {
                e.setCancelled(false);
            } else {
                e.setCancelled(true);
                deny(attacker, "you can't sweep attack players outside the pvp zone");
                deny(victim, attacker.getName() + " tried to sweep attack you");
            }
            return;
        }

        Player attacker = null;
        if (e.getDamager() instanceof Player p) {
            attacker = p;
        } else if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
        }

        if (attacker == null) {
            if (victimIsStupid) {
                e.setCancelled(false);
                return;
            }
            e.setCancelled(true);
            deny(victim, "you are protected from non-player damage in spawn");
            return;
        }

        boolean attackerIsStupid = isStupid(attacker);
        PvpZone attackerZone = getZone(attacker.getLocation().getBlockX(), attacker.getLocation().getBlockZ());

        if (attackerZone == victimZone && (attackerZone == PvpZone.SWORD || attackerZone == PvpZone.CRYSTAL)) {
            e.setCancelled(false);
            return;
        }

        if (victimIsStupid && !attackerIsStupid) {
            e.setCancelled(false);
            return;
        }

        e.setCancelled(true);
        if (attackerZone == PvpZone.SAFE && (victimZone == PvpZone.SWORD || victimZone == PvpZone.CRYSTAL)) {
            deny(attacker, "you can't attack players in the pvp zone from the safe zone");
            deny(victim, attacker.getName() + " tried to attack you from the safe zone");
        } else if ((attackerZone == PvpZone.SWORD || attackerZone == PvpZone.CRYSTAL) && victimZone == PvpZone.SAFE) {
            deny(attacker, "you can't attack players in the safe zone from the pvp zone");
            deny(victim, attacker.getName() + " tried to attack you from the pvp zone");
        } else if (attackerZone != victimZone) {
            deny(attacker, "you can't attack players across zones");
            deny(victim, attacker.getName() + " tried to attack you from a different zone");
        } else {
            deny(attacker, "pvp is only allowed inside the pvp zone");
            deny(victim, attacker.getName() + " tried to attack you — pvp is not allowed here");
        }
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onPotionThrow(PlayerInteractEvent e) {
        if (!e.getPlayer().getWorld().getName().equalsIgnoreCase("spawn")) return;
        if (e.getItem() == null) return;
        if (!THROWABLE_POTIONS.contains(e.getItem().getType())) return;

        int px = e.getPlayer().getLocation().getBlockX();
        int pz = e.getPlayer().getLocation().getBlockZ();
        PvpZone zone = getZone(px, pz);

        if (zone == PvpZone.SAFE) {
            e.getPlayer().setMetadata("arcane_safe_potion", new FixedMetadataValue(Arcane.getPlugin(), e.getPlayer().getUniqueId().toString()));
            return;
        }

        if (!isInPvpZone(px, pz)) {
            e.setCancelled(true);
            deny(e.getPlayer(), "you can only throw potions while in the pvp zone");
        }
    }

    // getAffectedEntities() on Paper returns an unmodifiable view — removeIf silently fails.
    // setIntensity(entity, 0) is the only reliable way to nullify effects per entity.
    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onPotionSplash(PotionSplashEvent e) {
        if (!e.getEntity().getWorld().getName().equalsIgnoreCase("spawn")) return;
        if (!(e.getPotion().getShooter() instanceof Player shooter)) return;

        int sx = shooter.getLocation().getBlockX();
        int sz = shooter.getLocation().getBlockZ();
        PvpZone shooterZone = getZone(sx, sz);
        UUID shooterUUID = shooter.getUniqueId();

        for (LivingEntity entity : e.getAffectedEntities()) {
            if (!(entity instanceof Player affected)) {
                e.setIntensity(entity, 0);
                continue;
            }

            if (affected.getUniqueId().equals(shooterUUID)) continue;

            PvpZone affectedZone = getZone(affected.getLocation().getBlockX(), affected.getLocation().getBlockZ());

            boolean bothInSamePvpZone = shooterZone == affectedZone
                    && (shooterZone == PvpZone.SWORD || shooterZone == PvpZone.CRYSTAL);

            if (!bothInSamePvpZone && !isStupid(affected)) e.setIntensity(affected, 0);
        }

        if (shooterZone == PvpZone.SAFE) shooter.removeMetadata("arcane_safe_potion", Arcane.getPlugin());
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onLingeringPotionSplash(LingeringPotionSplashEvent e) {
        if (!e.getEntity().getWorld().getName().equalsIgnoreCase("spawn")) return;
        if (!(e.getEntity().getShooter() instanceof Player shooter)) return;

        int sx = shooter.getLocation().getBlockX();
        int sz = shooter.getLocation().getBlockZ();
        PvpZone shooterZone = getZone(sx, sz);

        UUID shooterUUID = shooter.getUniqueId();
        e.getEntity().setMetadata("arcane_safe_lingering", new FixedMetadataValue(Arcane.getPlugin(), shooterUUID.toString()));
        e.getEntity().setMetadata("arcane_lingering_zone", new FixedMetadataValue(Arcane.getPlugin(), shooterZone.name()));
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onAreaEffectCloudApply(AreaEffectCloudApplyEvent e) {
        if (!e.getEntity().getWorld().getName().equalsIgnoreCase("spawn")) return;
        if (!e.getEntity().hasMetadata("arcane_safe_lingering")) return;

        String uuidStr = e.getEntity().getMetadata("arcane_safe_lingering").get(0).asString();
        UUID shooterUUID = UUID.fromString(uuidStr);

        String zoneName = e.getEntity().getMetadata("arcane_lingering_zone").get(0).asString();
        PvpZone shooterZone = PvpZone.valueOf(zoneName);

        e.getAffectedEntities().removeIf(entity -> {
            if (!(entity instanceof Player affected)) return true;
            if (affected.getUniqueId().equals(shooterUUID)) return false;

            PvpZone affectedZone = getZone(affected.getLocation().getBlockX(), affected.getLocation().getBlockZ());

            boolean bothInSamePvpZone = shooterZone == affectedZone
                    && (shooterZone == PvpZone.SWORD || shooterZone == PvpZone.CRYSTAL);

            if (!bothInSamePvpZone && isStupid(affected)) return false;

            return !bothInSamePvpZone;
        });
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onProjectileLaunch(ProjectileLaunchEvent e) {
        if (!e.getEntity().getWorld().getName().equalsIgnoreCase("spawn")) return;
        if (!(e.getEntity() instanceof EnderPearl pearl)) return;
        if (!(pearl.getShooter() instanceof Player shooter)) return;

        int sx = shooter.getLocation().getBlockX();
        int sz = shooter.getLocation().getBlockZ();
        PvpZone launchZone = getZone(sx, sz);

        pearl.setMetadata(META_PEARL_ZONE, new FixedMetadataValue(Arcane.getPlugin(), launchZone.name()));
        pearl.setMetadata(META_PEARL_SHOOTER, new FixedMetadataValue(Arcane.getPlugin(), shooter.getUniqueId().toString()));
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onEnderPearlLand(EntityTeleportAsyncEvent e) {
        if (e.getEntityType() != EntityType.PLAYER) return;
        if (!(e.getEntity() instanceof Player p)) return;
        if (!p.getWorld().getName().equalsIgnoreCase("spawn")) return;
        if (e.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return;

        PvpZone fromZone = getZone(e.getFrom().getBlockX(), e.getFrom().getBlockZ());
        PvpZone toZone = getZone(e.getTo().getBlockX(), e.getTo().getBlockZ());

        if (fromZone == toZone) return;
        if (fromZone == PvpZone.SAFE) return;

        deny(p, "your pearl was deflected at the zone boundary");
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onTeleport(EntityTeleportAsyncEvent e) {
        if (e.getEntityType() != EntityType.PLAYER) return;
        if (!(e.getEntity() instanceof Player p)) return;
        if (!p.getWorld().getName().equalsIgnoreCase("spawn")) return;
        if (e.getCause() != PlayerTeleportEvent.TeleportCause.CONSUMABLE_EFFECT) return;

        PvpZone fromZone = getZone(e.getFrom().getBlockX(), e.getFrom().getBlockZ());
        PvpZone toZone = getZone(e.getTo().getBlockX(), e.getTo().getBlockZ());

        if (fromZone == toZone) return;
        if (fromZone == PvpZone.SAFE) return;

        deny(p, "chorus fruit kept you inside the safe zone");
        e.setCancelled(true);
    }

    private static final double BOUNDARY_INSET = 2.5;

    private static Location clampToZoneBoundary(World world, Location from, Location target) {
        PvpZone fromZone = getZone(from.getBlockX(), from.getBlockZ());

        double clampedX, clampedZ;

        if (fromZone == PvpZone.SWORD || fromZone == PvpZone.CRYSTAL) {
            PvpZone toZone = getZone(target.getBlockX(), target.getBlockZ());
            if (toZone == fromZone) return target;

            if (toZone == PvpZone.SAFE || (fromZone == PvpZone.SWORD && toZone == PvpZone.CRYSTAL) || (fromZone == PvpZone.CRYSTAL && toZone == PvpZone.SWORD)) {
                clampedX = fromZone == PvpZone.SWORD
                        ? CENTER_X + BOUNDARY_INSET
                        : CENTER_X - BOUNDARY_INSET;
                clampedZ = target.getZ();
            } else {
                return target;
            }
        } else {
            return target;
        }

        return getSafeLocation(world, clampedX, clampedZ, from.getY(), target.getYaw(), target.getPitch());
    }

    private static Location getSafeLocation(World world, double x, double z, double fallbackY, float yaw, float pitch) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);

        int searchStart = (int) Math.floor(fallbackY);
        searchStart = Math.max(world.getMinHeight(), Math.min(world.getMaxHeight() - 1, searchStart));

        for (int y = searchStart; y >= world.getMinHeight(); y--) {
            Block feet = world.getBlockAt(bx, y, bz);
            Block head = world.getBlockAt(bx, y + 1, bz);
            Block ground = world.getBlockAt(bx, y - 1, bz);

            if (ground.getType().isSolid() && feet.getType().isAir() && head.getType().isAir()) {
                return new Location(world, x, y, z, yaw, pitch);
            }
        }

        for (int y = searchStart; y < world.getMaxHeight() - 1; y++) {
            Block feet = world.getBlockAt(bx, y, bz);
            Block head = world.getBlockAt(bx, y + 1, bz);
            Block ground = world.getBlockAt(bx, y - 1, bz);

            if (ground.getType().isSolid() && feet.getType().isAir() && head.getType().isAir()) {
                return new Location(world, x, y, z, yaw, pitch);
            }
        }

        return new Location(world, x, fallbackY, z, yaw, pitch);
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onEntityPlace(EntityPlaceEvent e) {
        if (e.getPlayer() == null) return;
        if (!e.getPlayer().getWorld().getName().equalsIgnoreCase("spawn")) return;

        int px = e.getPlayer().getLocation().getBlockX();
        int pz = e.getPlayer().getLocation().getBlockZ();
        int ex = e.getEntity().getLocation().getBlockX();
        int ez = e.getEntity().getLocation().getBlockZ();

        PvpZone playerZone = getZone(px, pz);
        PvpZone entityZone = getZone(ex, ez);

        if (playerZone != entityZone || (playerZone != PvpZone.SWORD && playerZone != PvpZone.CRYSTAL)) {
            e.setCancelled(true);
            deny(e.getPlayer(), "you can only place " + e.getEntityType().name().toLowerCase().replace("_", " ") + " inside the pvp zone");
            return;
        }

        if (e.getEntityType() == EntityType.END_CRYSTAL && playerZone == PvpZone.SWORD) {
            e.setCancelled(true);
            deny(e.getPlayer(), "end crystals are not allowed in the sword zone");
        }
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (!e.getPlayer().getWorld().getName().equalsIgnoreCase("spawn")) return;
        if (e.getPlayer().hasPermission("arcane.rank.management")) return;
        if (e.getClickedBlock() == null) return;

        switch (e.getAction()) {
            case RIGHT_CLICK_BLOCK, LEFT_CLICK_BLOCK -> {}
            default -> { return; }
        }

        if (e.useInteractedBlock() == org.bukkit.event.Event.Result.DENY) return;

        int px = e.getPlayer().getLocation().getBlockX();
        int pz = e.getPlayer().getLocation().getBlockZ();
        int bx = e.getClickedBlock().getX();
        int bz = e.getClickedBlock().getZ();

        if (isInPvpZone(px, pz) && isInPvpZone(bx, bz)) return;

        if (e.getClickedBlock().getType() == Material.ENDER_CHEST) return;

        e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        deny(e.getPlayer(), "you can only interact with blocks while in the pvp zone");
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onBucketEmpty(org.bukkit.event.player.PlayerBucketEmptyEvent e) {
        if (!e.getPlayer().getWorld().getName().equalsIgnoreCase("spawn")) return;
        if (e.getPlayer().hasPermission("arcane.rank.management")) return;
        e.setCancelled(true);
        deny(e.getPlayer(), "you can't place bucket contents in spawn");
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onBucketFill(org.bukkit.event.player.PlayerBucketFillEvent e) {
        if (!e.getPlayer().getWorld().getName().equalsIgnoreCase("spawn")) return;
        if (e.getPlayer().hasPermission("arcane.rank.management")) return;
        e.setCancelled(true);
        deny(e.getPlayer(), "you can't fill bucket contents in spawn");
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onBucketEntity(org.bukkit.event.player.PlayerBucketEntityEvent e) {
        if (!e.getPlayer().getWorld().getName().equalsIgnoreCase("spawn")) return;
        if (e.getPlayer().hasPermission("arcane.rank.management")) return;
        e.setCancelled(true);
        deny(e.getPlayer(), "you can't kidnap mobs in spawn");
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onFish(PlayerFishEvent e) {
        if (!e.getPlayer().getWorld().getName().equalsIgnoreCase("spawn")) return;
        if (e.getPlayer().hasPermission("arcane.rank.management")) return;
        if (e.getState() != PlayerFishEvent.State.CAUGHT_ENTITY) return;
        if (e.getCaught() == null) return;

        int px = e.getPlayer().getLocation().getBlockX();
        int pz = e.getPlayer().getLocation().getBlockZ();
        int tx = e.getCaught().getLocation().getBlockX();
        int tz = e.getCaught().getLocation().getBlockZ();

        if (isInPvpZone(px, pz) && isInPvpZone(tx, tz)) return;

        e.setCancelled(true);

        if (e.getCaught() instanceof Player victim) {
            if (!isInPvpZone(px, pz)) deny(e.getPlayer(), "you can't hook players from the safe zone");
            else deny(e.getPlayer(), "you can't hook players in the safe zone");
            deny(victim, e.getPlayer().getName() + " tried to hook you");
        } else {
            deny(e.getPlayer(), "you can only hook entities while both you and the target are in the pvp zone");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onLeavesDecay(org.bukkit.event.block.LeavesDecayEvent e) {
        if (!e.getBlock().getWorld().getName().equalsIgnoreCase("spawn")) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBlockSpread(org.bukkit.event.block.BlockSpreadEvent e) {
        if (!e.getBlock().getWorld().getName().equalsIgnoreCase("spawn")) return;
        if (!e.getNewState().getType().equals(Material.FIRE) && !e.getNewState().getType().equals(Material.SOUL_FIRE)) {
            if (isInSafeZone(e.getBlock().getX(), e.getBlock().getZ())) e.setCancelled(true);
            return;
        }
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBlockBurn(org.bukkit.event.block.BlockBurnEvent e) {
        if (!e.getBlock().getWorld().getName().equalsIgnoreCase("spawn")) return;
        Block block = e.getBlock();
        Material type = block.getType();
        World world = block.getWorld();
        int bx = block.getX();
        int by = block.getY();
        int bz = block.getZ();
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBlockFromTo(org.bukkit.event.block.BlockFromToEvent e) {
        if (!e.getBlock().getWorld().getName().equalsIgnoreCase("spawn")) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBlockGrow(org.bukkit.event.block.BlockGrowEvent e) {
        if (!e.getBlock().getWorld().getName().equalsIgnoreCase("spawn")) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onEntityDamageNonPlayer(EntityDamageByEntityEvent e) {
        if (!e.getEntity().getWorld().getName().equalsIgnoreCase("spawn")) return;
        if (e.getEntity() instanceof Player) return;

        int tx = e.getEntity().getLocation().getBlockX();
        int tz = e.getEntity().getLocation().getBlockZ();

        Player attacker = null;
        if (e.getDamager() instanceof Player p) {
            attacker = p;
        } else if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
        }

        if (attacker == null) return;

        int ax = attacker.getLocation().getBlockX();
        int az = attacker.getLocation().getBlockZ();

        if (!isInPvpZone(ax, az) || !isInPvpZone(tx, tz)) {
            e.setCancelled(true);
            deny(attacker, "you can only attack entities inside the pvp zone");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onCreatureSpawn(org.bukkit.event.entity.CreatureSpawnEvent e) {
        if (!e.getEntity().getWorld().getName().equalsIgnoreCase("spawn")) return;
        if (e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) e.setCancelled(true);
    }
}