# Fix Pagination Resume from Cache in InstrumentTutorialDialog

Currently, pagination stops working when the dialog is reopened from cache because the `lastDocument` snapshot is lost. This plan fixes it by using the timestamp of the last cached item as a fallback for pagination.

## Proposed Changes

### [soundrecorder component]

#### [MODIFY] [InstrumentTutorialDialog.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/java/sound/recorder/widget/tutorial/InstrumentTutorialDialog.kt)

- Modify `loadMoreRemote` to support starting from a timestamp if `lastDocument` is null.
- Logic:
    1. Check if `lastDocument` is null.
    2. If null, find the last `SongItem.Remote` in `allItems`.
    3. If a remote item exists, use its `submittedAt` timestamp with `.startAfter(timestamp)` in the Firestore query.
    4. This allows the query to "resume" correctly even after the dialog was closed and reopened from cache.

## Verification Plan

### Automated Tests
- Build the project to verify no compilation errors.

### Manual Verification
1. Open the dialog and load 2 pages (200 items).
2. Close the dialog.
3. Reopen the dialog (items should load from cache).
4. Scroll to the bottom and verify that Page 3 (items 201-300) loads successfully.
