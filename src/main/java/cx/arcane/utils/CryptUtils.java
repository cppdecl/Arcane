package cx.arcane.utils;

import com.google.common.hash.Hashing;
import com.google.common.hash.Hasher;
import org.mindrot.jbcrypt.BCrypt;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import java.util.Optional;
import java.util.Random;

/**
 * Utility class for cryptographic operations such as key pair generation,
 * verify token generation, AES decryption, and server hash calculation.
 */
public final class CryptUtils {

    /** Length of the verify token in bytes */
    public static final int VERIFY_TOKEN_LENGTH = 4;

    /** Algorithm used for asymmetric key pair generation */
    public static final String KEY_PAIR_ALGORITHM = "RSA";

    /** Default RSA key size */
    private static final int RSA_LENGTH = 1024;

    private CryptUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Generates an RSA key pair.
     *
     * @return an Optional containing the generated KeyPair, or empty if generation failed
     */
    public static Optional<KeyPair> generateKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KEY_PAIR_ALGORITHM);
            keyPairGenerator.initialize(RSA_LENGTH);
            return Optional.of(keyPairGenerator.generateKeyPair());
        } catch (NoSuchAlgorithmException e) {
            return Optional.empty();
        }
    }

    /**
     * Generates a random verify token.
     *
     * @param random Random instance used for token generation
     * @return a byte array containing the verify token
     */
    public static byte[] generateVerifyToken(Random random) {
        byte[] token = new byte[VERIFY_TOKEN_LENGTH];
        random.nextBytes(token);
        return token;
    }

    /**
     * Calculates the server hash string (used for Minecraft authentication).
     *
     * @param serverId     the server session ID
     * @param sharedSecret the shared AES key
     * @param publicKey    the server's public key
     * @return server hash as a hexadecimal string
     */
    public static String getServerIdHashString(String serverId, SecretKey sharedSecret, PublicKey publicKey) {
        byte[] serverHash = getServerIdHash(serverId, publicKey, sharedSecret);
        return new BigInteger(serverHash).toString(16);
    }

    /**
     * Decrypts a shared AES key using a private RSA key.
     *
     * @param privateKey the private key used for decryption
     * @param sharedKey  the encrypted AES key
     * @return an Optional containing the decrypted SecretKey, or empty if decryption failed
     */
    public static Optional<SecretKey> decryptSharedKey(PrivateKey privateKey, byte[] sharedKey) {
        return decrypt(privateKey, sharedKey).map(bytes -> new SecretKeySpec(bytes, "AES"));
    }

    /**
     * Verifies that the provided encrypted nonce matches the expected nonce after decryption.
     *
     * @param expected        the expected nonce
     * @param decryptionKey   the private key used to decrypt the nonce
     * @param encryptedNonce  the encrypted nonce
     * @return true if the nonce matches, false otherwise
     */
    public static boolean verifyNonce(byte[] expected, PrivateKey decryptionKey, byte[] encryptedNonce) {
        return decrypt(decryptionKey, encryptedNonce)
                .map(decrypted -> Arrays.equals(expected, decrypted))
                .orElse(false);
    }

    /**
     * Decrypts data with a private key.
     *
     * @param key  the private key
     * @param data the data to decrypt
     * @return an Optional containing decrypted data, or empty if decryption failed
     */
    private static Optional<byte[]> decrypt(PrivateKey key, byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance(key.getAlgorithm());
            cipher.init(Cipher.DECRYPT_MODE, key);
            return Optional.of(cipher.doFinal(data));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Computes the SHA-1 hash of the server ID, shared secret, and public key.
     *
     * @param sessionId    the server session ID
     * @param publicKey    the server's public key
     * @param sharedSecret the shared AES key
     * @return the hash as a byte array
     */
    private static byte[] getServerIdHash(String sessionId, PublicKey publicKey, SecretKey sharedSecret) {
        @SuppressWarnings("deprecation")
        Hasher hasher = Hashing.sha1().newHasher();
        hasher.putBytes(sessionId.getBytes(StandardCharsets.ISO_8859_1));
        hasher.putBytes(sharedSecret.getEncoded());
        hasher.putBytes(publicKey.getEncoded());
        return hasher.hash().asBytes();
    }

    public static String hashPassword(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(12));
    }

    public static boolean validatePassword(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null) return false;
        return BCrypt.checkpw(plainPassword, hashedPassword);
    }
}