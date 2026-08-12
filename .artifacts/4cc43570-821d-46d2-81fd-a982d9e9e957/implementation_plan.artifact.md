# Implementation Plan - Memory-Safe Music Listener

The goal is to ensure `MusicPlayerManager` listeners are properly cleaned up when Fragments are destroyed to prevent memory leaks, without interfering with the music playback or other components.

## Proposed Changes

### [app component]

#### [MODIFY] [BaseFragment.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/app/src/main/java/recording/host/BaseFragment.kt)
- Define a private property `musicPlayerListener` of type `MusicPlayerManager.PlayerListener?`.
- Update `setupMusicObserver` to:
    - Unregister any existing `musicPlayerListener`.
    - Create a new `PlayerListener` instance.
    - Register it using `MusicPlayerManager.addListener()`.
- Implement `onDestroyView` to:
    - Unregister the listener using `MusicPlayerManager.removeListener()`.
    - Set the local property to `null`.
- This ensures that each Fragment instance manages its own lifecycle-bound listener.

## Verification Plan

### Manual Verification
- Deploy the app and navigate to an instrument fragment.
- Play music and verify UI updates (Stop button animation).
- Navigate away and back, verify music status is still correctly detected (via the initial check in `setupMusicObserver`).
- Monitor Logcat for "Adding listener" and "Removing listener" messages from `MusicPlayerManager`.
