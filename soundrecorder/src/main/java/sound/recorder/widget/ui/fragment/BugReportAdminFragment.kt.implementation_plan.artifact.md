# Implement Reply Functionality in BugReportAdminFragment

Add a "Reply" feature to `BugReportAdminFragment` that allows admins to respond to bug reports and notify users via FCM.

## Proposed Changes

### UI Changes

#### [MODIFY] [item_song_request.xml](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/res/layout/item_song_request.xml)
- Add a `btnReply` TextView in the `layoutActions` section.

### Logic Changes

#### [MODIFY] [BugReportAdminFragment.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/java/sound/recorder/widget/ui/fragment/BugReportAdminFragment.kt)
- Update `BugRequest` data class to include `firebaseToken`.
- Update `loadRequests()` to map `firebaseToken` from Firestore documents.
- Port FCM notification logic from `NotePromotionAdminFragment.kt`:
    - `sendReplyNotification(req: BugRequest, replyMessage: String)`
    - `getOAuthToken(serviceAccountJson: String)`
- Implement `showReplyDialog(req: BugRequest)` to capture the admin's message and trigger the notification/update.
- Update `SongRequestAdapter`:
    - Bind `btnReply` and set its click listener to call `showReplyDialog(item)`.
    - Handle `btnReply` visibility (shown only if `firebaseToken` is present).

## Verification Plan

### Manual Verification
- Deploy the app.
- Go to `BugReportAdminFragment`.
- Click "Reply" on a bug report.
- Enter a message and click "Send".
- Verify that:
    - The Firestore document is updated (optional).
    - A notification is sent to the device associated with the `firebaseToken`.
    - A Toast message indicates success/failure.
