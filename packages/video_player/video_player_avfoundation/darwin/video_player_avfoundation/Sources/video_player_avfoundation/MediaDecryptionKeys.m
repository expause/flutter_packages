#import "MediaDecryptionKeys.h"

@implementation MediaDecryptionKeys

- (instancetype)initWithDecryptedKey:(NSData *)decryptedKey decryptedIv:(NSData *)decryptedIv {
    self = [super init];
    if (self) {
        _decryptedKey = decryptedKey;
        _decryptedIv = decryptedIv;
    }
    return self;
}

@end
