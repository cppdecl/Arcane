package cx.arcane.managers.crateManager;

import cx.arcane.managers.permissionManager.PermissionManager;
import cx.arcane.utils.Colors;
import cx.arcane.utils.PlayerUtils;
import cx.arcane.utils.Text;
import dev.triumphteam.gui.paper.Gui;
import dev.triumphteam.gui.paper.builder.item.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class CrateListener implements Listener {

    @EventHandler(ignoreCancelled = false, priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Block block = e.getClickedBlock();
        if (block == null) return;

        CrateData crate = CrateManager.getCrateByLocation(block.getLocation());
        if (crate == null) return;

        if (PermissionManager.check(p, "arcane.rank.management") && p.isSneaking()) return;

        e.setCancelled(true);

        if (!(block.getState() instanceof InventoryHolder holder)) {
            sendError(p, "This crate is invalid!");
            return;
        }


        p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1, 1);

        sendCrateMenu(p, crate, holder);
    }

    public void sendCrateMenu(Player p, CrateData crate, InventoryHolder holder) {
        if (!isValidCrate(crate)) return;

        Inventory inv = holder.getInventory();
        int rows = Math.max(1, (int) Math.ceil(inv.getSize() / 9.0));

        List<Component> lore = List.of(
                Component.text("With a ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                        .append(Text.toSmallCapsComponent(crate.getId() + " Crate Key").color(NamedTextColor.GRAY))
                        .append(Component.text(" you can", NamedTextColor.GRAY)),
                Component.text("choose any of the items you want.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        );

        Component fillerName = Text.toSmallCapsComponent(crate.getId() + " Crate")
                .color(crate.getColor())
                .decoration(TextDecoration.ITALIC, false);

        Gui.of(rows)
                .title(Text.toSmallCapsComponent("Choose An Item"))
                .spamPreventionDuration(110)
                .statelessComponent(con -> {
                    for (int i = 0; i < inv.getSize(); i++) {
                        ItemStack item = inv.getItem(i);
                        final int slot = i;

                        if (item == null || item.getType() == Material.AIR) {
                            con.setItem(slot, ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                                    .name(fillerName)
                                    .lore(lore)
                                    .asGuiItem());
                            continue;
                        }

                        final ItemStack finalItem = item;
                        con.setItem(slot, ItemBuilder.from(item).asGuiItem((player, ctx) -> {
                            if (!isValidCrateItem(finalItem, crate)) {
                                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1f, 0.5f);
                                return;
                            }

                            if (CrateManager.getKeyCount(p.getUniqueId(), crate.getId()) <= 0) {
                                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1f, 1f);
                                return;
                            }

                            sendConfirmMenu(p, finalItem, crate);
                        }));
                    }
                })
                .build()
                .open(p);
    }

    public void sendConfirmMenu(Player p, ItemStack reward, CrateData crate) {
        Component fillerName = Component.text(" ").decoration(TextDecoration.ITALIC, false);

        Gui.of(3)
                .title(Text.toSmallCapsComponent("Confirm Reward"))
                .spamPreventionDuration(110)
                .statelessComponent(con -> {
                    for (int i = 0; i < 27; i++) {
                        if (i == 11 || i == 13 || i == 15) continue;
                        con.setItem(i, ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                                .name(fillerName)
                                .asGuiItem());
                    }

                    con.setItem(13, ItemBuilder.from(reward).asGuiItem());

                    con.setItem(11, ItemBuilder.from(Material.RED_STAINED_GLASS_PANE)
                            .name(Text.toSmallCapsComponent("Cancel").color(Colors.RED).decoration(TextDecoration.ITALIC, false))
                            .asGuiItem((player, ctx) -> {
                                Location loc = crate.getLocation();
                                if (loc == null || !(loc.getBlock().getState() instanceof InventoryHolder holder)) return;
                                ctx.guiView().close();
                                sendCrateMenu(p, crate, holder);
                            }));

                    con.setItem(15, ItemBuilder.from(Material.LIME_STAINED_GLASS_PANE)
                            .name(Text.toSmallCapsComponent("Confirm").color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false))
                            .asGuiItem((player, ctx) -> {
                                if (CrateManager.getKeyCount(p.getUniqueId(), crate.getId()) <= 0) {
                                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1f, 1f);
                                    ctx.guiView().close();
                                    return;
                                }

                                if (!PlayerUtils.canFitInventory(p, reward)) {
                                    ctx.guiView().close();
                                    Component msg = Component.text("That won't fit in your inventory!", Colors.DARK_PINK);
                                    p.sendActionBar(msg);
                                    p.sendMessage(msg);
                                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1f, 1f);
                                    return;
                                }

                                PlayerUtils.giveOrDrop(player, reward);
                                CrateManager.takeKey(p.getUniqueId(), crate.getId(), 1);

                                Location loc = crate.getLocation();
                                if (loc != null && loc.getBlock().getState() instanceof InventoryHolder holder) {
                                    sendCrateMenu(p, crate, holder);
                                }

                                p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 1f, 2f);
                            }));
                })
                .build()
                .open(p);
    }

    public boolean isValidCrateItem(ItemStack item, CrateData crate) {
        if (!isValidCrate(crate)) return false;

        Location loc = crate.getLocation();
        if (loc == null) return false;

        Block block = loc.getBlock();
        if (!isValidStorage(block)) return false;

        Inventory inv = ((InventoryHolder) block.getState()).getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack crateItem = inv.getItem(i);
            if (crateItem != null && crateItem.isSimilar(item)) return true;
        }

        return false;
    }

    public boolean isValidCrate(CrateData crate) {
        if (CrateManager.getCrateById(crate.getId()) != crate) return false;
        Location loc = crate.getLocation();
        return loc != null && isValidStorage(loc.getBlock());
    }
    @EventHandler(ignoreCancelled = false, priority = EventPriority.MONITOR)
    public void onBlockDamage(BlockDamageEvent e) {
        CrateData crate = CrateManager.getCrateByLocation(e.getBlock().getLocation());
        if (crate == null) return;
        if (PermissionManager.check(e.getPlayer(), "arcane.rank.management") && e.getPlayer().isSneaking()) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent e) {
        CrateData crate = CrateManager.getCrateByLocation(e.getBlock().getLocation());
        if (crate == null) return;
        e.setCancelled(true);
        if (PermissionManager.check(e.getPlayer(), "arcane.rank.management") && e.getPlayer().isSneaking()) {
            sendError(e.getPlayer(), "Delete crate first before breaking!");
        }
    }

    private static boolean isValidStorage(Block block) {
        return block.getState() instanceof InventoryHolder;
    }

    private static void sendError(Player p, String message) {
        Component msg = Component.text(message, TextColor.color(0xff0000));
        p.sendMessage(msg);
        p.sendActionBar(msg);
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1f, 1f);
    }

    private static void sendSuccess(Player p, Component msg) {
        p.sendMessage(msg);
        p.sendActionBar(msg);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.MASTER, 1f, 1f);
    }
}