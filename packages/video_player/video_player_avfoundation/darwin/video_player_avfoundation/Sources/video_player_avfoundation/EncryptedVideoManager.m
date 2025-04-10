#import "EncryptedVideoManager.h"
#import <CommonCrypto/CommonCrypto.h>

@interface EncryptedVideoManager ()
@property (nonatomic, strong) NSMutableDictionary<NSString *, MediaDecryption *> *videoDecryptions;
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
        _videoDecryptions = [[NSMutableDictionary alloc] init]; // Corrected this line
        _sessionLock = [[NSLock alloc] init];
    }
    return self;
}

- (void)setDecryption:(MediaDecryption *)decryption forVideoId:(NSString *)videoId {
    [self.sessionLock lock];
    self.videoDecryptions[videoId] = decryption;
    [self.sessionLock unlock];
}

- (void)removeDecryption:(NSString *)videoId {
    [self.sessionLock lock];
    [self.videoDecryptions removeObjectForKey:videoId];
    [self.sessionLock unlock];
}

- (MediaDecryptionKeys *)getDecryptionKeys:(NSString *)videoId {
    [self.sessionLock lock];
    MediaDecryption *decryption = [self.videoDecryptions objectForKey:videoId];
    [self.sessionLock unlock];
    
    if (decryption == nil) {
        return nil;
    }

    // Derive session key and decrypt
    NSData *sessionKey = [self deriveSessionKey:decryption.uid iat:decryption.iat];
//    NSLog(@"______ iOS sessionKey: %@", [self hexStringFromData:sessionKey]);
    
    // Log encrypted DK and IV before decryption
//    NSLog(@"______ iOS Encrypted DK: %@", [self hexStringFromData:decryption.dk]);
//    NSLog(@"______ iOS Encrypted IV: %@", [self hexStringFromData:decryption.iv]);

    NSData *decryptedKey = [self aesDecrypt:decryption.dk key:sessionKey iv:decryption.dkAesIv];
    NSData *decryptedIv = [self aesDecrypt:decryption.iv key:sessionKey iv:decryption.ivAesIv];
    
    if (!decryptedKey || !decryptedIv) {
        NSLog(@"❌ Decryption failed – check DK or IV AES inputs");
        return nil;
    }
    
    // Log before unshifting
    NSString *encryptionKeyPrefix = @"expause-video-key-";
    NSString *encryptionSecretName = [encryptionKeyPrefix stringByAppendingString:videoId];
    NSString *ivKeyPrefix = @"expause-iv-key-";
    NSString *ivSecretName = [ivKeyPrefix stringByAppendingString:videoId];
    
//    NSLog(@"______ iOS DK secretName: %@", encryptionSecretName);
//    NSLog(@"______ iOS IV secretName: %@", ivSecretName);
//    NSLog(@"______ iOS Decrypted DK before unshift: %@", [self hexStringFromData:decryptedKey]);
//    NSLog(@"______ iOS Decrypted IV before unshift: %@", [self hexStringFromData:decryptedIv]);
    
    decryptedKey = [self unshiftKeyBytes:decryptedKey withSecretName:encryptionSecretName];
    decryptedIv = [self unshiftKeyBytes:decryptedIv withSecretName:ivSecretName];

    // Log after unshifting
//    NSLog(@"______ iOS Decrypted DK after unshift: %@", [self hexStringFromData:decryptedKey]);
//    NSLog(@"______ iOS Decrypted IV after unshift: %@", [self hexStringFromData:decryptedIv]);

    return [[MediaDecryptionKeys alloc] initWithDecryptedKey:decryptedKey decryptedIv:decryptedIv];
}

- (NSData *)deriveSessionKey:(NSString *)uid iat:(NSInteger)iat {
    NSString *material = [NSString stringWithFormat:@"%@-%ld", uid, (long)iat];
    NSData *inputData = [material dataUsingEncoding:NSUTF8StringEncoding];
    uint8_t digest[CC_SHA256_DIGEST_LENGTH];
    CC_SHA256(inputData.bytes, (CC_LONG)inputData.length, digest);
    return [NSData dataWithBytes:digest length:16]; // AES-128
}

- (NSData *)aesDecrypt:(NSData *)data key:(NSData *)key iv:(NSData *)iv {
    size_t outLength;
    NSMutableData *output = [NSMutableData dataWithLength:data.length + kCCBlockSizeAES128];

    CCCryptorStatus result = CCCrypt(kCCDecrypt, kCCAlgorithmAES128,
                                     kCCOptionPKCS7Padding, key.bytes, key.length,
                                     iv.bytes, data.bytes, data.length,
                                     output.mutableBytes, output.length, &outLength);

    if (result == kCCSuccess) {
        output.length = outLength;
        return output;
    }
    return nil;
}

- (NSData *)unshiftKeyBytes:(NSData *)key withSecretName:(NSString *)name {
    NSData *secretData = [name dataUsingEncoding:NSUTF8StringEncoding];
    uint8_t hash[CC_SHA256_DIGEST_LENGTH];
    CC_SHA256(secretData.bytes, (CC_LONG)secretData.length, hash);

    NSMutableData *result = [NSMutableData dataWithLength:key.length];
    const uint8_t *keyBytes = key.bytes;
    uint8_t *resultBytes = result.mutableBytes;

    for (NSUInteger i = 0; i < key.length; i++) {
        resultBytes[i] = keyBytes[i] ^ hash[i % CC_SHA256_DIGEST_LENGTH];
    }

    return result;
}

- (NSString *)extractVideoIdFromURL:(NSString *)url {
    NSArray<NSString *> *segments = [url componentsSeparatedByString:@"/"];
    return (segments.count > 5) ? segments[5] : nil;
}

- (NSString *)extractVideoIdFromHlsScheme:(NSString *)url {
    NSArray<NSString *> *segments = [url componentsSeparatedByString:@"/"];
    return (segments.count > 3) ? segments[3] : nil;
}

- (NSString *)hexStringFromData:(NSData *)data {
    const unsigned char *dataBuffer = (const unsigned char *)data.bytes;
    NSMutableString *hexString = [NSMutableString stringWithCapacity:data.length * 2];
    for (int i = 0; i < data.length; ++i) {
        [hexString appendFormat:@"%02x", dataBuffer[i]];
    }
    return [hexString copy];
}

@end
