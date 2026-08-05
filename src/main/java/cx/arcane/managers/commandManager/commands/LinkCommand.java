package cx.arcane.managers.commandManager.commands;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class LinkCommand {

    public static LiteralCommandNode<CommandSourceStack> build(
            String cmd,
            Component message,
            Sound sound
    ) {
        return Commands.literal(cmd)
                .executes(ctx -> handle(ctx, message, sound))
                .build();
    }

    private static int handle(
            CommandContext<CommandSourceStack> ctx,
            Component message,
            Sound sound
    ) {
        if (!(ctx.getSource().getExecutor() instanceof Player p)) return 0;

        p.sendMessage(message);
        p.playSound(p.getLocation(), sound, 1f, 1f);
        return 1;
    }
}
