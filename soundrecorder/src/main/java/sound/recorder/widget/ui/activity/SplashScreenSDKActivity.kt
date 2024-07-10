package sound.recorder.widget.ui.activity

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.TextView
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.gson.Gson
import org.json.JSONObject
import sound.recorder.widget.R
import sound.recorder.widget.base.BaseActivityWidget
import sound.recorder.widget.databinding.ActivitySplashSdkBinding
import sound.recorder.widget.model.MenuConfig
import sound.recorder.widget.util.DataSession


@SuppressLint("CustomSplashScreen")
class SplashScreenSDKActivity : BaseActivityWidget() {

    private lateinit var binding: ActivitySplashSdkBinding
    private var jsonName = ""
    private var currentVersionCode : Int? =null
    private var dataSession : DataSession? =null

    @SuppressLint("NotifyDataSetChanged", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashSdkBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    @SuppressLint("SetTextI18n")
    fun updateData(){
        FirebaseApp.initializeApp(this)

        dataSession = DataSession(this)
        jsonName = dataSession?.getJsonName().toString()


        if(dataSession?.getSplashScreenColor().toString().isNotEmpty()){
            binding.backgroundSplash.setBackgroundColor(Color.parseColor(dataSession?.getSplashScreenColor()))
        }

        if(dataSession?.getAppName().toString().isNotEmpty()){
            binding.tvTitle.text = dataSession?.getAppName() + "\n"+ "v "+ dataSession?.getVersionName().toString()
        }

        currentVersionCode = dataSession?.getVersionCode()

        if(dataSession?.getJsonName().toString().isNotEmpty()){
            if(dataSession?.getVersionCode()!=0||dataSession?.getVersionCode()!=null){
                checkVersion()
            }else{
                goToNextPage()
            }
        }else{
            goToNextPage()
        }
    }

    override fun onStart() {
        super.onStart()
        updateData()
    }

    override fun onResume() {
        super.onResume()

    }


    private fun checkVersion() {
        val mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(10)
            .setFetchTimeoutInSeconds(1)
            .build()
        mFirebaseRemoteConfig.setConfigSettingsAsync(configSettings)
        mFirebaseRemoteConfig.fetchAndActivate()
            .addOnCompleteListener(this) { task: Task<Boolean?> ->
                if (task.isSuccessful) {
                    val json = mFirebaseRemoteConfig.getString(jsonName)
                    try {
                        JSONObject(json)
                        val menuConfig = Gson().fromJson(json, MenuConfig::class.java)
                        if(menuConfig==null){
                            Log.d("value_json", "empty")
                            goToNextPage()
                        }else{
                            Log.d("value_json", Gson().toJson(menuConfig) + "---"+jsonName)
                            checkVersionSuccess(menuConfig)
                        }

                    } catch (e: Exception) {
                        goToNextPage()
                    }

                }else{
                    Log.d("value_json", task.exception?.message.toString() +"---"+jsonName)
                    goToNextPage()
                }
            }

    }

    private fun checkVersionSuccess(checkVersionResponse: MenuConfig) {
        val currentVersion = currentVersionCode
        val latestVersion = checkVersionResponse.versionCode
        val force = checkVersionResponse.forceUpdate
        val maintenance = checkVersionResponse.maintenance
        val showDialog = checkVersionResponse.showDialog

        Log.d("infoSDK",
            "App version code now = $currentVersion , App version code live = $latestVersion"
        )

        if(showDialog==true){
            if(maintenance==true){
                showUpdateDialog(getString(R.string.dialog_maintenance))
            }else{

                if(force==true){
                    //Kalau force update nya false
                    if (isLatestVersion(currentVersion!!, latestVersion!!)) {
                        goToNextPage()
                    }else{
                        showUpdateDialog(getString(R.string.dialog_msg_update_app))
                    }
                } else {
                    if(currentVersion!!<latestVersion!!){
                        showUpdateDialog(getString(R.string.dialog_msg_update_app_version))
                    }else{
                        goToNextPage()
                    }
                }
            }
        }else{
            goToNextPage()
        }

    }

    private fun isLatestVersion(currentVersion: Int, latestVersion: Int): Boolean {
        return currentVersion >= latestVersion
    }

    @SuppressLint("SetTextI18n")
    private fun showUpdateDialog(message : String) {

        // custom dialog
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_alert_update)
        dialog.setCancelable(false)

        // set the custom dialog components - text, image and button
        val tvMessage = dialog.findViewById<View>(R.id.tv_message) as TextView
        val btnPrimary = dialog.findViewById<View>(R.id.btn_primary) as Button
        val btnCancel = dialog.findViewById<View>(R.id.btn_cancel) as Button
        tvMessage.text = message

        // if button is clicked, close the custom dialog
        btnPrimary.setOnClickListener {
            if(message==getString(R.string.dialog_maintenance)){
                val intent = Intent()
                intent.putExtra("exit",true)
                setResult(RESULT_OK,intent)
                finish()
            }else{
                gotoPlayStore()
            }
        }

        if(message==getString(R.string.dialog_msg_update_app_version)){
            btnCancel.visibility = View.VISIBLE
        }else if(message==getString(R.string.dialog_maintenance)){
            btnPrimary.text = "Exit"
        }
        btnCancel.setOnClickListener {
            dialog.dismiss()
            goToNextPage()
        }
        dialog.show()
    }

    private fun gotoPlayStore() {
        val appPackageName = packageName
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appPackageName")))
        } catch (activityNotFoundException: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")))
        }
    }

    private fun goToNextPage(){
        val intent = Intent()
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        setResult(RESULT_OK,intent)
        finish()
    }
}