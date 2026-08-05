package cx.arcane.managers.commandManager.commands;

import cx.arcane.managers.commandManager.arguments.currencyArgument.Currency;
import cx.arcane.managers.commandManager.arguments.playerArgument.PlayerArgument;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.utils.Colors;
import cx.arcane.utils.Text;
import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Sender;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

@Command(name = "money")
@Permission("arcane.rank.management")
public class EcoCommand {

    @Execute(name = "set")
    public void executeMoneySet(@Sender Player sender, @Arg PlayerArgument player, @Arg String amount) {
        executeSet(sender, player, amount);
    }

    @Execute(name = "give")
    public void executeMoneyGive(@Sender Player sender, @Arg PlayerArgument player, @Arg String amount) {
        executeGive(sender, player, amount);
    }

    @Execute(name = "take")
    public void executeMoneyTake(@Sender Player sender, @Arg PlayerArgument player, @Arg String amount) {
        executeTake(sender, player, amount);
    }

    private void executeGive(Player sender, PlayerArgument arg, String amount) {

        PlayerData data = arg.get();

        long value;
        try {
            value = Text.parseAmountString(amount);
        } catch (Exception e) {
            sendError(sender, "That amount is invalid.");
            return;
        }

        if (!Text.isValidAmount(value)) {
            sendError(sender, "That amount is invalid.");
            return;
        }

        data.giveMoney(value);

        Component msg = Component.text().append(
                Component.text("Gave ", Colors.GRAY),
                Component.text("$" + Text.formatShortBalance(value), Colors.HOT_PINK),
                Component.text(" to ", Colors.GRAY),
                Component.text(data.getUsername(), Colors.HOT_PINK)
        ).build();

        send(sender, msg);

    }

    private void executeTake(Player sender, PlayerArgument arg, String amount) {

        PlayerData data = arg.get();

        long value;
        try {
            value = Text.parseAmountString(amount);
        } catch (Exception e) {
            sendError(sender, "That amount is invalid.");
            return;
        }

        if (!Text.isValidAmount(value)) {
            sendError(sender, "That amount is invalid.");
            return;
        }

        data.takeMoney(value);

        Component msg = Component.text().append(
                Component.text("Took ", Colors.GRAY),
                Component.text("$" + Text.formatShortBalance(value), Colors.HOT_PINK),
                Component.text(" from ", Colors.GRAY),
                Component.text(data.getUsername(), Colors.HOT_PINK)
        ).build();

        send(sender, msg);

    }

    private void executeSet(Player sender, PlayerArgument arg, String amount) {

        PlayerData data = arg.get();

        long value;
        try {
            value = Text.parseAmountString(amount);
        } catch (Exception e) {
            sendError(sender, "That amount is invalid.");
            return;
        }

        if (!Text.isValidAmount(value)) {
            sendError(sender, "That amount is invalid.");
            return;
        }

        data.setMoney(value);

        Component msg = Component.text().append(
                Component.text("Set ", Colors.GRAY),
                Component.text(data.getUsername() + "'s ", Colors.HOT_PINK),
                Component.text("money to ", Colors.GRAY),
                Component.text("$" + Text.formatShortBalance(value), Colors.HOT_PINK)
        ).build();

        send(sender, msg);

    }

    private void send(Player p, Component msg) {
        p.sendMessage(msg);
        p.sendActionBar(msg);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
    }

    private void sendError(Player p, String message) {
        Component msg = Component.text(message, Colors.DARK_PINK);
        p.sendMessage(msg);
        p.sendActionBar(msg);
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
    }
}