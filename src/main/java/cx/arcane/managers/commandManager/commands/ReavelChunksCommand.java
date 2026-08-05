package cx.arcane.managers.commandManager.commands;

import cx.arcane.managers.antiXrayManager.AntiXrayManager;
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

@Command(name = "rchunks", aliases = {"rc"})
@Permission("arcane.rank.management")
public class ReavelChunksCommand {

    @Execute
    public void execute(@Sender Player sender) {
        UUID uuid = sender.getUniqueId();
        long balance = EcoManager.getMoney(uuid);

        TextComponent msg = Component.text("Revealing chunks...", Colors.HOT_PINK);
        sender.sendActionBar(msg);
        sender.playSound(sender.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
        AntiXrayManager.revealConnectedAirChunksAsyncManually(sender);
    }
}