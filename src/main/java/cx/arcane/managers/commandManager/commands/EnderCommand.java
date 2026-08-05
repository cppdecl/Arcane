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
import org.jspecify.annotations.NullMarked;

import java.util.concurrent.CompletableFuture;

@NullMarked
public class EnderCommand {
    public static LiteralCommandNode<CommandSourceStack> build(String alias) {
        return Commands.literal(alias)
                .requires(EnderCommand::balanceRequirements)
                .executes(EnderCommand::handle)
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(EnderCommand::suggestPlayers)
                        .requires(EnderCommand::balanceOthersRequirements)
                        .executes(EnderCommand::handleOthers))
                .build();
    }

    private static CompletableFuture<Suggestions> suggestPlayers(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String typed = builder.getRemaining().toLowerCase();

        for (PlayerData pData : PlayerManager.getAll()) {
            if (pData.getUsername().toLowerCase().startsWith(typed)) {
                builder.suggest(pData.getUsername());
            }
        }
        return builder.buildFuture();
    }

    private static boolean balanceRequirements(CommandSourceStack stack) {
        if (!(stack.getExecutor() instanceof Player player)) return false;
        return player.hasPermission("arcane.rank.vip") || player.isOp();
    }

    private static boolean balanceOthersRequirements(CommandSourceStack stack) {
        if (!(stack.getExecutor() instanceof Player player)) return false;
        return player.hasPermission("arcane.rank.management") || player.isOp();
    }

    // ---------------- Command Handlers ---------------- //

    private static int handle(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player p)) return 0;

        // Open the executor's Ender Chest
        p.openInventory(p.getEnderChest());

        // Feedback
        TextComponent msg = Component.text("Opened your Ender Chest.", Colors.HOT_PINK);
        send(ctx, msg);
        p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1, 1);

        return 1;
    }

    private static int handleOthers(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player executor)) return 0;

        String nameQuery = StringArgumentType.getString(ctx, "name");
        PlayerData targetData = PlayerManager.getByName(nameQuery);

        if (targetData == null) {
            sendError(ctx, "That player does not exist!");
            return 0;
        }

        Player targetPlayer = Bukkit.getPlayer(targetData.getUniqueId()); // Make sure PlayerData has a reference to the online Player
        if (targetPlayer == null) {
            sendError(ctx, "That player is not online!");
            return 0;
        }

        // Open target's Ender Chest
        executor.openInventory(targetPlayer.getEnderChest());

        // Feedback
        TextComponent msg = Component.text("Opened " + targetPlayer.getName() + "'s Ender Chest.", Colors.HOT_PINK);
        send(ctx, msg);
        executor.playSound(executor.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1, 1);

        return 1;
    }

    // ---------------- Utility ---------------- //

    private static void send(CommandContext<CommandSourceStack> ctx, Component msg) {
        ctx.getSource().getExecutor().sendMessage(msg);
        if (ctx.getSource().getExecutor() instanceof Player p) {
            p.sendActionBar(msg);
        }
    }

    private static void sendError(CommandContext<CommandSourceStack> ctx, String message) {
        TextComponent res = Component.text(message, TextColor.color(0xff0000));
        ctx.getSource().getExecutor().sendMessage(res);
        if (ctx.getSource().getExecutor() instanceof Player p) {
            p.sendActionBar(res);
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
        }
    }
}
