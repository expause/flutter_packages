#import <Foundation/Foundation.h>

@interface MediaDecryption : NSObject

@property (nonatomic, strong) NSString *uid;
@property (nonatomic, assign) NSInteger iat;
@property (nonatomic, strong) NSData *dk;
@property (nonatomic, strong) NSData *iv;
@property (nonatomic, strong) NSData *dkAesIv;
@property (nonatomic, strong) NSData *ivAesIv;

- (instancetype)initWithUid:(NSString *)uid
                            iat:(NSInteger)iat
                             dk:(NSData *)dk
                             iv:(NSData *)iv
                        dkAesIv:(NSData *)dkAesIv
                        ivAesIv:(NSData *)ivAesIv;

@end
