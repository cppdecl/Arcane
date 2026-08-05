package cx.arcane.managers.commandManager.commands;

import cx.arcane.Arcane;
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

@Command(name = "disconnect")
@Permission("arcane.rank.management")
public class DisconnectCommand {

    @Execute
    public void executeOthers(@Sender Player pSender, @Arg PlayerArgument name) {
        PlayerData pTargetData = name.get();
        pTargetData.getPlayer().getScheduler().run(Arcane.getPlugin(), t -> {
            pTargetData.getPlayer().kick( Component.text(Text.toSmallCaps("connection timed out"), Colors.HOT_PINK));
        }, null);

        pSender.sendMessage(Component.text().append(
                Component.text("Successfully Force-Disconnected -> ", Colors.GRAY),
                Component.text(pTargetData.getUsername(), Colors.HOT_PINK)
        ).build());
    }
}