#import <UIKit/UIKit.h>
#import <AVFoundation/AVFoundation.h>
#import <QuartzCore/QuartzCore.h>
#import <Cordova/CDVPlugin.h>
#import <MobileCoreServices/UTCoreTypes.h>

typedef enum {
    STATUS_ERROR = 1,
    STATUS_SUCCESS = 2
} CDV_PLUGIN_STATUS;

@interface FilePicker : CDVPlugin <UINavigationControllerDelegate, UIImagePickerControllerDelegate>
{
    BOOL _inUse;
}
@property BOOL inUse;

-(void)pickFile:(CDVInvokedUrlCommand*)command;
+(NSString *)getUUID;
@end
