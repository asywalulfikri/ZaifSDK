package sound.recorder.widget.colorpicker

import android.app.Dialog
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Build
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatButton
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import sound.recorder.widget.R
import java.lang.ref.WeakReference


class ColorViewAdapter : RecyclerView.Adapter<ColorViewAdapter.ViewHolder> {
    private var onFastChooseColorListener: ColorPicker.OnFastChooseColorListener? = null
    private var mDataset: ArrayList<ColorPal>
    var colorPosition = -1
        private set
    var colorSelected = 0
        private set
    private var marginLeft = 0
    private var marginRight = 0
    private var marginTop = 0
    private var marginBottom = 0
    private var tickColor = Color.WHITE
    private var marginButtonLeft = 0
    private var marginButtonRight = 0
    private var marginButtonTop = 3
    private var marginButtonBottom = 3
    private var buttonWidth = -1
    private var buttonHeight = -1
    private var buttonDrawable = 0
    private var mDialog: WeakReference<CustomDialog>? = null

    inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v),
        View.OnClickListener {
        var colorItem: AppCompatButton
        override fun onClick(v: View) {
            if (colorPosition != -1 && colorPosition != layoutPosition) {
                mDataset[colorPosition].isCheck = false
                notifyItemChanged(colorPosition)
            }
            colorPosition = layoutPosition
            colorSelected = v.tag as Int
            mDataset[layoutPosition].isCheck = true
            notifyItemChanged(colorPosition)
            if (onFastChooseColorListener != null && mDialog != null) {
                onFastChooseColorListener?.setOnFastChooseColorListener(colorPosition, colorSelected)
                dismissDialog()
            }
        }

        init {
            //buttons settings
            colorItem = v.findViewById(R.id.color)
            colorItem.setTextColor(tickColor)
           // colorItem.setBackgroundResource(buttonDrawable)
            colorItem.setBackgroundColor(buttonDrawable)
            colorItem.setOnClickListener(this)
            val layoutParams = colorItem.layoutParams as LinearLayout.LayoutParams
            layoutParams.setMargins(
                marginButtonLeft,
                marginButtonTop,
                marginButtonRight,
                marginButtonBottom
            )
            if (buttonWidth != -1) layoutParams.width = buttonWidth
            if (buttonHeight != -1) layoutParams.height = buttonHeight

            //relative layout settings
            val linearLayout = v.findViewById<LinearLayout>(R.id.linearLayout)
            val lp = linearLayout.layoutParams as GridLayoutManager.LayoutParams
            lp.setMargins(marginLeft, marginTop, marginRight, marginBottom)
        }
    }

    private fun dismissDialog() {
        if (mDialog == null) return
        val dialog: Dialog? = mDialog!!.get()
        if (dialog != null && dialog.isShowing) {
            dialog.dismiss()
        }
    }

    constructor(
        myDataset: ArrayList<ColorPal>,
        onFastChooseColorListener: ColorPicker.OnFastChooseColorListener?,
        dialog: WeakReference<CustomDialog>?
    ) {
        mDataset = myDataset
        mDialog = dialog
        this.onFastChooseColorListener = onFastChooseColorListener
    }

    constructor(myDataset: ArrayList<ColorPal>) {
        mDataset = myDataset
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v: View =
            LayoutInflater.from(parent.context).inflate(R.layout.palette_item, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val color: Int = mDataset[position].color
        val textColor = if (ColorUtils.isWhiteText(color)) Color.WHITE else Color.BLACK
        if (mDataset[position].isCheck) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                holder.colorItem.text = "✔"
            } else {
                holder.colorItem.text = Html.fromHtml("&#x2713;")
            }
        } else holder.colorItem.text = ""
        holder.colorItem.setTextColor(if (tickColor == Color.WHITE) textColor else tickColor)
        if (buttonDrawable != 0) {
          //  holder.colorItem.background.setColorFilter(color, PorterDuff.Mode.SRC_IN)
            holder.colorItem.setBackgroundColor(color)
        } else {
            holder.colorItem.setBackgroundColor(color)
        }
        holder.colorItem.tag = color
    }

    override fun getItemCount(): Int {
        return mDataset.size
    }

    fun setMargin(left: Int, top: Int, right: Int, bottom: Int) {
        marginBottom = bottom
        marginLeft = left
        marginRight = right
        marginTop = top
    }

    fun setDefaultColor(color: Int) {
        for (i in mDataset.indices) {
            val colorPal: ColorPal = mDataset[i]
            if (colorPal.color == color) {
                //colorPal.isCheck = true
                colorPosition = i
                notifyItemChanged(i)
                colorSelected = color
            }
        }
    }

    fun setTickColor(color: Int) {
        tickColor = color
    }

    fun setColorButtonMargin(left: Int, top: Int, right: Int, bottom: Int) {
        marginButtonLeft = left
        marginButtonRight = right
        marginButtonTop = top
        marginButtonBottom = bottom
    }

    fun setColorButtonSize(width: Int, height: Int) {
        buttonWidth = width
        buttonHeight = height
    }

    fun setColorButtonDrawable(drawable: Int) {
        buttonDrawable = drawable
    }
}
