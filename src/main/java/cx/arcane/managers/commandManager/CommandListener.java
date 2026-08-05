package cx.arcane.managers.commandManager;

import com.destroystokyo.paper.brigadier.BukkitBrigadierCommandSource;
import cx.arcane.utils.Log;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.PluginCommandYamlParser;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.help.HelpTopic;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.bukkit.Bukkit.getServer;

public class CommandListener implements Listener {

    private record Rank(String permission, Set<String> allowedPlugins) {}

    private static final List<Rank> RANKS = List.of(
            new Rank("arcane.rank.management", Set.of("*")),
            new Rank("arcane.rank.vip",        Set.of()),        // no extra plugins
            new Rank("arcane.rank.default",    Set.of("acx"))
    );

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {

        String message = e.getMessage();

        String noSlash = message.startsWith("/") ? message.substring(1) : message;

        int space = noSlash.indexOf(' ');
        String rawCommand = space == -1 ? noSlash : noSlash.substring(0, space);
        String args       = space == -1 ? "" : noSlash.substring(space);

        String normalized = rawCommand.toLowerCase(Locale.ROOT);

        e.setMessage("/" + normalized + args);
    }
}