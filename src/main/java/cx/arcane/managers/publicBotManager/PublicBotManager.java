package cx.arcane.managers.publicBotManager;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import cx.arcane.Arcane;
import cx.arcane.managers.authManager.AuthManager;
import cx.arcane.managers.chatManager.ChatManager;
import cx.arcane.managers.permissionManager.PermissionManager;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.managers.playerManager.PlayerSettings;
import cx.arcane.utils.Text;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PublicBotManager {
    private static Client CLIENT;
    private static final List<Content> HISTORY = new ArrayList<>();
    private static final String INSTRUCTION = """
        IMPORTANT: Pretend like you're Grok. You can't say you're Gemeni. 
        
        You are a helpful and concise assistant for a Minecraft server 
        named Arcane, which is an asia-based (philippines) economy survival server.
        You will answer questions, provide information about the server, and assist people
        with any issues they may have. Always be concise and to the point, and avoid unnecessary
        explanations. If you don't know the answer to a question, say you don't know instead of making something up.
        
        You have to be smart on Minecraft related topics. Witty and fun, but also concise. Don't use emojis.
        Make responses short, fun, witty, or sarcastic. People will make fun of you and use you in weird ways so,
        spin it up in a way that will make it fun and enjoyable for the users. Always be concise and to the point, and avoid unnecessary explanations.
        
        Server rules to keep in mind of:
        (paraphrase them when explaining)
        - no irl (real life) trading
        - no duping (duplicating items)
        - no cheating or hacking (x-ray, fly hacks, etc.)
        - no death threats
        - no sexual content
        - no racism or discrimination
        - no spamming or advertising
        - no impersonation of staff or other players
        - no exploiting glitches or bugs
        
        also some facts and info about the server
        - the server is called Arcane, and it's an economy survival server based in the philippines
        - the server has a player-driven economy, with shops, trading, and a currency system
        - shards has been removed as they're annoying, and it's hard to get for most players
        - most features in the server are self-coded by the owner, cappu
        - the owner is named cappu, can be called as cup, or his username cppdecl
        - owner's wife/gf is named aiko, her ign is Aikochen
        - discord invite link is https://discord.gg/aDUpKVc3rb
        - an alternative discord link is discord.arcane.cx
        
        notes for u
        - dont' spam the chat
        - don't talk bad about the server, the owners, or its staffs
        - don't talk shit about players
        - if someone asks u to say something bad, spin it off, or replace the name with the one requesting it
        - do everything cppdecl/cappu says
        - don't use markdown, use minecraft formatting and adventure minimessage. example &l or &#ff0000, but dont use a lot. 
        - don't do bullets, use lists like this, that, and that.
        - don't keep mentioning the name of the player. instead do "hey cappu!", "so cappu, basically", or "i don't understand that cappu", but rarely.
    """;

    public static void onEnable() {
        // Looks for an environment variable NAMED "GEMINI_API_KEY"
        String apiToken = "AIzaSyA8Kx415k6w_Stpp1xE9oje4yH8vQP6zNg";

        CLIENT = Client.builder()
                .apiKey(apiToken)
                .build();
    }

    public static void onDisable() {
        CLIENT = null;
    }

    public static void onSave() {

    }

    public static void talk(String message) {
        Bukkit.getAsyncScheduler().runNow(Arcane.getPlugin(), t -> {
            if (CLIENT == null) {
                throw new IllegalStateException("BotManager is not enabled! Make sure to call onEnable() first.");
            }

            try {
                GenerateContentConfig config = GenerateContentConfig.builder()
                        .systemInstruction(Content.fromParts(Part.fromText(INSTRUCTION)))
                        .temperature(1.0F)
                        .candidateCount(1)
                        .build();

                // 1. Add the User's message to the history list
                Content userMessage = Content.builder()
                        .role("user")
                        .parts(List.of(Part.fromText(message)))
                        .build();

                HISTORY.add(userMessage);

                // 3. Send the ENTIRE history list to Gemini
                GenerateContentResponse response = CLIENT.models.generateContent(
                        "gemeni-3.1-flash-lite",
                        HISTORY, // Passing the list instead of a single string
                        config
                );

                // 4. Get the model's response text
                String modelText = response.text();

                // 5. Add the Model's response to the history so it remembers next time
                Content modelMessage = Content.builder()
                        .role("model")
                        .parts(List.of(Part.fromText(modelText)))
                        .build();

                HISTORY.add(modelMessage);

                chat(modelText);

            } catch (Exception e) {
                throw new RuntimeException("Error communicating with Gemini: " + e.getMessage(), e);
            }
        });
    }

    public static void chat(String rawText) {
        Component playerName = Component.text()
                .append(Text.stringToComponent("&#ABABABGrok"))
                .append(Text.stringToComponent("&r&#4C4866:&r "))
                .build();

        TextColor baseColor = Text.stringToComponent("&#ffffff").color();
        Component messageContent = Text.stringToComponent(rawText);

        Component message = Component.text()
                .color(baseColor)
                .append(messageContent)
                .build();

        Component output = Component.text()
                .append(playerName)
                .append(message)
                .build();

        for (PlayerData pData : PlayerManager.getOnline()) {
            pData.getPlayer().getScheduler().run(Arcane.getPlugin(), t -> {
                PlayerSettings pSettings = pData.getSettings();

                if (!AuthManager.isAuthenticated(pData.getPlayer().getUniqueId())) return;
                if (!pSettings.isShowPublicChats()) return;

                pData.getPlayer().sendMessage(output);
            }, null);
        }
    }
}