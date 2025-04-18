package io.flutter.plugins.videoplayer;

import androidx.annotation.Nullable;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;

import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * A flushing variant of a AES/CTR/NoPadding {@link Cipher}.
 *
 * <p>Unlike a regular {@link Cipher}, the update methods of this class are guaranteed to process
 * all of the bytes input (and hence output the same number of bytes).
 */
@UnstableApi
public final class EncryptedAesFlushingCipher {

    private final Cipher cipher;
    private final int blockSize;
    private final byte[] zerosBlock;
    private final byte[] flushedBlock;

    private int pendingXorBytes;

//    public EncryptedAesFlushingCipher(int mode, byte[] secretKey, @Nullable String nonce, long offset) {
//        this(mode, secretKey, getFNV64Hash(nonce), offset);
//    }

//    public EncryptedAesFlushingCipher(int mode, byte[] secretKey, long nonce, long offset) {
//        try {
//            cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
//            blockSize = cipher.getBlockSize();
//            zerosBlock = new byte[blockSize];
//            flushedBlock = new byte[blockSize];
//            long counter = offset / blockSize;
//            int startPadding = (int) (offset % blockSize);
//            cipher.init(
//                    mode,
////                    new SecretKeySpec(secretKey, Util.splitAtFirst(cipher.getAlgorithm(), "/")[0]),
//                    new SecretKeySpec(secretKey, "AES"),
//                    new IvParameterSpec(getInitializationVector(nonce, counter)));
//            if (startPadding != 0) {
//                updateInPlace(new byte[startPadding], 0, startPadding);
//            }
//        } catch (NoSuchAlgorithmException
//                 | NoSuchPaddingException
//                 | InvalidKeyException
//                 | InvalidAlgorithmParameterException e) {
//            // Should never happen.
//            throw new RuntimeException(e);
//        }
//    }

    public EncryptedAesFlushingCipher(int mode, byte[] secretKey, byte[] iv, long offset) {
        try {
            cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
            blockSize = cipher.getBlockSize();
            zerosBlock = new byte[blockSize];
            flushedBlock = new byte[blockSize];
            int startPadding = (int) (offset % blockSize);
            cipher.init(
                    mode,
                    new SecretKeySpec(secretKey, "AES"),
                    new IvParameterSpec(iv)
            );
            if (startPadding != 0) {
                updateInPlace(new byte[startPadding], 0, startPadding);
            }
        } catch (NoSuchAlgorithmException
                 | NoSuchPaddingException
                 | InvalidKeyException
                 | InvalidAlgorithmParameterException e) {
            // Should never happen.
            throw new RuntimeException(e);
        }
    }

    public void updateInPlace(byte[] data, int offset, int length) {
        update(data, offset, length, data, offset);
    }

    public void update(byte[] in, int inOffset, int length, byte[] out, int outOffset) {
        // If we previously flushed the cipher by inputting zeros up to a block boundary, then we need
        // to manually transform the data that actually ended the block. See the comment below for more
        // details.
        while (pendingXorBytes > 0) {
            out[outOffset] = (byte) (in[inOffset] ^ flushedBlock[blockSize - pendingXorBytes]);
            outOffset++;
            inOffset++;
            pendingXorBytes--;
            length--;
            if (length == 0) {
                return;
            }
        }

        // Do the bulk of the update.
        int written = nonFlushingUpdate(in, inOffset, length, out, outOffset);
        if (length == written) {
            return;
        }

        // We need to finish the block to flush out the remaining bytes. We do so by inputting zeros,
        // so that the corresponding bytes output by the cipher are those that would have been XORed
        // against the real end-of-block data to transform it. We store these bytes so that we can
        // perform the transformation manually in the case of a subsequent call to this method with
        // the real data.
        int bytesToFlush = length - written;
        Assertions.checkState(bytesToFlush < blockSize);
        outOffset += written;
        pendingXorBytes = blockSize - bytesToFlush;
        written = nonFlushingUpdate(zerosBlock, 0, pendingXorBytes, flushedBlock, 0);
        Assertions.checkState(written == blockSize);
        // The first part of xorBytes contains the flushed data, which we copy out. The remainder
        // contains the bytes that will be needed for manual transformation in a subsequent call.
        for (int i = 0; i < bytesToFlush; i++) {
            out[outOffset++] = flushedBlock[i];
        }
    }

    private int nonFlushingUpdate(byte[] in, int inOffset, int length, byte[] out, int outOffset) {
        try {
            return cipher.update(in, inOffset, length, out, outOffset);
        } catch (ShortBufferException e) {
            // Should never happen.
            throw new RuntimeException(e);
        }
    }

    private byte[] getInitializationVector(long nonce, long counter) {
        return ByteBuffer.allocate(16).putLong(nonce).putLong(counter).array();
    }

    /**
     * Returns the hash value of the input as a long using the 64 bit FNV-1a hash function. The hash
     * values produced by this function are less likely to collide than those produced by {@link
     * #hashCode()}.
     */
    private static long getFNV64Hash(@Nullable String input) {
        if (input == null) {
            return 0;
        }

        long hash = 0;
        for (int i = 0; i < input.length(); i++) {
            hash ^= input.charAt(i);
            // This is equivalent to hash *= 0x100000001b3 (the FNV magic prime number).
            hash += (hash << 1) + (hash << 4) + (hash << 5) + (hash << 7) + (hash << 8) + (hash << 40);
        }
        return hash;
    }
}
