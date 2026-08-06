# Walkthrough - Fixed IllegalArgumentException in BlurMaskFilter

I have fixed the `java.lang.IllegalArgumentException` crash occurring in `BlurMaskFilter.nativeConstructor`. The crash was caused by passing a radius value $\le 0$ to the `BlurMaskFilter` constructor.

## Changes Made

### Component: App UI

#### [DemungView.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/app/src/main/java/recording/host/DemungView.kt)
- Added `max(0.01f, ...)` to guard the blur radius in `drawOval` (shadow) and `drawText` (glow).
- The file already imported `kotlin.math.*`.

### Component: Sound Recorder Widgets

#### [DJSeekBar.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/java/sound/recorder/widget/music/DJSeekBar.kt)
- Added `import kotlin.math.max`.
- Guarded blur radius calculations for both the outer glow layers and the thumb shadow.

#### [MusicSeekBar.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/java/sound/recorder/widget/music/MusicSeekBar.kt)
- Added `import kotlin.math.max`.
- Guarded the blur radius for the thumb glow halo.

#### [SmoothSeekBar.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/java/sound/recorder/widget/music/SmoothSeekBar.kt)
- Added `import kotlin.math.max`.
- Guarded the blur radius for the thumb shadow.

## Verification Results

### Automated Tests
- Executed `:app:assembleDebug` and `:soundrecorder:assembleDebug`.
- **Result:** Build finished successfully.

### Manual Verification
- Code analysis confirms that all `BlurMaskFilter` instantiations now have a floor value of `0.01f`, preventing the native exception even if the calculated radius becomes 0 or negative due to layout scaling or animation states.
