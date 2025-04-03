#import <Foundation/Foundation.h>
#import <AVFoundation/AVFoundation.h>
#import "FVPDecryptionLoaderDelegate.h"
#import <CommonCrypto/CommonCryptor.h>
#import "EncryptedVideoManager.h"

@implementation FVPDecryptionLoaderDelegate

- (BOOL)resourceLoader:(AVAssetResourceLoader *)resourceLoader shouldWaitForLoadingOfRequestedResource:(AVAssetResourceLoadingRequest *)loadingRequest {
    bool requestsAllDataToEndOfResource = loadingRequest.dataRequest.requestsAllDataToEndOfResource;
    NSInteger requestedLength = loadingRequest.dataRequest.requestedLength;
    NSInteger requestedOffset = loadingRequest.dataRequest.requestedOffset;
    NSLog(@"_______====== requestsAllDataToEndOfResource: %@", requestsAllDataToEndOfResource ? @"YES" : @"NO");
    NSLog(@"_______====== requestedLength: %ld", (long)requestedLength);
    NSLog(@"_______====== requestedOffset: %ld", (long)requestedOffset);
    
    NSURL *customURL = loadingRequest.request.URL;
    
    // Modify the URL scheme back to https
    NSURLComponents *components = [NSURLComponents componentsWithURL:customURL resolvingAgainstBaseURL:NO];
    components.scheme = @"https";
    NSURL *url = components.URL;
    
    if (!url) {
        [loadingRequest finishLoadingWithError:[NSError errorWithDomain:@"com.yourapp.video" code:-1 userInfo:@{NSLocalizedDescriptionKey: @"Invalid URL"}]];
        return NO;
    }
    
    NSString *videoId = [[EncryptedVideoManager sharedInstance] extractVideoIdFromURL:url.absoluteString];
    
    if (!videoId) {
        [loadingRequest finishLoadingWithError:[NSError errorWithDomain:@"com.yourapp.video" code:-1 userInfo:@{NSLocalizedDescriptionKey: @"Invalid Video ID"}]];
        return NO;
    }
    
    NSData *key = [[EncryptedVideoManager sharedInstance] getVideoDecryptionKey:videoId];
    
    if (!key) {
        [loadingRequest finishLoadingWithError:[NSError errorWithDomain:@"com.yourapp.video" code:-1 userInfo:@{NSLocalizedDescriptionKey: @"Decryption key not found"}]];
        return NO;
    }
    
    NSLog(@"_______====== Attempting synchronous download");
    NSLog(@"_______====== Requesting for URL: %@", url.absoluteString);
    
    // Synchronous download
    NSData *encryptedData = [NSData dataWithContentsOfURL:url];
    
    if (!encryptedData) {
        NSLog(@"_______====== Failed to download encrypted data");
        [loadingRequest finishLoadingWithError:[NSError errorWithDomain:@"com.yourapp.video" code:-1 userInfo:@{NSLocalizedDescriptionKey: @"Failed to download encrypted data"}]];
        return NO;
    }
    
    NSLog(@"_______====== Data downloaded successfully, proceeding with decryption");
    
    NSData *decryptedData = [self decryptData:encryptedData withKey:key];
    
    if (decryptedData) {
        //         **Check if the request is for the master file (M3U8)**
//        if ([url.absoluteString containsString:@".m3u8"]) {
//            NSLog(@"_______====== Master file detected! Printing decrypted content:");
//            
//            NSString *m3u8String = [[NSString alloc] initWithData:decryptedData encoding:NSUTF8StringEncoding];
//            
//            if (m3u8String) {
//                NSLog(@"\n_______====== Decrypted Master File:\n%@", m3u8String);
//            } else {
//                NSLog(@"_______====== Failed to convert decrypted data to string.");
//            }
//        }
        
        //                 **Break decrypted data into chunks for AVPlayer**
//        NSUInteger length = decryptedData.length;
//        NSUInteger chunkSize = 64 * 1024; // 64 KB chunks
//        NSUInteger offset = 0;
//        
//        while (offset < length) {
//            NSUInteger thisChunkSize = MIN(chunkSize, length - offset);
//            NSData *chunk = [decryptedData subdataWithRange:NSMakeRange(offset, thisChunkSize)];
//            [loadingRequest.dataRequest respondWithData:chunk];
//            offset += thisChunkSize;
//        }
        
        // Log first and last n bytes if this is the specific video file
//        if ([url.absoluteString containsString:@"video1080p_fmp40000000000.m4s"]) {
//            NSUInteger n = 16; // Adjust as needed
//            NSUInteger length = decryptedData.length;
//            
//            NSData *firstNBytes = (length >= n) ? [decryptedData subdataWithRange:NSMakeRange(0, n)] : decryptedData;
//            NSData *lastNBytes = (length >= n) ? [decryptedData subdataWithRange:NSMakeRange(length - n, n)] : decryptedData;
//            
//            NSLog(@"_______====== First %lu bytes: %@", (unsigned long)n, firstNBytes);
//            NSLog(@"_______====== Last %lu bytes: %@", (unsigned long)n, lastNBytes);
//        }
        
        
        // Set content length and type
        if (loadingRequest.contentInformationRequest) {
            loadingRequest.contentInformationRequest.contentLength = decryptedData.length;
            
            NSString *urlString = url.absoluteString;
            if ([urlString hasSuffix:@".m3u8"]) {
                loadingRequest.contentInformationRequest.contentType = @"application/vnd.apple.mpegurl";
            } else if ([urlString hasSuffix:@".m4s"]) {
                if ([urlString containsString:@"video"]) {
                    loadingRequest.contentInformationRequest.contentType = @"video/mp4";
                } else if ([urlString containsString:@"audio"]) {
                    loadingRequest.contentInformationRequest.contentType = @"audio/mp4";
                }
            }
            
            loadingRequest.contentInformationRequest.byteRangeAccessSupported = YES;
        }
        
        [loadingRequest.dataRequest respondWithData:decryptedData];
        [loadingRequest finishLoading];
        
        NSLog(@"_______====== Data decrypted and sent to player");
        return YES;
    } else {
        NSLog(@"_______====== Data decryption failed");
        NSError *decryptError = [NSError errorWithDomain:@"com.yourapp.video" code:-1 userInfo:@{NSLocalizedDescriptionKey: @"Decryption failed"}];
        [loadingRequest finishLoadingWithError:decryptError];
        return NO;
    }
}

- (NSData *)decryptData:(NSData *)encryptedData withKey:(NSData *)key {
    // Extract the IV from the encrypted data (assuming the first 16 bytes are the IV)
    if (encryptedData.length < 16) {
        NSLog(@"[Decryption] Encrypted data too short");
        return nil;
    }
    
    NSData *iv = [encryptedData subdataWithRange:NSMakeRange(0, 16)];
    NSData *ciphertext = [encryptedData subdataWithRange:NSMakeRange(16, encryptedData.length - 16)];
    
    // Prepare output buffer
    size_t outLength;
    NSMutableData *decryptedData = [NSMutableData dataWithLength:ciphertext.length + kCCBlockSizeAES128];
    
    // Perform AES decryption
    CCCryptorStatus status = CCCrypt(kCCDecrypt,
                                     kCCAlgorithmAES,
                                     kCCOptionPKCS7Padding,
                                     key.bytes,
                                     kCCKeySizeAES128,
                                     iv.bytes,
                                     ciphertext.bytes,
                                     ciphertext.length,
                                     decryptedData.mutableBytes,
                                     decryptedData.length,
                                     &outLength);
    
    if (status == kCCSuccess) {
        decryptedData.length = outLength; // Resize to actual decrypted length
        return decryptedData;
    } else {
        NSLog(@"[Decryption] AES decryption failed with status: %d", status);
        return nil;
    }
}

@end
