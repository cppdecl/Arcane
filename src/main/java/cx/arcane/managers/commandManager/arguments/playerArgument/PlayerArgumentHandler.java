package cx.arcane.managers.commandManager.arguments.playerArgument;

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

public class PlayerArgumentHandler extends ArgumentResolver<CommandSender, PlayerArgument> {

    @Override
    protected ParseResult<PlayerArgument> parse(
            Invocation<CommandSender> invocation,
            Argument<PlayerArgument> argument,
            String string
    ) {
        PlayerArgument pArg = new PlayerArgument(PlayerManager.getByNameIgnoreCase(string));
        PlayerData pData = pArg.get();
        if (pData == null) {
            return ParseResult.failure(Component.text("That player does not exist.", Colors.DARK_PINK));
        }

        return ParseResult.success(pArg);
    }

    @Override
    public SuggestionResult suggest(
            Invocation<CommandSender> invocation,
            Argument<PlayerArgument> argument,
            SuggestionContext context
    ) {
        return PlayerManager.getAll()
                .stream()
                .map(PlayerData::getUsername)
                .collect(SuggestionResult.collector());
    }
}