# Fix IllegalArgumentException in BlurMaskFilter

The application is crashing with `java.lang.IllegalArgumentException` in `BlurMaskFilter.nativeConstructor`. This happens when the `radius` parameter passed to the `BlurMaskFilter` constructor is less than or equal to 0.

## Proposed Changes

I will update all occurrences of `BlurMaskFilter` instantiation to ensure that the radius is always greater than 0.

### Component: App UI

#### [MODIFY] [DemungView.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/app/src/main/java/recording/host/DemungView.kt)
- Ensure the radius for `BlurMaskFilter` in `drawBilah` is greater than 0.

### Component: Sound Recorder Widget

#### [MODIFY] [DJSeekBar.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/java/sound/recorder/widget/music/DJSeekBar.kt)
- Ensure the radius for `BlurMaskFilter` in `onDraw` is greater than 0.

#### [MODIFY] [MusicSeekBar.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/java/sound/recorder/widget/music/MusicSeekBar.kt)
- Ensure the radius for `BlurMaskFilter` in `drawThumb` is greater than 0.

#### [MODIFY] [SmoothSeekBar.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/java/sound/recorder/widget/music/SmoothSeekBar.kt)
- Ensure the radius for `BlurMaskFilter` in `onDraw` is greater than 0.

## Verification Plan

### Manual Verification
- I will check the code logic to ensure that `max(0.1f, calculatedRadius)` or similar guard is applied.
- Since I cannot easily trigger the crash without specific device/layout conditions, I will rely on code analysis and applying the fix to all identified vulnerable spots.
