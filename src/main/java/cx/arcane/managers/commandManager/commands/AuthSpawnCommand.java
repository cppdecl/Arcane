package cx.arcane.managers.commandManager.commands;

import cx.arcane.managers.teleportManager.TeleportManager;
import cx.arcane.utils.Colors;
import cx.arcane.utils.LocationUtils;
import cx.arcane.utils.Text;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Sender;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.time.Duration;

@Command(name = "authspawn")
@Permission("arcane.rank.management")
public class AuthSpawnCommand {

    @Execute
    public void execute(@Sender Player player) {
        Location loc = LocationUtils.getPoint("authspawn");
        TeleportManager.teleport(player, loc).name("authspawn").onTeleport(() -> {
            Title title = Title.title(Text.toSmallCapsComponent("Authentication").color(Colors.HOT_PINK), Component.text("arcane.cx"), Title.Times.times(Duration.ofMillis(800), Duration.ofMillis(3000), Duration.ofMillis(1000)));
            player.showTitle(title);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.MASTER, 1, 1);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER,1, 1);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER,1, 1);
        }).start();
    }
}