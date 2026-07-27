# Fix Main Thread ANR in MyApp Initialization

The application is experiencing a recurring ANR (Application Not Responding) during startup. The stack trace indicates that `CookieManager.getInstance()` is being called on the main thread within `MyApp.initializeAdMob()`, which blocks the UI while initializing the WebView engine (Chromium).

## Proposed Changes

### [soundrecorder component]

#### [MODIFY] [MyApp.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/java/sound/recorder/widget/MyApp.kt)

- Remove the `withContext(Dispatchers.Main)` wrapper around `CookieManager.getInstance()` in the `initializeAdMob` function.
- This will allow the WebView initialization to occur on a background thread (`Dispatchers.IO`), preventing it from blocking the main thread and causing ANRs.
- Modern Android versions support calling `CookieManager.getInstance()` from background threads.

## Verification Plan

### Automated Tests
- I will check if the project compiles after the change.
- Since this is a runtime ANR issue, static analysis or unit tests might not catch it, but ensuring the code still runs on the background thread is key.

### Manual Verification
- Deploy the app and monitor Logcat for any WebView-related errors during startup.
- Verify that AdMob initializes successfully (log "AdMob initialized: ...").
- Ensure no ANR occurs during the splash screen/startup phase.
