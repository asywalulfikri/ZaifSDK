# Implementation Plan - Fix WorkManager ANR during Startup

The goal is to fix the ANR (Application Not Responding) reported during app startup. The stack trace shows that the main thread is blocked by `WorkManager` initialization, which is triggered automatically by the `App Startup` library.

## Problem Analysis
- `WorkManager` initializes its internal Room database during app startup on the main thread.
- On slower devices (like Alcatel 1T10), this process can take too long, especially if other SDKs are also initializing.
- The stack trace indicates the main thread is stuck in `androidx.work.WorkManagerInitializer.create`.

## Proposed Changes

### 1. Disable Automatic WorkManager Initialization
We will remove `WorkManagerInitializer` from the `androidx.startup.InitializationProvider` in the manifest.

#### [MODIFY] [AndroidManifest.xml](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/AndroidManifest.xml)
- Add a `<meta-data>` tag with `tools:node="remove"` for `androidx.work.WorkManagerInitializer`.

### 2. Implement On-Demand Initialization
By implementing `Configuration.Provider` in the `Application` class, we tell `WorkManager` how to initialize itself only when it's actually needed.

#### [MODIFY] [MyApp.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/java/sound/recorder/widget/MyApp.kt)
- Make `MyApp` implement `androidx.work.Configuration.Provider`.
- Override `getWorkManagerConfiguration()` to return a default configuration.
- (Optional) Trigger a background initialization of `WorkManager` in `initializeEssentialSDKs` to ensure it's ready without blocking the UI.

## Verification Plan

### Automated Tests
- Build the project to ensure no compilation errors after adding the interface and manifest changes.
- Verify that `WorkManager` still works (e.g., check logs for `CoinNotificationWorker` scheduling).

### Manual Verification
- Deploy the app and monitor the logs during startup.
- Ensure that `WorkManager` initialization no longer appears in the main thread's startup phase (by checking that `WorkManagerInitializer` is NOT called by `AppInitializer`).
- Confirm that the `CoinNotificationWorker` can still be scheduled.
