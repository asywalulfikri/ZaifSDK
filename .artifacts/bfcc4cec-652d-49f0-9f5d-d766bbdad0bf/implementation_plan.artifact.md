# Fix IllegalArgumentException in BlurMaskFilter

The application is crashing with `java.lang.IllegalArgumentException` in `BlurMaskFilter.nativeConstructor`. This happens when the `radius` parameter passed to the `BlurMaskFilter` constructor is less than or equal to 0.

## User Review Required

> [!IMPORTANT]
> This fix adds a small floor value (0.01f) to the blur radius to prevent the crash when the calculated radius is 0 or less. This might result in a very subtle blur even when it was intended to be none, but it's much better than a crash. Alternatively, we could conditionally set the `maskFilter` to null, but adding a floor is simpler for one-liners.

## Proposed Changes

I will update all occurrences of `BlurMaskFilter` instantiation to ensure that the radius is always greater than 0.

### Component: App UI

#### [MODIFY] [DemungView.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/app/src/main/java/recording/host/DemungView.kt)
- Wrap radius calculations with `max(0.01f, ...)` for all `BlurMaskFilter` calls.

### Component: Sound Recorder Widgets

#### [MODIFY] [DJSeekBar.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/java/sound/recorder/widget/music/DJSeekBar.kt)
- Wrap radius calculations with `max(0.01f, ...)` for all `BlurMaskFilter` calls.

#### [MODIFY] [MusicSeekBar.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/java/sound/recorder/widget/music/MusicSeekBar.kt)
- Wrap radius calculations with `max(0.01f, ...)` for all `BlurMaskFilter` calls.

#### [MODIFY] [SmoothSeekBar.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/java/sound/recorder/widget/music/SmoothSeekBar.kt)
- Wrap radius calculations with `max(0.01f, ...)` for all `BlurMaskFilter` calls.

## Verification Plan

### Automated Tests
- None, as this is a UI rendering issue that is hard to unit test without screenshot testing or complex mocking.

### Manual Verification
- Code review of the applied changes to ensure all `BlurMaskFilter` calls are guarded.
- Build the project to ensure no syntax errors.
