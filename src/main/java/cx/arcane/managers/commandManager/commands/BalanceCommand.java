package cx.arcane.managers.commandManager.commands;

import cx.arcane.managers.commandManager.arguments.playerArgument.PlayerArgument;
import cx.arcane.managers.ecoManager.EcoManager;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.utils.Colors;
import cx.arcane.utils.Text;
import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Sender;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.UUID;

@Command(name = "balance", aliases = {"bal"})
public class BalanceCommand {

    @Execute
    public void execute(@Sender Player sender) {
        UUID uuid = sender.getUniqueId();
        long balance = EcoManager.getMoney(uuid);

        TextComponent msg = Component.text("You have ", Colors.GRAY)
                .append(Component.text("$" + Text.formatShortBalance(balance), Colors.HOT_PINK));

        sender.sendMessage(msg);
        sender.sendActionBar(msg);
        sender.playSound(sender.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
    }

    @Execute
    public void executeOthers(@Sender Player pSender, @Arg PlayerArgument name) {

        PlayerData pTargetData = name.get();

        long balance = EcoManager.getMoney(pTargetData.getUniqueId());

        TextComponent msg = Component.text(pTargetData.getUsername(), Colors.HOT_PINK)
                .append(Component.text(" has ", Colors.GRAY))
                .append(Component.text("$" + Text.formatShortBalance(balance), Colors.HOT_PINK));

        pSender.sendMessage(msg);
        pSender.sendActionBar(msg);
        pSender.playSound(pSender.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
    }

    private void sendError(Player p, String message) {
        TextComponent msg = Component.text(message, Colors.DARK_PINK);
        p.sendMessage(msg);
        p.sendActionBar(msg);
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
    }
}