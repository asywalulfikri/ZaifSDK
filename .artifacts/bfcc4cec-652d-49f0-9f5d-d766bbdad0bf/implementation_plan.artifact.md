# Update Firebase Query for Online Notes

The user wants to refine the Firebase Firestore query in `NoteFragmentFirebase.kt` to filter notes by their `published` status and supported languages. Specifically, the notes should be filtered by the device's language plus English ("en"), with "en" being mandatory in the filter list.

## Proposed Changes

### Component: Sound Recorder Widget

#### [MODIFY] [NoteFragmentFirebase.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/java/sound/recorder/widget/ui/fragment/NoteFragmentFirebase.kt)
- Add `import java.util.Locale`.
- Add `import com.google.firebase.firestore.Query` for ordering.
- Update `collectionPath` to `"balera.music.android"` (matching `UserNoteDialogHelper.kt` and the "note online" context).
- Update `fetchDocumentsFromCollection` to:
    - Filter by `.whereEqualTo("status", "published")`.
    - Filter by `.whereArrayContainsAny("language", listOf("en", deviceLanguage))`.
    - Add `.orderBy("submitted_at", Query.Direction.DESCENDING)` to show newest notes first.
    - Map `docId` and `status` from Firestore to the `Note` object.

## Verification Plan

### Manual Verification
- Verify the code compiles.
- Check that the query parameters correctly reflect the requirements ("status", "language" array, and ordering).
