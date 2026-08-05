package cx.arcane.managers.commandManager.commands;

import cx.arcane.Arcane;
import cx.arcane.managers.commandManager.arguments.playerArgument.PlayerArgument;
import cx.arcane.managers.geoManager.GeoData;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.utils.Colors;
import cx.arcane.utils.Text;
import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Sender;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.Set;

@Command(name = "pinfo", aliases = {"playerinfo", "pi"})
@Permission("arcane.rank.management")
public class PInfoCommand {

    @Execute
    public void execute(@Sender Player sender, @Arg PlayerArgument targetArg) {
        PlayerData target = targetArg.get();
        if (target == null) {
            sendError(sender, "That player does not exist.");
            return;
        }

        fetchDiscordAndSend(sender, target);
    }

    @Execute
    public void executeConsole(@Sender ConsoleCommandSender sender, @Arg String playerName) {
        PlayerData target = PlayerManager.getByNameIgnoreCase(playerName);
        if (target == null) {
            sendError(sender, "That player does not exist.");
            return;
        }

        fetchDiscordAndSend(sender, target);
    }

    private void fetchDiscordAndSend(CommandSender sender, PlayerData target) {
        Bukkit.getAsyncScheduler().runNow(Arcane.getPlugin(), task -> {
            String discordUser = "Unknown";

            if (target.isDiscordLinked()) {
                /*try {
                    discordUser = DiscordManager.getBot()
                            .retrieveUserById(target.getDiscordId())
                            .complete()
                            .getName();
                } catch (Exception ignored) {
                    discordUser = "Unknown";
                }*/
            }

            sendInfoPanel(sender, target, discordUser);
        });
    }

    private void sendInfoPanel(CommandSender sender, PlayerData target, String discordUser) {
        String staffTimezone = "GMT+8";
        if (sender instanceof Player staff) {
            PlayerData staffData = PlayerManager.getByUniqueId(staff.getUniqueId());
            if (staffData != null && staffData.getTimezone() != null) {
                staffTimezone = staffData.getTimezone();
            }
        }

        Set<PlayerData> related = PlayerManager.getPlayersWithRelatedIp(target);

        Component alts = Component.empty();
        boolean first = true;
        for (PlayerData alt : related) {
            if (!first) {
                alts = alts.append(Component.text(", ", NamedTextColor.DARK_GRAY));
            }

            TextColor color = /*alt.isBanned() ? TextColor.color(0xff0000) : */NamedTextColor.GRAY;
            alts = alts.append(
                    Component.text(alt.getUsername(), color)
                            .clickEvent(ClickEvent.runCommand("/pinfo " + alt.getUsername()))
            );
            first = false;
        }

        Component header = Text.toSmallCapsComponent("❖ Player Info")
                .color(TextColor.color(0xFF1F75));

        Component body = Component.text()
                .append(
                        Component.text(" • Name: ", TextColor.color(0xD61A68))
                                .append(Component.text(target.getUsername(), NamedTextColor.GRAY))
                                .append(target.isOnline()
                                        ? Component.text(" (Online)", TextColor.color(0x00ff00))
                                        : Component.text(" (Offline)", TextColor.color(0xff0000)))
                                .append(Component.newline())
                )
                .append(
                        Component.text(" • UUID: ", TextColor.color(0xFF1F75))
                                .append(Component.text(target.getUniqueId().toString(), NamedTextColor.GRAY)
                                        .clickEvent(ClickEvent.copyToClipboard(target.getUniqueId().toString())))
                                .append(Component.newline())
                )
                .append(
                        Component.text(" • Account Type: ", TextColor.color(0xFF1F75))
                                .append(Component.text(target.getAccountTypeString(), NamedTextColor.GRAY)
                                        .clickEvent(ClickEvent.copyToClipboard(target.getUniqueId().toString())))
                                .append(Component.newline())
                )
                .append(
                        Component.text(" • Location: ", TextColor.color(0xFF1F75))
                                .append(Component.text(getSafeLocationInfo(target.getLastGeoData()), NamedTextColor.GRAY))
                                .append(Component.newline())
                )
                .append(
                        Component.text(" • IP: ", TextColor.color(0xFF1F75))
                                .append(Component.text(getSafeAddress(target.getLastLoginAddress()), NamedTextColor.GRAY)
                                        .clickEvent(ClickEvent.copyToClipboard(getSafeAddress(target.getLastLoginAddress()))))
                                .append(Component.newline())
                )
                .append(
                        Component.text(" • ISP: ", TextColor.color(0xFF1F75))
                                .append(Component.text(getSafeIspInfo(target.getLastGeoData()) + " ", NamedTextColor.GRAY))
                                .append(Component.text("(", TextColor.color(0xFF1F75)))
                                .append(Component.text(getSafeAsnInfo(target.getLastGeoData()), TextColor.color(0xFC5696)))
                                .append(Component.text(")", TextColor.color(0xFF1F75)))
                                .append(Component.newline())
                )
                .append(
                        Component.text(" • Last Login: ", TextColor.color(0xFF1F75))
                                .append(Component.text(
                                        Text.instantToTimestamp(target.getLastLoginAt(), staffTimezone) + " ",
                                        NamedTextColor.GRAY
                                ))
                                .append(Component.text("(", TextColor.color(0xFF1F75)))
                                .append(Component.text(getSafeAddress(target.getLastLoginAddress()), TextColor.color(0xFC5696)))
                                .append(Component.text(")", TextColor.color(0xFF1F75)))
                                .append(Component.newline())
                )
                .append(
                        Component.text(" • Registered At: ", TextColor.color(0xFF1F75))
                                .append(Component.text(
                                        Text.instantToTimestamp(target.getRegisterAt(), staffTimezone) + " ",
                                        NamedTextColor.GRAY
                                ))
                                .append(Component.text("(", TextColor.color(0xFF1F75)))
                                .append(Component.text(getSafeAddress(target.getRegisterAddress()), TextColor.color(0xFC5696)))
                                .append(Component.text(")", TextColor.color(0xFF1F75)))
                                .append(Component.newline())
                )
                /*.append(
                        target.isDiscordLinked()
                                ? Component.text(" • Discord: ", TextColor.color(0xFF1F75))
                                .append(Component.text(discordUser + " ", NamedTextColor.GRAY))
                                .append(Component.text("(", TextColor.color(0xFF1F75)))
                                .append(Component.text(target.getDiscordId(), TextColor.color(0xFC5696))
                                        .clickEvent(ClickEvent.copyToClipboard(target.getDiscordId())))
                                .append(Component.text(")", TextColor.color(0xFF1F75)))
                                .append(Component.newline())
                                : Component.text(" • Discord: ", TextColor.color(0xFF1F75))
                                .append(Component.text("Not Linked / Verified", NamedTextColor.GRAY))
                                .append(Component.newline())
                )*/
                .append(
                        Component.text(" • Related: ", TextColor.color(0xFF1F75))
                                .append(alts)
                                .append(Component.text(" (", TextColor.color(0xFF1F75)))
                                .append(Component.text(related.size(), TextColor.color(0xFC5696)))
                                .append(Component.text(")", TextColor.color(0xFF1F75)))
                                .append(Component.newline())
                )
                /*.append(
                        target.isBanned()
                                ? Component.text(" • Banned: ", TextColor.color(0xFF1F75))
                                .append(Component.text("Yes ", TextColor.color(0xff0000)))
                                .append(Component.text("(", TextColor.color(0xFF1F75)))
                                .append(Component.text(
                                        Text.instantToTimestamp(Instant.ofEpochMilli(target.getActiveBanRecord().getAppliedAt()), staffTimezone),
                                        TextColor.color(0xFC5696)
                                ))
                                .append(Component.text(")", TextColor.color(0xFF1F75)))
                                .append(Component.newline())
                                : Component.text(" • Banned: ", TextColor.color(0xFF1F75))
                                .append(Component.text("No", NamedTextColor.GRAY))
                                .append(Component.newline())
                )*/
                .append(Text.toSmallCapsComponent("[History] ")
                        .color(TextColor.color(0xFC5696))
                        .clickEvent(ClickEvent.runCommand("/phist " + target.getUsername())))
                .append(Text.toSmallCapsComponent("[Alts] ")
                        .color(TextColor.color(0xFC5696))
                        .clickEvent(ClickEvent.runCommand("/alts " + target.getUsername())))
                .append(Text.toSmallCapsComponent("[Warp] ")
                        .color(TextColor.color(0xFC5696))
                        .clickEvent(ClickEvent.runCommand("/warpto " + target.getUsername())))
                .append(Text.toSmallCapsComponent("[Summon] ")
                        .color(TextColor.color(0xFC5696))
                        .clickEvent(ClickEvent.runCommand("/summon " + target.getUsername())))
                .append(Component.newline())
                .build();

        sendToSender(sender, () -> {
            sender.sendMessage(header);
            sender.sendMessage(body);

            if (sender instanceof Player player) {
                player.playSound(
                        Sound.sound(Key.key("minecraft:entity.experience_orb.pickup"), Sound.Source.PLAYER, 1.0f, 1.0f),
                        Sound.Emitter.self()
                );
            }
        });
    }

    private void sendToSender(CommandSender sender, Runnable action) {
        if (sender instanceof Player player) {
            player.getScheduler().run(Arcane.getPlugin(), task -> action.run(), null);
        } else {
            action.run();
        }
    }

    private void sendError(CommandSender sender, String message) {
        Component msg = Component.text(message, Colors.DARK_PINK);
        sender.sendMessage(msg);

        if (sender instanceof Player player) {
            player.sendActionBar(msg);
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1, 1);
        }
    }

    private static String getSafeAddress(java.net.InetAddress address) {
        return address == null ? "Unknown" : address.getHostAddress();
    }

    private static String getSafeLocationInfo(GeoData geoData) {
        if (geoData == null) return "Unknown Location";

        StringBuilder location = new StringBuilder();
        if (geoData.getCity() != null && !geoData.getCity().isEmpty()) {
            location.append(geoData.getCity());
        }
        if (geoData.getRegion() != null && !geoData.getRegion().isEmpty()) {
            if (!location.isEmpty()) location.append(", ");
            location.append(geoData.getRegion());
        }
        if (geoData.getCountry() != null && !geoData.getCountry().isEmpty()) {
            if (!location.isEmpty()) location.append(", ");
            location.append(geoData.getCountry());
        }

        return location.isEmpty() ? "Unknown Location" : location.toString();
    }

    private static String getSafeIspInfo(GeoData geoData) {
        if (geoData == null) return "Unknown ISP";

        String isp = geoData.getIsp();
        if (isp == null || isp.isEmpty()) {
            return "Unknown ISP";
        }

        return isp;
    }

    private static String getSafeAsnInfo(GeoData geoData) {
        if (geoData == null) return "Unknown ASN";

        Long asn = geoData.getAsn();
        if (asn == null || asn <= 0) {
            return "Unknown ASN";
        }

        return "AS" + asn;
    }
}