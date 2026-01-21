package sound.recorder.widget.util;


import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;

public class SafeFrameLayout extends FrameLayout {
    public SafeFrameLayout(Context context) {
        super(context);
    }

    private static final String TAG = "SafeFrameLayout";

    public SafeFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SafeFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        try {
            super.requestChildFocus(child, focused);
        } catch (IllegalArgumentException | NullPointerException e) {
            // Menangkap crash jika child bukan lagi descendant
            Log.w(TAG, "requestChildFocus ignored (likely AdView issue)", e);
        }
    }

    // Karena offsetDescendantRectToMyCoords adalah FINAL,
    // kita menangkap potensi masalah di level parent saat invalidasi view.
    @Override
    public ViewParent invalidateChildInParent(int[] location, android.graphics.Rect dirty) {
        try {
            return super.invalidateChildInParent(location, dirty);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "invalidateChildInParent ignored (likely AdView issue)", e);
            return null; // aman secara kontrak ViewParent
        }
    }
}