package sound.recorder.widget.colorpicker

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.res.TypedArray
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import sound.recorder.widget.R
import sound.recorder.widget.colorpicker.ColorUtils.dip2px
import java.lang.ref.WeakReference

@SuppressLint("InflateParams")
class ColorPicker(context: Activity) {
    private var onChooseColorListener: OnChooseColorListener? = null
    private var onFastChooseColorListener: OnFastChooseColorListener? = null

    interface OnChooseColorListener {
        fun onChooseColor(position: Int, color: Int)
        fun onCancel()
    }

    interface OnFastChooseColorListener {
        fun setOnFastChooseColorListener(position: Int, color: Int)
        fun onCancel()
    }

    interface OnButtonListener {
        fun onClick(v: View?, position: Int, color: Int)
    }

    private var colors: ArrayList<ColorPal>? = null
    private var colorViewAdapter: ColorViewAdapter? = null
    private var fastChooser = false
    private var ta: TypedArray? = null
    private val mContext: WeakReference<Activity>?
    private var columns: Int
    private var title: String?
    private var marginLeft = 0
    private var marginRight = 0
    private var marginTop = 0
    private var marginBottom = 0
    private var tickColor = 0
    private var marginColorButtonLeft: Int
    private var marginColorButtonRight: Int
    private var marginColorButtonTop: Int
    private var marginColorButtonBottom: Int
    private var colorButtonWidth = 0
    private var colorButtonHeight = 0
    private var colorButtonDrawable = 0
    private val negativeText: String
    private val positiveText: String
    private var roundColorButton = false
    private var dismiss: Boolean
    private var fullHeight = false
    private var mDialog: WeakReference<CustomDialog>? = null
    private val recyclerView: RecyclerView

    /**
     * getDialogBaseLayout which is the RelativeLayout that contains the RecyclerView
     *
     * @return RelativeLayout
     */
    val dialogBaseLayout: RelativeLayout
    private val buttons_layout: LinearLayout
    private var default_color: Int
    private var paddingTitleLeft = 0
    private var paddingTitleRight = 0
    private var paddingTitleBottom = 0
    private var paddingTitleTop = 0

    /**
     * getDialogViewLayout is the view inflated into the mDialog
     *
     * @return View
     */
    val dialogViewLayout: View
    private var disableDefaultButtons = false
    private val positiveButton: AppCompatButton
    private val negativeButton: AppCompatButton

    /**
     * Set buttons color using a resource array of colors example : check in library  res/values/colorpicker-array.xml
     *
     * @param resId Array resource
     * @return this
     */
    fun setColors(resId: Int): ColorPicker {
        if (mContext == null) return this
        val context = mContext.get() ?: return this
        ta = context.resources.obtainTypedArray(resId)
        colors = ArrayList()
        for (i in 0 until ta!!.length()) {
            colors!!.add(ColorPal(ta!!.getColor(i, 0), false))
        }
        return this
    }

    /**
     * Set buttons from an arraylist of Hex values
     *
     * @param colorsHexList List of hex values of the colors
     * @return this
     */
    fun setColors(colorsHexList: ArrayList<String?>): ColorPicker {
        colors = ArrayList()
        for (i in colorsHexList.indices) {
            colors!!.add(ColorPal(Color.parseColor(colorsHexList[i]), false))
        }
        return this
    }

    /**
     * Set buttons color  Example : Color.RED,Color.BLACK
     *
     * @param colorsList list of colors
     * @return this
     */
    fun setColors(vararg colorsList: Int): ColorPicker {
        colors = ArrayList()
        for (aColorsList in colorsList) {
            colors!!.add(ColorPal(aColorsList, false))
        }
        return this
    }

    /**
     * Choose the color to be selected by default
     *
     * @param color int
     * @return this
     */
    fun setDefaultColorButton(color: Int): ColorPicker {
        default_color = color
        return this
    }

    /**
     * Show the Material Dialog
     */
    fun show() {
        if (mContext == null) return
        val context = mContext.get() ?: return
        if (colors == null || colors!!.isEmpty()) setColors()
        val titleView = dialogViewLayout.findViewById<AppCompatTextView>(R.id.title)
        if (title != null) {
            titleView.text = title
            titleView.setPadding(
                dip2px(paddingTitleLeft.toFloat(), context),
                dip2px(paddingTitleTop.toFloat(), context),
                dip2px(paddingTitleRight.toFloat(), context),
                dip2px(paddingTitleBottom.toFloat(), context)
            )
        }
        mDialog = WeakReference(CustomDialog(context, dialogViewLayout))
        val gridLayoutManager = GridLayoutManager(context, columns)
        recyclerView.layoutManager = gridLayoutManager
        colorViewAdapter =
            if (fastChooser) ColorViewAdapter(
                colors!!,
                onFastChooseColorListener,
                mDialog
            ) else ColorViewAdapter(
                colors!!
            )
        if (fullHeight) {
            val lp = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
            )
            lp.addRule(RelativeLayout.BELOW, titleView.id)
            lp.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE)
            recyclerView.layoutParams = lp
        }
        recyclerView.adapter = colorViewAdapter
        if (marginBottom != 0 || marginLeft != 0 || marginRight != 0 || marginTop != 0) {
            colorViewAdapter!!.setMargin(marginLeft, marginTop, marginRight, marginBottom)
        }
        if (tickColor != 0) {
            colorViewAdapter!!.setTickColor(tickColor)
        }
        if (marginColorButtonBottom != 0 || marginColorButtonLeft != 0 || marginColorButtonRight != 0 || marginColorButtonTop != 0) {
            colorViewAdapter!!.setColorButtonMargin(
                dip2px(marginColorButtonLeft.toFloat(), context),
                dip2px(marginColorButtonTop.toFloat(), context),
                dip2px(marginColorButtonRight.toFloat(), context),
                dip2px(marginColorButtonBottom.toFloat(), context)
            )
        }
        if (colorButtonHeight != 0 || colorButtonWidth != 0) {
            colorViewAdapter!!.setColorButtonSize(
                dip2px(colorButtonWidth.toFloat(), context),
                dip2px(colorButtonHeight.toFloat(), context)
            )
        }
        if (roundColorButton) {
            setColorButtonDrawable(R.drawable.ic_circle_white)
        }
        if (colorButtonDrawable != 0) {
            colorViewAdapter!!.setColorButtonDrawable(colorButtonDrawable)
        }
        if (default_color != 0) {
            colorViewAdapter!!.setDefaultColor(default_color)
        }
        if (disableDefaultButtons) {
            positiveButton.visibility = View.GONE
            negativeButton.visibility = View.GONE
        }
        positiveButton.text = positiveText
        negativeButton.text = negativeText
        positiveButton.setOnClickListener {
            if (onChooseColorListener != null && !fastChooser) onChooseColorListener!!.onChooseColor(
                colorViewAdapter!!.colorPosition, colorViewAdapter!!.colorSelected
            )
            if (dismiss) {
                dismissDialog()
                if (onFastChooseColorListener != null) {
                    onFastChooseColorListener!!.onCancel()
                }
            }
        }
        negativeButton.setOnClickListener {
            if (dismiss) dismissDialog()
            if (onChooseColorListener != null) onChooseColorListener!!.onCancel()
        }
        if (mDialog == null) {
            return
        }
        val dialog: Dialog? = mDialog!!.get()
        if (dialog != null && !context.isFinishing) {
            dialog.show()
            //Keep mDialog open when rotate
            val lp = WindowManager.LayoutParams()
            lp.copyFrom(dialog.window!!.attributes)
            lp.width = WindowManager.LayoutParams.WRAP_CONTENT
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT
            dialog.window!!.attributes = lp
        }
    }

    /**
     * Define the number of columns by default value= 3
     *
     * @param c Columns number
     * @return this
     */
    fun setColumns(c: Int): ColorPicker {
        columns = c
        return this
    }

    /**
     * Define the title of the Material Dialog
     *
     * @param title Title
     * @return this
     */
    fun setTitle(title: String?): ColorPicker {
        this.title = title
        return this
    }

    /**
     * Set tick color
     *
     * @param color Color
     * @return this
     */
    fun setColorButtonTickColor(color: Int): ColorPicker {
        tickColor = color
        return this
    }

    /**
     * Set a single drawable for all buttons example : you can define a different shape ( then round or square )
     *
     * @param drawable Resource
     * @return this
     */
    fun setColorButtonDrawable(drawable: Int): ColorPicker {
        colorButtonDrawable = drawable
        return this
    }

    /**
     * Set the buttons size in DP
     *
     * @param width  width
     * @param height height
     * @return this
     */
    fun setColorButtonSize(width: Int, height: Int): ColorPicker {
        colorButtonWidth = width
        colorButtonHeight = height
        return this
    }

    /**
     * Set the Margin between the buttons in DP is 10
     *
     * @param left   left
     * @param top    top
     * @param right  right
     * @param bottom bottom
     * @return this
     */
    fun setColorButtonMargin(left: Int, top: Int, right: Int, bottom: Int): ColorPicker {
        marginColorButtonLeft = left
        marginColorButtonTop = top
        marginColorButtonRight = right
        marginColorButtonBottom = bottom
        return this
    }

    /**
     * Set round button
     *
     * @param roundButton true if you want a round button
     * @return this
     */
    fun setRoundColorButton(roundButton: Boolean): ColorPicker {
        roundColorButton = roundButton
        return this
    }

    /**
     * set a fast listener ( it shows a mDialog without buttons and the event fires as soon you select a color )
     *
     * @param listener OnFastChooseColorListener
     * @return this
     */
    fun setOnFastChooseColorListener(listener: OnFastChooseColorListener?): ColorPicker {
        fastChooser = true
        buttons_layout.visibility = View.GONE
        onFastChooseColorListener = listener
        dismissDialog()
        return this
    }

    /**
     * set a listener for the color picked
     *
     * @param listener OnChooseColorListener
     */
    fun setOnChooseColorListener(listener: OnChooseColorListener?): ColorPicker {
        onChooseColorListener = listener
        return this
    }

    /**
     * Add a  Button
     *
     * @param text     title of button
     * @param button   button to be added
     * @param listener listener
     * @return this
     */
    fun addListenerButton(text: String?, button: Button, listener: OnButtonListener): ColorPicker {
        button.setOnClickListener { v ->
            listener.onClick(
                v,
                colorViewAdapter!!.colorPosition,
                colorViewAdapter!!.colorSelected
            )
        }
        button.text = text
        if (button.parent != null) buttons_layout.removeView(button)
        buttons_layout.addView(button)
        return this
    }

    /**
     * add a new Button using default style
     *
     * @param text     title of button
     * @param listener OnButtonListener
     * @return this
     */
    fun addListenerButton(text: String?, listener: (Any, Any, Any) -> () -> Int): ColorPicker {
        if (mContext == null) return this
        val context = mContext.get() ?: return this
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(dip2px(10f, context), 0, 0, 0)
        /*Button button = new Button(context);
      button.setMinWidth(ColorUtils.INSTANCE.getDimensionDp(R.dimen.action_button_min_width, context));
      button.setMinimumWidth(ColorUtils.INSTANCE.getDimensionDp(R.dimen.action_button_min_width, context));
      button.setPadding(
              ColorUtils.INSTANCE.getDimensionDp(R.dimen.action_button_padding_horizontal, context) + ColorUtils.INSTANCE.dip2px(5, context), 0,
              ColorUtils.INSTANCE.getDimensionDp(R.dimen.action_button_padding_horizontal, context) + ColorUtils.INSTANCE.dip2px(5, context), 0);
     // button.setBackgroundResource(R.drawable.ic_circle_pressed);
      button.setTextSize(ColorUtils.INSTANCE.getDimensionDp(R.dimen.action_button_text_size, context));
      button.setTextColor(ContextCompat.getColor(context, R.color.black_de));

      button.setOnClickListener(new View.OnClickListener() {
         @Override
         public void onClick(View v) {
            listener.onClick(v, colorViewAdapter.getColorPosition(), colorViewAdapter.getColorSelected());
         }
      });
      button.setText(text);
      if (button.getParent() != null)
         buttons_layout.removeView(button);

      buttons_layout.addView(button);
      button.setLayoutParams(params);*/return this
    }

    /**
     * set if to dismiss the mDialog or not on button listener click, by default is set to true
     *
     * @param dismiss boolean
     * @return this
     */
    fun setDismissOnButtonListenerClick(dismiss: Boolean): ColorPicker {
        this.dismiss = dismiss
        return this
    }

    /**
     * set Match_parent to RecyclerView
     *
     * @return this
     */
    fun setDialogFullHeight(): ColorPicker {
        fullHeight = true
        return this
    }

    /**
     * getmDialog if you need more options
     *
     * @return CustomDialog
     */
    fun getmDialog(): CustomDialog? {
        return if (mDialog == null) null else mDialog!!.get()
    }

    /**
     * get the default PositiveButton
     *
     * @return Button
     */
    fun getPositiveButton(): Button {
        return positiveButton
    }

    /**
     * get the default NegativeButton
     *
     * @return Button
     */
    fun getNegativeButton(): Button {
        return negativeButton
    }

    /**
     * dismiss the mDialog
     */
    fun dismissDialog() {
        if (mDialog == null) return
        val dialog: Dialog? = mDialog!!.get()
        if (dialog != null && dialog.isShowing) {
            dialog.dismiss()
        }
    }

    /**
     * disables the postive and negative buttons
     *
     * @param disableDefaultButtons boolean
     * @return this
     */
    fun disableDefaultButtons(disableDefaultButtons: Boolean): ColorPicker {
        this.disableDefaultButtons = disableDefaultButtons
        return this
    }

    /**
     * set padding to the title in DP
     *
     * @param left   dp
     * @param top    dp
     * @param right  dp
     * @param bottom dp
     * @return this
     */
    fun setTitlePadding(left: Int, top: Int, right: Int, bottom: Int): ColorPicker {
        paddingTitleLeft = left
        paddingTitleRight = right
        paddingTitleTop = top
        paddingTitleBottom = bottom
        return this
    }

    /**
     * Set default colors defined in colorpicker-array.xml of the library
     *
     * @return this
     */
    private fun setColors(): ColorPicker {
        if (mContext == null) return this
        val context = mContext.get() ?: return this
        ta = context.resources.obtainTypedArray(R.array.default_colors)
        colors = ArrayList()
        for (i in 0 until ta!!.length()) {
            colors!!.add(ColorPal(ta!!.getColor(i, 0), false))
        }
        return this
    }

    private fun setMargin(left: Int, top: Int, right: Int, bottom: Int): ColorPicker {
        marginLeft = left
        marginRight = right
        marginTop = top
        marginBottom = bottom
        return this
    }

    /**
     * Constructor
     */
    init {
        dialogViewLayout = LayoutInflater.from(context).inflate(R.layout.color_palette_layout, null, false)
        dialogBaseLayout = dialogViewLayout.findViewById(R.id.colorpicker_base)
        recyclerView = dialogViewLayout.findViewById(R.id.color_palette)
        buttons_layout = dialogViewLayout.findViewById(R.id.buttons_layout)
        positiveButton = dialogViewLayout.findViewById(R.id.positive)
        negativeButton = dialogViewLayout.findViewById(R.id.negative)
        mContext = WeakReference(context)
        dismiss = true
        marginColorButtonBottom = 5
        marginColorButtonRight = marginColorButtonBottom
        marginColorButtonTop = marginColorButtonRight
        marginColorButtonLeft = marginColorButtonTop
        title = context.getString(R.string.colorpicker_dialog_title)
        negativeText = context.getString(R.string.colorpicker_dialog_cancel)
        positiveText = context.getString(R.string.colorpicker_dialog_ok)
        default_color = 0
        columns = 7
    }
}