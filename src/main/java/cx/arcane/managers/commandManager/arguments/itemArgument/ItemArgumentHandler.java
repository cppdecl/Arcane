package cx.arcane.managers.commandManager.arguments.itemArgument;

import cx.arcane.managers.itemManager.ItemManager;
import cx.arcane.utils.Colors;
import dev.rollczi.litecommands.argument.Argument;
import dev.rollczi.litecommands.argument.parser.ParseResult;
import dev.rollczi.litecommands.argument.resolver.ArgumentResolver;
import dev.rollczi.litecommands.invocation.Invocation;
import dev.rollczi.litecommands.suggestion.SuggestionContext;
import dev.rollczi.litecommands.suggestion.SuggestionResult;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;

import java.util.List;

public class ItemArgumentHandler extends ArgumentResolver<CommandSender, Material> {

    @Override
    protected ParseResult<Material> parse(
            Invocation<CommandSender> invocation,
            Argument<Material> argument,
            String string
    ) {

        Material material = ItemManager.getByName(string);

        if (material == null)
            material = ItemManager.getById(string);

        if (material == null)
            return ParseResult.failure(Component.text("Unknown item: " + string, Colors.DARK_PINK));

        return ParseResult.success(material);
    }

    @Override
    public SuggestionResult suggest(
            Invocation<CommandSender> invocation,
            Argument<Material> argument,
            SuggestionContext context
    ) {
        String input = context.getCurrent().multilevel();

        List<Material> results = input.isEmpty()
                ? ItemManager.getAll()
                : ItemManager.searchByName(input);

        return results.stream()
                .map(ItemManager::getDisplayName)
                .collect(SuggestionResult.collector());
    }
}