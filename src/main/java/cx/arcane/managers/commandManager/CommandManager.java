package cx.arcane.managers.commandManager;

import cx.arcane.Arcane;
import cx.arcane.managers.commandManager.commands.*;
import cx.arcane.managers.commandManager.arguments.currencyArgument.Currency;
import cx.arcane.managers.commandManager.arguments.currencyArgument.CurrencyArgumentHandler;
import cx.arcane.managers.commandManager.arguments.enchantmentArgument.EnchantmentArgument;
import cx.arcane.managers.commandManager.arguments.enchantmentArgument.EnchantmentArgumentHandler;
import cx.arcane.managers.commandManager.arguments.itemArgument.ItemArgumentHandler;
import cx.arcane.managers.commandManager.arguments.onlinePlayerArgument.OnlinePlayerArgument;
import cx.arcane.managers.commandManager.arguments.onlinePlayerArgument.OnlinePlayerArgumentHandler;
import cx.arcane.managers.commandManager.arguments.onlinePlayerExceptMeArgument.OnlinePlayerExceptMeArgument;
import cx.arcane.managers.commandManager.arguments.onlinePlayerExceptMeArgument.OnlinePlayerExceptMeArgumentHandler;
import cx.arcane.managers.commandManager.arguments.playerArgument.PlayerArgument;
import cx.arcane.managers.commandManager.arguments.playerArgument.PlayerArgumentHandler;
import cx.arcane.managers.commandManager.arguments.trimMaterialArgument.TrimMaterialArgument;
import cx.arcane.managers.commandManager.arguments.trimMaterialArgument.TrimMaterialArgumentHandler;
import cx.arcane.managers.commandManager.arguments.trimPatternArgument.TrimPatternArgument;
import cx.arcane.managers.commandManager.arguments.trimPatternArgument.TrimPatternArgumentHandler;
import cx.arcane.utils.Colors;
import cx.arcane.utils.Text;
import dev.rollczi.litecommands.LiteCommands;
import dev.rollczi.litecommands.adventure.LiteAdventureExtension;
import dev.rollczi.litecommands.bukkit.LiteBukkitFactory;
import dev.rollczi.litecommands.bukkit.LiteBukkitMessages;
import dev.rollczi.litecommands.folia.FoliaExtension;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;

import java.util.List;

public class CommandManager {

    private static LiteCommands<CommandSender> manager;
    private static MiniMessage miniMessageSerializer;

    public static void onEnable() {

        Bukkit.getPluginManager().registerEvents(new CommandListener(), Arcane.getPlugin());

        manager = LiteBukkitFactory.builder("acx", Arcane.getPlugin(), Arcane.getPlugin().getServer())
                .commands(
                        new LoginCommand(),
                        new RegisterCommand(),
                        new SetSpawnCommand(),
                        new SetAuthSpawnCommand(),
                        new StopCommand(),
                        new SpawnCommand(),
                        new AuthSpawnCommand(),
                        new TPACommand(),
                        new TPAAcceptCommand(),
                        new TPADenyCommand(),
                        new TPAHereCommand(),
                        new SettingsCommand(),
                        new BalanceCommand(),
                        new PayCommand(),
                        new EcoCommand(),
                        new AnonCommand(),
                        new BackCommand(),
                        new PingCommand(),
                        new EnchantCommand(),
                        new TrimCommand(),
                        new SkinCommand(),
                        new ZeusCommand(),
                        new SellCommand(),
                        new ShopCommand(),
                        new RTPCommand(),
                        new NightVisionCommand(),
                        new UpdateCommand(),
                        new SessionsCommand(),
                        new PInfoCommand(),
                        new ReavelChunksCommand(),
                        new DebugChunksCommand(),
                        new DebugMobSpawningCommand(),
                        new DisconnectCommand(),
                        new VanishCommand(),
                        new StupidCommand()
                )
                .argument(Material.class, new ItemArgumentHandler())
                .argument(PlayerArgument.class, new PlayerArgumentHandler())
                .argument(OnlinePlayerArgument.class, new OnlinePlayerArgumentHandler())
                .argument(OnlinePlayerExceptMeArgument.class, new OnlinePlayerExceptMeArgumentHandler())
                .argument(Currency.class, new CurrencyArgumentHandler())
                .argument(EnchantmentArgument.class, new EnchantmentArgumentHandler())
                .argument(TrimPatternArgument.class, new TrimPatternArgumentHandler())
                .argument(TrimMaterialArgument.class, new TrimMaterialArgumentHandler())
                .extension(new FoliaExtension(Arcane.getPlugin()))
                .extension(new LiteAdventureExtension<>(), config -> config
                        .miniMessage(true)
                        .legacyColor(true)
                        .colorizeArgument(true)
                        .serializer(miniMessageSerializer)
                )
                .message(LiteBukkitMessages.MISSING_PERMISSIONS, input -> Component.text("You don't have permission.", Colors.DARK_PINK))
                .message(LiteBukkitMessages.INVALID_USAGE, input -> Component.text("Invalid command usage.", Colors.DARK_PINK))
                .build();

        Arcane.getPlugin().getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {


            Component discordMessage =
                    Component.empty()
                            .append(Component.newline())
                            .append(Text.toSmallCapsComponent("Join Arcane's Official Discord Server!").color(Colors.HOT_PINK))
                            .append(Component.newline())
                            .append(
                                    Component.text("➵ ", Colors.HOT_PINK)
                                            .append(Component.text("discord.arcane.cx", NamedTextColor.WHITE)
                                                    .decorate(TextDecoration.UNDERLINED)
                                                    .clickEvent(ClickEvent.openUrl("https://discord.gg/aDUpKVc3rb")))
                            )
                            .append(Component.newline());


            Component storeMessage =
                    Component.empty()
                            .append(Component.newline())
                            .append(Text.toSmallCapsComponent("Visit Arcane's Webstore to Purchase Ranks & Cool Perks!").color(Colors.HOT_PINK))
                            .append(Component.newline())
                            .append(
                                    Component.text("➵ ", Colors.HOT_PINK)
                                            .append(Component.text("store.arcane.cx", NamedTextColor.WHITE)
                                                    .decorate(TextDecoration.UNDERLINED)
                                                    .clickEvent(ClickEvent.openUrl("https://store.arcane.cx")))
                            )
                            .append(Component.newline());

            List<String> voteSites = List.of(
                    "https://minecraft-mp.com/server/352157/vote",
                    "https://minecraft.buzz/vote/15548",
                    "https://topminecraftservers.org/vote/41160"
            );

            Component voteMessage = Component.empty()
                    .append(Component.newline())
                    .append(
                            Text.toSmallCapsComponent(
                                    "Vote for Arcane and Receive Cool Perks & Rewards!"
                            ).color(Colors.HOT_PINK)
                    )
                    .append(Component.newline()).append(Component.newline());

            int index = 1;

            for (String site : voteSites) {
                if (site == null || site.isEmpty()) continue;

                Component line = Component.text("➵ ", Colors.HOT_PINK)
                        .append(
                                Component.text("Vote Site #" + index + " ", NamedTextColor.WHITE)
                                        .clickEvent(ClickEvent.openUrl(site))
                        )
                        .append(
                                Component.text("| ", Colors.HOT_PINK)
                        )
                        .append(
                                Component.text("v" + index + ".arcane.cx", NamedTextColor.WHITE)
                                        .decorate(TextDecoration.UNDERLINED)
                                        .clickEvent(ClickEvent.openUrl(site)) // "Click!" clickable
                        )
                        .append(
                                Component.text(" | ", Colors.HOT_PINK)
                        )
                        .append(
                                Component.text("Click Link!", NamedTextColor.WHITE)
                                        .decorate(TextDecoration.UNDERLINED)
                                        .clickEvent(ClickEvent.openUrl(site)) // "Click!" clickable
                        )
                        .append(
                                Component.text("", Colors.HOT_PINK)
                        );

                voteMessage = voteMessage.append(line).append(Component.newline());
                index++;
            }

            commands.registrar().register(LinkCommand.build("discord", discordMessage, Sound.BLOCK_NOTE_BLOCK_COW_BELL));
            commands.registrar().register(LinkCommand.build("store", storeMessage, Sound.BLOCK_NOTE_BLOCK_COW_BELL));
            commands.registrar().register(LinkCommand.build("vote", voteMessage, Sound.BLOCK_NOTE_BLOCK_COW_BELL));

            commands.registrar().register(WorthCommand.build());
            commands.registrar().register(SetWorthCommand.build());
            commands.registrar().register(ColorsCommand.build());
            commands.registrar().register(CrateCommand.build("crate"));
            commands.registrar().register(HomeCommand.buildHomes("homes"));
            commands.registrar().register(HomeCommand.buildHome("home"));
            commands.registrar().register(HomeCommand.buildSetHome("sethome"));
            commands.registrar().register(HomeCommand.buildDelHome("delhome"));
            commands.registrar().register(NameCommand.buildName("name"));
            commands.registrar().register(CoinFlipCommand.build("cf"));
            commands.registrar().register(CoinFlipCommand.build("coinflip"));
            commands.registrar().register(SummonCommand.build("summon"));
            commands.registrar().register(WarpToCommand.build("warpto"));
            commands.registrar().register(MsgCommand.build("m"));
            commands.registrar().register(MsgCommand.build("msg"));
            commands.registrar().register(MsgCommand.build("message"));
            commands.registrar().register(ReplyCommand.build("r"));
            commands.registrar().register(ReplyCommand.build("reply"));

            commands.registrar().register(GSMCommand.build("gsm"));
            commands.registrar().register(FlySpeedCommand.build("flyspeed"));
            commands.registrar().register(ScaleCommand.build("scale"));

            commands.registrar().register(NukeCommand.build("nuke"));
            commands.registrar().register(BoomCommand.build("boom"));

            commands.registrar().register(AuctionCommand.build("ah"));
            commands.registrar().register(AuctionCommand.build("auction"));

            commands.registrar().register(GizmoCommand.build("gizmo"));

            commands.registrar().register(AnvilCommand.build());
            commands.registrar().register(CraftCommand.build());
            commands.registrar().register(SmithCommand.build());
            commands.registrar().register(EnderCommand.build("ender"));
            commands.registrar().register(EnderCommand.build("ec"));
            commands.registrar().register(EnderCommand.build("echest"));
            commands.registrar().register(EnderCommand.build("enderchest"));

            commands.registrar().register(TestVoteCommand.build("tvote"));
            commands.registrar().register(TestVoteCommand.build("testvote"));
            commands.registrar().register(TestVoteCommand.build("tv"));

            commands.registrar().register(ClanCommand.build("clan"));

            commands.registrar().register(RecoverCommand.build("recover"));

            commands.registrar().register(GiveCommand.build("give"));

            commands.registrar().register(BountyCommand.build("bounty"));
            commands.registrar().register(OrdersCommand.build("orders"));
            commands.registrar().register(OrdersCommand.build("order"));





        });
    }

    public static void onDisable() {
        manager.unregister();
    }

    public static void onSave() {

    }
}
