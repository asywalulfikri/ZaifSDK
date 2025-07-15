package sound.recorder.widget.ads


import sound.recorder.widget.builder.AdmobSDKBuilder
import sound.recorder.widget.builder.FanSDKBuilder
import sound.recorder.widget.builder.UnitySDKBuilder

public interface AdConfigProvider {
    fun getAdmobBuilder(): AdmobSDKBuilder?
    fun getFanBuilder(): FanSDKBuilder?
    fun getUnityBuilder(): UnitySDKBuilder?
}