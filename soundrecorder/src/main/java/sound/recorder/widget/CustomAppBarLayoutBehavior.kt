package sound.recorder.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.appbar.AppBarLayout

class CustomAppBarLayoutBehavior : AppBarLayout.Behavior {
    var isShouldScroll = false
        private set

    constructor() : super() {}
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {}

    override fun onStartNestedScroll(
        parent: CoordinatorLayout,
        child: AppBarLayout,
        directTargetChild: View,
        target: View,
        nestedScrollAxes: Int,
        type: Int
    ): Boolean {
        return isShouldScroll
    }

    override fun onTouchEvent(parent: CoordinatorLayout, child: AppBarLayout, ev: MotionEvent): Boolean {
        return if (isShouldScroll) {
            super.onTouchEvent(parent, child, ev)
        } else {
            false
        }
    }

    fun setScrollBehavior(shouldScroll: Boolean) {
        isShouldScroll = shouldScroll
    }
}