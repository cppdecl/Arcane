package cx.arcane.managers.commandManager.commands;

import cx.arcane.managers.commandManager.arguments.playerArgument.PlayerArgument;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.utils.Colors;
import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Sender;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

@Command(name = "ping")
public class PingCommand {

    @Execute
    public void execute(@Sender Player sender) {

        int ping = sender.getPing();
        TextColor color = getPingColor(ping);

        TextComponent msg = Component.text("Ping ", NamedTextColor.GRAY)
                .append(Component.text(ping + "ms", color));

        sender.sendMessage(msg);
        sender.sendActionBar(msg);
        sender.playSound(sender.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
    }

    @Execute
    @Permission("arcane.rank.management")
    public void executeOthers(
            @Sender Player sender,
            @Arg PlayerArgument targetArg
    ) {

        PlayerData targetData = targetArg.get();
        Player target = targetData.getPlayer();

        if (target == null) {
            sendError(sender, "That user does not exist or is offline!");
            return;
        }

        int ping = target.getPing();
        TextColor color = getPingColor(ping);

        TextComponent msg = Component.text("Ping ", NamedTextColor.GRAY)
                .append(Component.text(ping + "ms", color))
                .append(Component.text(" (" + target.getName() + ")", NamedTextColor.GRAY));

        sender.sendMessage(msg);
        sender.sendActionBar(msg);
        sender.playSound(sender.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
    }

    private TextColor getPingColor(int ping) {
        if (ping < 40) return TextColor.color(0x00ff38);
        if (ping < 120) return TextColor.color(0xffff00);
        if (ping < 300) return TextColor.color(0xffa500);
        return TextColor.color(0xff0000);
    }

    private void sendError(Player player, String message) {
        TextComponent msg = Component.text(message, Colors.DARK_PINK);
        player.sendMessage(msg);
        player.sendActionBar(msg);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
    }
}