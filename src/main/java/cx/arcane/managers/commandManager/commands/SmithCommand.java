package cx.arcane.managers.commandManager.commands;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class SmithCommand {

    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("smith")
                .requires(stack ->
                        stack.getExecutor() instanceof Player player &&
                                (player.hasPermission("arcane.rank.vip") || player.isOp())
                )
                .executes(ctx -> {
                    if (!(ctx.getSource().getExecutor() instanceof Player player))
                        return 0;

                    player.openSmithingTable(player.getLocation(), true);

                    return 1;
                })
                .build();
    }
}