package cx.arcane.managers.commandManager.commands;

import cx.arcane.managers.commandManager.arguments.onlinePlayerExceptMeArgument.OnlinePlayerExceptMeArgument;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.managers.playerManager.PlayerSettings;
import cx.arcane.managers.tpaManager.TPAManager;
import cx.arcane.utils.Colors;
import cx.arcane.utils.Text;
import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Sender;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import dev.triumphteam.gui.paper.Gui;
import dev.triumphteam.gui.paper.builder.item.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

@Command(name = "tpahere")
public class TPAHereCommand {

    @Execute
    public void execute(@Sender Player pSender, @Arg OnlinePlayerExceptMeArgument name) {

        PlayerData pTargetData = name.get();
        Player pTarget = pTargetData.getPlayer();

        if (!pTargetData.getSettings().isAllowTpaRequests()) {
            sendError(pSender, "That player has tpa requests disabled.");
            return;
        }

        pSender.playSound(pSender.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1, 1);

        final var gui = Gui.of(3)
                .title(Text.toSmallCapsComponent("Confirm Teleport Here Request"))
                .statelessComponent(con -> {

                    con.setItem(2, 2, ItemBuilder.from(Material.RED_STAINED_GLASS_PANE)
                            .name(Text.toSmallCapsComponent("Cancel").color(Colors.DARK_PINK))
                            .lore(Component.text("Click to cancel the request.", Colors.WHITE).decoration(TextDecoration.ITALIC, false))
                            .asGuiItem((p, ctx) -> {
                                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
                                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
                                ctx.guiView().close();
                            })
                    );

                    con.setItem(2, 4, ItemBuilder.from(Text.worldToIcon(pTarget.getWorld()))
                            .name(Text.toSmallCapsComponent("Location").color(Colors.HOT_PINK))
                            .lore(Component.text(Text.worldToName(pTarget.getWorld()), Colors.GRAY)
                                    .decoration(TextDecoration.ITALIC, false))
                            .asGuiItem((p, ctx) -> {
                                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
                            })
                    );

                    con.setItem(2, 5, ItemBuilder.skull()
                            .owner(pTarget)
                            .name(Text.toSmallCapsComponent("Player").color(Colors.HOT_PINK))
                            .lore(Component.text(pTarget.getName(), Colors.GRAY)
                                    .decoration(TextDecoration.ITALIC, false))
                            .asGuiItem((p, ctx) -> {
                                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
                            })
                    );

                    con.setItem(2, 6, ItemBuilder.from(Material.FEATHER)
                            .name(Text.toSmallCapsComponent("Region").color(Colors.HOT_PINK))
                            .lore(Component.text("Asia (", Colors.GRAY)
                                    .append(Component.text("67ms", Colors.HOT_PINK))
                                    .append(Component.text(")", Colors.GRAY))
                                    .decoration(TextDecoration.ITALIC, false))
                            .asGuiItem((p, ctx) -> {
                                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
                            })
                    );

                    con.setItem(2, 8, ItemBuilder.from(Material.LIME_STAINED_GLASS_PANE)
                            .name(Text.toSmallCapsComponent("Confirm").color(Colors.HOT_PINK))
                            .lore(Component.text("Click to send " + pTarget.getName() + " a teleport here request.", Colors.WHITE)
                                    .decoration(TextDecoration.ITALIC, false))
                            .asGuiItem((p, ctx) -> {
                                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
                                ctx.guiView().close();

                                PlayerSettings pDestinationSettings = PlayerManager.getByUniqueId(pTarget.getUniqueId()).getSettings();
                                if (!pDestinationSettings.isAllowTpaRequests()) {
                                    Component resMsg = Component.textOfChildren(
                                            Component.text(pTarget.getName(), Colors.DARK_PINK),
                                            Component.text(" does not accept teleport requests.", Colors.GRAY)
                                    );

                                    pSender.sendMessage(resMsg);
                                    pSender.sendActionBar(resMsg);
                                    pSender.playSound(pSender.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
                                    pSender.playSound(pSender.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1, 1);
                                    return;
                                }

                                if (TPAManager.hasValidOutgoingRequest(pSender, pTarget)) {

                                    Component resMsg = Component.textOfChildren(
                                            Component.text("You already sent ", Colors.DARK_PINK),
                                            Component.text(pTarget.getName(), Colors.DARK_PINK),
                                            Component.text(" a teleport request earlier!", Colors.DARK_PINK)
                                    );
                                    Component resAct = Component.text("Please wait before requesting again!", Colors.DARK_PINK);
                                    pSender.sendMessage(resMsg);
                                    pSender.sendActionBar(resAct);
                                    pSender.playSound(pSender.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
                                    pSender.playSound(pSender.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1, 1);

                                } else {

                                    TPAManager.addRequest(
                                            pSender,
                                            pTarget,
                                            TPAManager.TeleportType.TARGET_TO_SENDER
                                    );

                                    Component resMsg = Component.textOfChildren(
                                            Component.text("You sent ", Colors.GRAY),
                                            Component.text(pTarget.getName(), Colors.HOT_PINK),
                                            Component.text(" a teleport here request!", Colors.GRAY)
                                    );
                                    Component resAct = Component.text("You sent a teleport here request!", Colors.HOT_PINK);
                                    pSender.sendMessage(resMsg);
                                    pSender.sendActionBar(resAct);
                                    pSender.playSound(pSender.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
                                    pSender.playSound(pSender.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1, 1);

                                    Component tarMsg = Component.textOfChildren(
                                            Component.text(pSender.getName(), Colors.HOT_PINK),
                                            Component.text(" sent you a teleport here request ", Colors.GRAY),
                                            Component.text("(", Colors.GRAY),
                                            Component.text("Click Me", Colors.HOT_PINK),
                                            Component.text(")", Colors.GRAY)
                                    ).clickEvent(ClickEvent.runCommand("/tpaccept " + pSender.getName()));

                                    Component tarAct = Component.textOfChildren(
                                            Component.text(pSender.getName(), Colors.HOT_PINK),
                                            Component.text(" sent you a teleport here request!", Colors.GRAY)
                                    );

                                    pTarget.sendMessage(tarMsg);
                                    pTarget.sendActionBar(tarAct);
                                    pTarget.playSound(pSender.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
                                    pTarget.playSound(pSender.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1, 1);
                                }
                            })
                    );

                })
                .build();

        gui.open(pSender);
    }

    private void sendError(Player player, String message) {
        TextComponent msg = Component.text(message, Colors.DARK_PINK);
        player.sendMessage(msg);
        player.sendActionBar(msg);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
    }
}