//
//  EncryptedVideoManager.m
//  Pods
//
//  Created by Daniel Firu on 01.04.2025.
//

// EncryptedVideoManager.m
#import "EncryptedVideoManager.h"

@interface EncryptedVideoManager ()
@property (nonatomic, strong) NSMutableDictionary<NSString *, NSData *> *videoEncryptionKeys;
@property (nonatomic, strong) NSLock *sessionLock;
@end

@implementation EncryptedVideoManager

+ (instancetype)sharedInstance {
    static EncryptedVideoManager *sharedInstance = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        sharedInstance = [[EncryptedVideoManager alloc] init];
    });
    return sharedInstance;
}

- (instancetype)init {
    self = [super init];
    if (self) {
        _videoEncryptionKeys = [[NSMutableDictionary alloc] init];
        _sessionLock = [[NSLock alloc] init];
    }
    return self;
}

- (NSData *)getVideoDecryptionKey:(NSString *)videoId {
    [self.sessionLock lock];
    NSData *key = [self.videoEncryptionKeys objectForKey:videoId];
    [self.sessionLock unlock];
    return key;
}

- (void)setVideoDecryptionKey:(NSString *)videoId key:(NSData *)key {
    [self.sessionLock lock];
    [self.videoEncryptionKeys setObject:key forKey:videoId];
    [self.sessionLock unlock];
}

- (void)removeVideoDecryptionKey:(NSString *)videoId {
    [self.sessionLock lock];
    [self.videoEncryptionKeys removeObjectForKey:videoId];
    [self.sessionLock unlock];
}

- (NSString *)extractVideoIdFromURL:(NSString *)url {
    NSArray<NSString *> *segments = [url componentsSeparatedByString:@"/"];
    return (segments.count > 5) ? segments[5] : nil;
}

@end
