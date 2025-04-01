//
//  FVPDecryptionLoaderDelegate.m
//  video_player_avfoundation
//
//  Created by Daniel Firu on 01.04.2025.
//

#import <Foundation/Foundation.h>
#import <AVFoundation/AVFoundation.h>
#import "FVPDecryptionLoaderDelegate.h"

@implementation FVPDecryptionLoaderDelegate

- (BOOL)resourceLoader:(AVAssetResourceLoader *)resourceLoader shouldWaitForLoadingOfRequestedResource:(AVAssetResourceLoadingRequest *)loadingRequest {
    NSURL *url = loadingRequest.request.URL;
    
    // Modify the URL scheme back
    NSURLComponents *components = [NSURLComponents componentsWithURL:url resolvingAgainstBaseURL:NO];
    components.scheme = @"https"; // Change scheme to customScheme
    NSURL *customURL = components.URL;
    
    NSData *encryptedData = [self fetchEncryptedDataFromURL:customURL];
    
    if (encryptedData) {
        NSData *decryptedData = [self decryptData:encryptedData];
        [loadingRequest.dataRequest respondWithData:decryptedData];
        [loadingRequest finishLoading];
        return YES;
    }
    
    return NO;
}

- (NSData *)fetchEncryptedDataFromURL:(NSURL *)url {
    // Fetch encrypted data from the given URL
    return [NSData dataWithContentsOfURL:url];
}

- (NSData *)decryptData:(NSData *)data {
    // Decrypt data using the appropriate method
    return data; // Replace with actual decryption logic
}

@end
