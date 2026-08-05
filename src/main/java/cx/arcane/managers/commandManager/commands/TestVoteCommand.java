package cx.arcane.managers.commandManager.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import cx.arcane.managers.crateManager.CrateManager;
import cx.arcane.managers.msgManager.MsgManager;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.managers.voteManager.VoteAction;
import cx.arcane.managers.voteManager.VoteManager;
import cx.arcane.utils.Colors;
import cx.arcane.utils.Log;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.concurrent.CompletableFuture;

@SuppressWarnings("UnstableApiUsage")
@NullMarked
public class TestVoteCommand {

    public static LiteralCommandNode<CommandSourceStack> build(String alias) {
        return Commands.literal(alias)
                .requires(ctx -> ctx.getSender().hasPermission("arcane.rank.management"))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(TestVoteCommand::suggestPlayers)
                           .executes(TestVoteCommand::execute))
                .build();
    }

    private static CompletableFuture<Suggestions> suggestPlayers(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        if (!(ctx.getSource().getExecutor() instanceof Player sender)) return builder.buildFuture();
        String typed = builder.getRemaining().toLowerCase();
        for (PlayerData pData : PlayerManager.getAll()) {
            if (pData.getUsername().equals(sender.getName())) continue;
            if (pData.getUsername().toLowerCase().startsWith(typed)) builder.suggest(pData.getUsername());
        }
        return builder.buildFuture();
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player sender)) return 0;

        String targetName = StringArgumentType.getString(ctx, "player");

        PlayerData pData = PlayerManager.getByNameIgnoreCase(targetName);
        if (pData == null) {
            return err(sender, "That player does not exist.");
        }

        VoteAction vote = new VoteAction();
        vote.setUniqueId(pData.getUniqueId());
        vote.setUsername(pData.getUsername());
        vote.setServiceName("Arcane_Test");
        vote.setAddress("0.0.0.0");
        vote.setVoteTimestamp(System.currentTimeMillis());
        vote.setReceivedTimestamp(System.currentTimeMillis());
        vote.setRawPayload("This is a test from TestVoteCommand");

        Log.info("[Votifier V1] Dispatching test vote for " + pData.getUsername());
        VoteManager.onVote(vote, pData);

        sender.sendActionBar(Component.text("Sending fake vote for " + pData.getUsername() + "...", Colors.GRAY));

        pData.getMeta().setTotalVotes(pData.getMeta().getTotalVotes() - 1);
        CrateManager.takeKey(pData.getUniqueId(), "vote", 1);

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