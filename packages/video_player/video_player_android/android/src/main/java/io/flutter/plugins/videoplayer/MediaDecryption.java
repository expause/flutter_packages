package io.flutter.plugins.videoplayer;

import androidx.annotation.Nullable;

public class MediaDecryption {
    public String uid;
    public int iat;
    public final byte[] dk;
    public final byte[] dkAesIv;
    public final byte[] iv;
    public final byte[] ivAesIv;

    public MediaDecryption(String uid, int iat, @Nullable byte[] dk, @Nullable byte[] dkAesIv,
                           @Nullable byte[] iv, @Nullable byte[] ivAesIv) {
        this.uid = uid;
        this.iat = iat;
        this.dk = dk;
        this.dkAesIv = dkAesIv;
        this.iv = iv;
        this.ivAesIv = ivAesIv;
    }
}
