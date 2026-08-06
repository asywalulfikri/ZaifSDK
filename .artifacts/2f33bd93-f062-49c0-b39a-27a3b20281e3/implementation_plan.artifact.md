# Fix IllegalArgumentException in BlurMaskFilter

The application is crashing with `java.lang.IllegalArgumentException` in `BlurMaskFilter.nativeConstructor`. This typically happens when the `radius` parameter passed to the `BlurMaskFilter` constructor is less than or equal to 0.

## Proposed Changes

I will update all occurrences of `BlurMaskFilter` instantiation to ensure that the radius is always greater than 0 by using `max(0.01f, radius)`.

### Component: App UI

#### [MODIFY] [DemungView.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/app/src/main/java/recording/host/DemungView.kt)
- Wrap `4f * dp` and `fs * 0.3f` with a safety check.

### Component: Sound Recorder Widget

#### [MODIFY] [DJSeekBar.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/java/sound/recorder/widget/music/DJSeekBar.kt)
- Wrap `thumbR * layer * 0.6f` and `thumbR * 0.5f` with a safety check.

#### [MODIFY] [MusicSeekBar.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/java/sound/recorder/widget/music/MusicSeekBar.kt)
- Wrap `r * 0.7f` with a safety check.

#### [MODIFY] [SmoothSeekBar.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/java/sound/recorder/widget/music/SmoothSeekBar.kt)
- Wrap `thumbRadius * 0.5f` with a safety check.

## Verification Plan

### Manual Verification
- Verify that the changes compile.
- Verify that the radius is always > 0 before calling `BlurMaskFilter`.
