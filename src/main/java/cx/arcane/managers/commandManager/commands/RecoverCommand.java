package cx.arcane.managers.commandManager.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import cx.arcane.managers.playerManager.AccountType;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.utils.Colors;
import cx.arcane.utils.CryptUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.security.SecureRandom;
import java.util.concurrent.CompletableFuture;

@NullMarked
public class RecoverCommand {

    private static final SecureRandom RANDOM = new SecureRandom();

    // ================== Command Tree ==================

    public static LiteralCommandNode<CommandSourceStack> build(String alias) {
        return Commands.literal(alias)
                .requires(RecoverCommand::requirements)
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(RecoverCommand::suggestPlayers)
                        .executes(RecoverCommand::handle))
                .build();
    }

    // ================== Suggestions ==================

    private static CompletableFuture<Suggestions> suggestPlayers(
            CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder
    ) {
        String typed = builder.getRemaining().toLowerCase();

        for (PlayerData pData : PlayerManager.getAll()) {
            String name = pData.getUsername();
            if (name.toLowerCase().startsWith(typed)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }

    // ================== Permissions ==================

    private static boolean requirements(CommandSourceStack stack) {
        return stack.getExecutor() instanceof Player p
                && (p.hasPermission("arcane.rank.management"));
    }

    // ================== Handler ==================

    private static int handle(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player sender)) {
            return 0;
        }

        String targetName = StringArgumentType.getString(ctx, "name");
        PlayerData pData = PlayerManager.getByNameIgnoreCase(targetName);

        if (pData == null) {
            sendError(sender, "That user does not exist!");
            return 0;
        }

        if (pData.getAccountType() != AccountType.CRACKED) {
            sendError(sender, "You can only recover cracked accounts!");
            return 0;
        }

        String password = generatePassword();
        pData.setPassword(CryptUtils.hashPassword(password));

        String displayName = pData.getUsername();

        Component message = Component.text()
                .append(Component.text("Click me to copy ", Colors.WHITE)
                        .decoration(TextDecoration.ITALIC, false))
                .append(Component.text(displayName, Colors.HOT_PINK)
                        .decoration(TextDecoration.ITALIC, false))
                .append(Component.text("'s recovery password.", Colors.WHITE)
                        .decoration(TextDecoration.ITALIC, false))
                .clickEvent(ClickEvent.copyToClipboard(password))
                .build();

        sender.sendMessage(message);
        sender.playSound(sender.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);

        return 1;
    }

    // ================== Utilities ==================

    private static String generatePassword() {
        int value = RANDOM.nextInt(900000) + 100000;
        return String.valueOf(value);
    }

    private static void sendError(Player player, String message) {
        TextComponent res = Component.text(message, Colors.DARK_PINK);
        player.sendMessage(res);
        player.sendActionBar(res);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
    }
}