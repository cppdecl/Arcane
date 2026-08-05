package cx.arcane.utils;

import ca.spottedleaf.moonrise.common.util.TickThread;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.LightData;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import cx.arcane.Arcane;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import io.papermc.paper.FeatureHooks;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.*;

public class ChunkUtils {
    public static void refreshChunkForPlayer(Player bukkitPlayer, int chunkX, int chunkZ) {
        if (bukkitPlayer == null || !bukkitPlayer.isOnline()) return;

        CraftWorld cWorld = (CraftWorld) bukkitPlayer.getWorld();
        ServerLevel sLevel = cWorld.getHandle();

        TickThread.ensureTickThread(sLevel, chunkX, chunkZ, "Cannot refresh chunk asynchronously");

        ChunkHolder holder = sLevel.getChunkSource().chunkMap
                .getVisibleChunkIfPresent(ChunkPos.asLong(chunkX, chunkZ));

        if (holder == null) return;

        LevelChunk chunk = holder.getChunkToSend();
        if (chunk == null) return;

        ServerPlayer player = ((CraftPlayer) bukkitPlayer).getHandle();

        if (!holder.playerProvider.getPlayers(holder.getPos(), false).contains(player)) {
            return;
        }

        sendChunkRefreshPacket(player, chunk);
    }

    private static void sendChunkRefreshPacket(ServerPlayer player, LevelChunk chunk) {
        boolean shouldModify = chunk.getLevel().chunkPacketBlockController.shouldModify(player, chunk);
        ClientboundLevelChunkWithLightPacket packet =
                new ClientboundLevelChunkWithLightPacket(
                        chunk,
                        chunk.level.getLightEngine(),
                        null,
                        null,
                        shouldModify
                );

        player.connection.send(packet);
    }
}