package cx.arcane.managers.discordManager.commands;

import cx.arcane.managers.discordManager.CommandBase;
import cx.arcane.managers.discordManager.DiscordCommand;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;

@DiscordCommand(
        name = "Ping",
        description = "Checks if the bot is alive."
)
public class PingCommand extends CommandBase {
    @Override
    public void execute(User sender, List<String> params, MessageReceivedEvent e) {
        e.getJDA().getRestPing().queue(ping -> {
            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(0xFC1A6B)
                    .setTitle("Pong!")
                    .addField("REST Latency", "`" + ping + "ms`", true)
                    .addField("Gateway Latency", "`" + e.getJDA().getGatewayPing() + "ms`", true)
                    .setFooter("Requested by " + sender.getName(), sender.getAvatarUrl());

            e.getChannel().sendMessageEmbeds(embed.build()).queue();
        });
    }
}