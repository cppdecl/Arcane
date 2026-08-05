package cx.arcane.managers.playerManager.listeners;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.client.*;
import cx.arcane.Arcane;
import cx.arcane.managers.authManager.AuthManager;
import cx.arcane.managers.authManager.AuthState;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.managers.playerManager.PlayerMeta;
import cx.arcane.managers.playerManager.PlayerSettings;
import cx.arcane.utils.Log;
import cx.arcane.utils.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.minecraft.world.entity.LightningBolt;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.entity.CraftLightningStrike;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.util.RayTraceResult;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PlayerPacketListener implements PacketListener {

    @Override
    public void onPacketReceive(PacketReceiveEvent e) {
        if (e.getPlayer() == null || e.getConnectionState() != ConnectionState.PLAY) return;

        switch (e.getPacketType()) {
            case PacketType.Play.Client.ANIMATION -> {
                WrapperPlayClientAnimation packet = new WrapperPlayClientAnimation(e);
                onWrapperPlayClientAnimation(packet, e.getPlayer());
                break;
            }
            default -> { break; }
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent e) {
        if (e.getPlayer() == null || e.getConnectionState() != ConnectionState.PLAY) return;


    }

    public void onWrapperPlayClientAnimation(WrapperPlayClientAnimation packet, Player p) {
        if (!AuthManager.isAuthenticated(p.getUniqueId())) return;

        p.getScheduler().run(Arcane.getPlugin(), t -> {
            PlayerData pData = PlayerManager.getByUniqueId(p.getUniqueId());
            PlayerSettings pSettings = pData.getSettings();
            PlayerMeta pMeta = pData.getMeta();

            if (pMeta.isZeus()) {
                int range = 100;
                RayTraceResult result = p.getWorld().rayTraceBlocks(
                        p.getEyeLocation(),
                        p.getEyeLocation().getDirection(),
                        range,
                        FluidCollisionMode.NEVER,
                        true
                );

                if (result == null || result.getHitBlock() == null) return;

                Location loc = result.getHitBlock().getLocation().toCenterLocation();

                Bukkit.getRegionScheduler().run(Arcane.getPlugin(), loc, (task) -> {
                    loc.getWorld().spawnEntity(result.getHitBlock().getLocation().toCenterLocation(), EntityType.LIGHTNING_BOLT);
                });
            }
        }, null);
    }
}
