package afg.achat.afgApprovAchat.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class PasswordCryptoUtil {

    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private final SecretKeySpec secretKey;

    // La clé est désormais lue depuis application-secret.properties (fichier gitignoré,
    // packagé dans le WAR au build). Voir application-secret.properties.example pour le modèle.
    public PasswordCryptoUtil(@Value("${mail.encryption.key}") String cle) {
        if (cle == null || cle.length() != 32) {
            throw new IllegalStateException(
                    "La propriété 'mail.encryption.key' doit faire exactement 32 caractères. " +
                            "Vérifiez que application-secret.properties existe bien dans src/main/resources " +
                            "(copiez-le depuis application-secret.properties.example) et contient une clé valide.");
        }
        this.secretKey = new SecretKeySpec(cle.getBytes(), "AES");
    }

    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes());

            byte[] result = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(cipherText, 0, result, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new RuntimeException("Erreur chiffrement mot de passe mail", e);
        }
    }

    public String decrypt(String encrypted) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encrypted);
            byte[] iv = new byte[IV_LENGTH];
            byte[] cipherText = new byte[decoded.length - IV_LENGTH];
            System.arraycopy(decoded, 0, iv, 0, IV_LENGTH);
            System.arraycopy(decoded, IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plain = cipher.doFinal(cipherText);

            return new String(plain);
        } catch (Exception e) {
            throw new RuntimeException("Erreur déchiffrement mot de passe mail", e);
        }
    }
}