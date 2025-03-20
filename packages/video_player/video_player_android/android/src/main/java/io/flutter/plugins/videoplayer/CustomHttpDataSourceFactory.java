package io.flutter.plugins.videoplayer;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class CustomHttpDataSourceFactory implements HttpDataSource.Factory {
    private static final Map<String, String> videoSessionCookies = Collections.synchronizedMap(new HashMap<>());
    private static final ReentrantLock sessionLock = new ReentrantLock();
    private final String userAgent;

    public CustomHttpDataSourceFactory(String userAgent) {
        this.userAgent = userAgent;
    }

    @Override
    public HttpDataSource createDataSource() {
        return new CustomHttpDataSource(userAgent);
    }

    private static class CustomHttpDataSource extends DefaultHttpDataSource {
        public CustomHttpDataSource(String userAgent) {
            super(userAgent);
        }

        @Override
        public void setRequestProperty(String name, String value) {
            super.setRequestProperty(name, value);
        }

        @Override
        public Map<String, String> getRequestProperties() {
            Map<String, String> headers = super.getRequestProperties();
            String videoId = extractVideoId(getLastOpenedUri());
            if (videoId != null) {
                sessionLock.lock();
                try {
                    String sessionCookie = videoSessionCookies.get(videoId);
                    if (sessionCookie != null) {
                        headers.put("cookie", sessionCookie);
                    }
                } finally {
                    sessionLock.unlock();
                }
            }
            return headers;
        }

        @Override
        public void clearAllRequestProperties() {
            super.clearAllRequestProperties();
        }

        @Override
        public void addResponseProperty(String name, String value) {
            super.addResponseProperty(name, value);
            if ("set-cookie".equalsIgnoreCase(name)) {
                String videoId = extractVideoId(getLastOpenedUri());
                if (videoId != null) {
                    sessionLock.lock();
                    try {
                        videoSessionCookies.put(videoId, value);
                    } finally {
                        sessionLock.unlock();
                    }
                }
            }
        }
    }

    @Nullable
    private static String extractVideoId(@Nullable String url) {
        if (url == null) return null;
        String[] segments = url.split("/");
        return segments.length > 2 ? segments[2] : null;
    }
}
