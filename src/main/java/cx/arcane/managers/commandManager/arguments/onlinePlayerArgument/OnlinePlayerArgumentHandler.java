package cx.arcane.managers.commandManager.arguments.onlinePlayerArgument;

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
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class OnlinePlayerArgumentHandler extends ArgumentResolver<CommandSender, OnlinePlayerArgument> {

    @Override
    protected ParseResult<OnlinePlayerArgument> parse(
            Invocation<CommandSender> invocation,
            Argument<OnlinePlayerArgument> argument,
            String string
    ) {
        OnlinePlayerArgument pArg = new OnlinePlayerArgument(PlayerManager.getByNameIgnoreCase(string));
        PlayerData pData = pArg.get();
        if (pData == null) {
            return ParseResult.failure(Component.text("That player does not exist.", Colors.DARK_PINK));
        }

        Player p = pData.getPlayer();
        if (p == null || !p.isOnline()) {
            return ParseResult.failure(Component.text("That player does not exist.", Colors.DARK_PINK));
        }

        return ParseResult.success(pArg);
    }

    @Override
    public SuggestionResult suggest(
            Invocation<CommandSender> invocation,
            Argument<OnlinePlayerArgument> argument,
            SuggestionContext context
    ) {
        return PlayerManager.getAll()
                .stream()
                .filter(pData -> {
                    Player p = pData.getPlayer();
                    return p != null && p.isOnline();
                })
                .map(PlayerData::getUsername)
                .collect(SuggestionResult.collector());
    }
}