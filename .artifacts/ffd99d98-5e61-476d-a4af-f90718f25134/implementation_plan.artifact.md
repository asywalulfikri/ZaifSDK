# Implementation Plan - Countdown Before Recording

This plan describes how to implement a 3-2-1 countdown feature before the recording starts in the `InstrumentControlPanel`.

## User Review Required

> [!IMPORTANT]
> The countdown will be displayed as a large overlay dialog in the center of the screen to ensure it is visible to the user.
> During the countdown, the user won't be able to interact with the instrument until the recording officially starts.

## Proposed Changes

### `soundrecorder` Module

#### [MODIFY] [InstrumentDialogHelper.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/java/sound/recorder/widget/music/InstrumentDialogHelper.kt)
- Add a new function `showCountdownDialog(context: Context, onFinished: () -> Unit): Dialog` that:
    - Displays a transparent dialog with a large `TextView`.
    - Updates the text from "3" to "1" every second.
    - Dismisses itself and triggers `onFinished()` when the countdown ends.

#### [MODIFY] [InstrumentControlPanel.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/java/sound/recorder/widget/recording/InstrumentControlPanel.kt)
- Update `showStartRecordConfirmation()` to call `InstrumentDialogHelper.showCountdownDialog` after the user selects the recording mode (Mic or Instrument Only) and before calling `startRecording()`.

## Verification Plan

### Manual Verification
1. Open the instrument.
2. Click the **REC** button.
3. Select either "Instrument Only" or "With Mic".
4. Verify that a large "3", "2", "1" countdown appears in the center of the screen.
5. Verify that the recording starts immediately after "1" disappears.
6. Verify that the timer in the `InstrumentControlPanel` starts only after the countdown.
