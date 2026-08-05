package cx.arcane.managers.commandManager.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.utils.Colors;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jspecify.annotations.NullMarked;

import java.util.concurrent.CompletableFuture;

@NullMarked
public class SummonCommand {

    // ================== Command Tree ==================

    public static LiteralCommandNode<CommandSourceStack> build(String alias) {
        return Commands.literal(alias)
                .requires(SummonCommand::requirements)

                // /summon <player>
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(SummonCommand::suggestPlayers)
                        .executes(SummonCommand::handleSingle))

                // /summon all
                .then(Commands.literal("all")
                        .requires(SummonCommand::requirementsAll)
                        .executes(SummonCommand::handleAll))

                .build();
    }

    // ================== Suggestions ==================

    private static CompletableFuture<Suggestions> suggestPlayers(
            CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder
    ) {
        String typed = builder.getRemaining().toLowerCase();

        for (PlayerData pData : PlayerManager.getAll()) {
            String name = pData.getUsername();
            if (name.toLowerCase().startsWith(typed)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }

    // ================== Permissions ==================

    private static boolean requirements(CommandSourceStack stack) {
        return stack.getExecutor() instanceof Player p
                && (p.hasPermission("arcane.rank.management"));
    }

    private static boolean requirementsAll(CommandSourceStack stack) {
        return stack.getExecutor() instanceof Player p
                && (p.hasPermission("arcane.rank.management"));
    }

    // ================== Handlers ==================

    // ---- /summon <player> ----
    private static int handleSingle(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player sender)) {
            return 0;
        }

        String targetName = StringArgumentType.getString(ctx, "name");
        PlayerData pData = PlayerManager.getByNameIgnoreCase(targetName);

        if (pData == null) {
            sendError(sender, "That user does not exist!");
            return 0;
        }

        if (pData.getUniqueId().equals(sender.getUniqueId())) {
            sendError(sender, "You can't teleport to yourself!");
            return 0;
        }

        Player target = pData.getPlayer();
        if (target != null) {
            TextComponent targetMsg = Component.text(
                    "You have been summoned!",
                    Colors.HOT_PINK
            );

            TextComponent senderMsg = Component.text().append(
                    Component.text("Summoned ", Colors.WHITE),
                    Component.text(pData.getUsername() + "!", Colors.HOT_PINK)
            ).build();

            target.teleportAsync(sender.getLocation(), PlayerTeleportEvent.TeleportCause.COMMAND)
                    .thenRun(() -> {
                        target.sendMessage(targetMsg);
                        target.sendActionBar(targetMsg);
                        target.playSound(target, Sound.BLOCK_BEACON_ACTIVATE, 1, 1);

                        sender.sendMessage(senderMsg);
                        sender.sendActionBar(senderMsg);
                        sender.playSound(sender, Sound.BLOCK_BEACON_ACTIVATE, 1, 1);
                    });

        } else {
            TextComponent senderMsg = Component.text().append(
                    Component.text("Summoned ", Colors.WHITE),
                    Component.text(pData.getUsername() + "!", Colors.HOT_PINK),
                    Component.text(" (Offline)", Colors.DARK_PINK)
            ).build();

            pData.setLocation(sender.getLocation());

            sender.sendMessage(senderMsg);
            sender.sendActionBar(senderMsg);
            sender.playSound(sender, Sound.BLOCK_BEACON_ACTIVATE, 1, 1);
        }

        return 1;
    }

    // ---- /summon all ----
    private static int handleAll(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player sender)) {
            return 0;
        }

        int summoned = 0;

        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(sender)) continue;

            target.teleportAsync(sender.getLocation(), PlayerTeleportEvent.TeleportCause.COMMAND)
                    .thenRun(() -> {
                        TextComponent msg = Component.text(
                                "You have been summoned!",
                                Colors.HOT_PINK
                        );
                        target.sendMessage(msg);
                        target.sendActionBar(msg);
                        target.playSound(target, Sound.BLOCK_BEACON_ACTIVATE, 1, 1);
                    });

            summoned++;
        }

        if (summoned == 0) {
            sendError(sender, "No other players to summon!");
            return 0;
        }

        TextComponent res = Component.text(
                "Summoned everyone!",
                Colors.HOT_PINK
        );
        sender.sendMessage(res);
        sender.sendActionBar(res);
        sender.playSound(sender, Sound.BLOCK_BEACON_ACTIVATE, 1, 1);

        return 1;
    }

    // ================== Utilities ==================

    private static void sendError(Player player, String message) {
        TextComponent res = Component.text(message, Colors.DARK_PINK);
        player.sendMessage(res);
        player.sendActionBar(res);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
    }
}
