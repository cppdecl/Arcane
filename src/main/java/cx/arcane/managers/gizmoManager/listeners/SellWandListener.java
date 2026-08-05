package cx.arcane.managers.gizmoManager.listeners;

import cx.arcane.managers.gizmoManager.GizmoManager;
import cx.arcane.managers.priceManager.PriceManager;
import cx.arcane.utils.Colors;
import github.nighter.smartspawner.api.SmartSpawnerProvider;
import github.nighter.smartspawner.api.data.SpawnerDataDTO;
import github.nighter.smartspawner.api.data.SpawnerDataModifier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SellWandListener implements Listener {

    /* ---------- cooldown per block ---------- */
    private static final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private static final long BLOCK_COOLDOWN_MS = 1000L;

    @EventHandler(ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        Block block = event.getBlock();

        if (!GizmoManager.isGizmo(tool)) return;
        if (!GizmoManager.SellWand.getToolType().equals(GizmoManager.getToolType(tool))) return;

        if (GizmoManager.isExpired(tool)) {
            destroyTool(player);
            event.setCancelled(true);
            return;
        }

        SpawnerDataDTO spawner = SmartSpawnerProvider.getAPI().getSpawnerByLocation(block.getLocation());
        if (spawner != null) {
            SpawnerDataModifier modifier = SmartSpawnerProvider.getAPI().getSpawnerModifier(spawner.getSpawnerId());
            if (modifier != null) {

            }
        }


        event.setInstaBreak(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        ItemStack tool = player.getInventory().getItemInMainHand();

        if (!GizmoManager.isGizmo(tool)) return;
        if (!GizmoManager.SellWand.getToolType().equals(GizmoManager.getToolType(tool))) return;

        if (GizmoManager.isExpired(tool)) {
            destroyTool(player);
            event.setCancelled(true);
            return;
        }

        /* ---------- block cooldown ---------- */
        if (isOnCooldown(player, block, System.currentTimeMillis())) {
            event.setCancelled(true);
            return;
        }

        GizmoManager.markUsed(tool);

        /* ---------- sell ---------- */
        event.setCancelled(true);
        markCooldown(player, block, System.currentTimeMillis());

        runSellLogic(player, block);
    }

    /* ------------------------------------------------------------------ */
    /* helpers                                                            */
    /* ------------------------------------------------------------------ */


    private boolean isOnCooldown(Player player, Block block, long now) {
        cooldowns.putIfAbsent(player.getUniqueId(), new HashMap<>());

        String key = blockKey(block);
        long last = cooldowns.get(player.getUniqueId())
                .getOrDefault(key, 0L);

        return (now - last) < BLOCK_COOLDOWN_MS;
    }

    private void markCooldown(Player player, Block block, long now) {
        cooldowns.get(player.getUniqueId())
                .put(blockKey(block), now);
    }

    private String blockKey(Block block) {
        return block.getWorld().getName() + ","
                + block.getX() + ","
                + block.getY() + ","
                + block.getZ();
    }

    private void destroyTool(Player player) {
        player.getInventory().setItemInMainHand(null);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);

        Component msg = Component.text("Your ", NamedTextColor.GRAY)
                .append(Component.text("Sell Wand ", Colors.HOT_PINK))
                .append(Component.text("has deteriorated!", NamedTextColor.GRAY));

        player.sendMessage(msg);
        player.sendActionBar(msg);
    }

    private void runSellLogic(Player player, Block block) {
        PriceManager.sellBlockContents(block, player);
    }

    private boolean canPlayerBreak(Player player, Block block) {
        if (!block.getType().isBlock()) return false;
        if (block.getType().isAir()) return false;

        if (player.isGliding()) return false;

        if (player.getWorld().getName().equals("spawn")) return false;

        return player.getGameMode() == GameMode.CREATIVE ||
                !(block.getType().getHardness() < 0);
    }
}
