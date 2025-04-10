#import <AVFoundation/AVFoundation.h>
#import "MediaDecryption.h"
#import "MediaDecryptionKeys.h"

NS_ASSUME_NONNULL_BEGIN

@interface EncryptedVideoManager : NSObject

+ (instancetype)sharedInstance;

- (void)setDecryption:(MediaDecryption *)decryption forVideoId:(NSString *)videoId;
- (void)removeDecryption:(NSString *)videoId;
- (MediaDecryptionKeys *)getDecryptionKeys:(NSString *)videoId;
- (nullable NSString *)extractVideoIdFromURL:(NSString *)url;
- (nullable NSString *)extractVideoIdFromHlsScheme:(NSString *)url;

@end

NS_ASSUME_NONNULL_END
