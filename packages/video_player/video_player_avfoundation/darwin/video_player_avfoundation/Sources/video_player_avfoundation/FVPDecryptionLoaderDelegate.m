#import <Foundation/Foundation.h>
#import <AVFoundation/AVFoundation.h>
#import "FVPDecryptionLoaderDelegate.h"
#import <CommonCrypto/CommonCryptor.h>
#import "EncryptedVideoManager.h"

@implementation FVPDecryptionLoaderDelegate

- (BOOL)resourceLoader:(AVAssetResourceLoader *)resourceLoader shouldWaitForLoadingOfRequestedResource:(AVAssetResourceLoadingRequest *)loadingRequest {
    NSURL *customURL = loadingRequest.request.URL;
    
    // Modify the URL scheme back to https
    NSURLComponents *components = [NSURLComponents componentsWithURL:customURL resolvingAgainstBaseURL:NO];
    components.scheme = @"https";
    NSURL *url = components.URL;
    
    if (!url) {
        return NO;
    }

    NSString *videoId = [[EncryptedVideoManager sharedInstance] extractVideoIdFromURL:url.absoluteString];

    if (!videoId) {
        return NO;
    }

    NSData *key = [[EncryptedVideoManager sharedInstance] getVideoDecryptionKey:videoId];

    if (!key) {
        return NO;
    }

    // Perform async network request
    NSURLSessionDataTask *task = [[NSURLSession sharedSession] dataTaskWithURL:url completionHandler:^(NSData * _Nullable encryptedData, NSURLResponse * _Nullable response, NSError * _Nullable error) {
        
        if (error || !encryptedData) {
            [loadingRequest finishLoadingWithError:error];
            return;
        }

        NSData *decryptedData = [self decryptData:encryptedData withKey:key];

        if (decryptedData) {
            [loadingRequest.dataRequest respondWithData:decryptedData];
            [loadingRequest finishLoading];
            
            NSLog(@"_______====== Data decrypted");
        } else {
            NSLog(@"_______====== Data decryption failed");
            NSError *decryptError = [NSError errorWithDomain:@"com.yourapp.video" code:-1 userInfo:@{NSLocalizedDescriptionKey: @"Decryption failed"}];
            [loadingRequest finishLoadingWithError:decryptError];
        }
    }];
    
    [task resume];

    NSLog(@"_______====== Before returning from resourceLoader");
    return YES;
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
