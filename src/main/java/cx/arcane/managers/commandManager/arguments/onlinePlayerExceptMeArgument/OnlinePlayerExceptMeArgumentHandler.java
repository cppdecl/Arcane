package cx.arcane.managers.commandManager.arguments.onlinePlayerExceptMeArgument;

import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.utils.Colors;
import dev.rollczi.litecommands.argument.Argument;
import dev.rollczi.litecommands.argument.parser.ParseResult;
import dev.rollczi.litecommands.argument.resolver.ArgumentResolver;
import dev.rollczi.litecommands.invocation.Invocation;
import dev.rollczi.litecommands.suggestion.SuggestionContext;
import dev.rollczi.litecommands.suggestion.SuggestionResult;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class OnlinePlayerExceptMeArgumentHandler extends ArgumentResolver<CommandSender, OnlinePlayerExceptMeArgument> {

    @Override
    protected ParseResult<OnlinePlayerExceptMeArgument> parse(
            Invocation<CommandSender> invocation,
            Argument<OnlinePlayerExceptMeArgument> argument,
            String string
    ) {
        OnlinePlayerExceptMeArgument pArg = new OnlinePlayerExceptMeArgument(PlayerManager.getByNameIgnoreCase(string));
        PlayerData pData = pArg.get();

        if (pData == null) {
            return ParseResult.failure(Component.text("That player does not exist.", Colors.DARK_PINK));
        }

        Player p = pData.getPlayer();
        if (p == null || !p.isOnline()) {
            return ParseResult.failure(Component.text("That player does not exist.", Colors.DARK_PINK));
        }

        Player sender = invocation.sender() instanceof Player ? (Player) invocation.sender() : null;
        if (!PlayerManager.canSee(sender, p.getPlayer()))
            return ParseResult.failure(Component.text("That player does not exist.", Colors.DARK_PINK));

        if (invocation.sender() instanceof Player && sender.equals(p)) {
            return ParseResult.failure(Component.text("You can't do that to yourself.", Colors.DARK_PINK));
        }

        return ParseResult.success(pArg);
    }

    @Override
    public SuggestionResult suggest(
            Invocation<CommandSender> invocation,
            Argument<OnlinePlayerExceptMeArgument> argument,
            SuggestionContext context
    ) {
        Player sender = invocation.sender() instanceof Player p ? p : null;

        return PlayerManager.getAll()
                .stream()
                .filter(pData -> {
                    Player p = pData.getPlayer();
                    return p != null && p.isOnline() && (!p.equals(sender)) && PlayerManager.canSee(sender, p.getPlayer());
                })
                .map(PlayerData::getUsername)
                .collect(SuggestionResult.collector());
    }
}