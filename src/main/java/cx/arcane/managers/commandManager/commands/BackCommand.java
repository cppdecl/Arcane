package cx.arcane.managers.commandManager.commands;

import cx.arcane.managers.commandManager.arguments.onlinePlayerExceptMeArgument.OnlinePlayerExceptMeArgument;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.teleportManager.TeleportManager;
import cx.arcane.utils.Colors;
import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Sender;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

@Command(name = "back")
@Permission("arcane.rank.management")
public class BackCommand {

    @Execute
    public void execute(
            @Sender Player sender
    ) {

        if (!TeleportManager.hasLastLocation(sender)) {
            sendError(sender, "You have no previous location to return to.");
            return;
        }

        Location back = TeleportManager.getLastLocation(sender);

        if (back == null) {
            sendError(sender, "Your last location is no longer available.");
            return;
        }

        sender.teleportAsync(back, PlayerTeleportEvent.TeleportCause.COMMAND)
                .thenRun(() -> {

                    Component msg = Component.text("You teleported ", Colors.GRAY)
                            .append(Component.text("back ", Colors.HOT_PINK))
                            .append(Component.text("to your ", Colors.GRAY))
                            .append(Component.text("last ", Colors.HOT_PINK))
                            .append(Component.text("location.", Colors.GRAY));

                    sender.sendMessage(msg);
                    sender.sendActionBar(msg);
                    sender.playSound(sender.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);

                });
    }

    @Execute
    public void executeOthers(
            @Sender Player sender,
            @Arg OnlinePlayerExceptMeArgument name
    ) {

        PlayerData targetData = name.get();
        Player target = targetData.getPlayer();

        if (target == null) {
            sendError(sender, "That user does not exist or is offline!");
            return;
        }

        if (!TeleportManager.hasLastLocation(target)) {
            sendError(sender, "That player has no previous location.");
            return;
        }

        Location back = TeleportManager.getLastLocation(target);

        if (back == null) {
            sendError(sender, "That player's last location is unavailable.");
            return;
        }

        targetData.run(() -> {

            target.teleportAsync(back, PlayerTeleportEvent.TeleportCause.COMMAND)
                    .thenRun(() -> {

                        Component senderMsg = Component.text("You teleported ", Colors.GRAY)
                                .append(Component.text(target.getName(), Colors.HOT_PINK))
                                .append(Component.text(" back to their last location.", Colors.GRAY));

                        Component targetMsg = Component.text("You were ", Colors.GRAY)
                                .append(Component.text("teleported ", Colors.GRAY))
                                .append(Component.text("back ", Colors.HOT_PINK))
                                .append(Component.text("to your last location.", Colors.GRAY));

                        sender.sendMessage(senderMsg);
                        sender.sendActionBar(senderMsg);

                        target.sendMessage(targetMsg);
                        target.sendActionBar(targetMsg);
                        target.playSound(target.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);

                    });

        });
    }

    private void sendError(Player player, String message) {
        TextComponent msg = Component.text(message, Colors.DARK_PINK);
        player.sendMessage(msg);
        player.sendActionBar(msg);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
    }
}