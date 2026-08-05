package cx.arcane.managers.commandManager.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import cx.arcane.Arcane;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.concurrent.CompletableFuture;

@NullMarked
public class NukeCommand {

    public static LiteralCommandNode<CommandSourceStack> build(String alias) {
        return Commands.literal(alias)
                .requires(NukeCommand::requirements)
                .executes(ctx -> handleSelf(ctx, 5))
                .then(Commands.argument("yield", IntegerArgumentType.integer(1, 600))
                        .executes(ctx -> handleSelf(ctx,
                                IntegerArgumentType.getInteger(ctx, "yield"))))
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests(NukeCommand::suggestPlayers)
                        .executes(ctx -> handleOther(ctx, 5))
                        .then(Commands.argument("yield", IntegerArgumentType.integer(1, 600))
                                .executes(ctx -> handleOther(ctx,
                                        IntegerArgumentType.getInteger(ctx, "yield")))))
                .build();
    }

    private static boolean requirements(CommandSourceStack stack) {
        if (!(stack.getExecutor() instanceof Player player)) return false;
        return player.hasPermission("arcane.rank.management");
    }

    private static CompletableFuture<Suggestions> suggestPlayers(
            CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder
    ) {
        for (PlayerData data : PlayerManager.getOnline()) {
            Player player = Bukkit.getPlayer(data.getUniqueId());
            if (player != null && player.isOnline()) {
                builder.suggest(data.getUsername());
            }
        }
        return builder.buildFuture();
    }

    private static int handleSelf(CommandContext<CommandSourceStack> ctx, int yield) {
        if (!(ctx.getSource().getExecutor() instanceof Player p)) return 0;
        chainedExplosion(p.getLocation(), yield);
        return 1;
    }

    private static int handleOther(CommandContext<CommandSourceStack> ctx, int yield) {
        String name = StringArgumentType.getString(ctx, "target");
        PlayerData targetData = PlayerManager.getByName(name);

        if (targetData == null) {
            sendError(ctx, "That player does not exist!");
            return 0;
        }

        Player target = Bukkit.getPlayer(targetData.getUniqueId());
        if (target == null || !target.isOnline()) {
            sendError(ctx, "That player is not online!");
            return 0;
        }

        chainedExplosion(target.getLocation(), yield);
        return 1;
    }

    private static void chainedExplosion(Location center, int yield) {
        World world = center.getWorld();
        if (world == null) return;

        int maxRadius = Math.min(yield, 600);

        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        for (int r = 0; r <= maxRadius; r++) {
            int ring = r;

            Bukkit.getRegionScheduler().runDelayed(Arcane.getPlugin(), center, scheduledTask -> {
                int minY = Math.max(cy - ring, world.getMinHeight());
                int maxY = Math.min(cy + ring, world.getMaxHeight() - 1);

                for (int x = -ring; x <= ring; x++) {
                    for (int y = minY - cy; y <= maxY - cy; y++) {
                        for (int z = -ring; z <= ring; z++) {
                            double distance = Math.sqrt(x * x + y * y + z * z);
                            if (distance > ring || distance <= ring - 1) continue;

                            int bx = cx + x;
                            int by = cy + y;
                            int bz = cz + z;

                            Location blockLoc = new Location(world, bx, by, bz);
                            Bukkit.getRegionScheduler().run(Arcane.getPlugin(), blockLoc, t -> {
                                Block block = world.getBlockAt(bx, by, bz);
                                if (block.getType().isAir()) return;
                                if (block.getType() == Material.BEDROCK) return;

                                block.setType(Material.AIR, false);

                                if (Math.random() < 0.15 && by < world.getMaxHeight() - 1) {
                                    Block above = world.getBlockAt(bx, by + 1, bz);
                                    if (above.isEmpty()) above.setType(Material.FIRE);
                                }

                                if (Math.random() < 0.5) {
                                    world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE,
                                            bx + Math.random(), by + Math.random(), bz + Math.random(),
                                            4, 0.3, 0.3, 0.3, 0);
                                }
                                if (Math.random() < 0.5) {
                                    world.spawnParticle(Particle.FLAME,
                                            bx + Math.random(), by + Math.random(), bz + Math.random(),
                                            2, 0.2, 0.2, 0.2, 0);
                                }
                            });
                        }
                    }
                }

                for (int i = 0; i < 30; i++) {
                    double px = cx + (Math.random() - 0.5) * ring * 1.5;
                    double py = cy + (Math.random() - 0.5) * ring * 1.2;
                    double pz = cz + (Math.random() - 0.5) * ring * 1.5;
                    Location pLoc = new Location(world, px, py, pz);

                    if (Math.random() < 0.5) world.spawnParticle(Particle.EXPLOSION, px, py, pz, 1);
                    if (Math.random() < 0.5) world.spawnParticle(Particle.LARGE_SMOKE, px, py, pz, 2, 0.3, 0.3, 0.3, 0);
                    if (Math.random() < 0.5) world.spawnParticle(Particle.FLAME, px, py, pz, 2, 0.2, 0.2, 0.2, 0);
                    if (Math.random() < 0.5) world.spawnParticle(Particle.CRIT, px, py, pz, 2, 0.2, 0.2, 0.2, 0);
                }

                for (int i = 0; i < 4; i++) {
                    double offsetX = (Math.random() - 0.5) * ring;
                    double offsetZ = (Math.random() - 0.5) * ring;
                    Location soundLoc = center.clone().add(offsetX, 0, offsetZ);
                    world.playSound(soundLoc, Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.7f + (float) Math.random() * 0.6f);
                }

            }, (long) ring + 1);
        }

        world.playSound(center, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 4f, 0.5f);
        if (Math.random() < 0.5) world.spawnParticle(Particle.EXPLOSION, center, 5);
        if (Math.random() < 0.5) world.spawnParticle(Particle.FLAME, center, 10, 1, 1, 1, 0.1);
        if (Math.random() < 0.5) world.spawnParticle(Particle.LARGE_SMOKE, center, 10, 1, 1, 1, 0.1);
    }

    private static void sendError(CommandContext<CommandSourceStack> ctx, String message) {
        if (ctx.getSource().getExecutor() instanceof Player p) {
            p.sendMessage(Component.text(message, NamedTextColor.DARK_RED));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
        }
    }
}