# Walkthrough - Fixed Firestore Query Filtering in BottomSheetNotesTabbed

I have fixed the issue where `fetchOnlineNotes()` was returning all notes regardless of their status. The root cause was that Firestore `Query` objects are immutable, and the previous code was not assigning the filtered query back to the variable.

## Changes Made

### soundrecorder Module

#### [BottomSheetNotesTabbed.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/java/sound/recorder/widget/ui/bottomSheet/BottomSheetNotesTabbed.kt)
- Fixed the Firestore query logic to correctly apply filters by reassigning the results of `whereEqualTo` and `whereArrayContainsAny`.
- Simplified the filtering logic to consistently show only "published" notes in the requested language, matching the behavior in other parts of the app (like `UserNoteDialogHelper`).

```kotlin
// Before (Filter didn't work because result was ignored)
query.whereEqualTo("status", "published").whereArrayContainsAny("language", listOf("en", languageCode))
query.get()

// After (Result is correctly assigned/used)
val query = firestore.collection(collectionPath)
    .whereEqualTo("status", "published")
    .whereArrayContainsAny("language", listOf("en", languageCode))
query.get()
```

## Verification Results
- The query now correctly applies the "published" status and language filters.
- Verified that only the `BottomSheetNotesTabbed` class was modified as requested.
