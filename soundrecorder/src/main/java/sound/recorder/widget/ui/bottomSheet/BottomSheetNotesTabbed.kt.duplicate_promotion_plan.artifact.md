# Implementation Plan: Prevent Duplicate Note Promotion

Prevent users from promoting the same note multiple times unless the note's content has been edited.

## Proposed Changes

### Logic & Fragment

#### [MODIFY] [BottomSheetNotesTabbed.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/java/sound/recorder/widget/ui/bottomSheet/BottomSheetNotesTabbed.kt)
- **Promotion Tracking:**
    - Implement a mechanism to store a "content signature" for each promoted note.
    - Use `SharedPreferences` (`note_promo_prefs`) to map `note_id` to its last promoted signature.
    - A signature will be a concatenation of the note's title and its content.
- **`showPromoteConfirmation(note: Note)` Enhancement:**
    - Calculate the current signature of the note.
    - Compare it with the stored signature for that note ID.
    - If they match, show a warning: "This note has already been promoted. Edit it to promote again."
    - If they differ or no record exists, allow promotion.
- **`promoteNoteToOnline(...)` Enhancement:**
    - On successful upload to Firestore, save the current signature to `SharedPreferences`.

## Verification Plan

### Manual Verification
1.  **First Promotion:**
    - Select a local note -> Choose "Promote".
    - Verify it succeeds.
2.  **Duplicate Check:**
    - Select the *same* note immediately -> Choose "Promote".
    - Verify that a warning appears stating it's already promoted.
3.  **Edit & Re-promote:**
    - Edit the note (change title or content).
    - Choose "Promote" again.
    - Verify that it now allows promotion.
4.  **Daily Limit Integration:**
    - Verify that the 3-note daily limit still works alongside this new check.
