package cx.arcane.managers.commandManager.commands;

import cx.arcane.managers.commandManager.arguments.playerArgument.PlayerArgument;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.updateManager.UpdateManager;
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

@Command(name = "update")
@Permission("arcane.rank.management")
public class UpdateCommand {

    @Execute
    public void execute(@Sender Player sender) {
        UpdateManager.update(sender);
    }
}