package sound.recorder.widget.recording

import android.graphics.Color
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.widget.Button

class BlinkManager(
    private val config: ControlConfig,
    private val factory: ControlButtonFactory,
    private val getRecordBtn: () -> Button?,
    private val getStopBtn: () -> Button?,
    private val getRecordLabel: () -> String,
    private val isGameStyle: Boolean = false
) {

    private val handler = Handler(Looper.getMainLooper())

    // ─── BLINK: btnRecord ───
    private val blinkRecordRunnable = object : Runnable {
        private var isOn = false
        override fun run() {
            val btn = getRecordBtn() ?: return
            isOn = !isOn
            val stopStr = getRecordLabel()
            btn.text = if (isOn) "${stopStr.uppercase()} ●" else stopStr.uppercase()
            btn.setTextColor(if (isOn) Color.WHITE else config.textColor)
            btn.background = StateListDrawable().apply {
                if (isGameStyle) {
                    addState(intArrayOf(android.R.attr.state_pressed), factory.createGameStyleBg("F44336", true))
                    addState(intArrayOf(), factory.createGameStyleBgRec(isOn))
                } else {
                    addState(intArrayOf(android.R.attr.state_pressed), factory.createBg(pressed = true, isRed = true))
                    addState(intArrayOf(), factory.createBgRec(isOn))
                }
            }
            handler.postDelayed(this, 600)
        }
    }

    // ─── BLINK: btnStop ───
    private val blinkStopRunnable = object : Runnable {
        private var isOn = false
        override fun run() {
            val btn = getStopBtn() ?: return
            if (btn.visibility != android.view.View.VISIBLE) return
            isOn = !isOn
            btn.background = StateListDrawable().apply {
                if (isGameStyle) {
                    addState(intArrayOf(android.R.attr.state_pressed), factory.createGameStyleBg("F44336", true))
                    addState(intArrayOf(), if (isOn) factory.createGameStyleBgStopOn() else factory.createGameStyleBg("F44336", false))
                } else {
                    addState(intArrayOf(android.R.attr.state_pressed), factory.createBg(pressed = true, isRed = true))
                    addState(intArrayOf(), if (isOn) factory.createBgStopOn() else factory.createBg(pressed = false, isRed = true))
                }
            }
            btn.setTextColor(if (isOn) Color.WHITE else Color.parseColor("#FF6666"))
            handler.postDelayed(this, 500)
        }
    }

    fun startRecordBlink() {
        handler.post(blinkRecordRunnable)
    }

    fun stopRecordBlink() {
        handler.removeCallbacks(blinkRecordRunnable)
    }

    fun startStopBlink() {
        handler.removeCallbacks(blinkStopRunnable)
        handler.post(blinkStopRunnable)
    }

    fun stopStopBlink() {
        handler.removeCallbacks(blinkStopRunnable)
        resetStopBtn()
    }

    fun resetRecordBtn() {
        stopRecordBlink()
        getRecordBtn()?.apply {
            text = "REC"
            setTextColor(if (isGameStyle) Color.WHITE else config.textColor)
            background = StateListDrawable().apply {
                if (isGameStyle) {
                    addState(intArrayOf(android.R.attr.state_pressed), factory.createGameStyleBg("8BC34A", true))
                    addState(intArrayOf(), factory.createGameStyleBg("8BC34A", false))
                } else {
                    addState(intArrayOf(android.R.attr.state_pressed), factory.createBg(pressed = true))
                    addState(intArrayOf(), factory.createBg(pressed = false))
                }
            }
        }
    }

    private fun resetStopBtn() {
        getStopBtn()?.apply {
            background = StateListDrawable().apply {
                if (isGameStyle) {
                    addState(intArrayOf(android.R.attr.state_pressed), factory.createGameStyleBg("F44336", true))
                    addState(intArrayOf(), factory.createGameStyleBg("F44336", false))
                } else {
                    addState(intArrayOf(android.R.attr.state_pressed), factory.createBg(pressed = true, isRed = true))
                    addState(intArrayOf(), factory.createBg(pressed = false, isRed = true))
                }
            }
            setTextColor(Color.WHITE)
        }
    }

    fun removeAllCallbacks() {
        handler.removeCallbacks(blinkRecordRunnable)
        handler.removeCallbacks(blinkStopRunnable)
    }
}