package cx.arcane.managers.commandManager.commands;

import cx.arcane.utils.Colors;
import cx.arcane.utils.LocationUtils;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Sender;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;

@Command(name = "setauthspawn")
@Permission("arcane.rank.management")
public class SetAuthSpawnCommand {

    @Execute
    public void execute(@Sender Player player) {

        Location loc = LocationUtils.savePoint(player.getLocation(), "authspawn");

        player.sendMessage(Component.text("Auth spawn point has been set!", Colors.HOT_PINK));
        player.sendMessage(Component.text("Location: ", Colors.GRAY)
                .append(Component.text(loc.getX(), Colors.LIGHT_PINK))
                .append(Component.text(", ", Colors.GRAY))
                .append(Component.text(loc.getY(), Colors.LIGHT_PINK))
                .append(Component.text(", ", Colors.GRAY))
                .append(Component.text(loc.getZ(), Colors.LIGHT_PINK))
                .append(Component.text(" ("))
                .append(Component.text(loc.getWorld().getName(), Colors.LIGHT_PINK))
                .append(Component.text(")", Colors.GRAY)));
    }
}