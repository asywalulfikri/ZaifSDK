# Fix Firestore Query and Collection Path in BottomSheetNotesTabbed

The user is experiencing two issues:
1.  **Firestore Filter Issue**: `fetchOnlineNotes()` returns all notes regardless of `status` or `language` filters. This is because Firestore `Query` objects are immutable, and the filtered query was not reassigned to the `query` variable.
2.  **Collection Path Mismatch**: The `collectionPath` was set to `"not"`, but the project standard (and user intent) points to using `"balera.music.android"`.
3.  **NameNotFoundException**: A system log error regarding `balera.music.android`. This is likely a side effect of package changes during development, but ensuring consistent use of the package name/ID across the project will help.

## Proposed Changes

### soundrecorder Module

#### [MODIFY] [BottomSheetNotesTabbed.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/java/sound/recorder/widget/ui/bottomSheet/BottomSheetNotesTabbed.kt)
- Update `collectionPath` to `"balera.music.android"`.
- Fix `fetchOnlineNotes()` to correctly reassign the `query` variable when applying filters.
- Apply the "published" status and language filters correctly (reassigning the result of `whereEqualTo` and `whereArrayContainsAny`).

## Verification Plan

### Manual Verification
- Deploy the app to a device/emulator.
- Open the Notes BottomSheet.
- Verify that only notes with `status == "published"` and the correct `language` are displayed in the online tab.
- Check Logcat to see if the `NameNotFoundException` persists after a clean install (though this is often a system-level transient error).
