#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface MediaDecryptionKeys : NSObject

@property (nonatomic, strong) NSData *decryptedKey;
@property (nonatomic, strong) NSData *decryptedIv;

- (instancetype)initWithDecryptedKey:(NSData *)decryptedKey decryptedIv:(NSData *)decryptedIv;

@end

NS_ASSUME_NONNULL_END
