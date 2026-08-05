package cx.arcane.managers.commandManager.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import cx.arcane.managers.homeManager.HomeData;
import cx.arcane.managers.homeManager.HomeManager;
import cx.arcane.managers.teleportManager.TeleportManager;
import cx.arcane.utils.Colors;
import cx.arcane.utils.LocationUtils;
import cx.arcane.utils.Text;
import dev.triumphteam.gui.paper.Gui;
import dev.triumphteam.gui.paper.builder.item.ItemBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("UnstableApiUsage")
public class HomeCommand {

    public static LiteralCommandNode<CommandSourceStack> buildHomes(String alias) {
        return Commands.literal(alias)
                .executes(HomeCommand::handleHomes)
                .build();
    }

    public static LiteralCommandNode<CommandSourceStack> buildHome(String alias) {
        return Commands.literal(alias)
                .executes(HomeCommand::handleHomesGui)
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(HomeCommand::suggestHomes)
                        .executes(HomeCommand::handleHome))
                .build();
    }

    public static LiteralCommandNode<CommandSourceStack> buildSetHome(String alias) {
        return Commands.literal(alias)
                .then(Commands.argument("id", StringArgumentType.word())
                        .executes(HomeCommand::handleSetHome))
                .build();
    }

    public static LiteralCommandNode<CommandSourceStack> buildDelHome(String alias) {
        return Commands.literal(alias)
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(HomeCommand::suggestHomes)
                        .executes(HomeCommand::handleDelHome))
                .build();
    }

    private static Player sender(CommandContext<CommandSourceStack> ctx) {
        return (Player) ctx.getSource().getExecutor();
    }

    private static CompletableFuture<Suggestions> suggestHomes(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        UUID owner = sender(ctx).getUniqueId();
        String typed = builder.getRemaining().toLowerCase();
        for (HomeData hd : HomeManager.getHomes(owner)) {
            if (hd.getId().toLowerCase().startsWith(typed)) builder.suggest(hd.getId());
        }
        return builder.buildFuture();
    }

    private static int handleHomes(CommandContext<CommandSourceStack> ctx) {
        return handleHomesGui(ctx);
    }

    private static int handleHomesGui(CommandContext<CommandSourceStack> ctx) {
        Player p = sender(ctx);
        openHomesGui(p);
        return 1;
    }

    private static int handleHome(CommandContext<CommandSourceStack> ctx) {
        Player p = sender(ctx);
        String id = StringArgumentType.getString(ctx, "id").toLowerCase();

        HomeData home = HomeManager.getHome(p.getUniqueId(), id);
        if (home == null) return err(p, "No home named " + id + " found!");

        TeleportManager.teleport(p, home.getLocation()).name("Home " + home.getId()).onTeleport(() -> {
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.MASTER, 1, 1);
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER,1, 1);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER,1, 1);
        }).start();

        return 1;
    }

    private static int handleSetHome(CommandContext<CommandSourceStack> ctx) {
        Player p = sender(ctx);
        String id = StringArgumentType.getString(ctx, "id").toLowerCase();

        if (HomeManager.getHome(p.getUniqueId(), id) != null) {
            return err(p, "That home already exists!");
        }

        if (!HomeManager.canCreateHome(p.getUniqueId())) {
            return err(p, "You have reached the maximum amount of homes!");
        }

        if (id.isEmpty() || id.length() > 64) {
            return err(p, "Home ID must be between 1 and 64 characters!");
        }

        HomeManager.setHome(p.getUniqueId(), id, p.getLocation());

        Component msg = Component.text("Home ", Colors.GRAY).append(Component.text(id, Colors.HOT_PINK)).append(Component.text(" has been created!", Colors.GRAY));

        return ok(p, msg);
    }

    private static int handleDelHome(CommandContext<CommandSourceStack> ctx) {
        Player p = sender(ctx);
        String id = StringArgumentType.getString(ctx, "id").toLowerCase();

        if (!HomeManager.deleteHome(p.getUniqueId(), id)) return err(p, "Home " + id + " does not exist!");

        return ok(p, Component.text("Home ", Colors.GRAY)
                .append(Component.text(id, Colors.HOT_PINK))
                .append(Component.text(" has been deleted!", Colors.GRAY)));
    }

    private static void openHomesGui(Player p) {
        p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1f, 1f);

        Collection<HomeData> homes = HomeManager.getHomes(p.getUniqueId());
        HomeData[] slots = new HomeData[6];
        int i = 0;
        for (HomeData hd : homes) {
            if (i >= 6) break;
            slots[i++] = hd;
        }

        Gui.of(3)
                .title(Text.toSmallCapsComponent("Homes"))
                .spamPreventionDuration(110)
                .statelessComponent(con -> {
                    for (int slot = 0; slot < 5; slot++) {
                        HomeData home = slots[slot];
                        int col = 3 + slot;

                        if (home == null) {
                            con.setItem(2, col, ItemBuilder.from(Material.GRAY_BED)
                                    .name(Component.text(Text.toSmallCaps("Empty Home"), Colors.GRAY).decoration(TextDecoration.ITALIC, false))
                                    .asGuiItem());
                        } else {
                            con.setItem(2, col, ItemBuilder.from(Material.PINK_BED)
                                    .name(Component.text(Text.toSmallCaps("Home - ") + home.getId(), Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                                    .lore(Component.text("Click to Teleport", Colors.WHITE).decoration(TextDecoration.ITALIC, false))
                                    .asGuiItem((player, ctx) -> {
                                        ctx.guiView().close();
                                        TeleportManager.teleport(player, home.getLocation()).name("Home " + home.getId()).onTeleport(() -> {
                                            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.MASTER, 1, 1);
                                            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER,1, 1);
                                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER,1, 1);
                                        }).start();
                                    }));
                        }
                    }
                })
                .build()
                .open(p);
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