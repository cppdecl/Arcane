package cx.arcane.managers.dependencyManager;

import com.alessiodp.libby.Library;
import com.alessiodp.libby.PaperLibraryManager;
import cx.arcane.Arcane;

public class DependencyManager {

    private static String formatLib(String dependency) {
        return dependency.replace(".", "{}");
    }

    public static void onLoad() {
        PaperLibraryManager libraryManager = new PaperLibraryManager(Arcane.getPlugin(), "Libraries");

        libraryManager.addMavenCentral();

        try {
            libraryManager.loadLibrary(Library.builder()
                    .repository("https://repo.panda-lang.org/releases")
                    .resolveTransitiveDependencies(false)
                    .groupId(formatLib("dev.rollczi"))
                    .artifactId(formatLib("litecommands-bukkit"))
                    .version("3.10.9")
                    .build());

            libraryManager.loadLibrary(Library.builder()
                    .repository("https://repo.triumphteam.dev/snapshots")
                    .resolveTransitiveDependencies(true)
                    .groupId(formatLib("dev.triumphteam"))
                    .artifactId(formatLib("triumph-gui-paper"))
                    .version("4.0.0-SNAPSHOT")
                    .build());

        /*libraryManager.loadLibrary(Library.builder()
                .repository("https://repo.codemc.io/repository/maven-releases/")
                .resolveTransitiveDependencies(true)
                .groupId(formatLib("com.github.retrooper"))
                .artifactId(formatLib("packetevents-spigot"))
                .version("2.11.2")
                .build());*/

            libraryManager.loadLibrary(Library.builder()
                    .resolveTransitiveDependencies(true)
                    .groupId(formatLib("com.github.ben-manes.caffeine"))
                    .artifactId(formatLib("caffeine"))
                    .version("3.2.3")
                    .build());

            libraryManager.loadLibrary(Library.builder()
                    .resolveTransitiveDependencies(true)
                    .groupId(formatLib("com.github.ben-manes.caffeine"))
                    .artifactId(formatLib("guava"))
                    .version("3.2.3")
                    .build());

            libraryManager.loadLibrary(Library.builder()
                    .resolveTransitiveDependencies(true)
                    .groupId(formatLib("com.github.ben-manes.caffeine"))
                    .artifactId(formatLib("jcache"))
                    .version("3.2.3")
                    .build());

            libraryManager.loadLibrary(Library.builder()
                    .groupId(formatLib("club.minnced"))
                    .artifactId(formatLib("discord-webhooks"))
                    .version("0.8.4")
                    .isolatedLoad(false)
                    .resolveTransitiveDependencies(true)
                    .build());

            libraryManager.loadLibrary(Library.builder()
                    .groupId(formatLib("com.zaxxer"))
                    .artifactId(formatLib("HikariCP"))
                    .version("7.0.2")
                    .isolatedLoad(false)
                    .resolveTransitiveDependencies(true)
                    .build());

            libraryManager.loadLibrary(Library.builder()
                    .groupId(formatLib("org.mariadb.jdbc"))
                    .artifactId(formatLib("mariadb-java-client"))
                    .version("3.3.3")
                    .resolveTransitiveDependencies(true)
                    .build());

            libraryManager.loadLibrary(Library.builder()
                    .groupId(formatLib("com.fasterxml.jackson.core"))
                    .artifactId(formatLib("jackson-core"))
                    .version("2.21.2")
                    .resolveTransitiveDependencies(true)
                    .build());

            libraryManager.loadLibrary(Library.builder()
                    .groupId(formatLib("org.mindrot"))
                    .artifactId(formatLib("jbcrypt"))
                    .version("0.4")
                    .resolveTransitiveDependencies(true)
                    .build());

            libraryManager.loadLibrary(Library.builder()
                    .groupId(formatLib("com.maxmind.geoip2"))
                    .artifactId(formatLib("geoip2"))
                    .version("5.0.2")
                    .resolveTransitiveDependencies(true)
                    .build());

            libraryManager.loadLibrary(Library.builder()
                    .groupId(formatLib("net.dv8tion"))
                    .artifactId(formatLib("JDA"))
                    .version("6.2.1")
                    .resolveTransitiveDependencies(true)
                    .build());

            libraryManager.loadLibrary(Library.builder()
                    .groupId(formatLib("org.apache.commons"))
                    .artifactId(formatLib("commons-compress"))
                    .version("1.26.1")
                    .resolveTransitiveDependencies(true)
                    .build());

            libraryManager.loadLibrary(Library.builder()
                    .groupId(formatLib("com.bucket4j"))
                    .artifactId(formatLib("bucket4j_jdk17-core"))
                    .version("8.16.0")
                    .resolveTransitiveDependencies(true)
                    .build());

            libraryManager.loadLibrary(Library.builder()
                    .repository("https://repo.eternalcode.pl/snapshots")
                    .groupId(formatLib("dev.rollczi"))
                    .artifactId(formatLib("litecommands-folia"))
                    .version("3.10.10-SNAPSHOT")
                    .resolveTransitiveDependencies(true)
                    .build());

            libraryManager.loadLibrary(Library.builder()
                    .repository("https://repo.eternalcode.pl/snapshots")
                    .groupId(formatLib("dev.rollczi"))
                    .artifactId(formatLib("litecommands-adventure"))
                    .version("3.10.10-SNAPSHOT")
                    .resolveTransitiveDependencies(true)
                    .build());

            libraryManager.loadLibrary(Library.builder()
                    .groupId(formatLib("com.google.genai"))
                    .artifactId(formatLib("google-genai"))
                    .version("1.46.0")
                    .resolveTransitiveDependencies(true)
                    .build());

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}