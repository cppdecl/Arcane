package cx.arcane.managers.commandManager.commands;

import cx.arcane.managers.commandManager.arguments.onlinePlayerExceptMeArgument.OnlinePlayerExceptMeArgument;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.tpaManager.TPAManager;
import cx.arcane.managers.teleportManager.TeleportManager;
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
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

@Command(name = "tpaccept")
public class TPAAcceptCommand {

    @Execute
    public void execute(@Sender Player pSender, @Arg OnlinePlayerExceptMeArgument name) {

        PlayerData pTargetData = name.get();
        Player pTarget = pTargetData.getPlayer();

        if (!TPAManager.hasValidOutgoingRequest(pTarget, pSender)) {
            Component res = Component.text("That request does not exist!", Colors.DARK_PINK);
            pSender.sendMessage(res);
            pSender.sendActionBar(res);
            pSender.playSound(pSender.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1, 1);
            pSender.playSound(pSender.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            return;
        }

        pSender.playSound(pSender.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1, 1);

        TPAManager.TPARequest request = TPAManager.getRequest(pTarget, pSender);

        boolean isHere = request.getType() == TPAManager.TeleportType.TARGET_TO_SENDER;

        final var gui = Gui.of(3)
                .title(Text.toSmallCapsComponent("Accept Teleport" + (isHere ? " Here" : "") + " Request"))
                .statelessComponent(con -> {

                    con.setItem(2, 2, ItemBuilder.from(Material.RED_STAINED_GLASS_PANE)
                            .name(Text.toSmallCapsComponent("Cancel").color(Colors.DARK_PINK))
                            .lore(Component.text("Click to cancel the teleport" + (isHere ? " here" : "") + " request.", Colors.WHITE).decoration(TextDecoration.ITALIC, false))
                            .asGuiItem((p, ctx) -> {
                                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
                                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
                                ctx.guiView().close();
                                if (TPAManager.hasValidOutgoingRequest(pTarget, pSender)) {
                                    TPAManager.denyIncoming(pSender, pTarget.getUniqueId());
                                    Component resMsg = Component.textOfChildren(
                                            Component.text("You denied ", Colors.GRAY),
                                            Component.text(pTarget.getName() + "'s", Colors.HOT_PINK),
                                            Component.text(" teleport" + (isHere ? " here" : "") + " request!", Colors.GRAY)
                                    );
                                    Component resAct = Component.text("You denied " + pTarget.getName() + "'s teleport" + (isHere ? " here" : "") + " request!", Colors.HOT_PINK);
                                    pSender.sendMessage(resMsg);
                                    pSender.sendActionBar(resAct);
                                    pSender.playSound(pSender.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
                                    pSender.playSound(pSender.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1, 1);

                                    Component actMsg = Component.textOfChildren(
                                            Component.text(pSender.getName(), Colors.HOT_PINK),
                                            Component.text(" denied your teleport" + (isHere ? " here" : "") + " request.", Colors.GRAY)
                                    );
                                    pTarget.sendMessage(actMsg);
                                    pTarget.sendActionBar(actMsg);
                                    pTarget.playSound(pTarget.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
                                }
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
                            .lore(Component.text("Click to accept the teleport" + (isHere ? " here" : "") + " request.", Colors.WHITE).decoration(TextDecoration.ITALIC, false))
                            .asGuiItem((p, ctx) -> {
                                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
                                ctx.guiView().close();

                                if (TPAManager.hasValidOutgoingRequest(pTarget, pSender)) {
                                    TPAManager.acceptIncoming(pSender, pTarget.getUniqueId());
                                    Component resMsg = Component.textOfChildren(
                                            Component.text("You accepted ", Colors.GRAY),
                                            Component.text(pTarget.getName() + "'s", Colors.HOT_PINK),
                                            Component.text(" teleport" + (isHere ? " Here" : "") + " request!", Colors.GRAY)
                                    );
                                    Component resAct = Component.text("You accepted " + pTarget.getName() + "'s teleport" + (isHere ? " here" : "") + " request!", Colors.HOT_PINK);
                                    pSender.sendMessage(resMsg);
                                    pSender.sendActionBar(resAct);
                                    pSender.playSound(pSender.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
                                    pSender.playSound(pSender.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1, 1);

                                    Component actMsg = Component.textOfChildren(
                                            Component.text(pSender.getName(), Colors.HOT_PINK),
                                            Component.text(" accepted your teleport" + (isHere ? " here" : "") + " request!", Colors.GRAY)
                                    );
                                    pTarget.sendMessage(actMsg);
                                    pTarget.sendActionBar(actMsg);
                                    pTarget.playSound(pTarget.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);

                                    if (request.getType() == TPAManager.TeleportType.TARGET_TO_SENDER) {
                                        TeleportManager.teleport(pSender, pTarget).start();
                                    } else {
                                        TeleportManager.teleport(pTarget, pSender).start();
                                    }
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