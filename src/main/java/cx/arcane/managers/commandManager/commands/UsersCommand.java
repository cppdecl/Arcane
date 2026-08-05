package cx.arcane.managers.commandManager.commands;

import cx.arcane.managers.playerManager.PlayerSession;
import cx.arcane.utils.Colors;
import cx.arcane.utils.NetUtils;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Sender;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;

@Command(name = "sessions")
@Permission("arcane.rank.management")
public class UsersCommand {

    @Execute
    public void execute(@Sender Player p) {
        Collection<PlayerSession> sessions = NetUtils.getSessions();
        p.sendMessage(Component.text("Active Users (" + sessions.size() + "):", Colors.HOT_PINK));
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