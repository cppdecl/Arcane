package cx.arcane.managers.commandManager.commands;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import cx.arcane.Arcane;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.utils.Colors;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.concurrent.CompletableFuture;

@NullMarked
public class FlySpeedCommand {

    private static final double DEFAULT_SPEED = 1.0;

    // =========================================================

    public static LiteralCommandNode<CommandSourceStack> build(String alias) {
        return Commands.literal(alias)
                .requires(FlySpeedCommand::selfPerm)

                // /flyspeed
                .executes(FlySpeedCommand::resetSelf)

                // /flyspeed <value>
                .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                        .executes(FlySpeedCommand::setSelf)
                )

                // /flyspeed <name>
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(FlySpeedCommand::suggestPlayers)
                        .requires(FlySpeedCommand::otherPerm)
                        .executes(FlySpeedCommand::resetOther)

                        // /flyspeed <name> <value>
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                .executes(FlySpeedCommand::setOther)
                        )
                )
                .build();
    }

    // ---------------- Suggestions ---------------- //

    private static CompletableFuture<Suggestions> suggestPlayers(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String typed = builder.getRemaining().toLowerCase();
        for (PlayerData pData : PlayerManager.getAll()) {
            if (pData.getUsername().toLowerCase().startsWith(typed)) {
                builder.suggest(pData.getUsername());
            }
        }
        return builder.buildFuture();
    }

    // ---------------- Permissions ---------------- //

    private static boolean selfPerm(CommandSourceStack stack) {
        return stack.getExecutor() instanceof Player p &&
                (p.hasPermission("arcane.rank.management"));
    }

    private static boolean otherPerm(CommandSourceStack stack) {
        return stack.getExecutor() instanceof Player p &&
                (p.hasPermission("arcane.rank.management"));
    }

    // ---------------- Execution ---------------- //

    private static int resetSelf(CommandContext<CommandSourceStack> ctx) {
        Player p = (Player) ctx.getSource().getExecutor();
        applySpeed(p, DEFAULT_SPEED);
        send(ctx, Component.text("Fly speed reset ", NamedTextColor.GRAY)
                .append(Component.text("(x1)", Colors.HOT_PINK)));

        return 1;
    }

    private static int setSelf(CommandContext<CommandSourceStack> ctx) {
        Player p = (Player) ctx.getSource().getExecutor();
        double value = DoubleArgumentType.getDouble(ctx, "value");

        applySpeed(p, value);

        send(ctx, Component.text("Fly speed set ", NamedTextColor.GRAY)
                .append(Component.text("x" + value, Colors.HOT_PINK)));
        return 1;
    }

    private static int resetOther(CommandContext<CommandSourceStack> ctx) {
        Player target = getTarget(ctx);
        if (target == null) return 0;

        applySpeed(target, DEFAULT_SPEED);

        send(ctx, Component.text("Reset fly speed of ", NamedTextColor.GRAY)
                .append(Component.text(target.getName(), Colors.HOT_PINK)));
        return 1;
    }

    private static int setOther(CommandContext<CommandSourceStack> ctx) {
        Player target = getTarget(ctx);
        if (target == null) return 0;

        double value = DoubleArgumentType.getDouble(ctx, "value");
        applySpeed(target, value);

        send(ctx, Component.text("Set fly speed of ", NamedTextColor.GRAY)
                .append(Component.text(target.getName(), Colors.HOT_PINK))
                .append(Component.text(" to ", NamedTextColor.GRAY))
                .append(Component.text("x" + value, Colors.HOT_PINK)));
        return 1;
    }

    private static Player getTarget(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        Player p = Bukkit.getPlayerExact(name);

        if (p == null) {
            sendError(ctx, "Player not found", name);
            return null;
        }
        return p;
    }

    // ---------------- Speed Logic ---------------- //

    private static void applySpeed(Player player, double value) {
        player.getScheduler().run(Arcane.getPlugin(), t -> {
            float actualSpeed = (float) (0.2F * value); // walkspeed 0.1F
            player.setFlySpeed(Math.clamp(actualSpeed, 0, 1));
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 1.1f);
        }, null);
    }

    // ---------------- Messaging ---------------- //

    private static void send(CommandContext<CommandSourceStack> ctx, Component msg) {
        ctx.getSource().getExecutor().sendMessage(msg);
        if (ctx.getSource().getExecutor() instanceof Player p) {
            p.sendActionBar(msg);
        }
    }

    private static void sendError(CommandContext<CommandSourceStack> ctx, String main, String detail) {
        Component msg = Component.text(main, NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(": " + detail, NamedTextColor.GRAY));

        ctx.getSource().getExecutor().sendMessage(msg);

        if (ctx.getSource().getExecutor() instanceof Player p) {
            p.sendActionBar(msg);
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
        }
    }
}
