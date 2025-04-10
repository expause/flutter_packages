package io.flutter.plugins.videoplayer;

import android.util.Log;

import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class EncryptedVideoManager {
    private static final Map<String, String> videoSessionCookies = Collections.synchronizedMap(new HashMap<>());
    private static final Map<String, MediaDecryption> videoDecryptions = Collections.synchronizedMap(new HashMap<>());
    private static final ReentrantLock syncLock = new ReentrantLock();

    // Singleton instance
    private static final EncryptedVideoManager INSTANCE = new EncryptedVideoManager();

    // Private constructor to prevent instantiation
    private EncryptedVideoManager() {
    }

    // Public method to access the singleton instance
    public static EncryptedVideoManager getInstance() {
        return INSTANCE;
    }

    public Map<String, String> getRequestCookies(String url) {
        Map<String, String> headers = new HashMap<>();
        String videoId = extractVideoId(url);
        if (videoId != null) {
            syncLock.lock();
            try {
                String sessionCookie = videoSessionCookies.get(videoId);
                if (sessionCookie != null) {
                    headers.put("set-cookie", sessionCookie);
                }
            } finally {
                syncLock.unlock();
            }
        }
        return headers;
    }

    public void setRequestCookies(String url, String cookieName, String cookieValue) {
        if ("set-cookie".equalsIgnoreCase(cookieName)) {
            String videoId = extractVideoId(url);
            if (videoId != null) {
                syncLock.lock();
                try {
                    videoSessionCookies.put(videoId, cookieValue);
                } finally {
                    syncLock.unlock();
                }
            }
        }
    }

    public void clearRequestCookies(String url) {
        String videoId = extractVideoId(url);
        if (videoId != null) {
            syncLock.lock();
            try {
                videoSessionCookies.remove(videoId);
            } finally {
                syncLock.unlock();
            }
        }
    }

    public void setDecryption(String videoId, MediaDecryption data) {
        if (videoId != null && data != null) {
            syncLock.lock();
            try {
                videoDecryptions.put(videoId, data);
            } finally {
                syncLock.unlock();
            }
        }
    }

    @Nullable
    private MediaDecryption getDecryption(String videoId) {
        if (videoId != null) {
            syncLock.lock();
            try {
                return videoDecryptions.get(videoId);
            } finally {
                syncLock.unlock();
            }
        }
        return null;
    }

    @Nullable
    public MediaDecryptionKeys getDecryptionKeys(String videoId) {
        MediaDecryption encrypted = getDecryption(videoId);
        if (encrypted == null) return null;

        try {
            // 🔐 Derive session key from uid and iat
            String sessionKeyMaterial = encrypted.uid + "-" + encrypted.iat;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fullKey = digest.digest(sessionKeyMaterial.getBytes(StandardCharsets.UTF_8));
            byte[] sessionKey = Arrays.copyOfRange(fullKey, 0, 16); // AES-128

            // AES decrypt DK
            byte[] decryptedDk = decryptAes128CBC(encrypted.dk, sessionKey, encrypted.dkAesIv);
            // AES decrypt IV
            byte[] decryptedIv = decryptAes128CBC(encrypted.iv, sessionKey, encrypted.ivAesIv);

            // Unshift the bytes
            var encryptionKeyPrefix = "expause-video-key-";
            var encryptionSecretName = encryptionKeyPrefix + videoId;
            var ivKeyPrefix = "expause-iv-key-";
            var ivSecretName = ivKeyPrefix + videoId;

//            Log.d("______ Android", "DK secretName: " + encryptionSecretName);
//            Log.d("______ Android", "IV secretName: " + ivSecretName);
//            Log.d("______ Android", "Decrypted DK before unshift: " + Arrays.toString(decryptedDk));
//            Log.d("______ Android", "Decrypted IV before unshift: " + Arrays.toString(decryptedIv));

            decryptedDk = unshiftKeyBytes(decryptedDk, encryptionSecretName);
            decryptedIv = unshiftKeyBytes(decryptedIv, ivSecretName);
//            Log.d("______ Android", "Decrypted DK after unshift: " + Arrays.toString(decryptedDk));
//            Log.d("______ Android", "Decrypted IV after unshift: " + Arrays.toString(decryptedIv));

            return new MediaDecryptionKeys(decryptedDk, decryptedIv);
        } catch (Exception e) {
            Log.e("EncryptedVideoManager", "Decryption error: " + e.getMessage(), e);
            return null;
        }
    }

    public void removeDecryption(String videoId) {
        if (videoId != null) {
            syncLock.lock();
            try {
                videoDecryptions.remove(videoId);
            } finally {
                syncLock.unlock();
            }
        }
    }

    private byte[] decryptAes128CBC(byte[] encryptedData, byte[] key, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        return cipher.doFinal(encryptedData);
    }

    private byte[] unshiftKeyBytes(byte[] shiftedKey, String secretName) throws Exception {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hash = sha256.digest(secretName.getBytes(StandardCharsets.UTF_8));
        byte[] result = new byte[shiftedKey.length];

        for (int i = 0; i < shiftedKey.length; i++) {
            result[i] = (byte)(shiftedKey[i] ^ hash[i % hash.length]);
        }

        return result;
    }

    @Nullable
    public String extractVideoId(@Nullable String url) {
        if (url == null) return null;
        String[] segments = url.split("/");
        return segments.length > 5 ? segments[5] : null;
    }
}