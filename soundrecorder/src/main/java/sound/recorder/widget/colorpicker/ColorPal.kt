package sound.recorder.widget.colorpicker

class ColorPal(var color: Int, var isCheck: Boolean) {

    override fun equals(o: Any?): Boolean {
        return o is ColorPal && o.color == color
    }

}