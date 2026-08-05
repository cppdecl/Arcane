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
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static cx.arcane.utils.Text.parseAmountString;

@NullMarked
public class SetWorthCommand {

    private record ItemInput(String name, long price) {
        static ItemInput parse(String input) {
            int lastSpace = input.lastIndexOf(' ');
            if (lastSpace == -1) return new ItemInput(input, -1L);

            String possiblePrice = input.substring(lastSpace + 1).trim();
            long price = parseAmountString(possiblePrice);

            if (price <= 0 && !possiblePrice.equals("0")) return new ItemInput(input, -1L);

            return new ItemInput(input.substring(0, lastSpace).trim(), price);
        }

        boolean hasPrice() {
            return price >= 0L;
        }
    }

    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("setworth")
                .requires(SetWorthCommand::requirements)
                .then(Commands.argument("input", StringArgumentType.greedyString())
                        .suggests(SetWorthCommand::suggestItems)
                        .executes(SetWorthCommand::runSetWorth)
                )
                .build();
    }

    private static boolean requirements(CommandSourceStack stack) {
        CommandSender sender = stack.getSender();
        if (sender instanceof Player p)
            return p.hasPermission("arcane.rank.management");
        return true;
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

    private static int runSetWorth(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        ItemInput parsed = ItemInput.parse(StringArgumentType.getString(ctx, "input").trim());

        if (!parsed.hasPrice()) {
            sendError(ctx, "Usage: /setworth <item name> <price>");
            return Command.SINGLE_SUCCESS;
        }

        if (parsed.price() < 0) {
            sendError(ctx, "Price must be 0 or greater.");
            return Command.SINGLE_SUCCESS;
        }

        Material mat = ItemManager.getByName(parsed.name());
        if (mat == null) mat = ItemManager.getByNameClosest(parsed.name());

        if (mat == null) {
            sendError(ctx, "Unknown item: " + parsed.name());
            return Command.SINGLE_SUCCESS;
        }

        PriceManager.setPrice(mat, parsed.price());

        Component msg = Component.text("Set ", NamedTextColor.GRAY)
                .append(Component.text(ItemManager.getDisplayName(mat) + "'s ", Colors.HOT_PINK))
                .append(Component.text("price to ", NamedTextColor.GRAY))
                .append(Component.text("$" + String.format("%,d", parsed.price()), Colors.HOT_PINK));

        send(ctx, msg);

        PriceManager.checkShopPrice(ctx.getSource().getSender(), mat, true);

        return Command.SINGLE_SUCCESS;
    }

    private static void send(CommandContext<CommandSourceStack> ctx, Component msg) {
        CommandSender sender = ctx.getSource().getSender();
        sender.sendMessage(msg);
        if (sender instanceof Player p)
            p.sendActionBar(msg);
    }

    private static void sendError(CommandContext<CommandSourceStack> ctx, String message) {
        Component msg = Component.text(message, Colors.DARK_PINK);
        CommandSender sender = ctx.getSource().getSender();
        sender.sendMessage(msg);
        if (sender instanceof Player p) {
            p.sendActionBar(msg);
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }
    }
}