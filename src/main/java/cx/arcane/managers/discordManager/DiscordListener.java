package cx.arcane.managers.discordManager;

import cx.arcane.utils.Log;
import net.dv8tion.jda.api.events.ExceptionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent;
import net.dv8tion.jda.api.events.session.SessionResumeEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class DiscordListener extends ListenerAdapter {
    @Override
    public void onReady(@NotNull ReadyEvent e) {
        Log.info("[Discord] Bot is ready! Logged in as " + e.getJDA().getSelfUser().getAsTag());
    }

    @Override
    public void onSessionDisconnect(@NotNull SessionDisconnectEvent e) {
        Log.warn("[Discord] Session disconnected. Close code: " + e.getCloseCode());
    }

    @Override
    public void onSessionResume(@NotNull SessionResumeEvent e) {
        Log.info("[Discord] Session resumed.");
    }

    @Override
    public void onException(@NotNull ExceptionEvent e) {
        Log.error("[Discord] Exception: " + e.getCause().getMessage());
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent e) {
        if (e.getAuthor().isBot()) return;

        String raw = e.getMessage().getContentRaw();
        if (!raw.startsWith(DiscordManager.PREFIX)) return;

        String[] parts = raw.substring(DiscordManager.PREFIX.length()).trim().split("\\s+");
        if (parts.length == 0 || parts[0].isBlank()) return;

        String cmd = parts[0].toLowerCase();
        List<String> params = Arrays.asList(parts).subList(1, parts.length);

        Log.info("[Discord] Command '" + cmd + "' by " + e.getAuthor().getAsTag() + " | params: " + params);

        CommandBase command = DiscordManager.commands.get(cmd);
        if (command == null) return;

        command.execute(e.getAuthor(), params, e);
    }
}