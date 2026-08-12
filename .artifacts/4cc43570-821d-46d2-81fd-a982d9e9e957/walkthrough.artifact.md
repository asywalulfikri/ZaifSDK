# Walkthrough - Fixed DownloadManager Crash and ANR

I have resolved the `IllegalArgumentException` related to `local_filename` and eliminated potential ANRs in the download polling logic.

## Changes Made

### 1. Robust Download Polling with Coroutines
- Replaced the `Handler` based polling in `MusicListDialogHelper.kt` with a Coroutine-based solution (`appScope.launch`).
- Moved the `DownloadManager.query` call to `Dispatchers.IO` to ensure it never blocks the Main Thread, preventing ANRs.
- Implemented a polling loop using `delay(500L)` and `isActive` check for safe cancellation.

### 2. Fallback for `local_filename` Query Crash
- Wrapped the `dm.query` call in a `try-catch` block to handle the `IllegalArgumentException` seen on Android 10+ devices.
- Implemented a fallback mechanism: if the standard query fails due to the restricted `local_filename` column, the app now performs a manual query via `ContentResolver` using a safe projection that excludes prohibited columns.

### 3. Improved Lifecycle Management
- Introduced `pollJob` to specifically manage the lifecycle of the download polling.
- Updated `onDismissListener` to cancel `pollJob` when the dialog is closed, ensuring no background UI updates occur.
- Simplified `onCancelClick` and `loadFirestoreIfNeeded` to use the new `startPoll` logic, reducing code duplication and potential crash points.

## Benefits
- **No more crashes**: The `local_filename` restriction is now gracefully handled.
- **Improved Performance**: The UI thread is free from polling overhead, ensuring a smooth user experience.
- **Reliability**: Download progress will now update correctly even on devices with stricter `Downloads` provider policies.

## Verification Results
- **Thread Safety**: Verified that all UI updates are explicitly wrapped in `withContext(Dispatchers.Main)`.
- **Error Handling**: The fallback query uses standard `DownloadManager` column constants for maximum compatibility.
- **Resource Management**: The polling job is correctly tied to the dialog's visibility lifecycle.
