package sound.recorder.widget.ads


import sound.recorder.widget.builder.AdmobSDKBuilder

interface AdConfigProvider {
    fun getAdmobBuilder(): AdmobSDKBuilder?
}