package cx.arcane.managers.permissionManager;

import cx.arcane.Arcane;
import cx.arcane.utils.Log;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.context.ImmutableContextSet;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class PermissionManager {

    public static void onEnable() {
        try {
            LuckPermsProvider.get();
            Log.info("LuckPerms found, using it for permissions.");
        } catch (IllegalStateException e) {
            throw new RuntimeException("LuckPerms is not installed or not enabled.");
        }
    }

    public static void onDisable() {

    }
    public static void onSave() {

    }

    public static boolean check(Player player, String permission) {
        if (player == null)
            return false;

        LuckPerms lp = LuckPermsProvider.get();
        User user = lp.getUserManager().getUser(player.getUniqueId());
        if (user == null) return false;

        ImmutableContextSet context = ImmutableContextSet.builder()
                .build();

        CachedPermissionData data = user.getCachedData()
                .getPermissionData(
                        lp.getContextManager()
                                .getStaticQueryOptions()
                                .toBuilder()
                                .context(context)
                                .build()
                );

        // TRUE only allowed. If perm not exist or undefined for player, then it's no perm
        return data.checkPermission(permission) == net.luckperms.api.util.Tristate.TRUE;
    }

    public static CompletableFuture<Boolean> check(UUID uuid, String permission) {
        LuckPerms lp = LuckPermsProvider.get();

        return lp.getUserManager().loadUser(uuid).thenApply(user -> {
            if (user == null) return false;

            ImmutableContextSet context = ImmutableContextSet.builder()
                    .build();

            CachedPermissionData data = user.getCachedData()
                    .getPermissionData(
                            lp.getContextManager()
                                    .getStaticQueryOptions()
                                    .toBuilder()
                                    .context(context)
                                    .build()
                    );

            return data.checkPermission(permission) == net.luckperms.api.util.Tristate.TRUE;
        });
    }

    public static int getPermissionInt(Player player, String basePerms, int defaultValue) {
        Integer v = resolvePermissionValue(player, basePerms, Integer::parseInt, true);
        return v != null ? v : defaultValue;
    }

    public static long getPermissionLong(Player player, String basePerms, long defaultValue) {
        Long v = resolvePermissionValue(player, basePerms, Long::parseLong, true);
        return v != null ? v : defaultValue;
    }

    public static String getPermissionString(Player player, String basePerms, String defaultValue) {
        String v = resolvePermissionValue(player, basePerms, s -> s, false);
        return v != null ? v : defaultValue;
    }

    public static int getPermissionInt(UUID uniqueId, String basePerms, int defaultValue) {
        Integer v = resolvePermissionValue(uniqueId, basePerms, Integer::parseInt, true);
        return v != null ? v : defaultValue;
    }

    public static long getPermissionLong(UUID uniqueId, String basePerms, long defaultValue) {
        Long v = resolvePermissionValue(uniqueId, basePerms, Long::parseLong, true);
        return v != null ? v : defaultValue;
    }

    public static String getPermissionString(UUID uniqueId, String basePerms, String defaultValue) {
        String v = resolvePermissionValue(uniqueId, basePerms, s -> s, false);
        return v != null ? v : defaultValue;
    }

    private static <T> T resolvePermissionValue(
            Player player,
            String basePerms,
            Function<String, T> parser,
            boolean numericMax
    ) {
        return resolvePermissionValue(player.getUniqueId(), basePerms, parser, numericMax);
    }

    private static <T> T resolvePermissionValue(
            UUID uniqueId,
            String basePerms,
            Function<String, T> parser,
            boolean numericMax
    ) {
        LuckPerms lp = LuckPermsProvider.get();
        User user = lp.getUserManager().getUser(uniqueId);
        if (user == null) return null;

        String prefix = basePerms + ".";

        T userValue = resolveFromNodes(user.getNodes(), prefix, parser, numericMax);
        if (userValue != null) return userValue;

        return user.getInheritedGroups(user.getQueryOptions()).stream()
                .sorted(Comparator.comparingInt((Group g) -> g.getWeight().orElse(0)).reversed())
                .map(group -> resolveFromNodes(group.getNodes(), prefix, parser, numericMax))
                .filter(v -> v != null)
                .findFirst()
                .orElse(null);
    }

    private static <T> T resolveFromNodes(
            Iterable<Node> nodes,
            String prefix,
            Function<String, T> parser,
            boolean numericMax
    ) {
        T best = null;

        for (Node node : nodes) {
            if (!node.getValue()) continue;
            if (!node.getKey().startsWith(prefix)) continue;

            String raw = node.getKey().substring(prefix.length());

            try {
                T parsed = parser.apply(raw);
                if (parsed == null) continue;

                if (!numericMax) {
                    return parsed; // first wins for string
                }

                if (best == null || compareNumeric(parsed, best) > 0) {
                    best = parsed;
                }
            } catch (Exception ignored) {}
        }

        return best;
    }

    private static <T> int compareNumeric(T a, T b) {
        if (a instanceof Integer ai && b instanceof Integer bi)
            return Integer.compare(ai, bi);
        if (a instanceof Long al && b instanceof Long bl)
            return Long.compare(al, bl);
        return 0;
    }
}

