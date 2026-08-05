package cx.arcane.managers.commandManager.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import cx.arcane.managers.msgManager.MsgManager;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.utils.Colors;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.concurrent.CompletableFuture;

@SuppressWarnings("UnstableApiUsage")
@NullMarked
public class MsgCommand {

    public static LiteralCommandNode<CommandSourceStack> build(String alias) {
        return Commands.literal(alias)
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(MsgCommand::suggestPlayers)
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(MsgCommand::execute)))
                .build();
    }

    private static CompletableFuture<Suggestions> suggestPlayers(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        if (!(ctx.getSource().getExecutor() instanceof Player sender)) return builder.buildFuture();
        String typed = builder.getRemaining().toLowerCase();
        for (PlayerData pData : PlayerManager.getOnline()) {
            if (!PlayerManager.canSee(sender, pData.getPlayer()))
                continue;
            if (pData.getUsername().equals(sender.getName())) continue;
            if (pData.getUsername().toLowerCase().startsWith(typed)) builder.suggest(pData.getUsername());
        }
        return builder.buildFuture();
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player sender)) return 0;

        String targetName = StringArgumentType.getString(ctx, "player");
        String message = StringArgumentType.getString(ctx, "message");

        PlayerData targetData = PlayerManager.getByNameIgnoreCase(targetName);
        if (targetData == null) return err(sender, "That player is offline or doesn't exist.");
        if (!targetData.getSettings().isShowPrivateMessages()) return err(sender, "That player has private messages disabled.");

        Player target = targetData.getPlayer();

        if (!PlayerManager.canSee(sender, target)) {
            return err(sender, "That player is offline or doesn't exist.");
        }

        TextComponent toTarget = Component.text(sender.getName(), Colors.HOT_PINK)
                .append(Component.text(" -> ", Colors.DARK_GRAY))
                .append(Component.text("YOU: ", Colors.HOT_PINK))
                .append(Component.text(message, Colors.WHITE));

        TextComponent toSender = Component.text("YOU", Colors.HOT_PINK)
                .append(Component.text(" -> ", Colors.DARK_GRAY))
                .append(Component.text(targetData.getUsername() + ": ", Colors.HOT_PINK))
                .append(Component.text(message, Colors.WHITE));

        targetData.run(() -> {
            target.sendMessage(toTarget);
            playDmSound(target);
        });

        sender.sendMessage(toSender);
        playDmSound(sender);

        MsgManager.recordMessage(sender, target);
        return 1;
    }

    private static void playDmSound(Player p) {
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 2f);
    }

    private static int err(Player p, String message) {
        TextComponent msg = Component.text(message, Colors.DARK_PINK);
        p.sendMessage(msg);
        p.sendActionBar(msg);
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        return 0;
    }
}