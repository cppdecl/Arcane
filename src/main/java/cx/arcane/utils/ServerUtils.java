package cx.arcane.utils;

import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.MinecraftServer;
import org.bukkit.Bukkit;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Utility class for server-related helper methods.
 */
public final class ServerUtils {

    private static int protocol = -1;

    public static int getProtocol() {
        if (protocol == -1) {
            ServerStatus status = MinecraftServer.getServer().getStatus();
            if (status == null) return -1;

            status.version().ifPresent(a -> {
                protocol = a.protocol();
            });
        }

        return protocol;
    }


    // Prevent instantiation
    private ServerUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Converts a {@link Throwable}'s stack trace into a {@link String}.
     *
     * @param throwable the throwable to convert
     * @return the full stack trace as a string
     */
    public static String stacktraceToString(final Throwable throwable) {
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            throwable.printStackTrace(pw);
        }
        return sw.toString();
    }

    /**
     * Checks if the server is running Folia (a Paper fork with asynchronous region threading).
     *
     * @return true if Folia is detected, false otherwise
     */
    public static boolean isFolia() {
        return classForName(
                Bukkit.getServer().getClass().getClassLoader(),
                "io.papermc.paper.threadedregions.RegionizedServer"
        ) != null;
    }

    /**
     * Attempts to load a class by name using the given {@link ClassLoader}.
     *
     * @param classLoader the class loader to use
     * @param className   the fully-qualified class name
     * @return the Class object if found, null if not found or any exception occurs
     */
    public static Class<?> classForName(final ClassLoader classLoader, final String className) {
        try {
            return classLoader.loadClass(className);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    /**
     * Throws a checked or unchecked exception without needing to declare it.
     * Useful for sneaky rethrowing.
     *
     * @param throwable the throwable to throw
     * @param <T>       a generic throwable type
     * @throws T the throwable passed in
     */
    @SuppressWarnings("unchecked")
    public static <T extends Throwable> void sneakyThrow(final Throwable throwable) throws T {
        throw (T) throwable;
    }
}