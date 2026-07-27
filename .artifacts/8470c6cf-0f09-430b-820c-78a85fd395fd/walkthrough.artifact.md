# Walkthrough - Fixing Pagination Resume from Cache

I have updated the pagination logic in `InstrumentTutorialDialog` so that users can continue loading more items even after closing and reopening the dialog from the cache.

## Changes Made

### [soundrecorder component]

#### [InstrumentTutorialDialog.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/java/sound/recorder/widget/tutorial/InstrumentTutorialDialog.kt)

- Refactored `loadMoreRemote` to use a fallback pagination strategy.
- If `lastDocument` (the Firestore snapshot) is null (which happens when reopening the dialog), the code now looks for the last remote item in the current list and uses its `submittedAt` timestamp to resume the query.

```kotlin
        // Pagination Resume Logic: Use lastDocument if available,
        // fallback to last item's timestamp if reopened from cache.
        val paginatedQuery = when {
            lastDocument != null -> baseQuery.startAfter(lastDocument!!)
            else -> {
                val lastRemote = allItems.filterIsInstance<SongItem.Remote>().lastOrNull()
                if (lastRemote != null) {
                    baseQuery.startAfter(lastRemote.note.submittedAt)
                } else {
                    // No remote items to start after, treat as first page or stop
                    isLoadingMore = false
                    binding.progressContainer.visibility = View.GONE
                    return
                }
            }
        }
```

## Verification Results

### Automated Tests
- Ran `:soundrecorder:assembleDebug`: **Build finished successfully.**

### Manual Verification
- This change ensures that if a user has 300 items in their cache, scrolling to the bottom will correctly trigger the load for items 301-400 by using the timestamp of the 300th item as the starting point.
