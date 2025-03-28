package io.flutter.plugins.videoplayer;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class SecureStorageService {

    private static final String KEYSTORE_ALIAS_PREFIX = "ExpauseVideoKey_";
    private static SecureStorageService INSTANCE;
    private final SharedPreferences sharedPreferences;

    // Singleton implementation
    public static SecureStorageService getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (SecureStorageService.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SecureStorageService(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    // Constructor that accepts a Context
    private SecureStorageService(Context context) {
        this.sharedPreferences = context.getSharedPreferences("ExpauseStorage", Context.MODE_PRIVATE);
    }

    /**
     * Retrieves and decrypts the AES-128 key for a given video ID.
     */
    public SecretKeySpec getDecryptionKey(String videoId) throws Exception {
        // Retrieve encrypted key & IV from SharedPreferences
        String encryptedKeyBase64 = sharedPreferences.getString("videoKey_" + videoId, null);
        if (encryptedKeyBase64 == null) {
            throw new KeyStoreException("No encryption key found for video: " + videoId);
        }

        String ivBase64 = sharedPreferences.getString("videoIV_" + videoId, null);
        if (ivBase64 == null) {
            throw new KeyStoreException("No IV found for video: " + videoId);
        }

        byte[] encryptedKey = Base64.decode(encryptedKeyBase64, Base64.DEFAULT);
        byte[] iv = Base64.decode(ivBase64, Base64.DEFAULT);

        // Load the Keystore
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        String keystoreAlias = KEYSTORE_ALIAS_PREFIX + videoId;
        SecretKey secretKey = (SecretKey) keyStore.getKey(keystoreAlias, null);
        if (secretKey == null) {
            throw new KeyStoreException("Keystore key not found");
        }

        // Decrypt the AES-128 key
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));

        byte[] decryptedKey = cipher.doFinal(encryptedKey);

        return new SecretKeySpec(decryptedKey, "AES");
    }

    /**
     * Stores and encrypts an AES-128 key securely in Android Keystore.
     */
    public void storeDecryptionKey(String videoId, String key) {
        try {
            String keystoreAlias = KEYSTORE_ALIAS_PREFIX + videoId;
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);

            // Generate a new key if it doesn't exist
            if (!keyStore.containsAlias(keystoreAlias)) {
                KeyGenParameterSpec.Builder keyGenParameterSpecBuilder = new KeyGenParameterSpec.Builder(
                        keystoreAlias,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
                )
                        .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                        .setKeySize(128) // AES-128
                        .setUserAuthenticationRequired(false);

                // Check if StrongBox is supported
                KeyGenerator keyGenerator = KeyGenerator.getInstance("AES", "AndroidKeyStore");
//                boolean strongBoxSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
//                        keyGenerator.getProvider().getName().equals("AndroidKeyStore");
//
//                if (strongBoxSupported) {
//                    keyGenParameterSpecBuilder.setIsStrongBoxBacked(true);
//                }

                keyGenerator.init(keyGenParameterSpecBuilder.build());
                keyGenerator.generateKey();
            }

            // Encrypt the AES-128 key
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
            SecretKey secretKey = (SecretKey) keyStore.getKey(keystoreAlias, null);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);

            byte[] iv = cipher.getIV();
            byte[] encryptedKey = cipher.doFinal(Base64.decode(key, Base64.DEFAULT));

            // Store in SharedPreferences
            sharedPreferences.edit()
                    .putString("videoKey_" + videoId, Base64.encodeToString(encryptedKey, Base64.DEFAULT))
                    .putString("videoIV_" + videoId, Base64.encodeToString(iv, Base64.DEFAULT))
                    .apply();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Removes the stored AES-128 key and associated metadata for a video.
     */
    public void removeDecryptionKey(String videoId) {
        try {
            String keystoreAlias = KEYSTORE_ALIAS_PREFIX + videoId;
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);

            // Remove key from Keystore
            if (keyStore.containsAlias(keystoreAlias)) {
                keyStore.deleteEntry(keystoreAlias);
            }

            // Remove from SharedPreferences
            sharedPreferences.edit()
                    .remove("videoKey_" + videoId)
                    .remove("videoIV_" + videoId)
                    .apply();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Decrypts data using the stored AES-128 key.
     */
    public byte[] decryptData(String videoId, byte[] encryptedData) throws Exception {
        try {
            SecretKeySpec decryptionKey = getDecryptionKey(videoId);

            // Retrieve IV from SharedPreferences
            String ivBase64 = sharedPreferences.getString("videoIV_" + videoId, null);
            if (ivBase64 == null) {
                throw new KeyStoreException("No IV found for video: " + videoId);
            }
            byte[] iv = Base64.decode(ivBase64, Base64.DEFAULT);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
            cipher.init(Cipher.DECRYPT_MODE, decryptionKey, new IvParameterSpec(iv));

            return cipher.doFinal(encryptedData);
        } catch (Exception ex) {
            throw ex;
        }
    }
}