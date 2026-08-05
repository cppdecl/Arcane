package cx.arcane.managers.commandManager.arguments.currencyArgument;

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

import java.util.Locale;

public class CurrencyArgumentHandler extends ArgumentResolver<CommandSender, Currency> {

    @Override
    protected ParseResult<Currency> parse(
            Invocation<CommandSender> invocation,
            Argument<Currency> argument,
            String string
    ) {

        if (string.toLowerCase(Locale.ROOT).equals("money".toLowerCase(Locale.ROOT))) {
            return ParseResult.success(Currency.MONEY);
        }
        return ParseResult.failure(Component.text("Invalid currency.", Colors.DARK_PINK));
    }

    @Override
    public SuggestionResult suggest(
            Invocation<CommandSender> invocation,
            Argument<Currency> argument,
            SuggestionContext context
    ) {
        return SuggestionResult.of(
                "Money"
        );
    }
}