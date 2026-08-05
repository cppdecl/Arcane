package cx.arcane.managers.antiXrayManager.listeners.packet;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import cx.arcane.managers.antiXrayManager.AntiXrayManager;
import org.bukkit.entity.Player;

public class ChunkPacketListener implements PacketListener {

    @Override
    public void onPacketSend(PacketSendEvent e) {
        Player p = e.getPlayer();
        if (p == null || !p.isOnline()) return;

        switch (e.getPacketType()) {
            case PacketType.Play.Server.CHUNK_DATA ->
                    AntiXrayManager.handleSendChunk(e, new WrapperPlayServerChunkData(e));
            case PacketType.Play.Server.BLOCK_CHANGE ->
                    AntiXrayManager.handleBlockChange(e, new WrapperPlayServerBlockChange(e));
            case PacketType.Play.Server.MULTI_BLOCK_CHANGE ->
                    AntiXrayManager.handleMultiBlockChange(e, new WrapperPlayServerMultiBlockChange(e));
            case PacketType.Play.Server.SOUND_EFFECT ->
                    AntiXrayManager.handleSoundEffect(e, new WrapperPlayServerSoundEffect(e));
            default -> {}
        }
    }
}