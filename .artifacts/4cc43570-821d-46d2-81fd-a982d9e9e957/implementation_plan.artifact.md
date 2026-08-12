# Implementation Plan - Fix DownloadManager Query Crash and ANR

The user reported a crash (`IllegalArgumentException: column local_filename is not allowed in queries`) and potential ANRs during music downloads. This is a known issue on Android 10+ (API 29+) where the `Downloads` provider restricts access to the `local_filename` column, but some `DownloadManager` implementations still include it in their default query projection. Additionally, the polling logic is currently running on the Main Thread, which can lead to ANRs.

## User Review Required

> [!IMPORTANT]
> The fix involves moving the download polling logic to a background thread using Coroutines and implementing a fallback query mechanism. This will change how download progress is tracked but should remain transparent to the user.

## Proposed Changes

### [soundrecorder component]

#### [MODIFY] [MusicListDialogHelper.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/java/sound/recorder/widget/music/MusicListDialogHelper.kt)
- Refactor `startPoll` to use a Coroutine (`appScope.launch`) instead of `Handler.postDelayed`.
- Use `withContext(Dispatchers.IO)` for the `DownloadManager.query` call to prevent blocking the Main Thread.
- Implement a `try-catch` block around the query.
- If `DownloadManager.query` fails with `IllegalArgumentException` related to `local_filename`, implement a fallback using `ContentResolver.query` with a manual projection that excludes the restricted column.
- Update the UI (online adapter) on the Main Thread using `withContext(Dispatchers.Main)`.
- Ensure the polling loop is cancelled properly when the dialog is dismissed or the download finishes.

## Verification Plan

### Manual Verification
- Deploy the app to a device running Android 10+ (target SDK is 37).
- Trigger a music download.
- Verify that the download progress updates correctly without crashing.
- Verify that the app remains responsive (no ANR) during the download polling.
- Check Logcat for any "fallback query" messages to confirm the workaround is working if needed.
