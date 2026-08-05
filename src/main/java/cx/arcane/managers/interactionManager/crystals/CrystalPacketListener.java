package cx.arcane.managers.interactionManager.crystals;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import cx.arcane.Arcane;
import cx.arcane.managers.playerManager.PlayerManager;
import io.github.retrooper.packetevents.util.folia.FoliaScheduler;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Set;

public class CrystalPacketListener implements PacketListener {

    private static final Attribute BLOCK_RANGE  = Attribute.BLOCK_INTERACTION_RANGE;
    private static final Attribute ENTITY_RANGE = Attribute.ENTITY_INTERACTION_RANGE;

    private static final Set<PacketType.Play.Client> TRACKED_PACKETS = Set.of(
            PacketType.Play.Client.ANIMATION,
            PacketType.Play.Client.PLAYER_DIGGING,
            PacketType.Play.Client.CLICK_WINDOW,
            PacketType.Play.Client.CREATIVE_INVENTORY_ACTION,
            PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT,
            PacketType.Play.Client.USE_ITEM,
            PacketType.Play.Client.INTERACT_ENTITY
    );

    @Override
    public void onPacketReceive(PacketReceiveEvent e) {
        if (!(e.getPacketType() instanceof PacketType.Play.Client type)) return;
        if (!TRACKED_PACKETS.contains(type)) return;

        Player p = e.getPlayer();
        if (p == null) return;

        if (type == PacketType.Play.Client.ANIMATION) {
            handleAnimation(e);
        } else if (type == PacketType.Play.Client.INTERACT_ENTITY) {
            handleInteractEntity(e);
            updateLastPacket(e);
        } else {
            updateLastPacket(e);
        }
    }

    // -------------------- Animation (left-click crystal hit) -------------------- //

    private void handleAnimation(PacketReceiveEvent e) {
        if (e.isCancelled()) return;

        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.SPECTATOR) return;
        if (isAttackReduced(p)) return;

        CrystalUser user = CrystalTracker.getUser(p.getUniqueId());
        if (user == null) return;

        AnimationType last = user.getLastAnimation();
        Location eyeLoc = p.getEyeLocation();
        Vector direction = eyeLoc.getDirection();

        // Schedule on the region owning the player's current location
        FoliaScheduler.getRegionScheduler().run(Arcane.getPlugin(), eyeLoc, t -> {
            if (last == AnimationType.IGNORE) return;
            if (user.isIgnoreAnimation()) return;

            // Guard against player crossing region boundaries between packet and task
            Location newEye = p.getEyeLocation();
            if (eyeLoc.getWorld() != newEye.getWorld() || newEye.distanceSquared(eyeLoc) > 100) return;

            AttributeInstance range = p.getAttribute(ENTITY_RANGE);
            if (range == null) return;

            RayTraceResult result = eyeLoc.getWorld().rayTraceEntities(
                    eyeLoc, direction, range.getValue(), 0.0,
                    entity -> {
                        if (!((CraftEntity) entity).getHandle().isPickable()) return false;
                        if (entity.getType() != EntityType.PLAYER) return true;
                        Player target = (Player) entity;
                        if (target.getGameMode() == GameMode.SPECTATOR) return false;
                        return !p.getUniqueId().equals(target.getUniqueId()) && p.canSee(target);
                    }
            );
            if (result == null) return;

            if (result.getHitEntity() == null || result.getHitEntity().getType() != EntityType.END_CRYSTAL) return;

            // Ignore crystals spawned this tick - prevents double-pop
            if (result.getHitEntity().getTicksLived() == 0) return;

            PlayerManager.getByUniqueId(p.getUniqueId()).getMeta().setCrystalsExploded(
                    PlayerManager.getByUniqueId(p.getUniqueId()).getMeta().getCrystalsExploded() + 1
            );

            // Block occlusion check - skip if eye is inside crystal bounding box
            if (!result.getHitEntity().getBoundingBox().contains(eyeLoc.toVector())) {
                double blockRange = p.getGameMode() == GameMode.CREATIVE ? 5.0 : 4.5;
                RayTraceResult blockResult = eyeLoc.getWorld().rayTraceBlocks(eyeLoc, direction, blockRange);
                if (blockResult != null && blockResult.getHitBlock() != null) {
                    Vector eyeVec = eyeLoc.toVector();
                    if (eyeVec.distanceSquared(blockResult.getHitPosition()) <= eyeVec.distanceSquared(result.getHitPosition())) return;
                    if (last != AnimationType.START_DIGGING && last != AnimationType.ATTACK) return;
                }
            }

            p.attack(result.getHitEntity());
        });

        // Update last packet state AFTER scheduling so AnimationType reflects pre-anim state
        user.setLastAnimation(AnimationType.ANIMATION);
    }

    // -------------------- InteractEntity (right-click crystal to place) -------------------- //

    private void handleInteractEntity(PacketReceiveEvent e) {
        WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(e);
        if (packet.getAction() != WrapperPlayClientInteractEntity.InteractAction.INTERACT_AT) return;

        // Cheap pre-filter on packet thread: clicks near top of crystal aren't placements
        com.github.retrooper.packetevents.util.Vector3f hit = packet.getTarget().orElse(null);
        if (hit != null && hit.getY() > 0.5f) return;

        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.ADVENTURE) return;

        ItemStack handItem = packet.getHand() == InteractionHand.MAIN_HAND
                ? p.getInventory().getItemInMainHand()
                : p.getInventory().getItemInOffHand();
        if (handItem.getType() != Material.END_CRYSTAL) return;

        EnderCrystal crystal = CrystalTracker.getCrystal(p.getWorld().getName(), packet.getEntityId());
        if (crystal == null) return;

        // Snapshot on packet thread - no world access needed later for these
        Location eyeLoc = p.getEyeLocation();
        Vector direction = eyeLoc.getDirection();

        // Pre-compute expected block coords from crystal location - pure integer math
        Location cl = crystal.getLocation();
        int expectedX = (int) Math.floor(cl.getX() - 0.5);
        int expectedY = (int) Math.floor(cl.getY() - 1.0);
        int expectedZ = (int) Math.floor(cl.getZ() - 0.5);

        p.getScheduler().run(Arcane.getPlugin(), t -> {
            AttributeInstance range = p.getAttribute(BLOCK_RANGE);
            if (range == null) return;

            RayTraceResult result = eyeLoc.getWorld().rayTraceBlocks(eyeLoc, direction, range.getValue());
            if (result == null) return;

            Block block = result.getHitBlock();
            if (block == null) return;

            // Integer coord compare - faster than Location.equals() which does string + double comparison
            if (block.getX() != expectedX || block.getY() != expectedY || block.getZ() != expectedZ) return;
            if (!CrystalTracker.BLOCK_TYPES.contains(block.getType())) return;

            CrystalTracker.spawnCrystal(crystal.getLocation(), p, handItem);
        }, null);
    }

    // -------------------- Last-packet tracking (mirrors LastPacketListener) -------------------- //

    private void updateLastPacket(PacketReceiveEvent e) {
        Player p = e.getPlayer();
        CrystalUser user = CrystalTracker.getUser(p.getUniqueId());
        if (user == null) return;

        AnimationType next = resolveType(e);

        // Dropping order: ANIMATION -> WINDOW_CLICK, unlike most actions where ANIMATION is last.
        // If the last packet was an animation and this one is an inventory drop/creative action,
        // suppress the animation so it isn't processed as a swing on the main thread.
        if (user.getLastAnimation() == AnimationType.ANIMATION) {
            user.setIgnoreAnimation(next == AnimationType.INV_DROP || next == AnimationType.CREATIVE_INV_ACTION);
        }

        user.setLastAnimation(next);
    }

    private AnimationType resolveType(PacketReceiveEvent e) {
        PacketType.Play.Client type = (PacketType.Play.Client) e.getPacketType();

        if (type == PacketType.Play.Client.ANIMATION) return AnimationType.ANIMATION;

        if (type == PacketType.Play.Client.PLAYER_DIGGING) {
            WrapperPlayClientPlayerDigging w = new WrapperPlayClientPlayerDigging(e);
            if (w.getAction() == DiggingAction.DROP_ITEM || w.getAction() == DiggingAction.DROP_ITEM_STACK)
                return AnimationType.IGNORE;
            if (w.getAction() == DiggingAction.START_DIGGING)
                return AnimationType.START_DIGGING;
        }

        if (type == PacketType.Play.Client.CLICK_WINDOW) {
            WrapperPlayClientClickWindow w = new WrapperPlayClientClickWindow(e);
            if (w.getWindowClickType() == WrapperPlayClientClickWindow.WindowClickType.THROW
                    || (w.getWindowClickType() == WrapperPlayClientClickWindow.WindowClickType.PICKUP && w.getSlot() == -999))
                return AnimationType.INV_DROP;
        }

        if (type == PacketType.Play.Client.CREATIVE_INVENTORY_ACTION) return AnimationType.CREATIVE_INV_ACTION;
        if (type == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) return AnimationType.IGNORE;
        if (type == PacketType.Play.Client.USE_ITEM) return AnimationType.IGNORE;

        if (type == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity w = new WrapperPlayClientInteractEntity(e);
            if (w.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK)
                return AnimationType.ATTACK;
        }

        return AnimationType.MISC;
    }

    // -------------------- Util -------------------- //

    private static boolean isAttackReduced(Player p) {
        PotionEffect weakness = p.getPotionEffect(PotionEffectType.WEAKNESS);
        int weaknessLevel = weakness != null ? weakness.getAmplifier() + 1 : 0;
        PotionEffect strength = p.getPotionEffect(PotionEffectType.STRENGTH);
        int strengthLevel = strength != null ? strength.getAmplifier() + 1 : 0;
        return strengthLevel * 3 - weaknessLevel * 4 < 0;
    }
}