package cx.arcane.managers.commandManager.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import cx.arcane.managers.msgManager.MsgManager;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.utils.Colors;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.UUID;

@SuppressWarnings("UnstableApiUsage")
@NullMarked
public class ReplyCommand {

    public static LiteralCommandNode<CommandSourceStack> build(String alias) {
        return Commands.literal(alias)
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ReplyCommand::execute))
                .build();
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player sender)) return 0;

        String message = StringArgumentType.getString(ctx, "message");

        UUID targetId = MsgManager.getReplyTarget(sender.getUniqueId());
        if (targetId == null) return err(sender, "You have no one to reply to!");

        PlayerData targetData = PlayerManager.getByUniqueId(targetId);
        if (targetData == null) return err(sender, "That player is no longer online!");

        Player target = targetData.getPlayer();
        if (target == null) return err(sender, "That player is no longer online!");

        if (!PlayerManager.canSee(sender, target)) {
            return err(sender, "That player is no longer online!");
        }

        if (!targetData.getSettings().isShowPrivateMessages()) return err(sender, "That player does not accept private messages.");

        TextComponent toTarget = Component.text(sender.getName(), Colors.HOT_PINK)
                .append(Component.text(" -> ", Colors.DARK_GRAY))
                .append(Component.text("YOU: ", Colors.HOT_PINK))
                .append(Component.text(message, Colors.WHITE));

        TextComponent toSender = Component.text("YOU", Colors.HOT_PINK)
                .append(Component.text(" -> ", Colors.DARK_GRAY))
                .append(Component.text(target.getName() + ": ", Colors.HOT_PINK))
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