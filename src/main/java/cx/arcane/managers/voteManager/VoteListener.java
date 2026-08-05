package cx.arcane.managers.voteManager;

import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.utils.Log;
import org.bukkit.Bukkit;

import javax.crypto.Cipher;
import java.io.File;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;

public class VoteListener {

    private static ServerSocket server;
    private static PrivateKey privateKey;
    private static volatile boolean running;

    private static final int PORT = 12000;

    public static void startListener() {
        if (running) return;
        running = true;

        loadKeys();

        new Thread(VoteListener::runServer, "Arcane-VoteListener").start();
    }

    public static void stopListener() {
        running = false;
        try {
            if (server != null) {
                server.close();
                Log.info("Vote listener stopped");
            }
        } catch (Exception ignored) {}
    }

    private static void loadKeys() {
        try {
            File dir = new File("plugins/Arcane/Keys");
            if (!dir.exists()) dir.mkdirs();

            File priv = new File(dir, "private.key");
            File pub = new File(dir, "public.key");

            if (!priv.exists() || !pub.exists()) {
                Log.info("Generating Votifier RSA keys...");
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                KeyPair pair = generator.generateKeyPair();

                Files.writeString(priv.toPath(), Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
                Files.writeString(pub.toPath(), Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
                Log.info("Generated keys in plugins/Arcane/Keys/");
            }

            byte[] keyBytes = Base64.getDecoder().decode(Files.readString(priv.toPath()));
            privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            Log.info("Loaded Votifier private key");

        } catch (Exception e) {
        }
    }

    private static void runServer() {
        try {
            server = new ServerSocket(PORT);
            Log.info("Votifier V1 listener started on port " + PORT);

            while (running) {
                Socket socket = server.accept();
                socket.setSoTimeout(5000);

                new Thread(() -> handle(socket), "Arcane-VoteConnection").start();
            }

        } catch (Exception e) {
        }
    }

    private static void handle(Socket socket) {
        String remote = String.valueOf(socket.getRemoteSocketAddress());
        //Log.info("[Votifier V1] Incoming connection from " + remote);

        try (socket) {
            var in = socket.getInputStream();
            var out = socket.getOutputStream();

            //Log.info("[Votifier V1] Sending handshake to " + remote);
            out.write("VOTIFIER 1\n".getBytes(StandardCharsets.UTF_8));
            out.flush();

            //Log.info("[Votifier V1] Waiting for encrypted payload from " + remote);
            byte[] block = in.readAllBytes();

            //Log.info("[Votifier V1] Received " + block.length + " bytes from " + remote);
            if (block.length == 0) {
                //Log.info("[Votifier V1] Empty payload from " + remote + ", closing connection");
                return;
            }

            try {
                Cipher cipher = Cipher.getInstance("RSA");
                cipher.init(Cipher.DECRYPT_MODE, privateKey);

                //Log.info("[Votifier V1] Decrypting payload from " + remote);
                byte[] decrypted = cipher.doFinal(block);

                String voteData = new String(decrypted, StandardCharsets.UTF_8).trim();
                //Log.info("[Votifier V1] Decrypted vote payload from " + remote + ":\n" + voteData);

                String[] parts = voteData.split("\n");
                //Log.info("[Votifier V1] Parsed " + parts.length + " lines from decrypted payload");

                if (parts.length < 5) {
                    //Log.info("[Votifier V1] Invalid payload from " + remote + " - expected at least 5 lines");
                    return;
                }

                if (!parts[0].equals("VOTE")) {
                    //Log.info("[Votifier V1] Invalid payload header from " + remote + " - got: " + parts[0]);
                    return;
                }

                String service = parts[1];
                String username = parts[2];
                String address = parts[3];
                String timestampRaw = parts[4];

                Log.info("[Votifier V1] Vote details from " + remote
                        + " | service=" + service
                        + " | username=" + username
                        + " | address=" + address
                        + " | timestamp=" + timestampRaw);

                long ts;
                try {
                    ts = OffsetDateTime.parse(timestampRaw).toInstant().toEpochMilli();
                    //Log.info("[Votifier V1] Parsed timestamp as OffsetDateTime: " + ts);
                } catch (Exception e) {
                    //Log.info("[Votifier V1] OffsetDateTime parse failed, trying Instant format for: " + timestampRaw);
                    try {
                        ts = Instant.parse(timestampRaw).toEpochMilli();
                        //Log.info("[Votifier V1] Parsed timestamp as Instant: " + ts);
                    } catch (Exception ex) {
                        ts = System.currentTimeMillis();
                        //Log.info("[Votifier V1] Timestamp parse failed, using current system time: " + ts);
                    }
                }

                PlayerData pData = PlayerManager.getByNameIgnoreCase(username);
                if (pData == null) {
                    //Log.info("[Votifier V1] No online/offline player data found for username: " + username);
                    return;
                }

                //Log.info("[Votifier V1] Matched player data for " + username + " -> " + pData.getUniqueId());

                VoteAction vote = new VoteAction();
                vote.setUniqueId(pData.getUniqueId());
                vote.setUsername(pData.getUsername());
                vote.setServiceName(service);
                vote.setAddress(address);
                vote.setVoteTimestamp(ts);
                vote.setReceivedTimestamp(System.currentTimeMillis());
                vote.setRawPayload(voteData);

                //Log.info("[Votifier V1] Dispatching vote for " + pData.getUsername() + " from service " + service);
                VoteManager.onVote(vote, pData);


                //Log.info("[Votifier V1] Vote handled successfully for " + pData.getUsername());

            } catch (Exception e) {
                //Log.info("[Votifier V1] Failed to process vote from " + remote + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
                //e.printStackTrace();
            }

        } catch (Exception e) {
            //Log.info("[Votifier V1] Connection handling failed for " + remote + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
            //e.printStackTrace();
        }
    }
}