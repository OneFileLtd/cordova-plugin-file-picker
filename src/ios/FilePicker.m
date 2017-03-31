#import "FilePicker.h"
#import "CDVFile.h"

#define DOCUMENTS_FOLDER [NSHomeDirectory() stringByAppendingPathComponent:@"Documents"]
#define DEGREES_TO_RADIANS(degrees)  ((M_PI * degrees)/ 180)
#define MEGA_BYTES (1000.0f * 1000.0f)
#define NAVIGATION_BAR_HEIGHT 88

#pragma mark - ACTUAL VIEW CONTROLLER INSIDE PLUG IN - INTERFACE
/************************************************************************************************************
 *      Importer - ViewController - Private variables
 ************************************************************************************************************/
@interface FilePicker ()
{
    NSString *_callbackId;
}
@property (nonatomic, retain) NSString *callbackId;
@end

/************************************************************************************************************
 *      CDVImporter - Initialisation point of the plugin, creates a Navigation Controller and Pushes
 *      the main view controller on to it.
 ************************************************************************************************************/
@implementation FilePicker

@synthesize inUse = _inUse;

- (void)pluginInitialize
{
    NSLog(@"CDVImporter - pluginInitialize");
    self.inUse = NO;
}

// ----------------------------------
// -- ENTRY POINT FROM JAVA SCRIPT --
// ----------------------------------
- (void)pickFile:(CDVInvokedUrlCommand*)command
{
    self.callbackId = command.callbackId;
    NSDictionary* options = [command argumentAtIndex:0];

    if ([options isKindOfClass:[NSNull class]]) {
        options = [NSDictionary dictionary];
    }
    CDVPluginResult* result = nil;
    if (NSClassFromString(@"FilePicker") == nil) {
        result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageToErrorObject:0];
    } else if (self.inUse == YES) {
        result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageToErrorObject:0];
    } else {
        UIImagePickerController* imagePicker = [[UIImagePickerController alloc]init];
        // Check if image access is authorized
        if([UIImagePickerController isSourceTypeAvailable:UIImagePickerControllerSourceTypePhotoLibrary]) {
            imagePicker.sourceType = UIImagePickerControllerSourceTypePhotoLibrary;
            // Use delegate methods to get result of photo library -- Look up UIImagePicker delegate methods
            imagePicker.delegate = self;
            imagePicker.mediaTypes = [UIImagePickerController availableMediaTypesForSourceType:imagePicker.sourceType];
            [self.viewController presentViewController:imagePicker animated:true completion:nil];
        }
    }
}

- (void)imagePickerController:(UIImagePickerController *)picker didFinishPickingMediaWithInfo:(NSDictionary *)info {
    [picker.presentingViewController dismissViewControllerAnimated:YES completion:nil];
    UIImage *image = info[UIImagePickerControllerOriginalImage];
    NSString *stringPath = [NSTemporaryDirectory()stringByStandardizingPath];
    BOOL success = false;
    NSString *filePath;
    // Do something with picked image
    NSLog(@"%@", info);
    if([[info valueForKey:@"UIImagePickerControllerMediaType"] isEqualToString:@"public.image"])
    {
        image = [info valueForKey:@"UIImagePickerControllerOriginalImage"];
        
        NSError *error = nil;
        if (![[NSFileManager defaultManager] fileExistsAtPath:stringPath])
            [[NSFileManager defaultManager] createDirectoryAtPath:stringPath withIntermediateDirectories:NO attributes:nil error:&error];
        filePath = [stringPath stringByAppendingFormat:@"/%@.jpg", [FilePicker getUUID]];
        NSData *data = UIImageJPEGRepresentation(image, 1.0);
        success = [data writeToFile:filePath atomically:YES];
    
    }
    if([[info valueForKey:@"UIImagePickerControllerMediaType"] isEqualToString:@"public.movie"])
    {
        NSURL *videoURL = [info objectForKey:UIImagePickerControllerMediaURL];
        NSData *videoData = [NSData dataWithContentsOfURL:videoURL];
        filePath = [stringPath stringByAppendingFormat:@"/%@.mov", [FilePicker getUUID]];
        success = [videoData writeToFile:filePath atomically:NO];
    }
    NSDictionary *fileDict = [self getMediaDictionaryFromPath:filePath ofType:nil];
    NSArray *fileArray = [NSArray arrayWithObject:fileDict];
    NSLog(@"%@", fileArray);
    CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsArray:fileArray];
    [self.commandDelegate sendPluginResult:result callbackId:self.callbackId];
}

- (void)imagePickerControllerDidCancel:(UIImagePickerController *)picker
{
    [picker.presentingViewController dismissViewControllerAnimated:YES completion:nil];
    NSLog(@"%@", picker);
    CDVPluginResult* result = nil;
    result = [CDVPluginResult resultWithStatus:CDVCommandStatus_NO_RESULT messageToErrorObject:0];
    [self.commandDelegate sendPluginResult:result callbackId:self.callbackId];
}

+(NSString *)getUUID
{
    CFUUIDRef newUniqueId = CFUUIDCreate(kCFAllocatorDefault);
    NSString * uuidString = (__bridge_transfer NSString*)CFUUIDCreateString(kCFAllocatorDefault, newUniqueId);
    CFRelease(newUniqueId);
    return uuidString;
}

- (NSDictionary*)getMediaDictionaryFromPath:(NSString*)fullPath ofType:(NSString*)type
{
    NSFileManager* fileMgr = [[NSFileManager alloc] init];
    NSMutableDictionary* fileDict = [NSMutableDictionary dictionaryWithCapacity:5];
    
    CDVFile *fs = [self.commandDelegate getCommandInstance:@"File"];
    
    // Get canonical version of localPath
    NSURL *fileURL = [NSURL URLWithString:[NSString stringWithFormat:@"file://%@", fullPath]];
    NSURL *resolvedFileURL = [fileURL URLByResolvingSymlinksInPath];
    NSString *path = [resolvedFileURL path];
    
    CDVFilesystemURL *url = [fs fileSystemURLforLocalPath:path];
    
    [fileDict setObject:[fullPath lastPathComponent] forKey:@"name"];
    [fileDict setObject:fullPath forKey:@"fullPath"];
    if (url) {
        [fileDict setObject:[url absoluteURL] forKey:@"localURL"];
    }
    // determine type
    if (!type) {
        id command = [self.commandDelegate getCommandInstance:@"File"];
        if ([command isKindOfClass:[CDVFile class]]) {
            CDVFile* cdvFile = (CDVFile*)command;
            NSString* mimeType = [cdvFile getMimeTypeFromPath:fullPath];
            [fileDict setObject:(mimeType != nil ? (NSObject*)mimeType : [NSNull null]) forKey:@"type"];
        }
    }
    NSDictionary* fileAttrs = [fileMgr attributesOfItemAtPath:fullPath error:nil];
    [fileDict setObject:[NSNumber numberWithUnsignedLongLong:[fileAttrs fileSize]] forKey:@"size"];
    NSDate* modDate = [fileAttrs fileModificationDate];
    NSNumber* msDate = [NSNumber numberWithDouble:[modDate timeIntervalSince1970] * 1000];
    [fileDict setObject:msDate forKey:@"lastModifiedDate"];
    return fileDict;
}
@end
