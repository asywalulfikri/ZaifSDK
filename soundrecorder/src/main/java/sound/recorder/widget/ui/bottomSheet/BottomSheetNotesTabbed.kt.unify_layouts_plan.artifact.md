# Implementation Plan: Unify Note Row Layouts

Merge `item_note_firebase.xml` and `note_list_row.xml` into a single, master layout file to simplify maintenance and ensure a consistent UI across both Local and Online tabs.

## Proposed Changes

### UI Components

#### [MODIFY] [note_list_row.xml](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/res/layout/note_list_row.xml)
- Redesign to include all necessary views for both Local and Online notes.
- **Unified IDs:**
    - `title` -> `tvNoteTitle`
    - `note` -> `tvNoteDesc`
- **Added Views (from Online/Admin):**
    - `tvStatus`: Badge for note status.
    - `btnApprove`: Admin approval button.
    - `btnDeleteOnline`: Admin deletion button.
- **Preserved View:**
    - `timestamp`: Specifically for local notes.

#### [DELETE] [item_note_firebase.xml](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/res/layout/item_note_firebase.xml)
- Remove this redundant layout file.

### Logic & Adapters

#### [MODIFY] [NotesAdapter.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/java/sound/recorder/widget/notes/NotesAdapter.kt)
- Update `ViewHolder` to use the new IDs (`tvNoteTitle`, `tvNoteDesc`).
- Ensure `tvStatus`, `btnApprove`, and `btnDeleteOnline` are hidden for local notes (they will be hidden by default or handled in the adapter).

#### [MODIFY] [FirebaseNotesAdapter.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/java/sound/recorder/widget/adapter/FirebaseNotesAdapter.kt)
- Change `onCreateViewHolder` to use `R.layout.note_list_row`.
- Update logic to handle the `timestamp` view (hiding it for online notes).

## Verification Plan

### Manual Verification
- **Local Tab:** Verify that personal notes display correctly with the music icon, title, description, and timestamp. Admin buttons should be invisible.
- **Online Tab (Debug):** Verify all notes (including DRAFTs) are shown with status badges and Admin buttons. `timestamp` should be hidden.
- **Online Tab (Release):** Verify only published notes are shown. No badges or admin buttons should be visible.
- **General UI:** Ensure all dimensions (`sdp`, `ssp`) and colors are consistent and look good in both orientations.
