package io.flutter.plugins.videoplayer;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class EncryptedVideoManager {
    private static final Map<String, String> videoSessionCookies = Collections.synchronizedMap(new HashMap<>());
    private static final Map<String, byte[]> videoEncryptionKeys = Collections.synchronizedMap(new HashMap<>());
    private static final ReentrantLock sessionLock = new ReentrantLock();

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
}