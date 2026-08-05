package cx.arcane.managers.geoManager;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.AsnResponse;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.model.CountryResponse;
import cx.arcane.Arcane;
import cx.arcane.utils.Log;
import lombok.Getter;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.bukkit.Bukkit;

import java.io.Closeable;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.file.*;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * GeoManager handles:
 * - Downloading GeoLite2 databases from MaxMind
 * - Extracting .mmdb files
 * - Loading DatabaseReader instances
 * - Providing cached IP lookups
 *
 * Thread-safe for read access.
 */
public final class GeoManager {

    private static DatabaseReader cityReader;
    private static DatabaseReader asnReader;
    private static DatabaseReader countryReader;

    private static final Path GEO_DIR =
            Arcane.getPlugin().getDataFolder().toPath().resolve("GeoIP");

    /**
     * Whether at least one database reader is successfully loaded.
     */
    @Getter
    private static volatile boolean ready = false;

    /**
     * IP lookup cache.
     * Stores GeoData for 30 days.
     */
    private static final Cache<InetAddress, GeoData> CACHE =
            Caffeine.newBuilder()
                    .maximumSize(10_000)
                    .expireAfterWrite(Duration.ofDays(30))
                    .build();

    private GeoManager() {}

    /* =========================================================
       INITIALIZATION
       ========================================================= */

    /**
     * Initializes GeoManager by loading local database files.
     * Safe to call multiple times.
     */
    public static void onEnable() {
        /*Bukkit.getAsyncScheduler().runNow(Arcane.getPlugin(), t -> {
            String accountId = "1269363";
            String licenseKey = "";

            runUpdate(accountId, licenseKey);
        });*/

        loadReaders();
    }

    /**
     * Shuts down the manager and closes all open database readers.
     */
    public static void onDisable() {
        closeQuietly(cityReader);
        closeQuietly(asnReader);
        closeQuietly(countryReader);
        ready = false;
    }

    public static void onSave() {

    }

    /**
     * Loads all available GeoLite2 database files from disk.
     * Existing readers are closed before reloading.
     */
    private static synchronized void loadReaders() {

        try {
            Files.createDirectories(GEO_DIR);

            Path cityFile = GEO_DIR.resolve("GeoLite2-City.mmdb");
            Path asnFile = GEO_DIR.resolve("GeoLite2-ASN.mmdb");
            Path countryFile = GEO_DIR.resolve("GeoLite2-Country.mmdb");

            if (Files.exists(cityFile)) {
                cityReader = new DatabaseReader.Builder(cityFile.toFile()).build();
                ready = true;
            }

            if (Files.exists(asnFile)) {
                asnReader = new DatabaseReader.Builder(asnFile.toFile()).build();
                ready = true;
            }

            if (Files.exists(countryFile)) {
                countryReader = new DatabaseReader.Builder(countryFile.toFile()).build();
                ready = true;
            }

            if (!ready) {
                Log.error("[Geo] Failed to initialize GeoIP. No readers available. Please run /geo update");
            } else {
                Log.info("[Geo] Ready!");
            }

            CACHE.invalidateAll(); // DB changed, clear cache

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* =========================================================
       LOOKUP SERVICE
       ========================================================= */

    /**
     * Performs a cached lookup of the given IP address.
     *
     * @param address the IP address to lookup
     * @return GeoData containing country, city, ASN, ISP etc.
     *         Returns empty GeoData if readers are not loaded
     *         or if lookup fails.
     */
    public static GeoData lookup(InetAddress address) {
        if (address == null) {
            Log.info("[Geo] Tried to lookup but IP is null.");
            return new GeoData();
        }

        if (!ready) {
            Log.info("[Geo] Tried to lookup {} but GeoManager is not ready.", address.getHostAddress().toString());
            return new GeoData();
        }

        return CACHE.get(address, GeoManager::lookupInternal);
    }

    /**
     * Performs a direct lookup against loaded databases.
     *
     * @param address the IP address to query
     * @return GeoData result (never null)
     */
    private static GeoData lookupInternal(InetAddress address) {
        try {
            String country = "";
            String city = "";
            String region = "";
            String postal = "";
            Double lat = null;
            Double lon = null;
            Long asn = null;
            String isp = "";

            if (countryReader != null) {
                CountryResponse response = countryReader.country(address);
                if (response.country() != null) {
                    country = response.country().name();
                }
            }

            if (cityReader != null) {
                CityResponse response = cityReader.city(address);

                if (response.city() != null)
                    city = response.city().name();

                if (response.mostSpecificSubdivision() != null)
                    region = response.mostSpecificSubdivision().name();

                if (response.postal() != null)
                    postal = response.postal().code();

                if (response.location() != null) {
                    lat = response.location().latitude();
                    lon = response.location().longitude();
                }
            }

            if (asnReader != null) {
                AsnResponse response = asnReader.asn(address);
                asn = response.autonomousSystemNumber();
                isp = response.autonomousSystemOrganization();
            }

            return new GeoData(address, country, city, region, postal, lat, lon, asn, isp);

        } catch (Exception e) {
            return new GeoData();
        }
    }

    /* =========================================================
       UPDATE SERVICE (ASYNC - Paper Safe)
       ========================================================= */

    /**
     * Downloads latest GeoLite2 databases asynchronously.
     * Never blocks main thread.
     *
     * @param accountId  MaxMind account ID
     * @param licenseKey MaxMind license key
     */
    public static void updateAsync(String accountId, String licenseKey) {
        Bukkit.getAsyncScheduler().runNow(
                Arcane.getPlugin(),
                t -> runUpdate(accountId, licenseKey)
        );
    }

    private static void runUpdate(String accountId, String licenseKey) {
        try {
            Log.info("[Geo] Updating readers...");
            Map<String, String> databases = new LinkedHashMap<>();
            databases.put("ASN",
                    "https://download.maxmind.com/geoip/databases/GeoLite2-ASN/download?suffix=tar.gz");
            databases.put("City",
                    "https://download.maxmind.com/geoip/databases/GeoLite2-City/download?suffix=tar.gz");
            databases.put("Country",
                    "https://download.maxmind.com/geoip/databases/GeoLite2-Country/download?suffix=tar.gz");

            Files.createDirectories(GEO_DIR);

            for (String url : databases.values()) {
                downloadAndExtract(url, accountId, licenseKey);
            }
            Log.info("[Geo] Successfully updated readers!");

            loadReaders();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* =========================================================
       DOWNLOAD + EXTRACT
       ========================================================= */

    private static void downloadAndExtract(String url,
                                           String accountId,
                                           String licenseKey) throws Exception {

        Path tempFile = Files.createTempFile("geoip-", ".tar.gz");

        download(url, accountId, licenseKey, tempFile);
        extractMmdb(tempFile);

        Files.deleteIfExists(tempFile);
    }

    private static void download(String url,
                                 String accountId,
                                 String licenseKey,
                                 Path target) throws Exception {

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");

        String auth = Base64.getEncoder()
                .encodeToString((accountId + ":" + licenseKey).getBytes());

        conn.setRequestProperty("Authorization", "Basic " + auth);
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(30_000);

        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void extractMmdb(Path tarGz) throws Exception {
        try (InputStream fileIn = Files.newInputStream(tarGz);
             GZIPInputStream gzipIn = new GZIPInputStream(fileIn);
             TarArchiveInputStream tarIn = new TarArchiveInputStream(gzipIn)) {

            TarArchiveEntry entry;

            while ((entry = tarIn.getNextTarEntry()) != null) {
                if (!entry.isFile()) continue;
                if (!entry.getName().endsWith(".mmdb")) continue;

                String fileName =
                        entry.getName().substring(entry.getName().lastIndexOf('/') + 1);

                Path output = GEO_DIR.resolve(fileName);

                Files.copy(tarIn, output, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /* =========================================================
       UTIL
       ========================================================= */

    private static void closeQuietly(Closeable closeable) {
        try {
            if (closeable != null) closeable.close();
        } catch (Exception ignored) {}
    }
}