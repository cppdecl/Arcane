package cx.arcane.managers.commandManager.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import cx.arcane.Arcane;
import cx.arcane.managers.clanManager.ClanInvite;
import cx.arcane.managers.clanManager.ClanManager;
import cx.arcane.managers.clanManager.clanInfo.ClanData;
import cx.arcane.managers.clanManager.clanInfo.ClanMember;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.managers.teleportManager.TeleportManager;
import cx.arcane.utils.Colors;
import cx.arcane.utils.Text;
import dev.triumphteam.gui.paper.Gui;
import dev.triumphteam.gui.paper.builder.item.ItemBuilder;
import io.github.retrooper.packetevents.util.folia.FoliaScheduler;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.jspecify.annotations.NullMarked;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@NullMarked
public class ClanCommand {

    // private static final Component PREFIX = Text.stringToComponent("&8[<#9B3CE8>\uD83D\uDDE1&8] ");
    private static final Component PREFIX = Component.empty();

    public static LiteralCommandNode<CommandSourceStack> build(String alias) {
        return Commands.literal(alias)
                .requires(s -> s.getExecutor() instanceof Player)
                .then(Commands.literal("create")
                        .then(Commands.argument("tag", StringArgumentType.word())
                                .executes(ClanCommand::handleCreate)))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(ClanCommand::suggestOnlineNonMembers)
                                .executes(ClanCommand::handleInvite)))
                .then(Commands.literal("join")
                        .then(Commands.argument("tag", StringArgumentType.word())
                                .suggests(ClanCommand::suggestClanInvites)
                                .executes(ClanCommand::handleJoin)))
                .then(Commands.literal("leave")
                        .executes(ClanCommand::handleLeave))
                .then(Commands.literal("kick")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(ClanCommand::suggestMembersExceptSelf)
                                .executes(ClanCommand::handleKick)))
                .then(Commands.literal("transfer")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(ClanCommand::suggestMembersExceptSelf)
                                .executes(ClanCommand::handleTransfer)))
                .then(Commands.literal("sethome")
                        .executes(ClanCommand::handleSetHome))
                .then(Commands.literal("delhome")
                        .executes(ClanCommand::handleDelHome))
                .then(Commands.literal("home")
                        .executes(ClanCommand::handleHome))
                .then(Commands.literal("info")
                        .executes(ClanCommand::handleInfo))
                .then(Commands.literal("top")
                        .executes(ClanCommand::handleTop))
                .then(Commands.literal("disband")
                        .executes(ClanCommand::handleDisband))
                .then(Commands.literal("settings")
                        .then(Commands.literal("AllowFriendlyFire")
                                .executes(ClanCommand::handleFriendlyFireStatus)
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .suggests(ClanCommand::suggestBoolean)
                                        .executes(ClanCommand::handleFriendlyFireToggle))))
                .build();
    }

    // -------------------------------------------------------------------------
    // Suggestions
    // -------------------------------------------------------------------------

    private static CompletableFuture<Suggestions> suggestBoolean(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
        if ("true".startsWith(typed))  builder.suggest("true");
        if ("false".startsWith(typed)) builder.suggest("false");
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestClanInvites(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        if (!(ctx.getSource().getExecutor() instanceof Player p)) return builder.buildFuture();
        String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (ClanInvite invite : ClanManager.getPlayerReceivedInvites(p.getUniqueId())) {
            ClanData clan = ClanManager.getByUniqueId(invite.getClanId());
            if (clan == null) continue;
            if (clan.getTag().toLowerCase(Locale.ROOT).startsWith(typed)) builder.suggest(clan.getTag());
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestMembersExceptSelf(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        if (!(ctx.getSource().getExecutor() instanceof Player p)) return builder.buildFuture();
        ClanData clan = ClanManager.getPlayerClan(p.getUniqueId());
        if (clan == null) return builder.buildFuture();
        String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (ClanMember member : clan.getMembers().values()) {
            if (member.getUniqueId().equals(p.getUniqueId())) continue;
            PlayerData pData = PlayerManager.getByUniqueId(member.getUniqueId());
            if (pData == null) continue;
            if (pData.getUsername().toLowerCase(Locale.ROOT).startsWith(typed)) builder.suggest(pData.getUsername());
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestOnlineNonMembers(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        if (!(ctx.getSource().getExecutor() instanceof Player p)) return builder.buildFuture();
        ClanData clan = ClanManager.getPlayerClan(p.getUniqueId());
        if (clan == null) return builder.buildFuture();
        String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (PlayerData pData : PlayerManager.getOnline()) {
            if (pData.getUniqueId().equals(p.getUniqueId())) continue;
            ClanData targetClan = ClanManager.getPlayerClan(pData.getUniqueId());
            if (targetClan != null && targetClan.getUniqueId().equals(clan.getUniqueId())) continue;
            if (pData.getUsername().toLowerCase(Locale.ROOT).startsWith(typed)) builder.suggest(pData.getUsername());
        }
        return builder.buildFuture();
    }

    // -------------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------------

    private static int handleCreate(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player p)) return 0;

        String clanTag = StringArgumentType.getString(ctx, "tag").toUpperCase(Locale.ROOT);

        if (clanTag.length() != 3)                     return err(p, "Clan tag must be exactly 3 characters!");
        if (!clanTag.matches("^[A-Z0-9]{3}$"))         return err(p, "Clan tag can only contain letters and numbers!");
        if (ClanManager.hasClan(p.getUniqueId()))      return err(p, "You're already in a clan!");
        if (ClanManager.isNameTaken(clanTag))          return err(p, "That clan tag is already taken!");

        schedule(p, () -> Gui.of(3)
                .title(Text.toSmallCapsComponent("Confirm Clan Creation"))
                .statelessComponent(con -> {
                    con.setItem(2, 5, ItemBuilder.from(Material.NETHERITE_HELMET)
                            .name(Text.toSmallCapsComponent("Clan Tag").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                            .lore(Component.text(clanTag, Colors.WHITE).decoration(TextDecoration.ITALIC, false))
                            .flags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS)
                            .asGuiItem());

                    con.setItem(2, 3, ItemBuilder.from(Material.RED_STAINED_GLASS_PANE)
                            .name(Text.toSmallCapsComponent("Cancel").color(Colors.RED).decoration(TextDecoration.ITALIC, false))
                            .lore(Component.text("Click to cancel", Colors.WHITE).decoration(TextDecoration.ITALIC, false))
                            .asGuiItem((player, event) -> {
                                player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                event.guiView().close();
                            }));

                    con.setItem(2, 7, ItemBuilder.from(Material.LIME_STAINED_GLASS_PANE)
                            .name(Text.toSmallCapsComponent("Confirm").color(TextColor.color(0x00ff00)).decoration(TextDecoration.ITALIC, false))
                            .lore(Component.text("Click to confirm clan creation", Colors.WHITE).decoration(TextDecoration.ITALIC, false))
                            .asGuiItem((player, event) -> {
                                event.guiView().close();
                                player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);

                                if (ClanManager.hasClan(player.getUniqueId())) { err(player, "You're already in a clan!"); return; }
                                if (ClanManager.isNameTaken(clanTag))          { err(player, "That clan tag is already taken!"); return; }

                                Component broadcast = Component.text()
                                        .append(PREFIX)
                                        .append(Component.text("Clan ", Colors.WHITE))
                                        .append(Component.text(clanTag, Colors.HOT_PINK))
                                        .append(Component.text(" has been created by ", Colors.WHITE))
                                        .append(Component.text(player.getName(), Colors.HOT_PINK))
                                        .append(Component.text(".", Colors.WHITE))
                                        .build();

                                PlayerManager.broadcast(broadcast);

                                ClanManager.newClan(clanTag, player.getUniqueId());

                                ok(player, Component.text()
                                        .append(PREFIX)
                                        .append(Component.text("Clan ", Colors.WHITE))
                                        .append(Component.text(clanTag, Colors.HOT_PINK))
                                        .append(Component.text(" has been created successfuly!", Colors.WHITE))
                                        .build());
                            }));
                })
                .build()
                .open(p));

        return 1;
    }

    private static int handleInvite(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player p)) return 0;

        ClanData clan = ClanManager.getPlayerClan(p.getUniqueId());
        if (clan == null) return err(p, "You're not in a clan!");

        ClanMember leader = clan.getLeader();
        if (leader == null || !leader.getUniqueId().equals(p.getUniqueId())) return err(p, "Only the leader can invite members.");

        String targetName = StringArgumentType.getString(ctx, "player");
        PlayerData targetData = PlayerManager.getByName(targetName);
        if (targetData == null)                                                       return err(p, "That player does not exist!");
        if (targetData.getUniqueId().equals(p.getUniqueId()))                        return err(p, "You can't invite yourself!");
        if (ClanManager.hasClan(targetData.getUniqueId()))                           return err(p, "That player is already in a clan!");
        if (ClanManager.hasValidInvite(targetData.getUniqueId(), clan.getUniqueId())) return err(p, "That player has already been invited!");

        ClanManager.inviteToClan(clan.getUniqueId(), p.getUniqueId(), targetData.getUniqueId());

        clan.broadcast(Component.text()
                .append(PREFIX)
                .append(Component.text(targetData.getUsername(), Colors.HOT_PINK))
                .append(Component.text(" has been invited by ", Colors.WHITE))
                .append(Component.text(p.getName(), Colors.HOT_PINK))
                .append(Component.text(" to join the clan!", Colors.WHITE))
                .build());

        Player target = targetData.getPlayer();
        if (target != null) {
            send(target, Component.text()
                    .append(PREFIX)
                    .append(Component.text("You've been invited by ", Colors.WHITE))
                    .append(Component.text(p.getName(), Colors.HOT_PINK))
                    .append(Component.text(" to join ", Colors.WHITE))
                    .append(Component.text(clan.getTag(), Colors.HOT_PINK))
                    .append(Component.text(" — click to accept!", Colors.WHITE))
                    .build()
                    .clickEvent(ClickEvent.runCommand("/clan join " + clan.getTag())));
        }

        return 1;
    }

    private static int handleJoin(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player p)) return 0;

        if (ClanManager.hasClan(p.getUniqueId())) return err(p, "You're already in a clan!");

        String clanTag = StringArgumentType.getString(ctx, "tag").toUpperCase(Locale.ROOT);
        ClanData clan = ClanManager.getByName(clanTag);
        if (clan == null)                                                      return err(p, "That clan does not exist!");
        if (!ClanManager.hasValidInvite(p.getUniqueId(), clan.getUniqueId())) return err(p, "You need a clan invitation to join.");
        if (!ClanManager.acceptInvite(p.getUniqueId(), clan.getUniqueId()))   return err(p, "Failed to join clan.");

        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);

        clan.broadcast(Component.text()
                .append(PREFIX)
                .append(Component.text(p.getName(), Colors.HOT_PINK))
                .append(Component.text(" has joined the clan!", Colors.WHITE))
                .build());

        return 1;
    }

    private static int handleLeave(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player p)) return 0;

        ClanData clan = ClanManager.getPlayerClan(p.getUniqueId());
        if (clan == null) return err(p, "You're not in a clan!");

        ClanMember member = clan.getMember(p.getUniqueId());
        if (member == null)                                return err(p, "Internal error.");
        if ("Leader".equalsIgnoreCase(member.getRank()))   return err(p, "Disband the clan or transfer leadership first!");

        clan.broadcast(Component.text()
                .append(PREFIX)
                .append(Component.text(p.getName(), Colors.HOT_PINK))
                .append(Component.text(" has left the clan.", Colors.WHITE))
                .build());

        clan.removeMember(p.getUniqueId());
        return 1;
    }

    private static int handleKick(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player p)) return 0;

        ClanData clan = ClanManager.getPlayerClan(p.getUniqueId());
        if (clan == null) return err(p, "You're not in a clan!");

        ClanMember leader = clan.getLeader();
        if (leader == null || !leader.getUniqueId().equals(p.getUniqueId())) return err(p, "Only the clan leader can kick members!");

        String targetName = StringArgumentType.getString(ctx, "player");
        PlayerData targetData = PlayerManager.getByName(targetName);
        if (targetData == null) return err(p, "That player does not exist!");

        ClanMember targetMember = clan.getMember(targetData.getUniqueId());
        if (targetMember == null)                                       return err(p, "That player is not in your clan!");
        if (targetMember.getUniqueId().equals(p.getUniqueId()))        return err(p, "You cannot kick yourself!");

        clan.broadcast(Component.text()
                .append(PREFIX)
                .append(Component.text(targetData.getUsername(), Colors.HOT_PINK))
                .append(Component.text(" has been kicked by ", Colors.WHITE))
                .append(Component.text(p.getName(), Colors.HOT_PINK))
                .append(Component.text(".", Colors.WHITE))
                .build());

        clan.removeMember(targetMember.getUniqueId());
        return 1;
    }

    private static int handleTransfer(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player p)) return 0;

        ClanData clan = ClanManager.getPlayerClan(p.getUniqueId());
        if (clan == null) return err(p, "You're not in a clan!");

        ClanMember leader = clan.getLeader();
        if (leader == null || !leader.getUniqueId().equals(p.getUniqueId())) return err(p, "Only the leader can transfer ownership!");

        String targetName = StringArgumentType.getString(ctx, "player");
        PlayerData targetData = PlayerManager.getByName(targetName);
        if (targetData == null) return err(p, "That player does not exist!");

        ClanMember targetMember = clan.getMember(targetData.getUniqueId());
        if (targetMember == null)                                      return err(p, "That player is not in your clan!");
        if (targetMember.getUniqueId().equals(p.getUniqueId()))       return err(p, "You are already the leader!");

        leader.setRank("Member");
        targetMember.setRank("Leader");

        clan.broadcast(Component.text()
                .append(PREFIX)
                .append(Component.text(p.getName(), Colors.HOT_PINK))
                .append(Component.text(" transferred leadership to ", Colors.WHITE))
                .append(Component.text(targetData.getUsername(), Colors.HOT_PINK))
                .append(Component.text(".", Colors.WHITE))
                .build());

        return 1;
    }

    private static int handleSetHome(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player p)) return 0;

        ClanData clan = ClanManager.getPlayerClan(p.getUniqueId());
        if (clan == null) return err(p, "You're not in a clan!");

        ClanMember leader = clan.getLeader();
        if (leader == null || !leader.getUniqueId().equals(p.getUniqueId())) return err(p, "Only the leader can set the clan home!");

        clan.setHome(p.getLocation());

        clan.broadcast(Component.text()
                .append(PREFIX)
                .append(Component.text("Clan home updated by ", Colors.WHITE))
                .append(Component.text(p.getName(), Colors.HOT_PINK))
                .append(Component.text(".", Colors.WHITE))
                .build());

        return 1;
    }

    private static int handleDelHome(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player p)) return 0;

        ClanData clan = ClanManager.getPlayerClan(p.getUniqueId());
        if (clan == null) return err(p, "You're not in a clan!");

        ClanMember leader = clan.getLeader();
        if (leader == null || !leader.getUniqueId().equals(p.getUniqueId())) return err(p, "Only the leader can delete the clan home!");
        if (clan.getHome() == null)                                           return err(p, "Clan home hasn't been set!");

        clan.setHome(null);

        clan.broadcast(Component.text()
                .append(PREFIX)
                .append(Component.text("Clan home deleted by ", Colors.WHITE))
                .append(Component.text(p.getName(), Colors.HOT_PINK))
                .append(Component.text(".", Colors.WHITE))
                .build());

        return 1;
    }

    private static int handleHome(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player p)) return 0;

        ClanData clan = ClanManager.getPlayerClan(p.getUniqueId());
        if (clan == null)           return err(p, "You're not in a clan!");
        if (clan.getHome() == null) return err(p, "Clan home hasn't been set!");

        TeleportManager.teleport(p, clan.getHome()).onTeleport(() -> {
            p.showTitle(Title.title(
                    Text.toSmallCapsComponent("Clan Home").color(Colors.HOT_PINK),
                    Component.text("arcane.cx", Colors.WHITE),
                    Title.Times.times(Duration.ofMillis(800), Duration.ofMillis(3000), Duration.ofMillis(1000))
            ));
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.MASTER, 1f, 1f);
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 1f, 1f);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP,      SoundCategory.MASTER, 1f, 1f);
        }).start();

        return 1;
    }

    private static int handleFriendlyFireStatus(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player p)) return 0;

        ClanData clan = ClanManager.getPlayerClan(p.getUniqueId());
        if (clan == null) return err(p, "You're not in a clan!");

        boolean enabled = clan.isFriendlyFireEnabled();
        send(p, Component.text()
                .append(PREFIX)
                .append(Component.text("Friendly Fire is ", Colors.WHITE))
                .append(Component.text(enabled ? "ENABLED" : "DISABLED", enabled ? Colors.HOT_PINK : Colors.RED))
                .build());

        return 1;
    }

    private static int handleFriendlyFireToggle(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player p)) return 0;

        ClanData clan = ClanManager.getPlayerClan(p.getUniqueId());
        if (clan == null) return err(p, "You're not in a clan!");

        ClanMember leader = clan.getLeader();
        if (leader == null || !leader.getUniqueId().equals(p.getUniqueId())) return err(p, "Only the leader can change clan settings!");

        String value = StringArgumentType.getString(ctx, "value").toLowerCase(Locale.ROOT);
        if (!value.equals("true") && !value.equals("false")) return err(p, "Value must be true or false!");

        boolean newValue = Boolean.parseBoolean(value);
        clan.setFriendlyFireEnabled(newValue);

        clan.broadcast(Component.text()
                .append(PREFIX)
                .append(Component.text("Friendly Fire ", Colors.WHITE))
                .append(Component.text(newValue ? "ENABLED" : "DISABLED", newValue ? Colors.HOT_PINK : Colors.RED))
                .append(Component.text(" by ", Colors.WHITE))
                .append(Component.text(p.getName(), Colors.HOT_PINK))
                .build());

        return 1;
    }

    private static int handleInfo(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player p)) return 0;

        ClanData clan = ClanManager.getPlayerClan(p.getUniqueId());
        if (clan == null) return err(p, "You're not in a clan!");

        ClanMember leader = clan.getLeader();
        PlayerData leaderData = leader != null ? PlayerManager.getByUniqueId(leader.getUniqueId()) : null;
        if (leaderData == null) return err(p, "Internal error.");

        Component membersComponent = Component.empty();
        boolean first = true;
        for (ClanMember member : clan.getMemberList()) {
            PlayerData mData = PlayerManager.getByUniqueId(member.getUniqueId());
            if (mData == null) continue;
            Player online = mData.getPlayer();
            TextColor color = online != null ? Colors.HOT_PINK : Colors.WHITE;
            if (!first) membersComponent = membersComponent.append(Component.text(", ", NamedTextColor.DARK_GRAY));
            membersComponent = membersComponent.append(Component.text(mData.getUsername(), color));
            first = false;
        }

        double kda = clan.getDeaths() == 0 ? clan.getKills() : (double) clan.getKills() / clan.getDeaths();

        p.sendMessage(Text.toSmallCapsComponent("❖ Clan Info").color(Colors.HOT_PINK));
        p.sendMessage(Component.text()
                .append(label(" • Tag: "))
                .append(Component.text("" + clan.getTag() + "", Colors.LIGHT_PINK))
                .append(Component.text(" (", Colors.WHITE))
                .append(Component.text("Top #" + ClanManager.getClanPosition(clan.getUniqueId()), Colors.HOT_PINK))
                .append(Component.text(", ", NamedTextColor.DARK_GRAY))
                .append(Component.text(clan.getMemberCount() + " Members", Colors.LIGHT_PINK))
                .append(Component.text(")", Colors.WHITE))
                .append(Component.newline())
                .append(label(" • Created At: "))
                .append(Component.text(Text.instantToTimestamp(clan.getCreatedAt(), "UTC"), Colors.WHITE))
                .append(Component.newline())
                .append(label(" • Leader: "))
                .append(Component.text(leaderData.getUsername(), Colors.WHITE))
                .append(Component.newline())
                .append(label(" • Record: "))
                .append(Component.text(clan.getKills(), Colors.LIGHT_PINK))
                .append(Component.text(" Kills, ", Colors.WHITE))
                .append(Component.text(clan.getDeaths(), Colors.LIGHT_PINK))
                .append(Component.text(" Deaths ", Colors.WHITE))
                .append(Component.text("(", Colors.HOT_PINK))
                .append(Component.text(String.format("%.2f", kda), Colors.LIGHT_PINK))
                .append(Component.text(" KD)", Colors.HOT_PINK))
                .append(Component.newline())
                .append(label(" • Friendly Fire: "))
                .append(Component.text(clan.isFriendlyFireEnabled() ? "Enabled" : "Disabled",
                        clan.isFriendlyFireEnabled() ? Colors.HOT_PINK : Colors.WHITE))
                .append(Component.newline())
                .append(label(" • Members: "))
                .append(membersComponent)
                .append(Component.text(" (", Colors.HOT_PINK))
                .append(Component.text(clan.getMemberCount(), Colors.LIGHT_PINK))
                .append(Component.text(")", Colors.HOT_PINK))
                .build());

        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        return 1;
    }

    private static int handleTop(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player p)) return 0;

        List<ClanData> clans = ClanManager.getTopClansByKills(10);

        p.sendMessage(Text.toSmallCapsComponent("❖ Top Clans").color(Colors.HOT_PINK));

        int rank = 1;
        for (ClanData clan : clans) {
            double kda = clan.getDeaths() == 0 ? clan.getKills() : (double) clan.getKills() / clan.getDeaths();
            p.sendMessage(Component.text()
                    .append(Component.text(" #" + rank, Colors.HOT_PINK))
                    .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                    .append(Component.text("" + clan.getTag() + "", Colors.LIGHT_PINK))
                    .append(Component.text(" (", Colors.WHITE))
                    .append(Component.text(clan.getMemberCount() + " Members", Colors.LIGHT_PINK))
                    .append(Component.text(", ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(clan.getKills() + " Kills", Colors.LIGHT_PINK))
                    .append(Component.text(", ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(String.format("%.2f", kda) + " KD", Colors.LIGHT_PINK))
                    .append(Component.text(")", Colors.WHITE))
                    .build());
            rank++;
        }

        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        return 1;
    }

    private static int handleDisband(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player p)) return 0;

        ClanData clan = ClanManager.getPlayerClan(p.getUniqueId());
        if (clan == null) return err(p, "You're not in a clan!");

        ClanMember leader = clan.getLeader();
        if (leader == null || !leader.getUniqueId().equals(p.getUniqueId())) {
            return err(p, "Only the clan leader can disband the clan!");
        }

        schedule(p, () -> Gui.of(3)
                .title(Text.toSmallCapsComponent("Confirm Clan Disband"))
                .statelessComponent(con -> {

                    // Info item
                    con.setItem(2, 5, ItemBuilder.from(Material.TNT)
                            .name(Text.toSmallCapsComponent("Disband Clan")
                                    .color(Colors.RED)
                                    .decoration(TextDecoration.ITALIC, false))
                            .lore(
                                    Component.text("Clan: ", Colors.WHITE)
                                            .append(Component.text(clan.getTag(), Colors.HOT_PINK))
                                            .decoration(TextDecoration.ITALIC, false),
                                    Component.text("This action is irreversible!", Colors.RED)
                                            .decoration(TextDecoration.ITALIC, false)
                            )
                            .flags(ItemFlag.HIDE_ATTRIBUTES)
                            .asGuiItem());

                    // Cancel button
                    con.setItem(2, 3, ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                            .name(Text.toSmallCapsComponent("Cancel")
                                    .color(Colors.WHITE)
                                    .decoration(TextDecoration.ITALIC, false))
                            .lore(Component.text("Click to go back", Colors.WHITE)
                                    .decoration(TextDecoration.ITALIC, false))
                            .asGuiItem((player, event) -> {
                                player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                event.guiView().close();
                            }));

                    // Confirm button
                    con.setItem(2, 7, ItemBuilder.from(Material.RED_STAINED_GLASS_PANE)
                            .name(Text.toSmallCapsComponent("CONFIRM DISBAND")
                                    .color(Colors.RED)
                                    .decoration(TextDecoration.ITALIC, false))
                            .lore(Component.text("Click to permanently delete the clan", Colors.WHITE)
                                    .decoration(TextDecoration.ITALIC, false))
                            .asGuiItem((player, event) -> {
                                event.guiView().close();
                                player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);

                                ClanData liveClan = ClanManager.getPlayerClan(player.getUniqueId());
                                if (liveClan == null) {
                                    err(player, "Clan no longer exists.");
                                    return;
                                }

                                ClanMember liveLeader = liveClan.getLeader();
                                if (liveLeader == null || !liveLeader.getUniqueId().equals(player.getUniqueId())) {
                                    err(player, "You are no longer the leader.");
                                    return;
                                }

                                // Snapshot members
                                List<ClanMember> members = new ArrayList<>(liveClan.getMemberList());

                                Component broadcast = Component.text()
                                        .append(PREFIX)
                                        .append(Component.text("Clan ", Colors.WHITE))
                                        .append(Component.text(liveClan.getTag(), Colors.HOT_PINK))
                                        .append(Component.text(" has been disbanded by ", Colors.WHITE))
                                        .append(Component.text(player.getName(), Colors.HOT_PINK))
                                        .append(Component.text(".", Colors.WHITE))
                                        .build();

                                PlayerManager.broadcast(broadcast);

                                // Notify online members
                                for (ClanMember member : members) {
                                    PlayerData data = PlayerManager.getByUniqueId(member.getUniqueId());
                                    if (data != null && data.getPlayer() != null) {
                                        send(data.getPlayer(), Component.text()
                                                .append(PREFIX)
                                                .append(Component.text("Your clan has been disbanded.", Colors.DARK_PINK))
                                                .build());
                                    }
                                }

                                ClanManager.deleteClan(liveClan.getUniqueId());

                                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                            }));

                })
                .build()
                .open(p));

        return 1;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Component label(String text) {
        return Component.text(text, Colors.HOT_PINK);
    }

    private static void schedule(Player p, Runnable task) {
        FoliaScheduler.getEntityScheduler().execute(p, Arcane.getPlugin(), task, null, 1L);
    }

    private static void send(Player p, Component msg) {
        p.sendMessage(msg);
        p.sendActionBar(msg);
    }

    private static int ok(Player p, Component msg) {
        p.sendMessage(msg);
        p.sendActionBar(msg);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        return 1;
    }

    private static int err(Player p, String message) {
        Component msg = Component.text(message, Colors.DARK_PINK);
        p.sendMessage(msg);
        p.sendActionBar(msg);
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        return 0;
    }
}