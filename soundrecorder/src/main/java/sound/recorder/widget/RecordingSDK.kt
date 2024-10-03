package sound.recorder.widget

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import org.greenrobot.eventbus.EventBus
import sound.recorder.widget.colorpicker.ColorPicker
import sound.recorder.widget.colorpicker.ColorPicker.OnChooseColorListener
import sound.recorder.widget.model.Song
import sound.recorder.widget.util.Constant
import sound.recorder.widget.util.DataSession


object RecordingSDK {

    private fun initSdkRecording(ctx: Context,admobId : String,bannerId: String,interstitialId: String,rewardInterstitialId : String,rewardId : String,nativeId :String) {
        DataSession(ctx).setupAds(true,admobId,bannerId,interstitialId,rewardInterstitialId,rewardId,nativeId)
        DataSession(ctx).initiateSong(false)
    }


    fun initSdkColor(context: Context,colorWidget : Int,colorRunningText: Int) {
        DataSession(context).addColor(colorWidget,colorRunningText)
    }


    /*fun initSdk(context: Context,admobId: String, bannerId: String, interstitialId: String, rewardInterstitialId : String,rewardId : String, nativeId : String): RecordingSDK {

        initSdkRecording(
            context,
            admobId,
            bannerId,
            interstitialId,
            rewardInterstitialId,
            rewardId,
            nativeId

        )
        return this
    }*/
    fun run(): RecordingSDK {
        return this
    }

    fun addSong(context: Context,listSong :ArrayList<Song>){
        DataSession(context).initiateSong(true)
        EventBus.getDefault().postSticky(listSong)
    }

    /*fun addInfo(context: Context,versionCode : Int,versionName : String, appId : String,appName : String,jsonName : String,backgroundSplashScreen : String, isNote : Boolean,showSong : Boolean, llRecordBackground : String){
        DataSession(context).setInfoApp(versionCode,versionName,appId,appName,jsonName,backgroundSplashScreen,isNote,showSong,llRecordBackground)
    }*/

    fun isHaveSong(context: Context): Boolean{
        return DataSession(context).isContainSong()
    }


    @SuppressLint("QueryPermissionsNeeded")
    fun openEmail(context: Context, subject : String, body : String){
        try {
            val email = Intent(Intent.ACTION_SEND)
            email.setType("message/rfc822")
                .putExtra(Intent.EXTRA_EMAIL, arrayOf("feedbackmygame@gmail.com"))
                .putExtra(Intent.EXTRA_SUBJECT, subject)
                .putExtra(Intent.EXTRA_TEXT, body)
                .setPackage("com.google.android.gm")
            context.startActivity(email)
        }catch (e :Exception){
            Log.d("yamete",e.message.toString())
            Toast.makeText(context,e.message.toString(), Toast.LENGTH_SHORT).show()
        }
    }

    fun showDialogColorPicker(context: Context) {
        try {
            val colorPicker = ColorPicker(context as Activity)
            val colors: ArrayList<String> = ArrayList()
            colors.add("#82B926")
            colors.add("#a276eb")
            colors.add("#6a3ab2")
            colors.add("#666666")
            colors.add("#FFFF00")
            colors.add("#3C8D2F")
            colors.add("#FA9F00")
            colors.add("#FF0000")

            colorPicker
                .setColumns(7)
                .setRoundColorButton(true)
                .setOnChooseColorListener(object : OnChooseColorListener {
                    override fun onChooseColor(position: Int, color: Int) {

                        if (color != 0) {
                            DataSession(context).saveColor(
                                color,
                                Constant.KeyShared.backgroundColor
                            )
                        }
                    }

                    override fun onCancel() {

                    }
                })
                .addListenerButton(
                    "newButton"
                ) { _, position, _ ->
                    {
                        Log.d("position", "" + position)
                    }
                }.show()

        } catch (e: Exception) {

            Log.d("error",e.message.toString())

        }
    }



}