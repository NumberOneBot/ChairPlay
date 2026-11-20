#import "macos_bridge.h"
#import <Cocoa/Cocoa.h>

@interface CpWindowCloseObserver : NSObject
@property (nonatomic, assign) BOOL userClosed;
@property (nonatomic, weak) NSWindow *window;
@property (nonatomic, strong) id notificationObserver;
@end

@implementation CpWindowCloseObserver
- (instancetype)initWithWindow:(NSWindow *)win {
    self = [super init];
    if (self) {
        _userClosed = NO;
        _window = win;
        __weak CpWindowCloseObserver *weakSelf = self;
        _notificationObserver = [[NSNotificationCenter defaultCenter]
            addObserverForName:NSWindowWillCloseNotification
            object:win
            queue:[NSOperationQueue mainQueue]
            usingBlock:^(NSNotification *note) {
                (void)note;
                CpWindowCloseObserver *strongSelf = weakSelf;
                if (strongSelf) {
                    // Check if current event targets this window: click on
                    // the close button, or Cmd+W.
                    NSEvent *event = [NSApp currentEvent];
                    if (event && event.window == win &&
                        (event.type == NSEventTypeLeftMouseUp ||
                         event.type == NSEventTypeLeftMouseDown)) {
                        strongSelf.userClosed = YES;
                    }
                }
            }];
    }
    return self;
}

- (void)dealloc {
    if (_notificationObserver) {
        [[NSNotificationCenter defaultCenter] removeObserver:_notificationObserver];
    }
}
@end

void cp_win_paint_titlebar(int64_t ns_window, int dialog) {
    if (ns_window == 0) return;
    @autoreleasepool {
        NSWindow *win = (__bridge NSWindow *)(void *)(intptr_t)ns_window;
        dispatch_async(dispatch_get_main_queue(), ^{
            win.appearance = [NSAppearance appearanceNamed:NSAppearanceNameDarkAqua];
            if (dialog) {
                [[win standardWindowButton:NSWindowZoomButton] setHidden:YES];
                [[win standardWindowButton:NSWindowMiniaturizeButton] setHidden:YES];
            }
        });
    }
}

int64_t cp_win_watch_close(int64_t ns_window) {
    if (ns_window == 0) return 0;
    @autoreleasepool {
        NSWindow *win = (__bridge NSWindow *)(void *)(intptr_t)ns_window;
        CpWindowCloseObserver *obs = [[CpWindowCloseObserver alloc] initWithWindow:win];
        return (int64_t)(intptr_t)CFBridgingRetain(obs);
    }
}

int cp_win_user_closed(int64_t watch) {
    if (watch == 0) return 0;
    CpWindowCloseObserver *obs = (__bridge CpWindowCloseObserver *)(void *)(intptr_t)watch;
    if (obs.userClosed) {
        obs.userClosed = NO;
        return 1;
    }
    @autoreleasepool {
        NSEvent *event = [NSApp currentEvent];
        if (event && event.window == obs.window) {
            NSEventType type = event.type;
            if (type == NSEventTypeLeftMouseUp || type == NSEventTypeLeftMouseDown) {
                return 1;
            }
            if (type == NSEventTypeKeyDown) {
                NSString *chars = [event charactersIgnoringModifiers];
                if ((event.modifierFlags & NSEventModifierFlagCommand) &&
                    [chars isEqualToString:@"w"]) {
                    return 1;
                }
            }
        }
    }
    return 0;
}

void cp_win_unwatch_close(int64_t watch) {
    if (watch == 0) return;
    CFBridgingRelease((void *)(intptr_t)watch);
}

void cp_win_drag_by_picture(int64_t ns_window) {
    if (ns_window == 0) return;
    @autoreleasepool {
        NSWindow *win = (__bridge NSWindow *)(void *)(intptr_t)ns_window;
        dispatch_async(dispatch_get_main_queue(), ^{
            [win setMovableByWindowBackground:YES];
        });
    }
}

void cp_win_flash(int64_t ns_window, int on) {
    (void)ns_window;
    @autoreleasepool {
        if (on) {
            [NSApp requestUserAttention:NSInformationalRequest];
        } else {
            [NSApp cancelUserAttentionRequest:0];
        }
    }
}
