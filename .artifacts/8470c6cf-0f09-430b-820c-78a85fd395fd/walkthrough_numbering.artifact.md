# Walkthrough - Adding Item Numbering to Song List

I have added item numbering to each row in the `InstrumentTutorialDialog` song list to help users track the total number of items easily.

## Changes Made

### [soundrecorder component]

#### [item_song_tutorial.xml](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/res/layout/item_song_tutorial.xml)

- Added a new `TextView` (`tvNumber`) to display the item index.
- Adjusted the layout of `ivSongIcon` to be positioned to the right of the number.

```xml
        <!-- Number -->
        <TextView
            android:id="@+id/tvNumber"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_alignParentStart="true"
            android:layout_centerVertical="true"
            android:text="1"
            android:textColor="#8B93B8"
            android:textSize="@dimen/_10ssp"
            android:textStyle="bold"
            android:minWidth="@dimen/_20sdp"
            android:gravity="center"/>
```

#### [InstrumentTutorialDialog.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/java/sound/recorder/widget/tutorial/InstrumentTutorialDialog.kt)

- Updated `SongListAdapter.ViewHolder` to include `tvNumber`.
- Modified `onBindViewHolder` to set the position (starting from 1) on the `tvNumber` view.

```kotlin
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            // ...
            holder.tvNumber.text = "${position + 1}."
            // ...
        }
```

## Verification Results

### Automated Tests
- Ran `:soundrecorder:assembleDebug`: **Build finished successfully.**

### Manual Verification
- Each song item in the tutorial dialog now shows its sequence number (e.g., 1., 2., 3., ...), making it easy to see the total number of items as you scroll.
