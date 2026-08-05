package cx.arcane.managers.commandManager.arguments.trimPatternArgument;

import cx.arcane.utils.Colors;
import dev.rollczi.litecommands.argument.Argument;
import dev.rollczi.litecommands.argument.parser.ParseResult;
import dev.rollczi.litecommands.argument.resolver.ArgumentResolver;
import dev.rollczi.litecommands.invocation.Invocation;
import dev.rollczi.litecommands.suggestion.SuggestionContext;
import dev.rollczi.litecommands.suggestion.SuggestionResult;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;

public class TrimPatternArgumentHandler extends ArgumentResolver<CommandSender, TrimPatternArgument> {

    @Override
    protected ParseResult<TrimPatternArgument> parse(Invocation<CommandSender> invocation, Argument<TrimPatternArgument> argument, String string) {
        if (!string.contains(":")) string = "minecraft:" + string;
        String[] split = string.split(":");

        TrimPattern pattern = RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.TRIM_PATTERN)
                .get(new NamespacedKey(split[0], split[1]));

        if (pattern == null) {
            return ParseResult.failure(Component.text("Unknown trim pattern.", Colors.DARK_PINK));
        }

        return ParseResult.success(new TrimPatternArgument(pattern, split[1]));
    }

    @Override
    public SuggestionResult suggest(Invocation<CommandSender> invocation, Argument<TrimPatternArgument> argument, SuggestionContext context) {
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN);
        return registry.stream()
                .map(pattern -> registry.getKey(pattern).asMinimalString())
                .collect(SuggestionResult.collector());
    }
}