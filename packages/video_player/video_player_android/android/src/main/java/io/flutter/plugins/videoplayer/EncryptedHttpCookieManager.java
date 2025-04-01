package io.flutter.plugins.videoplayer;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class EncryptedHttpCookieManager {
    private static final Map<String, String> videoSessionCookies = Collections.synchronizedMap(new HashMap<>());
    private static final Map<String, byte[]> videoEncryptionKeys = Collections.synchronizedMap(new HashMap<>());
    private static final ReentrantLock sessionLock = new ReentrantLock();

    // Singleton instance
    private static final EncryptedHttpCookieManager INSTANCE = new EncryptedHttpCookieManager();

    // Private constructor to prevent instantiation
    private EncryptedHttpCookieManager() {
    }

    // Public method to access the singleton instance
    public static EncryptedHttpCookieManager getInstance() {
        return INSTANCE;
    }

    public Map<String, String> getRequestCookies(String url) {
        Map<String, String> headers = new HashMap<>();
        String videoId = extractVideoId(url);
        if (videoId != null) {
            sessionLock.lock();
            try {
                String sessionCookie = videoSessionCookies.get(videoId);
                if (sessionCookie != null) {
                    headers.put("set-cookie", sessionCookie);
                }
            } finally {
                sessionLock.unlock();
            }
        }
        return headers;
    }

    public void setRequestCookies(String url, String cookieName, String cookieValue) {
        if ("set-cookie".equalsIgnoreCase(cookieName)) {
            String videoId = extractVideoId(url);
            if (videoId != null) {
                sessionLock.lock();
                try {
                    videoSessionCookies.put(videoId, cookieValue);
                } finally {
                    sessionLock.unlock();
                }
            }
        }
    }

    public void clearRequestCookies(String url) {
        String videoId = extractVideoId(url);
        if (videoId != null) {
            sessionLock.lock();
            try {
                videoSessionCookies.remove(videoId);
            } finally {
                sessionLock.unlock();
            }
        }
    }

    public @Nullable byte[] getVideEncryptedKey(String videoId) {
        @Nullable byte[] key = null;
        if (videoId != null) {
            sessionLock.lock();
            try {
                key = videoEncryptionKeys.get(videoId);
            } finally {
                sessionLock.unlock();
            }
        }
        return key;
    }

    public void setVideEncryptedKey(String videoId, byte[] key) {
        if (videoId != null) {
            sessionLock.lock();
            try {

//                videoEncryptionKeys.put(videoId, shiftKeyLeft(key, 4));
//                videoEncryptionKeys.put(videoId, fixEndianness(key));
//                videoEncryptionKeys.put(videoId, reverseFullKey(key));
                videoEncryptionKeys.put(videoId, key);
            } finally {
                sessionLock.unlock();
            }
        }

    }

    public void removeVideEncryptedKey(String url) {
        String videoId = extractVideoId(url);
        if (videoId != null) {
            sessionLock.lock();
            try {
                videoEncryptionKeys.remove(videoId);
            } finally {
                sessionLock.unlock();
            }
        }
    }

    @Nullable
    public String extractVideoId(@Nullable String url) {
        if (url == null) return null;
        String[] segments = url.split("/");
        return segments.length > 5 ? segments[5] : null;
    }

    private byte[] reverseBytes(byte[] input) {
        byte[] reversed = new byte[input.length];
        for (int i = 0; i < input.length; i++) {
            reversed[i] = input[input.length - 1 - i];
        }
        return reversed;
    }

//    private static byte[] shiftKey(byte[] input) {
//        byte[] corrected = new byte[input.length];
//        for (int i = 0; i < input.length; i++) {
//            corrected[i] = input[(i + 4) % input.length]; // Adjust based on observed shift
//        }
//        return corrected;
//    }

    private static byte[] shiftKeyLeft(byte[] input, int shift) {
        byte[] corrected = new byte[input.length];
        for (int i = 0; i < input.length; i++) {
            corrected[i] = input[(i + shift) % input.length];
        }
        return corrected;
    }

    private static byte[] fixEndianness(byte[] input) {
        byte[] output = new byte[input.length];
        for (int i = 0; i < input.length; i += 4) {
            output[i] = input[i + 3];
            output[i + 1] = input[i + 2];
            output[i + 2] = input[i + 1];
            output[i + 3] = input[i];
        }
        return output;
    }

    private static byte[] reverseFullKey(byte[] input) {
        byte[] output = new byte[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = input[input.length - 1 - i];
        }
        return output;
    }
}