package cx.arcane.managers.commandManager.commands;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import cx.arcane.Arcane;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.utils.Colors;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.concurrent.CompletableFuture;

@NullMarked
public class ScaleCommand {

    private static final double MIN_SCALE = 0.06;
    private static final double MAX_SCALE = 16.0;
    private static final double DEFAULT_SCALE = 1.0;

    // =========================================================

    public static LiteralCommandNode<CommandSourceStack> build(String alias) {
        return Commands.literal(alias)
                .requires(ScaleCommand::selfPerm)

                // /scale
                .executes(ScaleCommand::resetSelf)

                // /scale <value>
                .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                        .executes(ScaleCommand::scaleSelf)
                )

                // /scale <name>
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(ScaleCommand::suggestPlayers)
                        .requires(ScaleCommand::otherPerm)
                        .executes(ScaleCommand::resetOther)

                        // /scale <name> <value>
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                .executes(ScaleCommand::scaleOther)
                        )
                )
                .build();
    }

    // ---------------- Suggestions ---------------- //

    private static CompletableFuture<Suggestions> suggestPlayers(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String typed = builder.getRemaining().toLowerCase();
        for (PlayerData pData : cx.arcane.managers.playerManager.PlayerManager.getAll()) {
            if (pData.getUsername().toLowerCase().startsWith(typed)) {
                builder.suggest(pData.getUsername());
            }
        }
        return builder.buildFuture();
    }

    // ---------------- Permissions ---------------- //

    private static boolean selfPerm(CommandSourceStack stack) {
        return stack.getExecutor() instanceof Player p &&
                (p.hasPermission("arcane.command.scale") || p.isOp());
    }

    private static boolean otherPerm(CommandSourceStack stack) {
        return stack.getExecutor() instanceof Player p &&
                (p.hasPermission("arcane.command.scale.others") || p.isOp());
    }

    // ---------------- Execution ---------------- //

    private static int resetSelf(CommandContext<CommandSourceStack> ctx) {
        Player p = (Player) ctx.getSource().getExecutor();
        assert p != null;
        applyScale(p, DEFAULT_SCALE);

        send(ctx,
                Component.text("Scale reset ", NamedTextColor.GRAY)
                        .append(Component.text("(x1)", Colors.HOT_PINK))
        );
        return 1;
    }

    private static int scaleSelf(CommandContext<CommandSourceStack> ctx) {
        Player p = (Player) ctx.getSource().getExecutor();
        double scale = DoubleArgumentType.getDouble(ctx, "value");

        applyScale(p, scale);

        send(ctx,
                Component.text("Scale set ", NamedTextColor.GRAY)
                        .append(Component.text("(x" + scale + ")", Colors.HOT_PINK))
        );
        return 1;
    }

    private static int resetOther(CommandContext<CommandSourceStack> ctx) {
        Player target = getTarget(ctx);

        applyScale(target, DEFAULT_SCALE);

        send(ctx,
                Component.text("Reset scale of ", NamedTextColor.GRAY)
                        .append(Component.text(target.getName(), Colors.HOT_PINK))
        );
        return 1;
    }

    private static int scaleOther(CommandContext<CommandSourceStack> ctx) {
        Player target = getTarget(ctx);

        double scale = DoubleArgumentType.getDouble(ctx, "value");
        applyScale(target, scale);

        send(ctx,
                Component.text("Scaled ", NamedTextColor.GRAY)
                        .append(Component.text(target.getName(), Colors.HOT_PINK))
                        .append(Component.text(" to ", NamedTextColor.GRAY))
                        .append(Component.text("x" + scale, Colors.HOT_PINK))
        );
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

    // ---------------- Scaling Logic ---------------- //

    private static void applyScale(Player player, double scale) {
        player.getScheduler().run(Arcane.getPlugin(), t -> {

            AttributeInstance scaleAttr = player.getAttribute(Attribute.SCALE);

            if (scaleAttr != null) {
                scaleAttr.setBaseValue(scale);
            }

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
        Component msg = Component.text(main, Colors.HOT_PINK)
                .append(Component.text(": " + detail, NamedTextColor.GRAY));

        ctx.getSource().getExecutor().sendMessage(msg);

        if (ctx.getSource().getExecutor() instanceof Player p) {
            p.sendActionBar(msg);
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
        }
    }
}
