package cx.arcane.managers.commandManager.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import cx.arcane.managers.itemManager.ItemManager;
import cx.arcane.managers.priceManager.PriceManager;
import cx.arcane.utils.Colors;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@NullMarked
public class WorthCommand {

    private record ItemInput(String name, int amount) {
        static ItemInput parse(String input) {
            int lastSpace = input.lastIndexOf(' ');
            if (lastSpace == -1) return new ItemInput(input, 1);

            try {
                int amount = Integer.parseInt(input.substring(lastSpace + 1));
                return new ItemInput(input.substring(0, lastSpace).trim(), Math.clamp(amount, 1, 64));
            } catch (NumberFormatException ignored) {
                return new ItemInput(input, 1);
            }
        }
    }

    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("worth")
                .then(Commands.argument("item", StringArgumentType.greedyString())
                        .suggests(WorthCommand::suggestItems)
                        .executes(WorthCommand::runWorth)
                )
                .build();
    }

    private static CompletableFuture<Suggestions> suggestItems(
            CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder
    ) {
        String partial = builder.getRemaining().toLowerCase(Locale.ROOT).trim();

        List<Material> results = partial.isEmpty()
                ? ItemManager.getAll()
                : ItemManager.searchByName(partial);

        for (Material material : results)
            builder.suggest(ItemManager.getDisplayName(material));

        return builder.buildFuture();
    }

    private static int runWorth(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        ItemInput parsed = ItemInput.parse(StringArgumentType.getString(ctx, "item").trim());

        Material mat = ItemManager.getByName(parsed.name());
        if (mat == null) mat = ItemManager.getByNameClosest(parsed.name());

        if (mat == null) {
            Component msg = Component.text("Unknown item: " + parsed.name(), Colors.DARK_PINK);
            sender.sendMessage(msg);
            if (sender instanceof Player player) player.sendActionBar(msg);
            return Command.SINGLE_SUCCESS;
        }

        long price = PriceManager.getByMaterial(mat).getPrice();

        if (price <= 0L) {
            Component msg = Component.text("That item is unsellable.", Colors.DARK_PINK);
            sender.sendMessage(msg);
            if (sender instanceof Player player) player.sendActionBar(msg);
            return Command.SINGLE_SUCCESS;
        }

        long total = price * parsed.amount();
        String formatted = String.format("$%,d", total);

        Component msg = Component.text().append(
                Component.text(parsed.amount() + " " + ItemManager.getDisplayName(mat), Colors.HOT_PINK),
                Component.text(" costs ", Colors.GRAY),
                Component.text(formatted, Colors.HOT_PINK)
        ).build();

        sender.sendMessage(msg);
        if (sender instanceof Player player) player.sendActionBar(msg);

        return Command.SINGLE_SUCCESS;
    }
}