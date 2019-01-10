#import "RCTVideoDrmManager.h"
#include <AVFoundation/AVFoundation.h>

@interface RCTVideoDrmManager (DRM) <AVContentKeySessionDelegate>
@end

@implementation RCTVideoDrmManager
{
  NSData *_fairplayCertificate;
  AVContentKeySession *_contentKeySession;
}

- (instancetype)initWithPlayerItem:(AVPlayerItem*)playerItem certificate:(NSData*)certificate {
  NSAssert(playerItem != nil && certificate != nil, @"not null");
  self = [super init];
  _fairplayCertificate = certificate;
  NSLog(@"Using fairplay cert %@", _fairplayCertificate);
  if (@available(iOS 11.0, *)) {
    _contentKeySession = [AVContentKeySession contentKeySessionWithKeySystem:AVContentKeySystemFairPlayStreaming];
    [_contentKeySession setDelegate:self queue:dispatch_get_main_queue()];
    [_contentKeySession addContentKeyRecipient:(AVURLAsset*)playerItem.asset];
    NSLog(@"Content key session created for %@", playerItem.asset);
  }
  return self;
}

#pragma mark AVContentKeySessionDelegate

- (void)contentKeySession:(AVContentKeySession *)session didProvideContentKeyRequest:(AVContentKeyRequest *)keyRequest {
  NSLog(@"%@", NSStringFromSelector(_cmd));

  NSString *assetIDString = [keyRequest.identifier stringByReplacingOccurrencesOfString:@"skd://" withString:@""];
  NSData *assetIDData = [assetIDString dataUsingEncoding:NSUTF8StringEncoding];

  NSLog(@"Requesting keys for %@ using certificate %ld bytes\n%@", assetIDString, _fairplayCertificate.length, _fairplayCertificate);
  [keyRequest makeStreamingContentKeyRequestDataForApp:_fairplayCertificate contentIdentifier:assetIDData options:@{AVContentKeyRequestProtocolVersionsKey : @[@1]} completionHandler:^(NSData *contentKeyRequestData, NSError *error) {
    NSString *jwt = @"eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJ2ZXJzaW9uIjoxLCJjb21fa2V5X2lkIjoiZjcwNmRlMzMtYzUyOS00OTI0LWI5OWUtYTkwZDAwZWI4MjhjIiwibWVzc2FnZSI6eyJ0eXBlIjoiZW50aXRsZW1lbnRfbWVzc2FnZSIsImtleXMiOlt7ImlkIjoiQzczNzYyRkUtM0ZCRi05ODQ5LTk1QjYtQ0U1ODdDOTNBQzY2In1dLCJleHBpcmF0aW9uX2RhdGUiOiIyMDE5LTAxLTEwVDE3OjE4OjQ5Ljg1MjY4MjVaIiwia2V5c19iYXNlZF9vbl9yZXF1ZXN0IjpmYWxzZSwicGVyc2lzdGVudCI6dHJ1ZX0sImJlZ2luX2RhdGUiOiIwMDAxLTAxLTAxVDAwOjAwOjAwIiwiZXhwaXJhdGlvbl9kYXRlIjoiMjAxOS0wMS0xMFQxNzoxODo0OS44NTI2ODI1WiJ9.u-mE3riOfTzY9JOJ_FANhLdK_EEo10DWmS6c_mqcsCE";
    NSString *url = @"https://drm-fairplay-licensing.axprod.net/AcquireLicense";
    
    NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:[NSURL URLWithString:url]];
    [request setValue:jwt forHTTPHeaderField:@"X-AxDRM-Message"];
    request.HTTPMethod = @"POST";
    request.HTTPBody = contentKeyRequestData;
    
    NSURLSession *session = [NSURLSession sessionWithConfiguration:NSURLSessionConfiguration.defaultSessionConfiguration];
    NSLog(@"Issuing key request against DRM server %@", request);
    [[session dataTaskWithRequest:request completionHandler:^(NSData *data, NSURLResponse *response, NSError *error) {
      if (data == nil || [(NSHTTPURLResponse*)response statusCode] / 100 != 2) {
        NSLog(@"Missing data %@ %@", error, response);
        [keyRequest processContentKeyResponseError:error];
        return;
      }
      NSLog(@"Got valid response from DRM server");
      AVContentKeyResponse *keyResponse = [AVContentKeyResponse contentKeyResponseWithFairPlayStreamingKeyResponseData:data];
      [keyRequest processContentKeyResponse:keyResponse];
    }] resume];
  }];
}

- (void)contentKeySession:(AVContentKeySession *)session didProvideRenewingContentKeyRequest:(AVContentKeyRequest *)keyRequest {
  NSLog(@"%@", NSStringFromSelector(_cmd));
}

/*!
 @method        contentKeySession:didProvidePersistableContentKeyRequest:
 @abstract      Provides the receiver with a new content key request that allows key persistence.
 @param         session
 An instance of AVContentKeySession that's providing a new content key request.
 @param         keyRequest
 An instance of AVPersistableContentKeyRequest.
 @discussion    Will be invoked by an AVContentKeyRequest as the result of a call to -respondByRequestingPersistableContentKeyRequest.
 */

- (void)contentKeySession:(AVContentKeySession *)session didProvidePersistableContentKeyRequest:(AVPersistableContentKeyRequest *)keyRequest {
  NSLog(@"%@", NSStringFromSelector(_cmd));
}

/*!
 @method        contentKeySession:didUpdatePersistableContentKey:forContentKeyIdentifier:
 @abstract      Provides the receiver with an updated persistable content key for a particular key request.
 @param         session
 An instance of AVContentKeySession that is providing the updated persistable content key.
 @param         persistableContentKey
 Updated persistable content key data that may be stored offline and used to answer future requests to content keys with matching key identifier.
 @param         keyIdentifier
 Container- and protocol-specific identifier for the persistable content key that was updated.
 @discussion    If the content key session provides an updated persistable content key data, the previous key data is no longer valid and cannot be used to answer future loading requests.
 */

- (void)contentKeySession:(AVContentKeySession *)session didUpdatePersistableContentKey:(NSData *)persistableContentKey forContentKeyIdentifier:(id)keyIdentifier API_AVAILABLE(ios(11.0)) API_UNAVAILABLE(macos, tvos, watchos) {
  NSLog(@"%@", NSStringFromSelector(_cmd));
}

/*!
 @method        contentKeySession:contentKeyRequest:didFailWithError:
 @abstract      Informs the receiver a content key request has failed.
 @param         session
 The instance of AVContentKeySession that initiated the content key request.
 @param         keyRequest
 The instance of AVContentKeyRequest that has failed.
 @param         error
 An instance of NSError that describes the failure that occurred.
 */

- (void)contentKeySession:(AVContentKeySession *)session contentKeyRequest:(AVContentKeyRequest *)keyRequest didFailWithError:(NSError *)err {
  NSLog(@"%@", NSStringFromSelector(_cmd));
}

/*!
 @method        contentKeySession:shouldRetryContentKeyRequest:reason:
 @abstract      Provides the receiver a content key request that should be retried because a previous content key request failed.
 @param         session
 An instance of AVContentKeySession that's providing the content key request that should be retried.
 @param         keyRequest
 An instance of AVContentKeyRequest that should be retried.
 @param         retryReason
 An enum value to explain why the receiver could retry the new content key request.
 @result        A BOOL value indicating receiver's desire to retry the failed content key request.
 @discussion    Will be invoked by an AVContentKeySession when a content key request should be retried. The reason for failure of previous content key request is specified. The receiver can decide if it wants to request AVContentKeySession to retry this key request based on the reason. If the receiver returns YES, AVContentKeySession would restart the key request process. If the receiver returns NO or if it does not implement this delegate method, the content key request would fail and AVContentKeySession would let the receiver know through -contentKeySession:contentKeyRequest:didFailWithError:.
 */

- (BOOL)contentKeySession:(AVContentKeySession *)session shouldRetryContentKeyRequest:(AVContentKeyRequest *)keyRequest reason:(AVContentKeyRequestRetryReason)retryReason {
  NSLog(@"%@", NSStringFromSelector(_cmd));
  return NO;
}

/*!
 @method        contentKeySession:contentKeyRequestDidSucceed:
 @abstract      Informs the receiver that the response to content key request was successfully processed.
 @param         session
 The instance of AVContentKeySession that initiated the content key request.
 @param         keyRequest
 The instance of AVContentKeyRequest whose response was successfully processed.
 @discussion    Will be invoked by an AVContentKeySession when it is certain that the response client provided via -[AVContentKeyRequest processContentKeyResponse:] was successfully processed by the system.
 */

- (void)contentKeySession:(AVContentKeySession *)session contentKeyRequestDidSucceed:(AVContentKeyRequest *)keyRequest API_AVAILABLE(macos(10.14), ios(12.0), tvos(12.0)) {
  NSLog(@"%@", NSStringFromSelector(_cmd));
}

/*!
 @method        contentKeySessionContentProtectionSessionIdentifierDidChange:
 @abstract      Informs the receiver that the value of -[AVContentKeySession contentProtectionSessionIdentifier] has changed.
 */

- (void)contentKeySessionContentProtectionSessionIdentifierDidChange:(AVContentKeySession *)session {
  NSLog(@"%@", NSStringFromSelector(_cmd));
}


/*!
 @method        contentKeySessionDidGenerateExpiredSessionReport:
 @abstract      Notifies the sender that a expired session report has been generated
 @param         session
 An instance of AVContentKeySession that recorded the generation of an expired session report.
 @discussion    Will be invoked by an AVContentKeySession when an expired session report is added to the storageURL
 */

- (void)contentKeySessionDidGenerateExpiredSessionReport:(AVContentKeySession *)session API_AVAILABLE(macos(10.14), ios(12.0), tvos(12.0)) {
  NSLog(@"%@", NSStringFromSelector(_cmd));
}

@end
