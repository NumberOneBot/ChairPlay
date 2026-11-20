#import "macos_bridge.h"
#import <Cocoa/Cocoa.h>

int cp_shell_apps_dark(void) {
    @autoreleasepool {
        NSString *style = [[NSUserDefaults standardUserDefaults] stringForKey:@"AppleInterfaceStyle"];
        if (!style) {
            return 0; // Light mode
        }
        if ([style caseInsensitiveCompare:@"Dark"] == NSOrderedSame) {
            return 1; // Dark mode
        }
        return 0;
    }
}

void cp_shell_show_error(const char *title, const char *message) {
    @autoreleasepool {
        NSString *nsTitle = title ? [NSString stringWithUTF8String:title] : @"ChairPlay";
        NSString *nsMsg = message ? [NSString stringWithUTF8String:message] : @"An error occurred.";

        if ([NSApp keyWindow] || [NSThread isMainThread]) {
            NSAlert *alert = [[NSAlert alloc] init];
            [alert setMessageText:nsTitle];
            [alert setInformativeText:nsMsg];
            [alert setAlertStyle:NSAlertStyleCritical];
            [alert runModal];
            return;
        }

        // Background thread / headless fallback to main thread or osascript
        dispatch_semaphore_t sem = dispatch_semaphore_create(0);
        dispatch_async(dispatch_get_main_queue(), ^{
            NSAlert *alert = [[NSAlert alloc] init];
            [alert setMessageText:nsTitle];
            [alert setInformativeText:nsMsg];
            [alert setAlertStyle:NSAlertStyleCritical];
            [alert runModal];
            dispatch_semaphore_signal(sem);
        });
        long ret = dispatch_semaphore_wait(sem, dispatch_time(DISPATCH_TIME_NOW, (int64_t)(3 * NSEC_PER_SEC)));
        if (ret != 0) {
            // osascript fallback if main thread didn't respond
            NSString *script = [NSString stringWithFormat:
                @"display alert \"%@\" message \"%@\" as critical",
                [nsTitle stringByReplacingOccurrencesOfString:@"\"" withString:@"\\\""],
                [nsMsg stringByReplacingOccurrencesOfString:@"\"" withString:@"\\\""]];
            NSAppleScript *appleScript = [[NSAppleScript alloc] initWithSource:script];
            [appleScript executeAndReturnError:nil];
        }
    }
}
