package cx.arcane.managers.commandManager.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import cx.arcane.managers.crateManager.CrateData;
import cx.arcane.managers.crateManager.CrateManager;
import cx.arcane.managers.crateManager.KeyData;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.utils.Colors;
import cx.arcane.utils.Text;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@NullMarked
public class CrateCommand {

    public static LiteralCommandNode<CommandSourceStack> build(String alias) {
        return Commands.literal(alias)
                .requires(CrateCommand::requirements)

                .then(Commands.literal("new")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("color", StringArgumentType.string())
                                        .then(Commands.argument("description", StringArgumentType.greedyString())
                                                .executes(CrateCommand::handleNew))
                                        .executes(CrateCommand::handleNewNoDesc))))

                .then(Commands.literal("info")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(CrateCommand::suggestCrates)
                                .executes(CrateCommand::handleInfo)))

                .then(Commands.literal("delete")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(CrateCommand::suggestCrates)
                                .executes(CrateCommand::handleDeleteWithId))
                        .executes(CrateCommand::handleDelete))

                .then(Commands.literal("rename")
                        .then(Commands.argument("current", StringArgumentType.word())
                                .suggests(CrateCommand::suggestCrates)
                                .then(Commands.argument("new", StringArgumentType.word())
                                        .executes(CrateCommand::handleRenameWithId)))
                        .then(Commands.argument("new", StringArgumentType.word())
                                .executes(CrateCommand::handleRenameAtFeet)))

                .then(Commands.literal("relocate")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(CrateCommand::suggestCrates)
                                .executes(CrateCommand::handleRelocateFeet)))

                .then(Commands.literal("edit")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(CrateCommand::suggestCrates)
                                .then(Commands.literal("color")
                                        .then(Commands.argument("color", StringArgumentType.string())
                                                .executes(CrateCommand::handleEditColor)))
                                .then(Commands.literal("description")
                                        .then(Commands.argument("description", StringArgumentType.greedyString())
                                                .executes(CrateCommand::handleEditDescription)))))

                .then(Commands.literal("key")
                        .then(Commands.literal("give")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(CrateCommand::suggestPlayers)
                                        .then(Commands.argument("crate", StringArgumentType.word())
                                                .suggests(CrateCommand::suggestCrates)
                                                .then(Commands.argument("amount", StringArgumentType.greedyString())
                                                        .executes(CrateCommand::handleKeyGive))))
                                .then(Commands.literal("all")
                                        .then(Commands.argument("crate", StringArgumentType.word())
                                                .suggests(CrateCommand::suggestCrates)
                                                .then(Commands.argument("amount", StringArgumentType.greedyString())
                                                        .executes(CrateCommand::handleKeyGiveAll)))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(CrateCommand::suggestPlayers)
                                        .then(Commands.argument("crate", StringArgumentType.word())
                                                .suggests(CrateCommand::suggestCrates)
                                                .then(Commands.argument("amount", StringArgumentType.greedyString())
                                                        .executes(CrateCommand::handleKeySet)))))
                        .then(Commands.literal("take")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(CrateCommand::suggestPlayers)
                                        .then(Commands.argument("crate", StringArgumentType.word())
                                                .suggests(CrateCommand::suggestCrates)
                                                .then(Commands.argument("amount", StringArgumentType.greedyString())
                                                        .executes(CrateCommand::handleKeyTake)))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(CrateCommand::suggestPlayers)
                                        .then(Commands.argument("crate", StringArgumentType.word())
                                                .suggests(CrateCommand::suggestCrates)
                                                .executes(CrateCommand::handleKeyRemove))))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(CrateCommand::suggestPlayers)
                                        .executes(CrateCommand::handleKeyClear)))
                        .then(Commands.literal("list")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(CrateCommand::suggestPlayers)
                                        .executes(CrateCommand::handleKeyList)))
                        .then(Commands.literal("total")
                                .executes(CrateCommand::handleKeyTotal)))

                .build();
    }

    private static boolean requirements(CommandSourceStack stack) {
        CommandSender sender = stack.getExecutor();
        if (sender instanceof Player p) return p.hasPermission("arcane.rank.management");
        return true;
    }

    private static Player senderPlayer(CommandContext<CommandSourceStack> ctx) {
        return (Player) ctx.getSource().getExecutor();
    }

    private static CompletableFuture<Suggestions> suggestPlayers(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String typed = builder.getRemaining().toLowerCase();
        for (PlayerData pd : PlayerManager.getAll()) {
            if (pd.getUsername().toLowerCase().startsWith(typed)) builder.suggest(pd.getUsername());
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestCrates(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String typed = builder.getRemaining().toLowerCase();
        for (String id : CrateManager.getCrates().keySet()) {
            if (id.startsWith(typed)) builder.suggest(id);
        }
        return builder.buildFuture();
    }

    // ---------------------- Handlers ---------------------- //

    private static int handleNew(CommandContext<CommandSourceStack> ctx) {
        Player p = senderPlayer(ctx);
        String id = StringArgumentType.getString(ctx, "name").toLowerCase();
        String colorStr = StringArgumentType.getString(ctx, "color");
        String descStr = StringArgumentType.getString(ctx, "description");

        if (CrateManager.hasCrate(id)) return err(p, "A crate with id " + id + " already exists!");

        TextColor color = TextColor.fromHexString(colorStr);
        if (color == null) return err(p, "Invalid color: " + colorStr);

        Block block = feetBlock(p);
        if (!isValidStorage(block)) return err(p, "You must stand on a valid storage block!");

        CrateManager.addCrate(new CrateData(id, block.getLocation(), color, Text.stringToComponent(descStr)));
        return ok(p, Component.text("Crate ", Colors.GRAY)
                .append(Component.text(id, Colors.HOT_PINK))
                .append(Component.text(" has been created!", Colors.GRAY)));
    }

    private static int handleNewNoDesc(CommandContext<CommandSourceStack> ctx) {
        Player p = senderPlayer(ctx);
        String id = StringArgumentType.getString(ctx, "name").toLowerCase();
        String colorStr = StringArgumentType.getString(ctx, "color");

        if (CrateManager.hasCrate(id)) return err(p, "A crate with id " + id + " already exists!");

        TextColor color = TextColor.fromHexString(colorStr);
        if (color == null) return err(p, "Invalid color: " + colorStr);

        Block block = feetBlock(p);
        if (!isValidStorage(block)) return err(p, "You must stand on a valid storage block!");

        CrateManager.addCrate(new CrateData(id, block.getLocation(), color, Component.empty()));
        return ok(p, Component.text("Crate ", Colors.GRAY)
                .append(Component.text(id, Colors.HOT_PINK))
                .append(Component.text(" has been created!", Colors.GRAY)));
    }

    private static int handleInfo(CommandContext<CommandSourceStack> ctx) {
        Player p = senderPlayer(ctx);
        String id = StringArgumentType.getString(ctx, "name").toLowerCase();

        CrateData crate = CrateManager.getCrateById(id);
        if (crate == null) return err(p, "No crate with id " + id + " found!");

        p.sendMessage(Text.toSmallCapsComponent(crate.getId() + " Crate").color(crate.getColor()));
        if (crate.getDescription() != null) p.sendMessage(crate.getDescription());
        p.sendMessage(Component.text(CrateManager.getKeyCount(p.getUniqueId(), id), crate.getColor())
                .append(Component.text(" Keys", Colors.GRAY)));
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f);
        return 1;
    }

    private static int handleDelete(CommandContext<CommandSourceStack> ctx) {
        Player p = senderPlayer(ctx);
        Block block = feetBlock(p);

        CrateData crate = CrateManager.getCrateByLocation(block.getLocation());
        if (crate == null) return err(p, "No crate found under your feet!");

        removeCrateAndKeys(crate);
        return ok(p, Component.text("Crate ", Colors.GRAY)
                .append(Component.text(crate.getId(), Colors.HOT_PINK))
                .append(Component.text(" has been deleted!", Colors.GRAY)));
    }

    private static int handleDeleteWithId(CommandContext<CommandSourceStack> ctx) {
        Player p = senderPlayer(ctx);
        String id = StringArgumentType.getString(ctx, "name").toLowerCase();

        CrateData crate = CrateManager.getCrateById(id);
        if (crate == null) return err(p, "No crate with id " + id + " found!");

        removeCrateAndKeys(crate);
        return ok(p, Component.text("Crate ", Colors.GRAY)
                .append(Component.text(id, Colors.HOT_PINK))
                .append(Component.text(" has been deleted!", Colors.GRAY)));
    }

    private static int handleRenameWithId(CommandContext<CommandSourceStack> ctx) {
        Player p = senderPlayer(ctx);
        String current = StringArgumentType.getString(ctx, "current").toLowerCase();
        String newId = StringArgumentType.getString(ctx, "new").toLowerCase();

        if (current.equals(newId)) return err(p, "New name is the same as the current name!");

        CrateData crate = CrateManager.getCrateById(current);
        if (crate == null) return err(p, "No crate with id " + current + " found!");

        if (CrateManager.hasCrate(newId)) return err(p, "A crate with id " + newId + " already exists!");

        renameCrate(crate, current, newId);
        return ok(p, Component.text("Crate ", Colors.GRAY)
                .append(Component.text(current, Colors.HOT_PINK))
                .append(Component.text(" renamed to ", Colors.GRAY))
                .append(Component.text(newId, Colors.HOT_PINK))
                .append(Component.text("!", Colors.GRAY)));
    }

    private static int handleRenameAtFeet(CommandContext<CommandSourceStack> ctx) {
        Player p = senderPlayer(ctx);
        String newId = StringArgumentType.getString(ctx, "new").toLowerCase();

        CrateData crate = CrateManager.getCrateByLocation(feetBlock(p).getLocation());
        if (crate == null) return err(p, "No crate under your feet!");

        String oldId = crate.getId();
        if (oldId.equals(newId)) return err(p, "New name is the same as the current name!");
        if (CrateManager.hasCrate(newId)) return err(p, "A crate with id " + newId + " already exists!");

        renameCrate(crate, oldId, newId);
        return ok(p, Component.text("Crate ", Colors.GRAY)
                .append(Component.text(oldId, Colors.HOT_PINK))
                .append(Component.text(" renamed to ", Colors.GRAY))
                .append(Component.text(newId, Colors.HOT_PINK))
                .append(Component.text("!", Colors.GRAY)));
    }

    private static int handleRelocateFeet(CommandContext<CommandSourceStack> ctx) {
        Player p = senderPlayer(ctx);
        String id = StringArgumentType.getString(ctx, "name").toLowerCase();

        CrateData crate = CrateManager.getCrateById(id);
        if (crate == null) return err(p, "No crate with id " + id + " found!");

        Block block = feetBlock(p);
        if (!isValidStorage(block)) return err(p, "Target location is not a valid storage block!");

        crate.setLocation(block.getLocation());
        return ok(p, Component.text("Crate ", Colors.GRAY)
                .append(Component.text(id, Colors.HOT_PINK))
                .append(Component.text(" has been relocated!", Colors.GRAY)));
    }

    private static int handleEditColor(CommandContext<CommandSourceStack> ctx) {
        Player p = senderPlayer(ctx);
        String id = StringArgumentType.getString(ctx, "name").toLowerCase();
        String colorStr = StringArgumentType.getString(ctx, "color");

        CrateData crate = CrateManager.getCrateById(id);
        if (crate == null) return err(p, "No crate with id " + id + " found!");

        TextColor color = TextColor.fromHexString(colorStr);
        if (color == null) return err(p, "Invalid color: " + colorStr);

        crate.setColor(color);
        return ok(p, Component.text("Updated color of crate ", Colors.GRAY)
                .append(Component.text(id, Colors.HOT_PINK))
                .append(Component.text("!", Colors.GRAY)));
    }

    private static int handleEditDescription(CommandContext<CommandSourceStack> ctx) {
        Player p = senderPlayer(ctx);
        String id = StringArgumentType.getString(ctx, "name").toLowerCase();
        String descStr = StringArgumentType.getString(ctx, "description");

        CrateData crate = CrateManager.getCrateById(id);
        if (crate == null) return err(p, "No crate with id " + id + " found!");

        crate.setDescription(Text.stringToComponent(descStr));
        return ok(p, Component.text("Updated description of crate ", Colors.GRAY)
                .append(Component.text(id, Colors.HOT_PINK))
                .append(Component.text("!", Colors.GRAY)));
    }

    private static int handleKeyGive(CommandContext<CommandSourceStack> ctx) {
        Player p = senderPlayer(ctx);
        String targetName = StringArgumentType.getString(ctx, "player");
        String crateId = StringArgumentType.getString(ctx, "crate").toLowerCase();
        long amount = parseAmount(ctx, "amount");

        if (amount <= 0) return err(p, "Amount must be greater than 0!");

        PlayerData target = PlayerManager.getByName(targetName);
        if (target == null) return err(p, "Player not found: " + targetName);

        if (!CrateManager.hasCrate(crateId)) return err(p, "No crate with id " + crateId + " found!");

        CrateManager.giveKey(target.getUniqueId(), crateId, amount);
        return ok(p, Component.text("Gave ", Colors.GRAY)
                .append(Component.text(amount, Colors.HOT_PINK))
                .append(Component.text(" key(s) of ", Colors.GRAY))
                .append(Component.text(crateId, Colors.HOT_PINK))
                .append(Component.text(" to ", Colors.GRAY))
                .append(Component.text(targetName, Colors.HOT_PINK))
                .append(Component.text("!", Colors.GRAY)));
    }

    private static int handleKeyGiveAll(CommandContext<CommandSourceStack> ctx) {
        Player p = senderPlayer(ctx);
        String crateId = StringArgumentType.getString(ctx, "crate").toLowerCase();
        long amount = parseAmount(ctx, "amount");

        if (amount <= 0) return err(p, "Amount must be greater than 0!");
        if (!CrateManager.hasCrate(crateId)) return err(p, "No crate with id " + crateId + " found!");

        int count = 0;
        for (Player target : org.bukkit.Bukkit.getOnlinePlayers()) {
            PlayerData td = PlayerManager.getByName(target.getName());
            if (td == null) continue;
            CrateManager.giveKey(td.getUniqueId(), crateId, amount);
            count++;
        }

        return ok(p, Component.text("Gave ", Colors.GRAY)
                .append(Component.text(amount, Colors.HOT_PINK))
                .append(Component.text(" key(s) of ", Colors.GRAY))
                .append(Component.text(crateId, Colors.HOT_PINK))
                .append(Component.text(" to ", Colors.GRAY))
                .append(Component.text(count + " players", Colors.HOT_PINK))
                .append(Component.text("!", Colors.GRAY)));
    }

    private static int handleKeySet(CommandContext<CommandSourceStack> ctx) {
        Player p = senderPlayer(ctx);
        String targetName = StringArgumentType.getString(ctx, "player");
        String crateId = StringArgumentType.getString(ctx, "crate").toLowerCase();
        long amount = parseAmount(ctx, "amount");

        if (amount < 0) return err(p, "Amount must be 0 or greater!");

        PlayerData target = PlayerManager.getByName(targetName);
        if (target == null) return err(p, "Player not found: " + targetName);

        if (!CrateManager.hasCrate(crateId)) return err(p, "No crate with id " + crateId + " found!");

        KeyData kd = CrateManager.getKeyData().computeIfAbsent(
                target.getUniqueId(),
                id -> new KeyData(id, new java.util.concurrent.ConcurrentHashMap<>())
        );
        kd.getKeys().put(crateId, amount);

        return ok(p, Component.text("Set ", Colors.GRAY)
                .append(Component.text(targetName, Colors.HOT_PINK))
                .append(Component.text("'s ", Colors.GRAY))
                .append(Component.text(crateId, Colors.HOT_PINK))
                .append(Component.text(" keys to ", Colors.GRAY))
                .append(Component.text(amount, Colors.HOT_PINK))
                .append(Component.text("!", Colors.GRAY)));
    }

    private static int handleKeyTake(CommandContext<CommandSourceStack> ctx) {
        Player p = senderPlayer(ctx);
        String targetName = StringArgumentType.getString(ctx, "player");
        String crateId = StringArgumentType.getString(ctx, "crate").toLowerCase();
        long amount = parseAmount(ctx, "amount");

        if (amount <= 0) return err(p, "Amount must be greater than 0!");

        PlayerData target = PlayerManager.getByName(targetName);
        if (target == null) return err(p, "Player not found: " + targetName);

        long current = CrateManager.getKeyCount(target.getUniqueId(), crateId);
        if (current < amount) return err(p, targetName + " only has " + current + " key(s) of " + crateId + "!");

        CrateManager.takeKey(target.getUniqueId(), crateId, amount);
        return ok(p, Component.text("Took ", Colors.GRAY)
                .append(Component.text(amount, Colors.HOT_PINK))
                .append(Component.text(" key(s) of ", Colors.GRAY))
                .append(Component.text(crateId, Colors.HOT_PINK))
                .append(Component.text(" from ", Colors.GRAY))
                .append(Component.text(targetName, Colors.HOT_PINK))
                .append(Component.text("!", Colors.GRAY)));
    }

    private static int handleKeyRemove(CommandContext<CommandSourceStack> ctx) {
        Player p = senderPlayer(ctx);
        String targetName = StringArgumentType.getString(ctx, "player");
        String crateId = StringArgumentType.getString(ctx, "crate").toLowerCase();

        PlayerData target = PlayerManager.getByName(targetName);
        if (target == null) return err(p, "Player not found: " + targetName);

        KeyData kd = CrateManager.getKeyData().get(target.getUniqueId());
        if (kd == null || !kd.hasKey(crateId)) return err(p, targetName + " has no keys of " + crateId + "!");

        kd.removeKey(crateId);
        return ok(p, Component.text("Removed all keys of ", Colors.GRAY)
                .append(Component.text(crateId, Colors.HOT_PINK))
                .append(Component.text(" from ", Colors.GRAY))
                .append(Component.text(targetName, Colors.HOT_PINK))
                .append(Component.text("!", Colors.GRAY)));
    }

    private static int handleKeyClear(CommandContext<CommandSourceStack> ctx) {
        Player p = senderPlayer(ctx);
        String targetName = StringArgumentType.getString(ctx, "player");

        PlayerData target = PlayerManager.getByName(targetName);
        if (target == null) return err(p, "Player not found: " + targetName);

        if (!CrateManager.getKeyData().containsKey(target.getUniqueId()))
            return err(p, targetName + " has no crate keys!");

        CrateManager.clearKeys(target.getUniqueId());
        return ok(p, Component.text("Cleared all keys from ", Colors.GRAY)
                .append(Component.text(targetName, Colors.HOT_PINK))
                .append(Component.text("!", Colors.GRAY)));
    }

    private static int handleKeyList(CommandContext<CommandSourceStack> ctx) {
        Player p = senderPlayer(ctx);
        String targetName = StringArgumentType.getString(ctx, "player");

        PlayerData target = PlayerManager.getByName(targetName);
        if (target == null) return err(p, "Player not found: " + targetName);

        KeyData kd = CrateManager.getKeyData().get(target.getUniqueId());
        if (kd == null || kd.getKeys().isEmpty()) return err(p, targetName + " has no crate keys!");

        Component msg = Component.text(targetName, Colors.HOT_PINK)
                .append(Component.text("'s keys:", Colors.GRAY));

        for (Map.Entry<String, Long> entry : kd.getKeys().entrySet()) {
            msg = msg.append(Component.newline())
                    .append(Component.text(entry.getKey(), Colors.HOT_PINK))
                    .append(Component.text(" - ", Colors.GRAY))
                    .append(Component.text(entry.getValue(), Colors.HOT_PINK));
        }

        p.sendMessage(msg);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1f);
        return 1;
    }

    private static int handleKeyTotal(CommandContext<CommandSourceStack> ctx) {
        Player p = senderPlayer(ctx);

        Map<String, Long> totals = new HashMap<>();
        for (KeyData kd : CrateManager.getKeyData().values()) {
            for (Map.Entry<String, Long> entry : kd.getKeys().entrySet()) {
                totals.merge(entry.getKey(), entry.getValue(), Long::sum);
            }
        }

        if (totals.isEmpty()) return ok(p, Component.text("No keys exist for any crate.", Colors.GRAY));

        Component msg = Component.text("Total keys across all players:", Colors.GRAY);
        for (Map.Entry<String, Long> entry : totals.entrySet()) {
            msg = msg.append(Component.newline())
                    .append(Component.text(entry.getKey(), Colors.HOT_PINK))
                    .append(Component.text(" - ", Colors.GRAY))
                    .append(Component.text(entry.getValue(), Colors.HOT_PINK));
        }

        p.sendMessage(msg);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1f);
        return 1;
    }

    // ---------------------- Utility ---------------------- //

    private static Block feetBlock(Player p) {
        return p.getLocation().getBlock().getRelative(0, -1, 0);
    }

    private static boolean isValidStorage(Block block) {
        return block.getState() instanceof InventoryHolder;
    }

    private static void renameCrate(CrateData crate, String oldId, String newId) {
        CrateManager.removeCrate(oldId);
        crate.setId(newId);
        CrateManager.addCrate(crate);
        for (KeyData kd : CrateManager.getKeyData().values()) {
            if (!kd.hasKey(oldId)) continue;
            long count = kd.getKeyCount(oldId);
            kd.removeKey(oldId);
            kd.addKey(newId, count);
        }
    }

    private static void removeCrateAndKeys(CrateData crate) {
        String id = crate.getId();
        CrateManager.removeCrate(id);
        CrateManager.getKeyData().values().forEach(kd -> kd.removeKey(id));
    }

    private static long parseAmount(CommandContext<CommandSourceStack> ctx, String argName) {
        try {
            return (long) Text.parseAmountString(StringArgumentType.getString(ctx, argName));
        } catch (Exception e) {
            return 0L;
        }
    }

    private static int err(Player p, String message) {
        Component msg = Component.text(message, Colors.DARK_PINK);
        p.sendMessage(msg);
        p.sendActionBar(msg);
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        return 0;
    }

    private static int ok(Player p, Component msg) {
        p.sendMessage(msg);
        p.sendActionBar(msg);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1f);
        return 1;
    }
}