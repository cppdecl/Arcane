package cx.arcane.managers.commandManager.commands;

import cx.arcane.managers.permissionManager.PermissionManager;
import cx.arcane.managers.rtpManager.RTPManager;
import cx.arcane.utils.Colors;
import cx.arcane.utils.Log;
import cx.arcane.utils.Text;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Sender;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.triumphteam.gui.paper.Gui;
import dev.triumphteam.gui.paper.builder.item.ItemBuilder;
import dev.triumphteam.gui.element.GuiItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.*;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;

@Command(name = "rtp")
public class RTPCommand {

    @Execute
    public void execute(@Sender Player p) {

        if (!checkCooldown(p)) return;

        p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1, 1);
        openMainGui(p);
    }

    // ========================
    // GUI (SHOP STYLE)
    // ========================

    private static void openMainGui(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);

        Gui.of(3)
                .title(Text.toSmallCapsComponent("Random Teleport"))
                .spamPreventionDuration(110)
                .statelessComponent(con -> {

                    con.setItem(2, 3, worldItem(
                            p,
                            Material.GRASS_BLOCK,
                            "Overworld",
                            "world"
                    ));

                    con.setItem(2, 5, worldItem(
                            p,
                            Material.NETHERRACK,
                            "Nether",
                            "world_nether"
                    ));

                    con.setItem(2, 7, worldItem(
                            p,
                            Material.END_STONE,
                            "End",
                            "world_the_end"
                    ));
                })
                .build()
                .open(p);
    }

    private static GuiItem<Player, org.bukkit.inventory.ItemStack> worldItem(
            Player p,
            Material material,
            String name,
            String worldName
    ) {
        return ItemBuilder.from(material)
                .name(Text.toSmallCapsComponent(name)
                        .color(Colors.HOT_PINK)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(Component.text("Click to teleport to a random location", NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, false))
                .asGuiItem((player, ctx) -> {

                    World world = Bukkit.getWorld(worldName);

                    if (world == null) {
                        p.sendMessage(Component.text("World not found!", Colors.HOT_PINK));
                        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
                        return;
                    }

                    RTPManager.start(world, p);

                    ctx.guiView().close();
                });
    }

    // ========================
    // COOLDOWN
    // ========================

    private static boolean checkCooldown(Player p) {
        int cooldownMs = PermissionManager.getPermissionInt(p, "arcane.cooldown.rtp", 30000);
        //Instant last = RTPManager.getLastTeleportAt(p.getUniqueId());

       // Log.info("[RTP] Cooldown = " + cooldownMs);

      //  if (last == null) return true;

      //  Instant readyAt = last.plusMillis(cooldownMs);

      /*  if (Instant.now().isBefore(readyAt)) {
            long remainingMs = Duration.between(Instant.now(), readyAt).toMillis();
            long seconds = (remainingMs + 999) / 1000;

            sendError(p,
                    Component.text("Please wait ", NamedTextColor.GRAY)
                            .append(Component.text(seconds + "s ", Colors.HOT_PINK))
                            .append(Component.text("before using RTP again!", NamedTextColor.GRAY))
            );
            return false;
        }*/

        return true;
    }

    // ========================
    // MESSAGE
    // ========================

    private static void sendError(Player p, Component msg) {
        p.sendMessage(msg);
        p.sendActionBar(msg);
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
    }
}