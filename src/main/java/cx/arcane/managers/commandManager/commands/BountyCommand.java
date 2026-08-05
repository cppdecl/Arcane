package cx.arcane.managers.commandManager.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import cx.arcane.managers.bountyManager.BountyManager;
import cx.arcane.managers.bountyManager.BountyGuiHelper;
import cx.arcane.managers.ecoManager.EcoManager;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.utils.Text;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.concurrent.CompletableFuture;

@NullMarked
public class BountyCommand {
    public static LiteralCommandNode<CommandSourceStack> build(String alias) {
        return Commands.literal(alias)
                .requires(BountyCommand::requirements)
                .executes(BountyCommand::handle)
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(BountyCommand::handleSearch))
                .then(Commands.literal("place")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(BountyCommand::suggestPlayers)
                                .then(Commands.argument("reward", StringArgumentType.greedyString())
                                        .executes(BountyCommand::handlePlaceBounty))))
                .build();
    }


    private static boolean requirements(CommandSourceStack stack) {

        if (!(stack.getExecutor() instanceof Player player)) {
            return false;
        }

        return true;
    }


    private static CompletableFuture<Suggestions> suggestPlayers(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String typed = builder.getRemaining().toLowerCase();

        for (String name : PlayerManager.getUsernames()) {
            if (name.toLowerCase().startsWith(typed)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }

    private static int handle(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player p)) return 0;
        p.playSound(p, Sound.BLOCK_ENDER_CHEST_OPEN, 1, 1);
        BountyGuiHelper.open(p, "");
        return 1;
    }

    private static int handleSearch(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player p)) return 0;
        String nameQuery = StringArgumentType.getString(ctx, "name");
        p.playSound(p, Sound.BLOCK_ENDER_CHEST_OPEN, 1, 1);
        BountyGuiHelper.open(p, nameQuery);
        return 1;
    }

    private static int handlePlaceBounty(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player p)) return 0;
        String nameQuery = StringArgumentType.getString(ctx, "name");
        PlayerData pDataTarget = PlayerManager.getByName(nameQuery);
        if (pDataTarget == null) {
            TextComponent response = Component.text("That player does not exist!").color(TextColor.color(0xff0000));
            p.sendMessage(response);
            p.sendActionBar(response);
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            return 0;
        }

        if (pDataTarget.getUniqueId().equals(p.getUniqueId())) {
            TextComponent response = Component.text("You can't place a bounty on yourself!").color(TextColor.color(0xff0000));
            p.sendMessage(response);
            p.sendActionBar(response);
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            return 0;
        }

        String stringAmount = StringArgumentType.getString(ctx, "reward");
        long price = Text.parseAmountString(stringAmount);
        if (!Text.isValidAmount(price)) {
            TextComponent response = Component.text("Please enter a valid amount.").color(TextColor.color(0xff0000));
            p.sendMessage(response);
            p.sendActionBar(response);
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            return 0;
        }

        if (price > EcoManager.getMoney(p.getUniqueId())) {
            TextComponent response = Component.text("You can't afford to place this bounty!").color(TextColor.color(0xff0000));
            p.sendMessage(response);
            p.sendActionBar(response);
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            return 0;
        }

        if (price < 10_000) {
            TextComponent response = Component.text("You can only place a bounty of $10K or higher!").color(TextColor.color(0xff0000));
            p.sendMessage(response);
            p.sendActionBar(response);
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            return 0;
        }

        BountyManager.handlePlaceBounty(p, pDataTarget.getUniqueId(), price);

        return 1;
    }
}