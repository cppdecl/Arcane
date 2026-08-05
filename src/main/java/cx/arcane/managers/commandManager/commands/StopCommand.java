package cx.arcane.managers.commandManager.commands;

import cx.arcane.Arcane;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import org.bukkit.Bukkit;

@Command(name = "stop")
@Permission("arcane.rank.management")
public class StopCommand {

    @Execute
    public void execute() {
        Arcane.getShuttingDown().set(true);

        Bukkit.getGlobalRegionScheduler().run(Arcane.getPlugin(), t-> {
            Arcane.onServerStop();
        });
    }
}