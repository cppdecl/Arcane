package cx.arcane.managers.antiXrayManager.listeners;

import com.github.retrooper.packetevents.event.PacketListener;
import cx.arcane.Arcane;
import cx.arcane.utils.Colors;
import cx.arcane.utils.Log;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.*;

public class AntiCheatTestListener implements Listener, PacketListener {

    private static final Map<UUID, BlockData> originalBlockData = new HashMap<>();
    private static final Map<UUID, PlayerDetectionData> playerDataMap = new HashMap<>();

    public static record Detection(String name, Map<String, List<String>> keys) {}

    private static final List<Detection> modConfigs = List.of(
            new Detection("Freecam Mod", Map.of(
                    "key.freecam.toggle", List.of("Toggle Freecam", "Freecam")
            )),
            new Detection("Meteor Client", Map.of(
                    "key.meteor-client.open-gui", List.of("Open GUI", "Meteor", "key.meteor-client"),
                    "key.meteor-client.toggle", List.of("Toggle Meteor", "key.meteor-client")
            )),
            new Detection("Nexica Development Client", Map.of(
                    "key.nexica.open", List.of("Open Nexica UI", "Nexica")
            )),
            new Detection("Arcane Developer Client", Map.of(
                    "key.inventory_slots.config", List.of("Open Inventory Slots Config")
            )),
            new Detection("Glazed Addon", Map.of(
                    "key.glazed.activate-key", List.of("Open GUI", "Glazed", "key.glazed")
            )),
            new Detection("Accurate Block Placement", Map.of(
                    "net.clayborn.accurateblockplacement.togglevanillaplacement", List.of("Toggle Placement Mode")
            )),
            new Detection("Tweakeroo Mod", Map.of(
                    "tweakeroo.feature_toggle.name.tweaksnapaim", List.of("Snap Aim")
            ))
    );

    private static class PlayerDetectionData {
        final Player player;
        final Set<String> detectedNames = new HashSet<>();
        boolean signResponded = false;
        boolean anvilResponded = false;
        boolean done = false;

        PlayerDetectionData(Player player) { this.player = player; }
    }

    public static void onJoin(Player p) {
        /*if (FloodgateApi.getInstance().isFloodgatePlayer(p.getUniqueId())) return;

        PlayerDetectionData data = new PlayerDetectionData(p);
        playerDataMap.put(p.getUniqueId(), data);
        p.getScheduler().runDelayed(Arcane.getPlugin(), t -> openSign(p), null, 20L);*/
    }

    public static void onQuit(Player p) {
        UUID uuid = p.getUniqueId();
        playerDataMap.remove(uuid);
        originalBlockData.remove(uuid);
    }

    private static void restoreBlock(Player player) {
        BlockData bd = originalBlockData.remove(player.getUniqueId());
        if (bd != null) player.getLocation().clone().add(player.getEyeLocation().getDirection().multiply(-3)).getBlock().setBlockData(bd);
    }

    private static void openSign(Player player) {
        try {
            var block = player.getLocation().clone().add(player.getEyeLocation().getDirection().multiply(-3)).getBlock();
            originalBlockData.put(player.getUniqueId(), block.getBlockData());

            block.setType(Material.OAK_SIGN);
            Sign sign = (Sign) block.getState();
            var backSide = sign.getSide(Side.BACK);

            Component content = Component.empty().content("");
            for (Detection detection : modConfigs) {
                for (String key : detection.keys().keySet()) {
                    content = content.append(Component.translatable(key, ""));
                    content = content.append(Component.text(","));
                }
            }

            backSide.line(0, content);
            sign.update();
            sign.setAllowedEditorUniqueId(player.getUniqueId());

            player.getScheduler().runDelayed(Arcane.getPlugin(), t -> {
                player.openSign(sign, Side.BACK);
                player.closeInventory();

                player.getScheduler().runDelayed(Arcane.getPlugin(), t2 -> {
                    PlayerDetectionData data = playerDataMap.get(player.getUniqueId());
                    if (data != null && !data.signResponded) restoreBlock(player);
                }, null, 3L);

            }, null, 1L);

        } catch (Exception ignored) {}
    }

    @EventHandler
    public static void onSignChange(SignChangeEvent e) {
        Player player = e.getPlayer();

        PlayerDetectionData data = playerDataMap.get(player.getUniqueId());
        if (data == null) return;
        if (data.signResponded) return;

        String content = e.line(0) != null
                ? PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(e.line(0)))
                : "";

        Log.info("Player " + player.getName() + " -> (SIGN) -> " + content);

        checkContentForDetections(data, content);

        data.signResponded = true;
        originalBlockData.remove(player.getUniqueId());

        report(data);
    }

    private static void checkContentForDetections(PlayerDetectionData data, String content) {
        for (Detection detection : modConfigs) {
            for (List<String> values : detection.keys().values()) {
                for (String value : values) {
                    if (content.contains(value)) {
                        data.detectedNames.add(detection.name());
                    }
                }
            }
        }
    }

    private static void report(PlayerDetectionData data) {
        if (data.detectedNames.isEmpty()) return;

        Component msg = Component.text()
                .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
                .append(Component.text(data.player.getName(), Colors.HOT_PINK))
                .append(Component.text(" is using ", NamedTextColor.GRAY))
                .append(Component.text(String.join(", ", data.detectedNames), Colors.HOT_PINK))
                .build();

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.hasPermission("arcane.rank.management")) continue;

            online.sendMessage(msg);
            online.playSound(online.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.MASTER, 1, 1);
            online.playSound(online.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 1, 1);
            online.playSound(online.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 1, 1);
        }
    }
}