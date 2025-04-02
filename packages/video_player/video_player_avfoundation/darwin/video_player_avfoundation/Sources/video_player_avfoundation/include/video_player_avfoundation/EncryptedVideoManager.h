//
//  EncryptedVideoManager.h
//  Pods
//
//  Created by Daniel Firu on 01.04.2025.
//

#import <AVFoundation/AVFoundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface EncryptedVideoManager : NSObject

+ (instancetype)sharedInstance;

- (nullable NSData *)getVideoDecryptionKey:(NSString *)videoId;
- (void)setVideoDecryptionKey:(NSString *)videoId key:(NSData *)key;
- (void)removeVideoDecryptionKey:(NSString *)videoId;
- (nullable NSString *)extractVideoIdFromURL:(NSString *)url;

@end

NS_ASSUME_NONNULL_END
