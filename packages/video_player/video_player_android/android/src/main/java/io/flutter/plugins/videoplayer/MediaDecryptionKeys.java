package io.flutter.plugins.videoplayer;

import androidx.annotation.Nullable;

public class MediaDecryptionKeys {
    public final byte[] dk;
    public final byte[] iv;

    public MediaDecryptionKeys(@Nullable byte[] dk,
                               @Nullable byte[] iv) {
        this.dk = dk;
        this.iv = iv;
    }
}
