package cx.arcane.managers.commandManager.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.concurrent.CompletableFuture;

@NullMarked
public class BoomCommand {

    public static LiteralCommandNode<CommandSourceStack> build(String alias) {
        return Commands.literal(alias)
                .requires(BoomCommand::requirements)
                .executes(ctx -> handleSelf(ctx, 5f))
                .then(Commands.argument("yield", IntegerArgumentType.integer(1, 500))
                        .executes(ctx -> handleSelf(ctx,
                                mapYield(IntegerArgumentType.getInteger(ctx, "yield")))))
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests(BoomCommand::suggestPlayers)
                        .executes(ctx -> handleOther(ctx, 5f))
                        .then(Commands.argument("yield", IntegerArgumentType.integer(1, 500))
                                .executes(ctx -> handleOther(ctx,
                                        mapYield(IntegerArgumentType.getInteger(ctx, "yield"))))))
                .build();
    }

    private static boolean requirements(CommandSourceStack stack) {
        if (!(stack.getExecutor() instanceof Player player)) return false;
        return player.hasPermission("arcane.rank.management");
    }

    private static CompletableFuture<Suggestions> suggestPlayers(
            CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder
    ) {
        for (PlayerData data : PlayerManager.getOnline()) {
            Player player = Bukkit.getPlayer(data.getUniqueId());
            if (player != null && player.isOnline()) {
                builder.suggest(data.getUsername());
            }
        }
        return builder.buildFuture();
    }

    private static int handleSelf(CommandContext<CommandSourceStack> ctx, float yield) {
        if (!(ctx.getSource().getExecutor() instanceof Player p)) return 0;
        explode(p, yield);
        return 1;
    }

    private static int handleOther(CommandContext<CommandSourceStack> ctx, float yield) {
        String name = StringArgumentType.getString(ctx, "target");
        PlayerData targetData = PlayerManager.getByName(name);

        if (targetData == null) {
            sendError(ctx, "That player does not exist!");
            return 0;
        }

        Player target = Bukkit.getPlayer(targetData.getUniqueId());
        if (target == null || !target.isOnline()) {
            sendError(ctx, "That player is not online!");
            return 0;
        }

        explode(target, yield);
        return 1;
    }

    private static void explode(Player p, float yield) {
        Location loc = p.getLocation();
        p.getScheduler().run(cx.arcane.Arcane.getPlugin(), scheduledTask -> {
            p.getWorld().createExplosion(p, loc, yield, true, true, false);
            p.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
        }, null);
    }

    private static float mapYield(int level) {
        return switch (level) {
            case 1 -> 4f;
            case 2 -> 5f;
            default -> Math.min(2f + level * 2f, 500f);
        };
    }

    private static void sendError(CommandContext<CommandSourceStack> ctx, String message) {
        if (ctx.getSource().getExecutor() instanceof Player p) {
            p.sendMessage(Component.text(message, NamedTextColor.DARK_RED));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
        }
    }
}