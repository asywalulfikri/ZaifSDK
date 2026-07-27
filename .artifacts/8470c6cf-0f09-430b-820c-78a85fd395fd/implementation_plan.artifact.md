# Fix Startup Collision ANR (AdMob vs App Update)

The application is experiencing a second type of ANR where the Main Thread is overwhelmed during startup by concurrent service bindings from `app-update` and initializations from `AdMob`.

## Proposed Changes

### [soundrecorder component]

#### [MODIFY] [MyApp.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/java/sound/recorder/widget/MyApp.kt)
- Add a strategic delay (e.g., 1500ms) before calling `MobileAds.initialize` in `initializeAdMob`.
- This "staggered" approach ensures the Main Thread has finished critical activity startup and other service bindings (like Google Play App Update) before AdMob starts its heavy background-to-main-thread communication.

#### [MODIFY] [InAppUpdateHelper.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/java/sound/recorder/widget/base/InAppUpdateHelper.kt)
- Move `AppUpdateManagerFactory.create(activity)` from the constructor/property initializer to the `checkUpdate` and `onResume` methods, lazily.
- This prevents the Play Store service from being bound immediately upon Activity creation, further spreading out the startup load.

## Verification Plan

### Automated Tests
- Build the project to ensure no syntax errors.

### Manual Verification
- Deploy to a device (especially the one reported, if available) and check if the ANR still occurs at startup.
- Verify that AdMob and App Update still work correctly after the delay.
