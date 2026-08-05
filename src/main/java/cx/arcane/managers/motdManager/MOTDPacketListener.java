package cx.arcane.managers.motdManager;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.status.server.WrapperStatusServerResponse;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cx.arcane.utils.ServerUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

public class MOTDPacketListener implements PacketListener {

    @Override
    public void onPacketSend(PacketSendEvent e) {
        if (e.getPacketType() != PacketType.Status.Server.RESPONSE) return;

        WrapperStatusServerResponse wrapper = new WrapperStatusServerResponse(e);

        StatusBuilder builder = new StatusBuilder(wrapper)
                .name("Arcane")
                .protocol(ServerUtils.getProtocol())
                .descriptionLineOne(MiniMessage.miniMessage().deserialize("                       <#F40BBA><bold>ARCANE.CX</bold>                   <#A8A8A8>Asia \uD83C\uDF0F"))
                .descriptionLineTwo(MiniMessage.miniMessage().deserialize("              <gradient:#F949A1:#FDBCD3> • sᴜʀᴠɪᴠᴀʟ ᴍᴜʟᴛɪᴘʟᴀʏᴇʀ • "))
                .playerCount(Bukkit.getServer().getOnlinePlayers().size())
                .maxPlayers(Bukkit.getServer().getOnlinePlayers().size())
                .favicon(createLogo());

        wrapper.setComponent(builder.build());

        e.markForReEncode(true);
    }

    private BufferedImage createLogo() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 64, 64, CachedLogo.serverLogoPixels, 0, 64);
        return image;
    }

    public static class StatusBuilder {

        private final JsonObject original;

        private String versionName;
        private Integer protocol;

        private Integer online;
        private Integer max;

        private Component lineOne;
        private Component lineTwo;

        private final List<Component> samples = new ArrayList<>();

        private BufferedImage favicon;

        public StatusBuilder(WrapperStatusServerResponse wrapper) {
            this.original = wrapper.getComponent();
        }

        public StatusBuilder name(String name) {
            this.versionName = name;
            return this;
        }

        public StatusBuilder protocol(int protocol) {
            this.protocol = protocol;
            return this;
        }

        public StatusBuilder playerCount(int online) {
            this.online = online;
            return this;
        }

        public StatusBuilder maxPlayers(int max) {
            this.max = max;
            return this;
        }

        public StatusBuilder descriptionLineOne(Component component) {
            this.lineOne = component;
            return this;
        }

        public StatusBuilder descriptionLineTwo(Component component) {
            this.lineTwo = component;
            return this;
        }

        public StatusBuilder playerSample(Component component) {
            this.samples.add(component);
            return this;
        }

        public StatusBuilder favicon(BufferedImage image) {
            this.favicon = image;
            return this;
        }

        public JsonObject build() {

            JsonObject root = original.deepCopy();

            // ========================
            // Version
            // ========================
            JsonObject version = root.has("version")
                    ? root.getAsJsonObject("version")
                    : new JsonObject();

            if (versionName != null) version.addProperty("name", versionName);
            if (protocol != null) version.addProperty("protocol", protocol);

            root.add("version", version);

            // ========================
            // Players
            // ========================
            JsonObject players = root.has("players")
                    ? root.getAsJsonObject("players")
                    : new JsonObject();

            if (online != null) players.addProperty("online", online);
            if (max != null) players.addProperty("max", max);

            if (!samples.isEmpty()) {
                JsonArray sampleArray = new JsonArray();
                for (Component component : samples) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("name", GsonComponentSerializer.gson().serialize(component));
                    obj.addProperty("id", UUID.randomUUID().toString());
                    sampleArray.add(obj);
                }
                players.add("sample", sampleArray);
            }

            root.add("players", players);

            // ========================
            // Description (2 lines)
            // ========================
            if (lineOne != null || lineTwo != null) {

                Component combined = Component.empty();

                if (lineOne != null) combined = combined.append(lineOne);
                if (lineTwo != null) {
                    combined = combined.append(Component.newline()).append(lineTwo);
                }

                JsonObject description = GsonComponentSerializer.gson()
                        .serializer()
                        .fromJson(
                                GsonComponentSerializer.gson().serialize(combined),
                                JsonObject.class
                        );

                root.add("description", description);
            }

            // ========================
            // Favicon
            // ========================
            if (favicon != null) {
                root.addProperty("favicon", encodeFavicon(favicon));
            }

            return root;
        }

        private String encodeFavicon(BufferedImage image) {
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ImageIO.write(image, "PNG", output);
                return "data:image/png;base64," +
                        Base64.getEncoder().encodeToString(output.toByteArray());
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }
}