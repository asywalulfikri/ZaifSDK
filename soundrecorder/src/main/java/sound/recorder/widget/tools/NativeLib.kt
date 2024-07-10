package sound.recorder.widget.tools

class NativeLib {

    /**
     * A native method that is implemented by the 'widget' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {
        // Used to load the 'widget' library on application startup.
        init {
            System.loadLibrary("widget")
        }
    }
}