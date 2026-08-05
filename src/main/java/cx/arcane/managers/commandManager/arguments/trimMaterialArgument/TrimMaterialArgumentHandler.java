package cx.arcane.managers.commandManager.arguments.trimMaterialArgument;

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
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;

public class TrimMaterialArgumentHandler extends ArgumentResolver<CommandSender, TrimMaterialArgument> {

    @Override
    protected ParseResult<TrimMaterialArgument> parse(Invocation<CommandSender> invocation, Argument<TrimMaterialArgument> argument, String string) {
        if (!string.contains(":")) string = "minecraft:" + string;
        String[] split = string.split(":");

        TrimMaterial material = RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.TRIM_MATERIAL)
                .get(new NamespacedKey(split[0], split[1]));

        if (material == null) {
            return ParseResult.failure(Component.text("Unknown trim material.", Colors.DARK_PINK));
        }

        return ParseResult.success(new TrimMaterialArgument(material, split[1]));
    }

    @Override
    public SuggestionResult suggest(Invocation<CommandSender> invocation, Argument<TrimMaterialArgument> argument, SuggestionContext context) {
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_MATERIAL);
        return registry.stream()
                .map(material -> registry.getKey(material).asMinimalString())
                .collect(SuggestionResult.collector());
    }
}