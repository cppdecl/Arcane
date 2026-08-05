package cx.arcane.managers.updateManager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cx.arcane.Arcane;
import cx.arcane.utils.Log;
import org.bukkit.command.CommandSender;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class UpdateManager {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String MODRINTH_API = "https://api.modrinth.com/v2";
    private static final String MODRINTH_TOKEN = "mrp_noV96l4VJXiTxZwZ6CzPy2JyKcVkJYOpKJExe935jw3UYEJJgT807jXz1LFO";
    private static final String GEYSER_API = "https://download.geysermc.org/v2/projects";

    public enum ReleaseType {
        RELEASE("release"),
        BETA("beta"),
        ALPHA("alpha"),
        ANY(null);

        final String modrinthValue;

        ReleaseType(String modrinthValue) {
            this.modrinthValue = modrinthValue;
        }
    }

    public record ModrinthProject(String displayName, String projectId, String gameVersion, String loader, ReleaseType releaseType) {}
    public record GeyserProject(String displayName, String projectId, String download) {}

    private static final List<ModrinthProject> MODRINTH_PROJECTS = List.of(
            new ModrinthProject("ViaVersion", "viaversion","1.21.11","folia", ReleaseType.RELEASE),
            new ModrinthProject("ViaBackwards", "viabackwards","1.21.11","folia", ReleaseType.RELEASE),
            new ModrinthProject("PacketEvents","packetevents","1.21.11","folia", ReleaseType.RELEASE),
            new ModrinthProject("Tab","tab-was-taken","1.21.11", "folia", ReleaseType.RELEASE),
            new ModrinthProject("SimpleVoiceChat","simple-voice-chat","1.21.11", "folia", ReleaseType.RELEASE),
            new ModrinthProject("Worlds","worlds-1","1.21.11", "folia", ReleaseType.RELEASE),
            new ModrinthProject("SmartSpawner","smartspawner","1.21.11", "folia", ReleaseType.RELEASE),
            new ModrinthProject("FancyNPCs","fancynpcs","1.21.11", "folia", ReleaseType.RELEASE),
            new ModrinthProject("FancyHolograms","fancyholograms","1.21.11", "folia", ReleaseType.RELEASE),
            new ModrinthProject("PlaceholderAPI","placeholderapi","1.21.11", "folia", ReleaseType.RELEASE),
            new ModrinthProject("GrimAC","grimac","1.21.11", "folia", ReleaseType.ALPHA)
    );

    private static final List<GeyserProject> GEYSER_PROJECTS = List.of(
            new GeyserProject("Geyser","geyser","spigot"),
            new GeyserProject("Floodgate","floodgate","spigot")
    );

    private static Path updateDir;

    public static void onEnable() {
        Log.info("[Update] Enabling UpdateManager...");
        updateDir = Paths.get(Arcane.getPlugin().getDataFolder().getParentFile().getAbsolutePath(), "update");
        try {
            Files.createDirectories(updateDir);
            Log.info("[Update] Update directory ready: {}", updateDir.toAbsolutePath());
        } catch (Exception e) {
            Log.error("[Update] Failed to create update directory: {}", e.getMessage());
        }
        Log.info("[Update] UpdateManager enabled. Tracking {} Modrinth + {} Geyser project(s).",
                MODRINTH_PROJECTS.size(), GEYSER_PROJECTS.size());
    }

    public static void onDisable() {
        Log.info("[Update] UpdateManager disabled.");
    }

    public static void onSave() {

    }

    public static void update(CommandSender sender) {
        int total = MODRINTH_PROJECTS.size() + GEYSER_PROJECTS.size();
        Log.info("[Update] Update triggered by '{}'.", sender.getName());
        sender.sendMessage("§e[Update] §7Starting update check for §f" + total + " §7plugin(s)...");

        Arcane.getPlugin().getServer().getAsyncScheduler().runNow(Arcane.getPlugin(), task -> {
            List<String> failed = new ArrayList<>();
            List<String> errored = new ArrayList<>();
            List<String> downloaded = new ArrayList<>();

            for (ModrinthProject project : MODRINTH_PROJECTS) {
                Log.info("[Update] Checking '{}' (id: {}, loader: {}, version: {}, type: {}) — invoked by '{}'.",
                        project.displayName(), project.projectId(), project.loader(), project.gameVersion(),
                        project.releaseType().name().toLowerCase(), sender.getName());
                sender.sendMessage("§e[Update] §7Checking §f" + project.displayName()
                        + " §8[" + project.loader() + " " + project.gameVersion()
                        + " " + project.releaseType().name().toLowerCase() + "]§7...");

                DownloadResult result = downloadLatestFromModrinth(project, sender);

                switch (result) {
                    case SUCCESS -> downloaded.add(project.displayName());
                    case API_ERROR -> errored.add(project.displayName());
                    case DOWNLOAD_FAILED -> failed.add(project.displayName());
                }
            }

            for (GeyserProject project : GEYSER_PROJECTS) {
                Log.info("[Update] Checking '{}' (id: {}, download: {}) — invoked by '{}'.",
                        project.displayName(), project.projectId(), project.download(), sender.getName());
                sender.sendMessage("§e[Update] §7Checking §f" + project.displayName() + " §8[geyser latest]§7...");

                DownloadResult result = downloadLatestFromGeyser(project, sender);

                switch (result) {
                    case SUCCESS -> downloaded.add(project.displayName());
                    case API_ERROR -> errored.add(project.displayName());
                    case DOWNLOAD_FAILED -> failed.add(project.displayName());
                }
            }

            Log.info("[Update] Update complete — downloaded: {}, api errors: {}, download failures: {}.",
                    downloaded.size(), errored.size(), failed.size());

            sender.sendMessage("§a[Update] §7Done! Downloaded: §f" + downloaded.size()
                    + "§7, API errors: §c" + errored.size() + "§7, Download failures: §c" + failed.size());

            if (!downloaded.isEmpty())
                sender.sendMessage("§a  ✔ §7Downloaded: §f" + String.join("§7, §f", downloaded));
            if (!errored.isEmpty())
                sender.sendMessage("§c  ✖ §7API errors: §f" + String.join("§7, §f", errored));
            if (!failed.isEmpty())
                sender.sendMessage("§c  ✖ §7Download failed: §f" + String.join("§7, §f", failed));
        });
    }

    private static DownloadResult downloadLatestFromModrinth(ModrinthProject project, CommandSender sender) {
        Log.info("[Update] [{}] Fetching version list (loader: {}, game_version: {}, release_type: {})...",
                project.displayName(), project.loader(), project.gameVersion(),
                project.releaseType().name().toLowerCase());

        try {
            String urlStr = MODRINTH_API + "/project/" + project.projectId() + "/version"
                    + "?loaders=%5B%22" + project.loader() + "%22%5D"
                    + "&game_versions=%5B%22" + project.gameVersion() + "%22%5D"
                    + "&include_changelog=false";

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", MODRINTH_TOKEN);
            conn.setRequestProperty("User-Agent", "Arcane/1.0 (arcane@cx)");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            int status = conn.getResponseCode();
            Log.info("[Update] [{}] Modrinth responded HTTP {}.", project.displayName(), status);

            if (status == 401) {
                Log.error("[Update] [{}] Unauthorized — invalid or expired token.", project.displayName());
                sender.sendMessage("§c[Update] §7Unauthorized for §f" + project.displayName() + " §7— check API token.");
                return DownloadResult.API_ERROR;
            }

            if (status == 404) {
                Log.error("[Update] [{}] Project not found on Modrinth (id: {}).", project.displayName(), project.projectId());
                sender.sendMessage("§c[Update] §7Project not found: §f" + project.displayName());
                return DownloadResult.API_ERROR;
            }

            if (status != 200) {
                Log.error("[Update] [{}] Unexpected HTTP {} from Modrinth.", project.displayName(), status);
                sender.sendMessage("§c[Update] §7Unexpected API error for §f" + project.displayName() + " §7(HTTP " + status + ").");
                return DownloadResult.API_ERROR;
            }

            JsonNode versions;
            try (InputStream is = conn.getInputStream()) {
                versions = MAPPER.readTree(is);
            }

            if (!versions.isArray() || versions.isEmpty()) {
                Log.error("[Update] [{}] No versions matched loader='{}' game_version='{}'.",
                        project.displayName(), project.loader(), project.gameVersion());
                sender.sendMessage("§c[Update] §7No matching version found for §f" + project.displayName()
                        + " §8[" + project.loader() + " " + project.gameVersion() + "]§7.");
                return DownloadResult.API_ERROR;
            }

            JsonNode latest = null;
            if (project.releaseType() == ReleaseType.ANY) {
                latest = versions.get(0);
            } else {
                for (JsonNode version : versions) {
                    if (project.releaseType().modrinthValue.equals(version.path("version_type").asText())) {
                        latest = version;
                        break;
                    }
                }
            }

            if (latest == null) {
                Log.error("[Update] [{}] No '{}' release found for loader='{}' game_version='{}'.",
                        project.displayName(), project.releaseType().name().toLowerCase(),
                        project.loader(), project.gameVersion());
                sender.sendMessage("§c[Update] §7No §f" + project.releaseType().name().toLowerCase()
                        + " §7release found for §f" + project.displayName()
                        + " §8[" + project.loader() + " " + project.gameVersion() + "]§7.");
                return DownloadResult.API_ERROR;
            }

            String versionNumber = latest.path("version_number").asText("unknown");
            String versionId = latest.path("id").asText();
            String versionType = latest.path("version_type").asText("unknown");

            Log.info("[Update] [{}] Latest matching version: '{}' (id: {}, type: {}).",
                    project.displayName(), versionNumber, versionId, versionType);

            JsonNode files = latest.path("files");
            if (!files.isArray() || files.isEmpty()) {
                Log.error("[Update] [{}] No files attached to version '{}'.", project.displayName(), versionNumber);
                sender.sendMessage("§c[Update] §7No files found for §f" + project.displayName() + " §7v" + versionNumber + "§7.");
                return DownloadResult.API_ERROR;
            }

            JsonNode primaryFile = null;
            for (JsonNode file : files) {
                if (file.path("primary").asBoolean(false)) {
                    primaryFile = file;
                    break;
                }
            }
            if (primaryFile == null)
                primaryFile = files.get(0);

            String downloadUrl = primaryFile.path("url").asText();
            String filename = primaryFile.path("filename").asText(project.displayName() + ".jar");
            long fileSize = primaryFile.path("size").asLong(0);

            Log.info("[Update] [{}] Downloading '{}' ({} bytes) from: {}", project.displayName(), filename, fileSize, downloadUrl);
            sender.sendMessage("§e[Update] §7Downloading §f" + project.displayName()
                    + " §7v" + versionNumber + " §8(" + filename + ")§7...");

            boolean success = downloadFile(project.displayName(), downloadUrl, filename, sender);

            if (success) {
                Log.info("[Update] [{}] Successfully saved to plugins/update/{}.", project.displayName(), filename);
                sender.sendMessage("§a[Update] §f" + project.displayName() + " §7v" + versionNumber + " downloaded successfully.");
                return DownloadResult.SUCCESS;
            }

            return DownloadResult.DOWNLOAD_FAILED;

        } catch (Exception e) {
            Log.error("[Update] [{}] Exception during update: {}", project.displayName(), e.getMessage());
            e.printStackTrace();
            sender.sendMessage("§c[Update] §7Exception while updating §f" + project.displayName() + "§7: " + e.getMessage());
            return DownloadResult.API_ERROR;
        }
    }

    private static DownloadResult downloadLatestFromGeyser(GeyserProject project, CommandSender sender) {
        Log.info("[Update] [{}] Fetching latest build metadata...", project.displayName());

        try {
            String metaUrl = GEYSER_API + "/" + project.projectId() + "/versions/latest/builds/latest";

            URL url = new URL(metaUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Arcane/1.0 (arcane@cx)");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            int status = conn.getResponseCode();
            Log.info("[Update] [{}] Geyser API responded HTTP {}.", project.displayName(), status);

            if (status == 404) {
                Log.error("[Update] [{}] Project or version not found on Geyser downloads (id: {}).", project.displayName(), project.projectId());
                sender.sendMessage("§c[Update] §7Project not found: §f" + project.displayName());
                return DownloadResult.API_ERROR;
            }

            if (status != 200) {
                Log.error("[Update] [{}] Unexpected HTTP {} from Geyser API.", project.displayName(), status);
                sender.sendMessage("§c[Update] §7Unexpected API error for §f" + project.displayName() + " §7(HTTP " + status + ").");
                return DownloadResult.API_ERROR;
            }

            JsonNode meta;
            try (InputStream is = conn.getInputStream()) {
                meta = MAPPER.readTree(is);
            }

            int build = meta.path("build").asInt(-1);
            String version = meta.path("version").asText("unknown");
            String filename = project.displayName().toLowerCase() + "-" + project.download() + ".jar";

            if (build == -1) {
                Log.error("[Update] [{}] Build number missing from Geyser API response.", project.displayName());
                sender.sendMessage("§c[Update] §7Invalid build metadata for §f" + project.displayName() + "§7.");
                return DownloadResult.API_ERROR;
            }

            String downloadUrl = GEYSER_API + "/" + project.projectId() + "/versions/latest/builds/latest/downloads/" + project.download();

            Log.info("[Update] [{}] Latest build: {} (version: {}), downloading as '{}'.",
                    project.displayName(), build, version, filename);
            sender.sendMessage("§e[Update] §7Downloading §f" + project.displayName()
                    + " §7v" + version + " §8(build " + build + ")§7...");

            boolean success = downloadFile(project.displayName(), downloadUrl, filename, sender);

            if (success) {
                Log.info("[Update] [{}] Successfully saved to plugins/update/{}.", project.displayName(), filename);
                sender.sendMessage("§a[Update] §f" + project.displayName() + " §7v" + version + " build " + build + " downloaded successfully.");
                return DownloadResult.SUCCESS;
            }

            return DownloadResult.DOWNLOAD_FAILED;

        } catch (Exception e) {
            Log.error("[Update] [{}] Exception during Geyser update: {}", project.displayName(), e.getMessage());
            e.printStackTrace();
            sender.sendMessage("§c[Update] §7Exception while updating §f" + project.displayName() + "§7: " + e.getMessage());
            return DownloadResult.API_ERROR;
        }
    }

    private static boolean downloadFile(String displayName, String downloadUrl, String filename, CommandSender sender) {
        Log.info("[Update] [{}] Opening download connection...", displayName);

        try {
            URL url = new URL(downloadUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Arcane/1.0 (arcane@cx)");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);

            int status = conn.getResponseCode();
            Log.info("[Update] [{}] Download server responded HTTP {}.", displayName, status);

            if (status != 200) {
                Log.error("[Update] [{}] Download failed with HTTP {}.", displayName, status);
                sender.sendMessage("§c[Update] §7Download failed for §f" + displayName + " §7(HTTP " + status + ").");
                return false;
            }

            Path dest = updateDir.resolve(filename);
            Log.info("[Update] [{}] Writing to: {}", displayName, dest.toAbsolutePath());

            try (InputStream is = conn.getInputStream()) {
                Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
            }

            long written = Files.size(dest);
            Log.info("[Update] [{}] Write complete — {} bytes written to '{}'.", displayName, written, dest.getFileName());
            return true;

        } catch (Exception e) {
            Log.error("[Update] [{}] Exception while downloading file: {}", displayName, e.getMessage());
            e.printStackTrace();
            sender.sendMessage("§c[Update] §7Failed to write §f" + filename + "§7: " + e.getMessage());
            return false;
        }
    }

    private enum DownloadResult {
        SUCCESS,
        API_ERROR,
        DOWNLOAD_FAILED
    }
}