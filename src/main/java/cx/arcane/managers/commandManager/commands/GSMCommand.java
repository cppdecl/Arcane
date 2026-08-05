package cx.arcane.managers.commandManager.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import cx.arcane.utils.Colors;
import cx.arcane.utils.Log;
import cx.arcane.utils.Text;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class GSMCommand {

    public static LiteralCommandNode<CommandSourceStack> build(String alias) {
        return Commands.literal(alias)
                .requires(stack -> {
                    CommandSender sender = stack.getSender();
                    return !(sender instanceof Player) || sender.hasPermission("arcane.rank.management");
                })
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> handle(ctx, StringArgumentType.getString(ctx, "message"))))
                .build();
    }

    private static int handle(CommandContext<CommandSourceStack> ctx, String message) {
        if (message.isBlank()) {
            sendError(ctx, "Broadcast message cannot be empty.");
            return 0;
        }

        Component msgComponent = Text.stringToComponent(message);

        Title title = Title.title(Text.toSmallCapsComponent("Announcement").color(Colors.RED), msgComponent, 5, 90, 5);

        // Send to all online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(Component.text("Announcement", Colors.RED).append(Component.text( " -> ", NamedTextColor.DARK_GRAY).append(msgComponent.color(NamedTextColor.WHITE))));
            p.showTitle(title);
        }

        Log.info("[GSM] {}", PlainTextComponentSerializer.plainText().serialize(msgComponent));

        send(ctx, Component.text("Broadcast sent to ", NamedTextColor.GRAY).append(Component.text(Bukkit.getOnlinePlayers().size(), NamedTextColor.LIGHT_PURPLE).append(Component.text( " players online.", NamedTextColor.GRAY))));

        return 1;
    }

    private static void send(CommandContext<CommandSourceStack> ctx, Component msg) {
        if (ctx.getSource().getSender() instanceof Player p) {
            p.sendActionBar(msg);
        }
    }

    private static void sendError(CommandContext<CommandSourceStack> ctx, String message) {
        TextComponent res = Component.text(message, TextColor.color(0xff0000));
        ctx.getSource().getSender().sendMessage(res);
        if (ctx.getSource().getSender() instanceof Player p) {
            p.sendActionBar(res);
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
        }
    }
}
