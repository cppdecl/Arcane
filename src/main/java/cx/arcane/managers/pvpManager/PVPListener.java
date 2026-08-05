package cx.arcane.managers.pvpManager;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import cx.arcane.managers.bountyManager.BountyManager;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.managers.playerManager.listeners.SpawnPVPListener;
import cx.arcane.utils.Colors;
import cx.arcane.utils.Log;
import cx.arcane.utils.Text;
import dev.triumphteam.gui.GuiView;
import dev.triumphteam.gui.container.GuiContainer;
import dev.triumphteam.gui.element.GuiItem;
import dev.triumphteam.gui.layout.GuiLayout;
import dev.triumphteam.gui.paper.Gui;
import dev.triumphteam.gui.paper.builder.item.ItemBuilder;
import dev.triumphteam.gui.slot.Slot;
import dev.triumphteam.gui.state.pagination.PagerState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.projectiles.ProjectileSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PVPListener implements Listener {

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;

        Player player = e.getPlayer();
        Block block = e.getClickedBlock();
        if (block == null) return;

        ItemStack hand = player.getInventory().getItemInMainHand();

        if (block.getType() == Material.RESPAWN_ANCHOR  && block.getWorld().getEnvironment() != World.Environment.NETHER) {

            RespawnAnchor anchorData = (RespawnAnchor) block.getBlockData();

            boolean canExplode = (hand.getType() != Material.GLOWSTONE && anchorData.getCharges() > 0) || (hand.getType() == Material.GLOWSTONE && anchorData.getCharges() >= 4);

            if (canExplode) {
                ItemStack drop = block.getDrops()
                        .stream()
                        .findFirst()
                        .orElse(null);

                if (drop == null) return;

                PVPManager.trackExplosive(
                        block.getLocation(),
                        player,
                        PVPManager.ExplosiveType.RESPAWN_ANCHOR,
                        drop
                );
            }

            return;
        }

        if (block.getBlockData() instanceof Bed bedData) {
            Block base = (bedData.getPart() == Bed.Part.HEAD)
                    ? block
                    : block.getRelative(bedData.getFacing());

            ItemStack drop = base.getDrops()
                    .stream()
                    .findFirst()
                    .orElse(null);

            Block head = bedData.getPart() == Bed.Part.HEAD
                    ? block
                    : block.getRelative(bedData.getFacing());

            Block foot = bedData.getPart() == Bed.Part.FOOT
                    ? block
                    : block.getRelative(bedData.getFacing().getOppositeFace());

            if (drop == null) return;

            PVPManager.trackExplosive(head.getLocation(), player, PVPManager.ExplosiveType.BED, drop);
            PVPManager.trackExplosive(foot.getLocation(), player, PVPManager.ExplosiveType.BED, drop);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBowShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        if (!(e.getProjectile() instanceof AbstractArrow arrow)) return;
        if (e.getBow() == null) return;

        PVPManager.trackArrow(arrow.getEntityId(), e.getBow().clone());
    }

    @EventHandler
    public void onCrystalInteract(EntityDamageByEntityEvent e) {
        if (e.getEntityType() == EntityType.END_CRYSTAL) {
            if (e.getDamager() instanceof Player player) {
                long current = PlayerManager.getByUniqueId(player.getUniqueId())
                        .getMeta().getCrystalsExploded();
                PlayerManager.getByUniqueId(player.getUniqueId())
                        .getMeta().setCrystalsExploded(current + 1);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {

        if (e.getEntity() instanceof EnderCrystal crystal && e.getDamager() instanceof Player hitter) {
            PVPManager.trackCrystalAttack(crystal.getEntityId(), hitter, buildCrystalItem(crystal));
            return;
        }

        if (e.getEntity() instanceof ExplosiveMinecart minecart && e.getDamager() instanceof Player hitter) {
            PVPManager.trackMinecartAttacker(minecart.getEntityId(), hitter);
            return;
        }

        if (!(e.getEntity() instanceof Player victim)) return;

        Player attacker = null;
        ItemStack weapon = null;
        PVPManager.ExplosiveType weaponType = null;

        switch (e.getDamager()) {

            case Player p -> {
                attacker = p;
                weapon = p.getInventory().getItemInMainHand().clone();
            }

            case AbstractArrow arrow -> {
                if (arrow.getShooter() instanceof Player p) {
                    attacker = p;
                    weapon = PVPManager.getArrowWeapon(arrow.getEntityId());
                    if (weapon == null || weapon.getType().isAir()) {
                        weapon = p.getInventory().getItemInMainHand().clone();
                    }
                }
            }

            case Projectile proj -> {
                ProjectileSource shooter = proj.getShooter();
                if (shooter instanceof Player p) attacker = p;
                // No weapon snapshot available for misc projectiles; stays null → falls back to mainhand.
            }

            case EnderCrystal crystal -> {
                PVPManager.CrystalAttack attack = PVPManager.getCrystalAttack(crystal.getEntityId());
                if (attack != null) {
                    attacker = attack.attacker();
                    weapon = attack.weapon();
                    weaponType = PVPManager.ExplosiveType.CRYSTAL;
                }
            }

            case TNTPrimed tnt -> {
                if (tnt.getSource() instanceof Player p) {
                    attacker = p;
                    weapon = ItemStack.of(Material.TNT);
                    weaponType = PVPManager.ExplosiveType.TNT;
                }
            }

            case ExplosiveMinecart minecart -> {
                attacker = PVPManager.getMinecartAttacker(minecart.getEntityId());
                if (attacker != null) {
                    weapon = ItemStack.of(Material.TNT_MINECART);
                    weaponType = PVPManager.ExplosiveType.TNT;
                }
            }

            default -> {}
        }

        if (attacker == null || attacker.equals(victim)) return;

        if (PVPManager.isFriendlyFire(attacker, victim)) {
            e.setCancelled(true);
            attacker.sendActionBar(Component.text("You can't attack allies!", NamedTextColor.DARK_GRAY));
            return;
        }

        if (SpawnPVPListener.isInSafeZone(victim.getLocation().getBlockX(), victim.getLocation().getBlockZ())) {
            e.setCancelled(true);
            return;
        }

        PVPManager.onDamage(victim, attacker, e.getFinalDamage(), weapon, weaponType);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamageByBlock(EntityDamageByBlockEvent e) {

        if (!(e.getEntity() instanceof Player victim)) {
            return;
        }

     //   Log.info("Victim: " + victim.getName());

        BlockState state = e.getDamagerBlockState();

        if (state == null) {
            return;
        }

        PVPManager.ExplosiveSource source = PVPManager.getExplosive(state.getLocation());

        if (source == null) {
            return;
        }

        ItemStack stack = source.stack();
        Player owner = source.owner();

        if (owner == null) {
            return;
        }

        if (owner.equals(victim)) {
            return;
        }


        if (PVPManager.isFriendlyFire(owner, victim)) {
            e.setCancelled(true);
            owner.sendActionBar(
                    Component.text("You can't damage allies!", NamedTextColor.DARK_GRAY)
            );

            return;
        }

        double dmg = e.getFinalDamage();

        PVPManager.onDamage(
                victim,
                owner,
                dmg,
                source.stack(),
                source.type()
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();

        PVPSession session = PVPManager.getSession(victim.getUniqueId());
        Player killer = null;
        if (session != null && session.getLastAttackerId() != null) {
            killer = Bukkit.getPlayer(session.getLastAttackerId());
        }

        e.deathMessage(null);

        Component killMethod = null;
        if (killer != null && !killer.equals(victim)) {
            killMethod = resolveKillMethod(killer, victim); // resolve BEFORE handleDeath
        }

        PVPManager.handleDeath(victim, killer);

        final Component message;
        if (killer != null && !killer.equals(victim)) {
            message = Component.text("☠ ", TextColor.color(0xFF0000))
                    .append(Component.text(victim.getName() + " ", TextColor.color(0xFF0000)))
                    .append(Component.text("has been killed by ", NamedTextColor.GRAY))
                    .append(Component.text(killer.getName() + " ", TextColor.color(0xFF0000)))
                    .append(Component.text("using ", NamedTextColor.GRAY))
                    .append(killMethod);
        } else {
            message = Component.text("☠ ", TextColor.color(0xFF0000))
                    .append(Component.text(victim.getName(), TextColor.color(0xFF0000)))
                    .append(Component.text(" has died", NamedTextColor.GRAY));
        }

        for (PlayerData data : PlayerManager.getOnline()) {
            if (!data.getSettings().isShowSystemMessages() || !data.getSettings().isShowDeathMessages()) continue;
            data.getPlayer().sendMessage(message);
        }

        if (killer != null) {
            BountyManager.handleClaimBounty(killer, victim.getUniqueId());
        }
    }

    private Component resolveKillMethod(Player killer, Player victim) {
        PVPSession session = PVPManager.getSession(victim.getUniqueId());
        ItemStack weapon = session != null ? session.getAttackerWeapon() : null;

        if (weapon == null || weapon.getType().isAir()) {
            ItemStack hand = killer.getInventory().getItemInMainHand();
            weapon = hand.getType().isAir() ? null : hand;
        }

        return buildWeaponComponent(killer, weapon);
    }

    public static Component buildWeaponComponent(Player player, @Nullable ItemStack item) {

        if (item == null || item.getType().isAir()) {
            return Component.text("Fists", Colors.RED);
        }

        TextColor itemColor = null;
        Component itemComponent = Component.text(
                PlainTextComponentSerializer.plainText().serialize(item.effectiveName()),
                Colors.RED
        );

        if (item.getItemMeta().hasCustomName()) {
            itemComponent = item.effectiveName();
        }

        if (item.getItemMeta() != null && !item.getItemMeta().hasDisplayName()) {
            itemColor = Colors.RED;
        }

        if (item.getType() == Material.AIR) {
            itemComponent = Component.text("Fists");
        }

        itemComponent = itemComponent.hoverEvent(HoverEvent.showText(Component.text("Click to Inspect Weapon", Colors.HOT_PINK)))
                .clickEvent(ClickEvent.callback(a -> {
                    Player viewer = (Player) a;

                    Gui gui = Gui.of(3)
                            .title(Component.text(player.getName() + "'s Weapon"))
                            .statelessComponent(con -> {
                                ItemStack currentStack = item;
                                con.setItem(2, 5, ItemBuilder.from(currentStack).asGuiItem((p, ctx) -> {
                                    if (currentStack.getType().name().contains("SHULKER_BOX") || currentStack.getType().name().contains("BUNDLE")) {
                                        openShulkerOrBundle(viewer, currentStack, ctx.guiView());
                                    }
                                }));
                            })
                            .build();

                    gui.open(viewer);
                    viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
                }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build()));

        return itemComponent;
    }


    public static void openShulkerOrBundle(Player viewer, ItemStack item, GuiView parentGui) {
        if (item == null || item.getType().isAir()) return;

        // Check if it's a Shulker Box or a Bundle
        boolean isShulker = item.getType().name().contains("SHULKER_BOX");
        boolean isBundle = item.getType().name().contains("BUNDLE");

        if (!isShulker && !isBundle) return;

        // Extract contents into a list (filtering out air/null)
        List<ItemStack> contents = new ArrayList<>();
        if (isShulker) {
            BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
            ShulkerBox box = (ShulkerBox) meta.getBlockState();
            for (ItemStack i : box.getInventory().getContents()) {
                if (i != null && !i.getType().isAir()) contents.add(i);
            }
        } else {
            BundleMeta meta = (BundleMeta) item.getItemMeta();
            for (ItemStack i : meta.getItems()) {
                if (i != null && !i.getType().isAir()) contents.add(i);
            }
        }

        // STATE FLAG: Track if we are programmatically changing menus
        final var navigating = new boolean[]{false};

        // Pagination configuration
        int maxContentRows = 3;
        int itemsPerPage = maxContentRows * 7;
        boolean paginated = contents.size() > itemsPerPage;

        int contentRows = paginated
                ? maxContentRows
                : Math.max(1, (int) Math.ceil(contents.size() / 7.0));

        int rows = contentRows + 2;
        int totalPages = (contents.size() + itemsPerPage - 1) / itemsPerPage;

        Gui subGui = Gui.of(rows)
                .title(item.effectiveName().color(NamedTextColor.DARK_GRAY))
                .onClose(() -> {
                    // Only return to parent if the user closed the inventory (like pressing ESC)
                    if (!navigating[0]) {
                        parentGui.open();
                        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
                    }
                })
                .component(component -> {

                    PagerState<ItemStack> pageState = null;

                    if (paginated) {
                        pageState = PagerState.of(
                                contents,
                                GuiLayout.box(Slot.of(2, 2), Slot.of(rows - 1, 8))
                        );
                        component.remember(pageState);
                    }

                    PagerState<ItemStack> finalPageState = pageState;

                    component.render(con -> {

                        if (paginated) {
                            // Paginated Render
                            finalPageState.forEach(entry -> {
                                ItemStack currentStack = entry.element();
                                // Pass the state flag to the items
                                con.setItem(entry.slot(), createContainerItem(viewer, currentStack, navigating));
                            });

                            if (finalPageState.getCurrentPage() > 1) {
                                con.setItem(rows, 1, prevItemButton(viewer, finalPageState));
                            }

                            if (finalPageState.getCurrentPage() < totalPages && totalPages > 1) {
                                con.setItem(rows, 9, nextItemButton(viewer, finalPageState));
                            }

                        } else {
                            // Regular non-paginated Render
                            int index = 0;
                            for (ItemStack currentStack : contents) {
                                int col = (index % 7) + 2;
                                int row = (index / 7) + 2;

                                // Pass the state flag to the items
                                con.setItem(row, col, createContainerItem(viewer, currentStack, navigating));
                                index++;
                            }
                        }

                        // Visible "Return" button
                        con.setItem(rows, 5, ItemBuilder.from(Material.RED_STAINED_GLASS_PANE)
                                .name(Text.toSmallCapsComponent("Back")
                                        .color(TextColor.color(0xff0000))
                                        .decoration(TextDecoration.ITALIC, false))
                                .lore(Component.text("Click to return", Colors.WHITE)
                                        .decoration(TextDecoration.ITALIC, false))
                                .asGuiItem((player, ctx) -> {
                                    navigating[0] = true; // Mark as navigating so onClose doesn't trigger
                                    parentGui.open();
                                    viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
                                }));
                    });
                })
                .build();

        subGui.open(viewer);
        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
    }

    public static GuiItem<Player, ItemStack> createContainerItem(Player viewer, ItemStack stack, boolean[] navigating) {
        return ItemBuilder.from(stack).asGuiItem((p, ctx) -> {
            if (stack.getType().name().contains("SHULKER_BOX") || stack.getType().name().contains("BUNDLE")) {
                navigating[0] = true; // Mark as navigating so onClose doesn't trigger
                openShulkerOrBundle(viewer, stack, ctx.guiView());
            }
        });
    }

    public static GuiItem<Player, ItemStack> prevItemButton(Player p, PagerState<ItemStack> state) {
        return ItemBuilder.from(Material.ARROW)
                .name(Text.toSmallCapsComponent("Previous")
                        .color(Colors.HOT_PINK)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(Component.text("Click to go to previous page", Colors.WHITE)
                        .decoration(TextDecoration.ITALIC, false))
                .asGuiItem((player, ctx) -> {
                    p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1, 1);
                    state.prev();
                });
    }

    public static GuiItem<Player, ItemStack> nextItemButton(Player p, PagerState<ItemStack> state) {
        return ItemBuilder.from(Material.ARROW)
                .name(Text.toSmallCapsComponent("Next")
                        .color(Colors.HOT_PINK)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(Component.text("Click to go to next page", Colors.WHITE)
                        .decoration(TextDecoration.ITALIC, false))
                .asGuiItem((player, ctx) -> {
                    p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1, 1);
                    state.next();
                });
    }

    private static ItemStack buildCrystalItem(EnderCrystal crystal) {
        ItemStack item = ItemStack.of(Material.END_CRYSTAL);
        Component customName = crystal.customName();
        if (customName != null) {
            item.editMeta(meta -> meta.displayName(customName));
        }
        return item;
    }

    // -------------------------------------------------------------------------
    // Movement / commands / quit
    // -------------------------------------------------------------------------

    /**
     * Prevents in-combat players from entering the safe zone by resetting their position
     * to the last valid out-of-zone location on any block-level movement.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player player = e.getPlayer();
        if (!PVPManager.isInCombat(player.getUniqueId())) return;

        Location to   = e.getTo();
        Location from = e.getFrom();

        if (to.getBlockX() == from.getBlockX()
                && to.getBlockY() == from.getBlockY()
                && to.getBlockZ() == from.getBlockZ()) return;

        if (SpawnPVPListener.isInSafeZone(to.getBlockX(), to.getBlockZ())) {
            e.setTo(from);
        }
    }

    /**
     * Blocks command usage while a player is in combat.
     * Runs at MONITOR with ignoreCancelled=false to intercept all commands regardless
     * of prior event state.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player player = e.getPlayer();
        if (PVPManager.isInCombat(player.getUniqueId()))
            PVPManager.cancelCommandIfInCombat(player, e);
    }

    /**
     * Handles combat logging: if the quitting player is in combat they are killed,
     * their last attacker is notified, and both sessions are cleaned up.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent e) {
        PVPManager.handleLogout(e.getPlayer());
    }
}