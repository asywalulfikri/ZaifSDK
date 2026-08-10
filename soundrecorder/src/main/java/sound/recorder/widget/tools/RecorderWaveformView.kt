package sound.recorder.widget.tools

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

internal class RecorderWaveformView: View {

    private lateinit var amplitudes: ArrayList<Int>
    @Volatile
    private var spikesToDraw: List<RectF> = emptyList()
    private val allSpikes = ArrayList<RectF>()
    private lateinit var paintRead: Paint
    private var w : Float = 9f
    private var d : Float = 4f
    private var sw : Int = 0
    private var maxSpikes : Int = 0
    private var maxAmp : Int = 200

    constructor(context: Context?) : super(context){
        init(null)
    }
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs){
        init(attrs)
    }
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ){
        init(attrs)
    }

    @SuppressLint("NewApi")
    constructor(
        context: Context?,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes){
        init(attrs)
    }

    // this function is to avoid duplicating code in every constructor
    // indeed each constructor is called in a specific situation
    // and we want the View to de the same thing no matter what
    private fun init(attrs: AttributeSet?){
        amplitudes = ArrayList()
        paintRead = Paint() //Paint.ANTI_ALIAS_FLAG
        paintRead.color = Color.rgb(255, 127, 0) // orange

        // get screen width
        val displayMetrics = resources.displayMetrics
        sw = displayMetrics.widthPixels/3

        maxSpikes = (sw/(w+d)).toInt()
        allSpikes.clear()
        spikesToDraw = emptyList()
    }

    fun reset(){
        amplitudes.clear()
        allSpikes.clear()
        spikesToDraw = emptyList()
        postInvalidate()
    }

    fun updateAmps(amp: Int?){

        if(amp!=null){
            val norm  = min(amp/7, maxAmp)
            amplitudes.add(norm)
            val amps = amplitudes.takeLast(maxSpikes)

            val newSpikes = ArrayList<RectF>()

            for(i in amps.indices){
                val deltaVal = maxAmp.toFloat()
                val top = deltaVal - amps[i]
                val bottom = top + amps[i]
                
                newSpikes.add(RectF(sw-i*(w+d), top, sw-i*(w+d) - w, bottom))
                newSpikes.add(RectF(sw-i*(w+d), deltaVal-2, sw-i*(w+d) - w, deltaVal+amps[i]))
            }
            spikesToDraw = newSpikes
            postInvalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        // Draw the last known snapshot without locking
        val toDraw = spikesToDraw
        toDraw.forEach {
            canvas.drawRoundRect(it, 6f, 6f, paintRead)
        }
    }
}