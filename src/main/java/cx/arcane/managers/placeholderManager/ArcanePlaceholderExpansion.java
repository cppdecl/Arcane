package cx.arcane.managers.placeholderManager;

import cx.arcane.managers.authManager.AuthManager;
import cx.arcane.managers.clanManager.clanInfo.ClanData;
import cx.arcane.managers.crateManager.CrateData;
import cx.arcane.managers.crateManager.CrateManager;
import cx.arcane.managers.ecoManager.EcoData;
import cx.arcane.managers.ecoManager.EcoManager;
import cx.arcane.managers.permissionManager.PermissionManager;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.managers.playerManager.PlayerMeta;
import cx.arcane.managers.playerManager.PlayerSettings;
import cx.arcane.managers.skinManager.CachedSkin;
import cx.arcane.managers.skinManager.SkinManager;
import cx.arcane.managers.voteManager.VoteStatistics;
import cx.arcane.utils.Text;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

public class ArcanePlaceholderExpansion extends PlaceholderExpansion {

    public ArcanePlaceholderExpansion() {
    }

    @Override
    public @NotNull String getAuthor() {
        return "cx.arcane";
    }

    @Override
    public @NotNull String getIdentifier() {
        return "Arcane";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {

        if (params.startsWith("money_top_name_") || params.startsWith("money_top_value_")) {

            boolean isName = params.contains("_name_");
            String prefix = (isName ? "money_top_name_" : "money_top_value_");


            int index;
            try {
                index = Integer.parseInt(params.substring(prefix.length())) - 1;
            } catch (NumberFormatException e) {
                return "Unknown Placeholder";
            }

            if (index < 0) return "Unknown Placeholder";

            List<EcoData> top = EcoManager.getTopMoney(index + 1);

            if (index >= top.size()) return "N/A";

            EcoData entry = top.get(index);

            if (isName) {
                PlayerData pd = PlayerManager.getByUniqueId(entry.getUniqueId());
                return pd != null ? pd.getUsername() : "N/A";
            }

            return Text.formatShortBalanceWithSign("$", entry.getMoney());
        }

        if (params.startsWith("votes_top_name_") || params.startsWith("votes_top_value_")) {

            boolean isName = params.contains("_name_");
            String prefix = (isName ? "votes_top_name_" : "votes_top_value_");

            int index;
            try {
                index = Integer.parseInt(params.substring(prefix.length())) - 1;
            } catch (NumberFormatException e) {
                return "Unknown Placeholder";
            }

            if (index < 0) return "Unknown Placeholder";

            List<PlayerData> top = PlayerManager.getTopVotes(10);

            if (index >= top.size()) return "N/A";

            PlayerData entry = top.get(index);

            if (isName) {
                PlayerData pd = PlayerManager.getByUniqueId(entry.getUniqueId());
                return pd != null ? pd.getUsername() : "N/A";
            }

            return String.valueOf(Text.formatShortBalance(entry.getMeta().getTotalVotes()));
        }

        if (params.startsWith("bought_top_name_") || params.startsWith("bought_top_value_")) {

            boolean isName = params.contains("_name_");
            String prefix = (isName ? "bought_top_name_" : "bought_top_value_");

            int index;
            try {
                index = Integer.parseInt(params.substring(prefix.length())) - 1;
            } catch (NumberFormatException e) {
                return "Unknown Placeholder";
            }

            if (index < 0) return "Unknown Placeholder";

            List<PlayerData> top = PlayerManager.getTopMostBought(10);

            if (index >= top.size()) return "N/A";

            PlayerData entry = top.get(index);

            if (isName) {
                PlayerData pd = PlayerManager.getByUniqueId(entry.getUniqueId());
                return pd != null ? pd.getUsername() : "N/A";
            }

            return Text.formatShortBalanceWithSign("$", entry.getMeta().getTotalBought());
        }

        if (params.startsWith("sold_top_name_") || params.startsWith("sold_top_value_")) {

            boolean isName = params.contains("_name_");
            String prefix = (isName ? "sold_top_name_" : "sold_top_value_");

            int index;
            try {
                index = Integer.parseInt(params.substring(prefix.length())) - 1;
            } catch (NumberFormatException e) {
                return "Unknown Placeholder";
            }

            if (index < 0) return "Unknown Placeholder";

            List<PlayerData> top = PlayerManager.getTopMostSold(10);

            if (index >= top.size()) return "N/A";

            PlayerData entry = top.get(index);

            if (isName) {
                PlayerData pd = PlayerManager.getByUniqueId(entry.getUniqueId());
                return pd != null ? pd.getUsername() : "N/A";
            }

            return Text.formatShortBalanceWithSign("$", entry.getMeta().getTotalSold());
        }

        if (params.startsWith("crystals_top_name_") || params.startsWith("crystals_top_value_")) {

            boolean isName = params.contains("_name_");
            String prefix = (isName ? "crystals_top_name_" : "crystals_top_value_");

            int index;
            try {
                index = Integer.parseInt(params.substring(prefix.length())) - 1;
            } catch (NumberFormatException e) {
                return "Unknown Placeholder";
            }

            if (index < 0) return "Unknown Placeholder";

            List<PlayerData> top = PlayerManager.getTopMostCrystals(10);

            if (index >= top.size()) return "N/A";

            PlayerData entry = top.get(index);

            if (isName) {
                PlayerData pd = PlayerManager.getByUniqueId(entry.getUniqueId());
                return pd != null ? pd.getUsername() : "N/A";
            }

            return Text.formatShortBalance(entry.getMeta().getCrystalsExploded());
        }

        if (params.startsWith("anchors_top_name_") || params.startsWith("anchors_top_value_")) {

            boolean isName = params.contains("_name_");
            String prefix = (isName ? "anchors_top_name_" : "anchors_top_value_");

            int index;
            try {
                index = Integer.parseInt(params.substring(prefix.length())) - 1;
            } catch (NumberFormatException e) {
                return "Unknown Placeholder";
            }

            if (index < 0) return "Unknown Placeholder";

            List<PlayerData> top = PlayerManager.getTopMostAnchors(10);

            if (index >= top.size()) return "N/A";

            PlayerData entry = top.get(index);

            if (isName) {
                PlayerData pd = PlayerManager.getByUniqueId(entry.getUniqueId());
                return pd != null ? pd.getUsername() : "N/A";
            }

            return Text.formatShortBalance(entry.getMeta().getAnchorsExploded());
        }

        if (params.startsWith("kills_top_name_") || params.startsWith("kills_top_value_") ||
                params.startsWith("deaths_top_name_") || params.startsWith("deaths_top_value_") ||
                params.startsWith("playtime_top_name_") || params.startsWith("playtime_top_value_")) {

            boolean isKills = params.startsWith("kills_");
            boolean isDeaths = params.startsWith("deaths_");
            boolean isPlaytime = params.startsWith("playtime_");
            boolean isName = params.contains("_name_");

            String prefix = isKills
                    ? (isName ? "kills_top_name_" : "kills_top_value_")
                    : isDeaths
                    ? (isName ? "deaths_top_name_" : "deaths_top_value_")
                    : (isName ? "playtime_top_name_" : "playtime_top_value_");

            int index;
            try {
                index = Integer.parseInt(params.substring(prefix.length())) - 1;
            } catch (NumberFormatException e) {
                return "Unknown Placeholder";
            }

            if (index < 0) return "Unknown Placeholder";

            List<PlayerData> top = isKills
                    ? PlayerManager.getTopKills(index + 1)
                    : isDeaths
                    ? PlayerManager.getTopDeaths(index + 1)
                    : PlayerManager.getTopPlaytime(index + 1);

            if (index >= top.size()) return "N/A";

            PlayerData entry = top.get(index);

            if (isName) return entry.getUsername();

            if (isPlaytime) {
                long totalSeconds = entry.getMeta().getPlaytimeSeconds();

                long years = totalSeconds / (365 * 24 * 60 * 60);
                totalSeconds %= 365 * 24 * 60 * 60;

                long days = totalSeconds / (24 * 60 * 60);
                totalSeconds %= 24 * 60 * 60;

                long hours = totalSeconds / (60 * 60);
                totalSeconds %= 60 * 60;

                long minutes = totalSeconds / 60;
                long seconds = totalSeconds % 60;

                if (years > 0) return days > 0 ? years + "y " + days + "d" : years + "y";
                if (days > 0) return hours > 0 ? days + "d " + hours + "h" : days + "d";
                if (hours > 0) return minutes > 0 ? hours + "h " + minutes + "m" : hours + "h";
                if (minutes > 0) return seconds > 0 ? minutes + "m " + seconds + "s" : minutes + "m";
                return seconds + "s";
            }

            return isKills
                    ? String.valueOf(entry.getMeta().getKills())
                    : String.valueOf(entry.getMeta().getDeaths());
        }

        switch(params.toLowerCase(Locale.ROOT)) {
            case "votes_total" -> {
                return String.valueOf(VoteStatistics.getTotalVotesAllTime());
            }
            case "votes_monthly" -> {
                return String.valueOf(VoteStatistics.getTotalVotesMonthly());
            }
            case "votes_weekly" -> {
                return String.valueOf(VoteStatistics.getTotalVotesWeekly());
            }
            case "votes_daily" -> {
                return String.valueOf(VoteStatistics.getTotalVotesDaily());
            }
            case "voteparty_votes" -> {
                return String.valueOf(VoteStatistics.getVotePartyVotes());
            }
            case "voteparty_required" -> {
                return String.valueOf(VoteStatistics.getVotePartyRequired());
            }
        }

        PlayerData pData = PlayerManager.getByUniqueId(player.getUniqueId());
        if (pData == null) return "";
        PlayerMeta pMeta = pData.getMeta();
        PlayerSettings pSettings = pData.getSettings();
        ClanData pClan = pData.getClan();

        switch(params.toLowerCase(Locale.ROOT)) {
            case "scoreboard" -> {
                if (AuthManager.isAuthenticated(pData.getUniqueId()) && pData.hasClan()) {
                    return "authed_has_clan";
                }

                if (AuthManager.isAuthenticated(pData.getUniqueId())) {
                    return "authed_normal";
                }

                return "none";
            }
            case "clan" -> {
                if (pData.hasClan()) {
                    return pData.getClan().getTag();
                }
                return "";
            }
            case "clanx" -> {
                if (pData.hasClan()) {
                    return pData.getClan().getTag() + " ";
                }
                return "";
            }
            case "xclan" -> {
                if (pData.hasClan()) {
                    return " " + pData.getClan().getTag();
                }
                return "";
            }
            case "has_clan" -> {
                return Boolean.toString(pData.hasClan());
            }
            case "belowname" -> {
                String base = "<shadow:#000000:1.0><#FF2977>❤ %arcane_health% <#382F3C>| <shadow:#000000:1.0><#00ff00>$%arcane_money_balance_short%";
                if (pData.hasClan()) {
                    base += " <#382F3C>| <#FF4C9C>" + pClan.getTag();
                }

                return base;
            }
            case "vanished" -> {
                return Boolean.toString(pMeta.isVanish());
            }
            case "votes" -> {
                return String.valueOf(Text.formatShortBalance(pMeta.getTotalVotes()));
            }
            case "votes_position" -> {
                return String.valueOf(PlayerManager.getVotesPosition(pData.getUniqueId()));
            }
            case "bought" -> {
                return String.valueOf(Text.formatShortBalance(pMeta.getTotalBought()));
            }
            case "bought_position" -> {
                return String.valueOf(PlayerManager.getMostBoughtPosition(pData.getUniqueId()));
            }
            case "crystals" -> {
                return String.valueOf(Text.formatShortBalance(pMeta.getCrystalsExploded()));
            }
            case "crystals_position" -> {
                return String.valueOf(PlayerManager.getMostCrystalsPosition(pData.getUniqueId()));
            }
            case "anchors" -> {
                return String.valueOf(Text.formatShortBalance(pMeta.getAnchorsExploded()));
            }
            case "anchors_position" -> {
                return String.valueOf(PlayerManager.getMostAnchorsPosition(pData.getUniqueId()));
            }
            case "sold" -> {
                return String.valueOf(Text.formatShortBalance(pMeta.getTotalSold()));
            }
            case "sold_position" -> {
                return String.valueOf(PlayerManager.getMostSoldPosition(pData.getUniqueId()));
            }
            case "authenticated" -> {
                return AuthManager.isAuthenticated(pData.getUniqueId()) ? "true" : "false";
            }
            case "kills" -> {
                return String.valueOf(Text.formatShortBalance(pMeta.getKills()));
            }
            case "deaths" -> {
                return String.valueOf(Text.formatShortBalance(pMeta.getDeaths()));
            }
            case "kdr" -> {
                long deaths = pMeta.getDeaths();
                if (deaths == 0) return String.valueOf(pMeta.getKills());
                return String.format("%.2f", (double) pMeta.getKills() / deaths);
            }
            case "killstreak" -> {
                return String.valueOf(Text.formatShortBalance(pMeta.getKillstreak()));
            }
            case "kills_position" -> {
                return String.valueOf(PlayerManager.getKillsPosition(pData.getUniqueId()));
            }
            case "deaths_position" -> {
                return String.valueOf(PlayerManager.getDeathsPosition(pData.getUniqueId()));
            }
            case "kdr_position" -> {
                return String.valueOf(PlayerManager.getKdrPosition(pData.getUniqueId()));
            }
            case "killstreak_position" -> {
                return String.valueOf(PlayerManager.getKillstreakPosition(pData.getUniqueId()));
            }
            case "playtime_position" -> {
                return String.valueOf(PlayerManager.getPlaytimePosition(pData.getUniqueId()));
            }
            case "health" -> {
                double health = Math.min(pData.getPlayer().getHealth(), 20.0);
                return String.format("%.1f", health);
            }
            case "prefix" -> {
                if (pSettings.isAnonymous()) return "<obf>";
                String color = PermissionManager.getPermissionString(player.getUniqueId(), "arcane.color.tag", "");
                return PlaceholderAPI.setPlaceholders(player, "%luckperms_prefix%" + color);
            }
            case "suffix" -> {
                return PlaceholderAPI.setPlaceholders(player, "%luckperms_suffix%");
            }
            case "hide_belowname" -> {
                if (PermissionManager.check((Player) player, "arcane.flags.staff") || pSettings.isAnonymous())
                    return "true";

                return "false";
            }
            case "player_count" -> {
                int totalPlayers = 0;
                Player viewer = player.getPlayer();
                for (Player others : Bukkit.getOnlinePlayers()) {
                    totalPlayers++;
                }
                return Integer.toString(totalPlayers);
            }
            case "money_balance" -> {
                return Text.formatBalance(pData.getMoney());
            }
            case "elo" -> {
                return Text.formatBalance(pMeta.getElo());
            }
            case "money_balance_short" -> {
                return Text.formatShortBalance(pData.getMoney());
            }
            case "money_position" -> {
                return String.valueOf(EcoManager.getMoneyPosition(pData.getUniqueId()));
            }
            case "playtime_short" -> {
                long totalSeconds = pMeta.getPlaytimeSeconds();

                long years = totalSeconds / (365 * 24 * 60 * 60);
                totalSeconds %= 365 * 24 * 60 * 60;

                long days = totalSeconds / (24 * 60 * 60);
                totalSeconds %= 24 * 60 * 60;

                long hours = totalSeconds / (60 * 60);
                totalSeconds %= 60 * 60;

                long minutes = totalSeconds / 60;
                long seconds = totalSeconds % 60;

                if (years > 0) {
                    return years + "y";
                } else if (days > 0) {
                    return days + "d";
                } else if (hours > 0) {
                    return hours + "h";
                } else if (minutes > 0) {
                    return minutes + "m";
                } else {
                    return seconds + "s";
                }
            }
            case "playtime" -> {
                long totalSeconds = pMeta.getPlaytimeSeconds();

                long years = totalSeconds / (365 * 24 * 60 * 60);
                totalSeconds %= 365 * 24 * 60 * 60;

                long days = totalSeconds / (24 * 60 * 60);
                totalSeconds %= 24 * 60 * 60;

                long hours = totalSeconds / (60 * 60);
                totalSeconds %= 60 * 60;

                long minutes = totalSeconds / 60;
                long seconds = totalSeconds % 60;

                if (years > 0) {
                    if (days > 0) return years + "y " + days + "d";
                    else return years + "y";
                } else if (days > 0) {
                    if (hours > 0) return days + "d " + hours + "h";
                    else return days + "d";
                } else if (hours > 0) {
                    if (minutes > 0) return hours + "h " + minutes + "m";
                    else return hours + "h";
                } else if (minutes > 0) {
                    if (seconds > 0) return minutes + "m " + seconds + "s";
                    else return minutes + "m";
                } else {
                    return seconds + "s";
                }
            }
            default -> {

                if (params.startsWith("crate_key_count_")) {
                    String crateId = params.substring("crate_key_count_".length()).toLowerCase(Locale.ROOT);
                    if (crateId.isEmpty()) return "0";
                    long amount = CrateManager.getKeyCount(pData.getUniqueId(), crateId);
                    return String.valueOf(amount);
                }

                break;
            }
        }

        return "Unknown Placeholder";
    }
}

