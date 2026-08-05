package cx.arcane.managers.gizmoManager.listeners;

import cx.arcane.Arcane;
import cx.arcane.managers.gizmoManager.GizmoManager;
import cx.arcane.utils.Colors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class FireAxeListener implements Listener {

    private static final int BREAK_DELAY_TICKS = 1;

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block start = event.getBlock();

        if (!isLog(start.getType())) return;

        ItemStack tool = player.getInventory().getItemInMainHand();

        if (!GizmoManager.isGizmo(tool)) return;
        if (!GizmoManager.FireAxe.getToolType().equals(GizmoManager.getToolType(tool))) return;

        if (GizmoManager.isExpired(tool)) {
            destroyTool(player, event);
            return;
        }

        GizmoManager.markUsed(tool);
        runFloodFill(player, tool, start);
    }

    private void runFloodFill(Player player, ItemStack tool, Block start) {
        Queue<Block> queue = new ArrayDeque<>();
        Set<Block> visited = new HashSet<>();

        visited.add(start);
        for (Block adj : getAdjacent(start)) {
            if (isLog(adj.getType())) {
                visited.add(adj);
                queue.add(adj);
            }
        }

        scheduleNext(player, tool, queue, visited);
    }

    private void scheduleNext(Player player, ItemStack tool, Queue<Block> queue, Set<Block> visited) {
        if (!player.isOnline() || queue.isEmpty()) return;

        ItemStack current = player.getInventory().getItemInMainHand();
        if (current.getType() != tool.getType()) return;

        Block block = queue.poll();
        if (block == null || !isLog(block.getType())) {
            scheduleNext(player, tool, queue, visited);
            return;
        }

        int bx = block.getX();
        int bz = block.getZ();
        World world = block.getWorld();

        Bukkit.getRegionScheduler().runDelayed(Arcane.getPlugin(), world, bx >> 4, bz >> 4, rt -> {
            if (!player.isOnline()) return;

            ItemStack heldNow = player.getInventory().getItemInMainHand();
            if (heldNow.getType() != tool.getType()) return;

            if (!isLog(block.getType())) {
                scheduleNext(player, tool, queue, visited);
                return;
            }

            if (!canPlayerBreak(player, block)) {
                scheduleNext(player, tool, queue, visited);
                return;
            }

            block.breakNaturally(heldNow);
            playWoodBreak(block);

            for (Block adj : getAdjacent(block)) {
                if (!visited.contains(adj) && isLog(adj.getType())) {
                    visited.add(adj);
                    queue.add(adj);
                }
            }

            scheduleNext(player, tool, queue, visited);
        }, BREAK_DELAY_TICKS);
    }

    private void playWoodBreak(Block block) {
        World w = block.getWorld();
        float pitch = 0.85f + (float) (Math.random() * 0.3f);
        w.playSound(block.getLocation(), Sound.BLOCK_WOOD_BREAK, SoundCategory.BLOCKS, 0.8f, pitch);
        w.playSound(block.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_STEP, 1.0f, 2.0f);
    }

    private boolean isLog(Material mat) {
        return mat.name().endsWith("_LOG") || mat.name().endsWith("_STEM");
    }

    private List<Block> getAdjacent(Block b) {
        World w = b.getWorld();
        int x = b.getX(), y = b.getY(), z = b.getZ();
        List<Block> neighbors = new ArrayList<>(26);
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    neighbors.add(w.getBlockAt(x + dx, y + dy, z + dz));
                }
        return neighbors;
    }

    private void destroyTool(Player player, BlockBreakEvent event) {
        player.getInventory().setItemInMainHand(null);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
        Component msg = Component.text("Your ", NamedTextColor.GRAY)
                .append(Component.text("Fire Axe ", Colors.HOT_PINK))
                .append(Component.text("has crumbled!", NamedTextColor.GRAY));
        player.sendMessage(msg);
        player.sendActionBar(msg);
        if (event != null) event.setCancelled(true);
    }

    private boolean canPlayerBreak(Player player, Block block) {
        if (!block.getType().isBlock()) return false;
        if (block.getType().isAir()) return false;
        if (player.isGliding()) return false;
        if (player.getWorld().getName().equals("spawn")) return false;
        return player.getGameMode() == GameMode.CREATIVE || !(block.getType().getHardness() < 0);
    }
}