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
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jspecify.annotations.NullMarked;

import java.util.concurrent.CompletableFuture;

@NullMarked
public class WarpToCommand {

    // ================== Command Tree ==================

    public static LiteralCommandNode<CommandSourceStack> build(String alias) {
        return Commands.literal(alias)
                .requires(WarpToCommand::requirements)
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(WarpToCommand::suggestPlayers)
                        .executes(WarpToCommand::handle))
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

    // ================== Handler ==================

    private static int handle(CommandContext<CommandSourceStack> ctx) {
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

        Location targetLocation = pData.getLocation();

        Player target = pData.getPlayer();
        if (target != null) {
            targetLocation = target.getLocation();

            TextComponent senderMsg = Component.text().append(
                    Component.text("You teleported to ", Colors.WHITE),
                    Component.text(pData.getUsername() + "'s ", Colors.HOT_PINK),
                    Component.text("location!", Colors.WHITE)
            ).build();

            sender.teleportAsync(targetLocation, PlayerTeleportEvent.TeleportCause.COMMAND)
                    .thenRun(() -> {
                        sender.sendMessage(senderMsg);
                        sender.sendActionBar(senderMsg);
                        sender.playSound(sender, Sound.BLOCK_BEACON_ACTIVATE, 1, 1);
                    });

        } else {

            TextComponent senderMsg = Component.text().append(
                    Component.text("You teleported to ", Colors.WHITE),
                    Component.text(pData.getUsername() + "'s ", Colors.HOT_PINK),
                    Component.text("location!", Colors.WHITE),
                    Component.text(" (Offline)", Colors.DARK_PINK)
            ).build();

            sender.teleportAsync(targetLocation, PlayerTeleportEvent.TeleportCause.COMMAND)
                    .thenRun(() -> {
                        sender.sendMessage(senderMsg);
                        sender.sendActionBar(senderMsg);
                        sender.playSound(sender, Sound.BLOCK_BEACON_ACTIVATE, 1, 1);
                    });
        }

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
