#import <Foundation/Foundation.h>
#import <AVFoundation/AVFoundation.h>

@interface RCTVideoDrmManager : NSObject
- (instancetype)initWithPlayerItem:(AVPlayerItem*)playerItem certificate:(NSData*)certificate;
@end
