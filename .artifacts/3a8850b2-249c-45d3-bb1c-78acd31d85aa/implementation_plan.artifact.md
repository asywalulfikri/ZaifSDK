# Implementation Plan - Optimization and ANR Prevention for Notes Feature

Review and refactor `BottomSheetNotesTabbed.kt` to prevent potential ANRs and improve overall performance by offloading blocking operations to background threads.

## User Review Required

> [!IMPORTANT]
> The changes involve introducing Kotlin Coroutines to handle asynchronous operations. This will significantly improve UI responsiveness but requires careful management of fragment lifecycles.

## Proposed Changes

### [soundrecorder] Component

#### [MODIFY] [BottomSheetNotesTabbed.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/java/sound/recorder/widget/ui/bottomSheet/BottomSheetNotesTabbed.kt)
- **Asynchronous DB/Cache Operations**: Use `lifecycleScope.launch(Dispatchers.IO)` for:
    - Loading local notes from `dbHelper`.
    - Reading and saving JSON cache.
    - Promoting notes to Firestore (already using listeners, but data prep can be async).
- **Search Optimization**: Implement a simple background filter for the search functionality to avoid blocking the UI thread during typing.
- **Efficient UI Updates**:
    - Avoid recreating `RecyclerView` adapters every time a tab is switched.
    - Initialize `localAdapter` and `onlineAdapter` once and update their data.
- **General Clean-up**:
    - Remove redundant code in `setupLocalRecyclerView` and `setupOnlineRecyclerView`.
    - Add null safety checks where necessary.

## Verification Plan

### Automated Tests
- Run `:soundrecorder:assembleDebug` to ensure no syntax errors were introduced.

### Manual Verification
- **Functional Check**: Verify that adding, editing, deleting, and promoting notes still works correctly.
- **Performance Check**: Verify that searching through a large list of notes doesn't cause UI lag.
- **Tab Switching**: Verify that switching between "My Song Notes" and "Online Song Notes" is smooth and preserves state.
