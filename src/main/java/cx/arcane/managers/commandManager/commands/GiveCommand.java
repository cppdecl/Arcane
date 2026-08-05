package cx.arcane.managers.commandManager.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import cx.arcane.managers.itemManager.ItemManager;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.utils.Colors;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

@NullMarked
public class GiveCommand {

    private static final String SELECTOR_ALL = "@all";
    private static final String SELECTOR_RANDOM = "@random";
    private static final String SELECTOR_HAND = "@hand";
    private static final Random RANDOM = new Random();
    private static final Logger LOGGER = Logger.getLogger("Arcane");

    // ================== Command Tree ==================

    public static LiteralCommandNode<CommandSourceStack> build(String alias) {
        return Commands.literal(alias)
                .requires(GiveCommand::requirements)
                .then(Commands.argument("input", StringArgumentType.greedyString())
                        .suggests(GiveCommand::suggestInput)
                        .executes(GiveCommand::handle))
                .build();
    }

    // ================== Suggestions ==================

    private static CompletableFuture<Suggestions> suggestInput(
            CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder
    ) {
        String full = builder.getInput();
        String afterCmd = full.substring(full.indexOf(' ') + 1);

        int firstSpace = afterCmd.indexOf(' ');

        if (firstSpace == -1) {
            String typed = afterCmd.toLowerCase();
            for (String sel : List.of(SELECTOR_ALL, SELECTOR_RANDOM)) {
                if (sel.startsWith(typed)) builder.suggest(sel);
            }
            for (PlayerData pData : PlayerManager.getAll()) {
                String name = pData.getUsername();
                if (name.toLowerCase().startsWith(typed)) builder.suggest(name);
            }
            return builder.buildFuture();
        }

        String target = afterCmd.substring(0, firstSpace);
        String rest = afterCmd.substring(firstSpace + 1);

        if (target.equalsIgnoreCase(SELECTOR_RANDOM)) {
            int secondSpace = rest.indexOf(' ');

            if (secondSpace == -1) {
                boolean isCount = !rest.isEmpty() && rest.chars().allMatch(Character::isDigit);
                if (rest.isEmpty() || isCount) {
                    if (rest.isEmpty()) {
                        SuggestionsBuilder offset = builder.createOffset(builder.getStart() + firstSpace + 1);
                        offset.suggest("1");
                        return offset.buildFuture();
                    }
                    return builder.buildFuture();
                }
                SuggestionsBuilder offset = builder.createOffset(builder.getStart() + firstSpace + 1);
                suggestItemTokens(rest.toLowerCase(Locale.ROOT), offset);
                return offset.buildFuture();
            }

            String secondToken = rest.substring(0, secondSpace);
            boolean secondIsCount = !secondToken.isEmpty() && secondToken.chars().allMatch(Character::isDigit);

            int itemOffset = firstSpace + 1 + (secondIsCount ? secondSpace + 1 : 0);
            String itemPart = secondIsCount ? rest.substring(secondSpace + 1) : rest;
            SuggestionsBuilder offset = builder.createOffset(builder.getStart() + itemOffset);
            suggestItemTokens(itemPart.toLowerCase(Locale.ROOT), offset);
            return offset.buildFuture();
        }

        SuggestionsBuilder offset = builder.createOffset(builder.getStart() + firstSpace + 1);
        suggestItemTokens(rest.toLowerCase(Locale.ROOT), offset);
        return offset.buildFuture();
    }

    private static void suggestItemTokens(String itemPart, SuggestionsBuilder builder) {
        int lastSpace = itemPart.lastIndexOf(' ');
        if (lastSpace != -1) {
            String lastToken = itemPart.substring(lastSpace + 1);
            boolean isNumber = !lastToken.isEmpty() && lastToken.chars().allMatch(Character::isDigit);
            if (isNumber) return;
        }

        if (SELECTOR_HAND.startsWith(itemPart)) builder.suggest(SELECTOR_HAND);

        List<Material> results = itemPart.isEmpty()
                ? ItemManager.getAll()
                : ItemManager.searchByName(itemPart);

        for (Material mat : results) builder.suggest(ItemManager.getDisplayName(mat));
    }

    // ================== Permissions ==================

    private static boolean requirements(CommandSourceStack stack) {
        return stack.getExecutor() instanceof Player p
                && p.hasPermission("arcane.rank.management");
    }

    // ================== Handler ==================

    private static int handle(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player sender)) return 0;

        String input = StringArgumentType.getString(ctx, "input").trim();
        int firstSpace = input.indexOf(' ');

        if (firstSpace == -1) {
            sendError(sender, "Usage: /give <target|@all|@random> [count] <item|@hand> [amount]");
            return 0;
        }

        String targetArg = input.substring(0, firstSpace);
        String rest = input.substring(firstSpace + 1).trim();

        int randomCount = 1;
        String itemArg = rest;

        if (targetArg.equalsIgnoreCase(SELECTOR_RANDOM)) {
            int secondSpace = rest.indexOf(' ');
            if (secondSpace != -1) {
                String secondToken = rest.substring(0, secondSpace);
                try {
                    randomCount = Integer.parseInt(secondToken);
                    if (randomCount < 1) randomCount = 1;
                    itemArg = rest.substring(secondSpace + 1).trim();
                } catch (NumberFormatException ignored) {}
            }
        }

        ParsedItemArg parsed = parseItemArg(itemArg);

        ItemStack stack = resolveItem(sender, parsed.itemToken());
        if (stack == null) {
            if (parsed.itemToken().equalsIgnoreCase(SELECTOR_HAND)) {
                sendError(sender, "You are not holding anything!");
            } else {
                sendError(sender, "Unknown item: " + parsed.itemToken());
            }
            return 0;
        }

        ItemStack toGive = stack.clone();
        toGive.setAmount(parsed.amount());

        List<Player> targets = resolveTargets(sender, targetArg, randomCount);
        if (targets.isEmpty()) {
            sendError(sender, "No valid targets found!");
            return 0;
        }

        String itemDisplayName = ItemManager.getDisplayName(toGive.getType());

        for (Player target : targets) {
            target.getInventory().addItem(toGive.clone());

            TextComponent targetMsg = Component.text()
                    .append(Component.text("You received ", Colors.WHITE))
                    .append(Component.text(parsed.amount() + "x ", Colors.HOT_PINK))
                    .append(Component.text(itemDisplayName + "!", Colors.WHITE))
                    .build();

            target.sendMessage(targetMsg);
            target.sendActionBar(targetMsg);
            target.playSound(target, Sound.ENTITY_ITEM_PICKUP, 1f, 1f);

            LOGGER.info("[Give] " + sender.getName() + " -> " + target.getName()
                    + " | " + parsed.amount() + "x " + itemDisplayName);
        }

        TextComponent senderMsg = Component.text()
                .append(Component.text("Gave ", Colors.WHITE))
                .append(Component.text(parsed.amount() + "x ", Colors.HOT_PINK))
                .append(Component.text(itemDisplayName, Colors.WHITE))
                .append(Component.text(" to ", Colors.WHITE))
                .append(Component.text(
                        targets.size() == 1 ? targets.get(0).getName() : targets.size() + " players",
                        Colors.HOT_PINK))
                .append(Component.text("!", Colors.WHITE))
                .build();

        sender.sendMessage(senderMsg);
        sender.sendActionBar(senderMsg);
        sender.playSound(sender, Sound.ENTITY_ITEM_PICKUP, 1f, 1f);

        return 1;
    }

    // ================== Resolution ==================

    private static List<Player> resolveTargets(Player sender, String targetArg, int randomCount) {
        List<Player> targets = new ArrayList<>();

        switch (targetArg.toLowerCase()) {
            case SELECTOR_ALL -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.equals(sender)) targets.add(p);
                }
            }
            case SELECTOR_RANDOM -> {
                List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
                online.remove(sender);
                Collections.shuffle(online, RANDOM);
                int pick = Math.min(randomCount, online.size());
                targets.addAll(online.subList(0, pick));
            }
            default -> {
                PlayerData pData = PlayerManager.getByNameIgnoreCase(targetArg);
                if (pData != null) {
                    Player p = pData.getPlayer();
                    if (p != null) targets.add(p);
                }
            }
        }

        return targets;
    }

    private static @Nullable ItemStack resolveItem(Player sender, String itemToken) {
        if (itemToken.equalsIgnoreCase(SELECTOR_HAND)) {
            ItemStack held = sender.getInventory().getItemInMainHand();
            return held.getType() == Material.AIR ? null : held;
        }

        Material mat = ItemManager.getByName(itemToken);
        if (mat == null) mat = ItemManager.getByNameClosest(itemToken);
        if (mat == null) return null;

        return new ItemStack(mat);
    }

    private static ParsedItemArg parseItemArg(String itemArg) {
        int lastSpace = itemArg.lastIndexOf(' ');

        if (lastSpace != -1) {
            String lastToken = itemArg.substring(lastSpace + 1);
            try {
                int amount = Integer.parseInt(lastToken);
                if (amount > 0) {
                    return new ParsedItemArg(itemArg.substring(0, lastSpace).trim(), amount);
                }
            } catch (NumberFormatException ignored) {}
        }

        return new ParsedItemArg(itemArg, 1);
    }

    private record ParsedItemArg(String itemToken, int amount) {}

    // ================== Utilities ==================

    private static void sendError(Player player, String message) {
        TextComponent res = Component.text(message, Colors.DARK_PINK);
        player.sendMessage(res);
        player.sendActionBar(res);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
    }
}