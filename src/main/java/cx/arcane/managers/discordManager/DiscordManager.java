package cx.arcane.managers.discordManager;

import cx.arcane.Arcane;
import cx.arcane.managers.discordManager.commands.PingCommand;
import cx.arcane.utils.Log;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.bukkit.Bukkit;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DiscordManager {
    private static final String TOKEN = "";

    static final String PREFIX = "!";
    static final Map<String, CommandBase> commands = new HashMap<>();

    public enum StatusMode {
        PLAYER_COUNT,
        MAINTENANCE
    }

    private static JDA jdaClient;
    private static ScheduledTask updateStatusTask;
    private static StatusMode currentMode = StatusMode.PLAYER_COUNT;

    public static void onEnable() {
        registerCommands();

        try {
            jdaClient = JDABuilder.createLight(TOKEN)
                    .enableIntents(GatewayIntent.getIntents(GatewayIntent.ALL_INTENTS))
                    .setAutoReconnect(true)
                    .addEventListeners(new DiscordListener())
                    .build()
                    .awaitReady();
        } catch (Exception e) {
            Log.error("[DiscordManager] Failed to connect: " + e.getMessage());
            return;
        }

        jdaClient.getPresence().setActivity(Activity.customStatus("Starting..."));

        updateStatusTask = Bukkit.getAsyncScheduler().runAtFixedRate(
                Arcane.getPlugin(),
                task -> {
                    if (jdaClient.getStatus() != JDA.Status.CONNECTED) return;
                    jdaClient.getPresence().setActivity(Activity.customStatus(resolveStatus()));
                },
                10L, 10L, TimeUnit.SECONDS
        );
    }

    private static void registerCommands() {
        for (CommandBase cmd : List.of(
                new PingCommand()
        )) {
            commands.put(cmd.getName().toLowerCase(), cmd);
        }
    }

    public static void onDisable() {
        if (updateStatusTask != null) {
            updateStatusTask.cancel();
            updateStatusTask = null;
        }

        if (jdaClient == null) return;

        if (jdaClient.getStatus() == JDA.Status.CONNECTED)
            jdaClient.getPresence().setActivity(Activity.customStatus("Restarting..."));

        jdaClient.shutdown();
        try {
            if (!jdaClient.awaitShutdown(Duration.ofSeconds(15))) {
                jdaClient.shutdownNow();
                jdaClient.awaitShutdown();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void setStatusMode(StatusMode mode) {
        currentMode = mode;
        if (jdaClient == null || jdaClient.getStatus() != JDA.Status.CONNECTED) return;
        jdaClient.getPresence().setActivity(Activity.customStatus(resolveStatus()));
    }

    public static StatusMode getStatusMode() {
        return currentMode;
    }

    private static String resolveStatus() {
        return switch (currentMode) {
            case MAINTENANCE -> "Maintenance";
            case PLAYER_COUNT -> {
                int count = Bukkit.getOnlinePlayers().size();
                yield count == 0 ? "No Players Online"
                        : count + (count == 1 ? " Player Online" : " Players Online");
            }
        };
    }

    public static JDA getBot() {
        return jdaClient;
    }

    public static void privateMessage(String discordId, String message) {
        jdaClient.retrieveUserById(discordId).queue(user -> {
            user.openPrivateChannel().queue(channel -> {
                channel.sendMessage(message).queue(null, fail ->
                        Log.warn("[DiscordManager] Failed to send DM to " + discordId)
                );
            });
        }, fail -> Log.warn("[DiscordManager] Could not find Discord user: " + discordId));
    }
}