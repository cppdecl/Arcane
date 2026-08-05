package cx.arcane.managers.commandManager.commands;

import cx.arcane.managers.commandManager.arguments.playerArgument.PlayerArgument;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
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

@Command(name = "pay")
public class PayCommand {

    @Execute
    public void execute(@Sender Player pSender, @Arg PlayerArgument targetArg, @Arg String amountArg) {

        PlayerData pTargetData = targetArg.get();
        UUID targetUUID = pTargetData.getUniqueId();

        PlayerData pSenderData = PlayerManager.getByUniqueId(pSender.getUniqueId());
        UUID senderUUID = pSender.getUniqueId();

        if (senderUUID.equals(targetUUID)) {
            sendError(pSender, "You can't pay yourself.");
            return;
        }

        if (!pTargetData.getSettings().isAllowPayments()) {
            sendError(pSender, "That player does not receive payments.");
            return;
        }

        long amount;
        try {
            amount = Text.parseAmountString(amountArg);
        } catch (Exception e) {
            sendError(pSender, "That amount is invalid.");
            return;
        }

        if (!Text.isValidAmount(amountArg)) {
            sendError(pSender, "That amount is invalid.");
            return;
        }

        if (!pSenderData.transferMoney(pTargetData.getUniqueId(), amount)) {
            sendError(pSender, "You don't have enough money!");
            return;
        }

        String formatted = "$" + Text.formatShortBalance(amount);

        TextComponent senderMsg = Component.text("You paid ", Colors.GRAY)
                .append(Component.text(formatted, Colors.HOT_PINK))
                .append(Component.text(" to ", Colors.GRAY))
                .append(Component.text(pTargetData.getUsername(), Colors.HOT_PINK));

        pSender.sendMessage(senderMsg);
        pSender.sendActionBar(senderMsg);
        pSender.playSound(pSender.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);

        pTargetData.run(() -> {
            if (!pTargetData.getSettings().isShowSystemMessages()) return;

            TextComponent targetMsg = Component.text("You received ", Colors.GRAY)
                    .append(Component.text(formatted, Colors.HOT_PINK))
                    .append(Component.text(" from ", Colors.GRAY))
                    .append(Component.text(pSender.getName(), Colors.HOT_PINK));
            pTargetData.getPlayer().sendMessage(targetMsg);
            pTargetData.getPlayer().sendActionBar(targetMsg);
            pTargetData.getPlayer().playSound(pTargetData.getPlayer().getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
        });
    }

    private void sendError(Player player, String message) {
        TextComponent msg = Component.text(message, Colors.DARK_PINK);
        player.sendMessage(msg);
        player.sendActionBar(msg);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
    }
}