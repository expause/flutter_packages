// MediaDecryption.m
#import "MediaDecryption.h"

@implementation MediaDecryption

- (instancetype)initWithUid:(NSString *)uid
                            iat:(NSInteger)iat
                             dk:(NSData *)dk
                             iv:(NSData *)iv
                        dkAesIv:(NSData *)dkAesIv
                        ivAesIv:(NSData *)ivAesIv {
    self = [super init];
    if (self) {
        _uid = uid;
        _iat = iat;
        _dk = dk;
        _iv = iv;
        _dkAesIv = dkAesIv;
        _ivAesIv = ivAesIv;
    }
    return self;
}

@end
