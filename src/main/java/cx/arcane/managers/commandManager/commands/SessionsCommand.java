package cx.arcane.managers.commandManager.commands;

import cx.arcane.managers.playerManager.PlayerSession;
import cx.arcane.managers.teleportManager.TeleportManager;
import cx.arcane.utils.Colors;
import cx.arcane.utils.LocationUtils;
import cx.arcane.utils.NetUtils;
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
import java.time.Instant;
import java.util.Collection;

@Command(name = "sessions")
@Permission("arcane.rank.management")
public class SessionsCommand {

    @Execute
    public void execute(@Sender Player p) {
        Collection<PlayerSession> sessions = NetUtils.getSessions();
        p.sendMessage(Component.text("Active Sessions (" + sessions.size() + "):", Colors.HOT_PINK));
        for (PlayerSession session : sessions) {
            int durationSeconds = (int) Duration.between(session.getStartedAt(), Instant.now()).toSeconds();
            String netAddress = session.getAddress().toString();
            String username = session.getUsername();
            p.sendMessage(Component.text().append(
                    Component.text(" - ", Colors.DARK_PINK),
                    Component.text(username, Colors.HOT_PINK),
                    Component.text(" [", Colors.DARK_PINK),
                    Component.text(netAddress, Colors.LIGHT_PINK),
                    Component.text("] (", Colors.DARK_PINK),
                    Component.text(durationSeconds + "s", Colors.LIGHT_PINK),
                    Component.text(")", Colors.DARK_PINK)
            ).build());
        }
    }
}