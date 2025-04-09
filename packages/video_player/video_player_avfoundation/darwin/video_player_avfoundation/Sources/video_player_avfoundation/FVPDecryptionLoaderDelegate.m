#import <Foundation/Foundation.h>
#import <AVFoundation/AVFoundation.h>
#import "FVPDecryptionLoaderDelegate.h"
#import <CommonCrypto/CommonCryptor.h>
#import "EncryptedVideoManager.h"

@implementation FVPDecryptionLoaderDelegate

- (BOOL)resourceLoader:(AVAssetResourceLoader *)resourceLoader shouldWaitForLoadingOfRequestedResource:(AVAssetResourceLoadingRequest *)loadingRequest {
    NSURL *customURL = loadingRequest.request.URL;
    NSLog(@"______________ Requested URL: %@", customURL.absoluteString);
    
    // Modify the URL scheme back to https
    NSURLComponents *components = [NSURLComponents componentsWithURL:customURL resolvingAgainstBaseURL:NO];
    components.scheme = @"https";
    NSURL *actualUrl = components.URL;
    
    if (!actualUrl) {
        [loadingRequest finishLoadingWithError:[NSError errorWithDomain:@"com.yourapp.video" code:-1 userInfo:@{NSLocalizedDescriptionKey: @"Invalid URL"}]];
        return NO;
    }
    
    bool isKeyDecryptionRequest = [customURL.absoluteURL.absoluteString containsString:@"hlsScheme"];
    bool isMasterFileRequest = [actualUrl.absoluteURL.absoluteString containsString:@".m3u8"];
    bool isMasterPlaylistFileRequest = ![actualUrl.absoluteURL.absoluteString containsString:@"master.m3u8"];
    bool isSegmentRequest = !isKeyDecryptionRequest && !isMasterFileRequest;
    
    if (isSegmentRequest) {
        NSLog(@"______________ Requested video segment. Returning NO");
        return NO;
    }
    
    NSString *videoId = isKeyDecryptionRequest ?
    [[EncryptedVideoManager sharedInstance] extractVideoIdFromHlsScheme:actualUrl.absoluteString] :
    [[EncryptedVideoManager sharedInstance] extractVideoIdFromURL:actualUrl.absoluteString];
    
    if (!videoId) {
        [loadingRequest finishLoadingWithError:[NSError errorWithDomain:@"com.yourapp.video" code:-1 userInfo:@{NSLocalizedDescriptionKey: @"Invalid Video ID"}]];
        return NO;
    }
    
    NSData *key = [[EncryptedVideoManager sharedInstance] getVideoDecryptionKey:videoId];
    
    if (!key) {
        [loadingRequest finishLoadingWithError:[NSError errorWithDomain:@"com.yourapp.video" code:-1 userInfo:@{NSLocalizedDescriptionKey: @"Decryption key not found"}]];
        return NO;
    }
    
    if (isKeyDecryptionRequest) {
        NSLog(@"______________ Requested hls key");
        // Respond with decryption key
        if (key) {
            //            loadingRequest.contentInformationRequest.contentType = AVStreamingKeyDeliveryContentKeyType;
            loadingRequest.contentInformationRequest.contentType = AVStreamingKeyDeliveryPersistentContentKeyType;
            loadingRequest.contentInformationRequest.byteRangeAccessSupported = YES;
            loadingRequest.contentInformationRequest.contentLength = key.length;
            [loadingRequest.dataRequest respondWithData:key];
            [loadingRequest finishLoading];
            return YES;
        }
    }
    
    if (isMasterFileRequest) {
        NSLog(@"______________ Requested master file");
        
        // Decrypt both master and variant playlist
        NSData *encryptedData = [NSData dataWithContentsOfURL:actualUrl];
        if (!encryptedData) {
            NSLog(@"_______====== Failed to download encrypted data");
            [loadingRequest finishLoadingWithError:[NSError errorWithDomain:@"com.yourapp.video" code:-1 userInfo:@{NSLocalizedDescriptionKey: @"Failed to download encrypted data"}]];
            return NO;
        }
        
        NSData *iv = [[EncryptedVideoManager sharedInstance] getVideoIvKey:videoId];
        
        NSData *decryptedData = [self decryptData:encryptedData withKey:key withIv:iv];
        if (!decryptedData) {
            NSLog(@"_______====== Data decryption failed");
            NSError *decryptError = [NSError errorWithDomain:@"com.yourapp.video" code:-1 userInfo:@{NSLocalizedDescriptionKey: @"Decryption failed"}];
            [loadingRequest finishLoadingWithError:decryptError];
            return NO;
        }
        
        NSMutableString *masterFile = [[NSMutableString alloc] initWithData:decryptedData encoding:NSUTF8StringEncoding];
        
        if (isMasterPlaylistFileRequest) {
            NSLog(@"______________ Is Playlist. Replacing IV");
            
            // Replace IV for playlist file only
            const unsigned char *ivBytes = (const unsigned char *)[iv bytes];
            NSMutableString *ivHex = [NSMutableString stringWithString:@"0x"];
            for (int i = 0; i < iv.length; i++) {
                [ivHex appendFormat:@"%02x", ivBytes[i]];
            }
            
            NSLog(@"IV for videoId %@: %@", videoId, ivHex);
            
            NSRegularExpression *regex = [NSRegularExpression regularExpressionWithPattern:@"IV=0x[0-9a-fA-F]{32}" options:0 error:nil];
            NSString *updatedPlaylist = [regex stringByReplacingMatchesInString:masterFile options:0 range:NSMakeRange(0, masterFile.length) withTemplate:[NSString stringWithFormat:@"IV=%@", ivHex]];
            masterFile = [updatedPlaylist mutableCopy];
        }
        
        NSData *finalData = [masterFile dataUsingEncoding:NSUTF8StringEncoding];
        [loadingRequest.dataRequest respondWithData:finalData];
        [loadingRequest finishLoading];
        return YES;
    }
    
    return NO;
}

- (NSData *)decryptData:(NSData *)encryptedData withKey:(NSData *)key withIv:(NSData *)iv {
    // Prepare output buffer
    size_t outLength;
    NSMutableData *decryptedData = [NSMutableData dataWithLength:encryptedData.length + kCCBlockSizeAES128];
    
    // Perform AES decryption
    CCCryptorStatus status = CCCrypt(kCCDecrypt,
                                     kCCAlgorithmAES,
                                     kCCOptionPKCS7Padding,
                                     key.bytes,
                                     kCCKeySizeAES128,
                                     iv.bytes,
                                     encryptedData.bytes,
                                     encryptedData.length,
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
