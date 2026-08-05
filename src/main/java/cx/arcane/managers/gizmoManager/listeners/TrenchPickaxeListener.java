package cx.arcane.managers.gizmoManager.listeners;

import cx.arcane.managers.gizmoManager.GizmoManager;
import cx.arcane.utils.Colors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class TrenchPickaxeListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {

        Player player = event.getPlayer();
        Block center = event.getBlock();
        World world = center.getWorld();

        ItemStack tool = player.getInventory().getItemInMainHand();

        if (!GizmoManager.isGizmo(tool)) return;
        if (!GizmoManager.TrenchPickaxe.getToolType().equals(GizmoManager.getToolType(tool))) return;

        if (GizmoManager.isExpired(tool)) {
            player.getInventory().setItemInMainHand(null);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);

            Component msg = Component.text("Your ", NamedTextColor.GRAY)
                    .append(Component.text("Trench Pickaxe ", Colors.HOT_PINK))
                    .append(Component.text("has crumbled!", NamedTextColor.GRAY));

            player.sendMessage(msg);
            player.sendActionBar(msg);

            event.setCancelled(true);
            return;
        }

        if (!center.isPreferredTool(tool) || center.getDrops(tool).isEmpty()) {
            return;
        }

        GizmoManager.markUsed(tool);

        world.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_STEP, 1.0F, 2.0F);

        Vector dir = player.getEyeLocation().getDirection().normalize();
        int fx = (int) Math.round(dir.getX());
        int fy = (int) Math.round(dir.getY());
        int fz = (int) Math.round(dir.getZ());

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {

                    Block target = world.getBlockAt(
                            center.getX() + fx + dx,
                            center.getY() + fy + dy,
                            center.getZ() + fz + dz
                    );

                    if (!canPlayerBreak(player, target)) continue;

                    if (!target.isPreferredTool(tool)) continue;
                    if (target.getDrops(tool).isEmpty()) continue;

                    target.breakNaturally(tool);
                }
            }
        }

        event.setCancelled(true);
    }

    private boolean canPlayerBreak(Player player, Block block) {
        if (!block.getType().isBlock()) return false;
        if (block.getType().isAir()) return false;

        if (player.getWorld().getName().equals("spawn")) return false;

        if (player.isGliding()) return false;

        return player.getGameMode() == GameMode.CREATIVE ||
                !(block.getType().getHardness() < 0);
    }
}
