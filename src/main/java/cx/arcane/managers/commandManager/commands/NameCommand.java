package cx.arcane.managers.commandManager.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import cx.arcane.utils.Colors;
import cx.arcane.utils.Text;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.concurrent.CompletableFuture;

public class NameCommand {

    public static LiteralCommandNode<CommandSourceStack> buildName(String alias) {
        return Commands.literal(alias)
                .requires(src -> src.getExecutor() instanceof Player p && p.hasPermission("arcane.rank.management"))

                // /name -> remove name
                .executes(NameCommand::removeName)

                // /name <name...> -> set name (greedy string)
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(NameCommand::setName)
                )
                .build();
    }

    private static Player sender(CommandContext<CommandSourceStack> ctx) {
        return (Player) ctx.getSource().getExecutor();
    }

    private static int removeName(CommandContext<CommandSourceStack> ctx) {
        Player p = sender(ctx);
        handle(p, null);
        return 1;
    }

    private static int setName(CommandContext<CommandSourceStack> ctx) {
        Player p = sender(ctx);
        String name = StringArgumentType.getString(ctx, "name");
        handle(p, name);
        return 1;
    }

    private static void handle(Player p, String name) {
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            sendError(p, "You must be holding an item to rename it!");
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            sendError(p, "Failed to get item metadata.");
            return;
        }

        if (name == null || name.isBlank()) {
            meta.displayName(null);
            send(p, Component.text("Item name has been removed.", Colors.HOT_PINK));
        } else {
            Component newName = Text.stringToComponent(name).decoration(TextDecoration.ITALIC, false);
            meta.displayName(newName);
            send(p, Component.text("Item name has been updated!", Colors.HOT_PINK));
        }

        item.setItemMeta(meta);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 1f, 1.2f);
    }

    private static void send(Player p, Component msg) {
        p.sendMessage(msg);
        p.sendActionBar(msg);
    }

    private static void sendError(Player p, String message) {
        Component res = Component.text(message, Colors.DARK_PINK);
        p.sendMessage(res);
        p.sendActionBar(res);
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1f, 1f);
    }
}