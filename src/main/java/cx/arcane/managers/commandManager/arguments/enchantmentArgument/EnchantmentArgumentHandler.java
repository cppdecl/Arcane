package cx.arcane.managers.commandManager.arguments.enchantmentArgument;

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
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.jetbrains.annotations.NotNull;

public class EnchantmentArgumentHandler extends ArgumentResolver<CommandSender, EnchantmentArgument> {

    @Override
    protected ParseResult<EnchantmentArgument> parse(
            Invocation<CommandSender> invocation,
            Argument<EnchantmentArgument> argument,
            String string
    ) {

        if (!string.contains(":")) {
            string = "minecraft:" + string;
        }

        String[] split = string.split(":");

        Registry<@NotNull Enchantment> registry =
                RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);

        Enchantment enchantment = registry.get(new NamespacedKey(split[0], split[1]));

        if (enchantment == null) {
            return ParseResult.failure(Component.text("That enchantment does not exist.", Colors.DARK_PINK));
        }

        return ParseResult.success(new EnchantmentArgument(enchantment, split[1]));
    }

    @Override
    public SuggestionResult suggest(
            Invocation<CommandSender> invocation,
            Argument<EnchantmentArgument> argument,
            SuggestionContext context
    ) {

        Registry<@NotNull Enchantment> registry =
                RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);

        return registry.stream()
                .map(enchant -> enchant.getKey().asMinimalString())
                .collect(SuggestionResult.collector());
    }
}