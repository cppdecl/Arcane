package cx.arcane.managers.antiXrayManager.listeners.packet;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import cx.arcane.managers.antiXrayManager.AntiXrayManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Arrays;

public class EntityPacketListener implements PacketListener {

    @Override
    public void onPacketSend(PacketSendEvent e) {
        Player p = e.getPlayer();
        if (p == null || !p.isOnline()) return;

        Location loc = p.getLocation();
        if (loc.getWorld() == null || !loc.getWorld().getName().equals("world")) return;

        switch (e.getPacketType()) {
            case PacketType.Play.Server.DESTROY_ENTITIES      -> handleDestroy(e, p);
            case PacketType.Play.Server.SPAWN_ENTITY          -> handleSpawn(e, p);
            case PacketType.Play.Server.ENTITY_VELOCITY       -> { if (AntiXrayManager.isEntityHidden(p, new WrapperPlayServerEntityVelocity(e).getEntityId()))       e.setCancelled(true); }
            case PacketType.Play.Server.ENTITY_TELEPORT       -> { if (AntiXrayManager.isEntityHidden(p, new WrapperPlayServerEntityTeleport(e).getEntityId()))       e.setCancelled(true); }
            case PacketType.Play.Server.ENTITY_RELATIVE_MOVE  -> { if (AntiXrayManager.isEntityHidden(p, new WrapperPlayServerEntityRelativeMove(e).getEntityId()))   e.setCancelled(true); }
            case PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION -> { if (AntiXrayManager.isEntityHidden(p, new WrapperPlayServerEntityRelativeMoveAndRotation(e).getEntityId())) e.setCancelled(true); }
            case PacketType.Play.Server.ENTITY_ROTATION       -> { if (AntiXrayManager.isEntityHidden(p, new WrapperPlayServerEntityRotation(e).getEntityId()))       e.setCancelled(true); }
            case PacketType.Play.Server.ENTITY_POSITION_SYNC  -> { if (AntiXrayManager.isEntityHidden(p, new WrapperPlayServerEntityPositionSync(e).getId()))         e.setCancelled(true); }
            default -> {}
        }
    }

    private void handleDestroy(PacketSendEvent e, Player p) {
        WrapperPlayServerDestroyEntities packet = new WrapperPlayServerDestroyEntities(e);
        int[] ids = packet.getEntityIds();

        int writeIdx = 0;
        int[] filtered = new int[ids.length];

        for (int entityId : ids) {
            if (AntiXrayManager.isEntityHidden(p, entityId)) continue;
            if (AntiXrayManager.isEntityTracked(p, entityId))
                AntiXrayManager.removeEntity(p, entityId);
            filtered[writeIdx++] = entityId;
        }

        if (writeIdx == ids.length) return;

        if (writeIdx == 0) {
            e.setCancelled(true);
            return;
        }

        packet.setEntityIds(Arrays.copyOf(filtered, writeIdx));
        e.markForReEncode(true);
    }

    private void handleSpawn(PacketSendEvent e, Player p) {
        WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(e);
        double x = packet.getPosition().getX();
        double y = packet.getPosition().getY();
        double z = packet.getPosition().getZ();

        boolean shouldHide = shouldHide(p, x, y, z);

        if (!AntiXrayManager.isEntityTracked(p, packet.getEntityId()))
            AntiXrayManager.addEntity(p, packet.getEntityId(), shouldHide);

        if (shouldHide) e.setCancelled(true);
    }

    private boolean shouldHide(Player p, double x, double y, double z) {
        if (y > 0) return false;

        boolean playerBelow = AntiXrayManager.isDeepslateLevel(p);
        boolean reachable   = AntiXrayManager.isChunkReachable(p, new Location(p.getWorld(), x, y, z));

        return !playerBelow || !reachable;
    }
}