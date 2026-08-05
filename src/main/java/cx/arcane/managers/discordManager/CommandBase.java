package cx.arcane.managers.discordManager;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;

public abstract class CommandBase {
    public String getName() {
        return meta().name().toLowerCase();
    }

    public String getDescription() {
        return meta().description();
    }

    public DiscordCommand.Param[] getParams() {
        return meta().params();
    }

    private DiscordCommand meta() {
        DiscordCommand meta = getClass().getAnnotation(DiscordCommand.class);
        if (meta == null) throw new IllegalStateException(getClass().getSimpleName() + " is missing @DiscordCommand");
        return meta;
    }

    public abstract void execute(User sender, List<String> params, MessageReceivedEvent e);

    protected String combineFrom(List<String> params, int from) {
        return combine(params, from, params.size());
    }

    protected String combine(List<String> params, int from, int to) {
        if (from < 1 || to < from || to > params.size())
            throw new IllegalArgumentException("Invalid range: " + from + " to " + to + " (size=" + params.size() + ")");
        return String.join(" ", params.subList(from - 1, to));
    }
}