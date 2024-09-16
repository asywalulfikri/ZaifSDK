package sound.recorder.widget.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import sound.recorder.widget.R

open class DataSession(private val mContext: Context) {

    companion object {
        const val TAG = "Preferences"
        lateinit var sharedPref: SharedPreferences
    }

    init {
        sharedPref = mContext.getSharedPreferences("recordingWidget", 0)
    }

    fun getShared(): SharedPreferences{
        return mContext.getSharedPreferences("recordingWidget", 0)
    }

    fun getSharedUpdate(): SharedPreferences {
        return mContext.getSharedPreferences("recordingWidget", 0)
    }

    fun getAnimation(): Boolean{
        return sharedPref.getBoolean(Constant.KeyShared.animation, false)
    }

    fun isCoverSong(): Boolean{
        return sharedPref.getBoolean("showSong", false)
    }

    fun getVersionCode(): Int{
        return sharedPref.getInt("versionCode", 0)
    }

    fun getVersionName(): String?{
        return sharedPref.getString("versionName", "")
    }

    fun getSplashScreenColor(): String?{
        return sharedPref.getString("backgroundSplashScreen","")
    }

    fun getAppName(): String?{
        return sharedPref.getString(Constant.KeyShared.appName,"")
    }

    fun getDeveloperName(): String?{
        return sharedPref.getString(Constant.KeyShared.developerName,"")
    }


    fun getShowNote(): Boolean{
        return sharedPref.getBoolean(Constant.KeyShared.showNote,false)
    }

    fun getShowSetting(): Boolean{
        return sharedPref.getBoolean(Constant.KeyShared.showSetting,false)
    }

    fun getBackgroundRecord(): String{
        return sharedPref.getString("llRecordBackground","#FFF9AA").toString()
    }

    fun getJsonName(): String?{
        return sharedPref.getString("jsonName","")
    }

    fun getVolume(): Int{
        return sharedPref.getInt(Constant.KeyShared.volume, 100)
    }

    fun setupAds(status : Boolean,admobId : String, bannerId : String, interstitialId : String,rewardInterstitialId : String,rewardId : String, nativeId : String){
        val editor = sharedPref.edit()
        editor.putBoolean("initiate", status)
        editor.putString(Constant.KeyShared.admobId, admobId)
        editor.putString(Constant.KeyShared.admobBannerId, bannerId)
        editor.putString(Constant.KeyShared.admobInterstitialId, interstitialId)
        editor.putString(Constant.KeyShared.admobRewardId,rewardId)
        editor.putString(Constant.KeyShared.admobRewardId,rewardInterstitialId)
        editor.putString(Constant.KeyShared.admobNativeId,nativeId)

        editor.apply()
    }

    fun setInfoApp(versionCode : Int,versionName : String,appId : String,appName: String, jsonName : String,splashScreenType: String,showNote: Boolean,showSong : Boolean, llRecordBackground : String){
        val editor = sharedPref.edit()
        editor.putInt("versionCode", versionCode)
        editor.putString("backgroundSplashScreen", splashScreenType)
        editor.putString("appName",appName)
        editor.putString("appId",appId)
        editor.putString("versionName",versionName)
        editor.putString("jsonName",jsonName)
        editor.putBoolean("showNote",showNote)
        editor.putBoolean("showSong",showSong)
        editor.putString("llRecordBackground",llRecordBackground)

        editor.apply()
    }

    fun saveVolumeAudio(volume :  Float){
        val editor = sharedPref.edit()
        editor.putFloat(Constant.KeyShared.volumeAudio,volume)
        editor.apply()
    }

    fun getVolumeAudio() : Float{
        return sharedPref.getFloat(Constant.KeyShared.volumeAudio,0.5f)
    }


    fun addColor(colorWidget : Int, colorRunningText: Int){
        val editor = sharedPref.edit()
        if(colorWidget!=0){
            editor.putInt(Constant.KeyShared.colorWidget, colorWidget)
        }
        if(colorRunningText!=0){
            editor.putInt(Constant.KeyShared.colorRunningText, colorRunningText)
        }
        editor.apply()
    }


    fun getLanguage(): String{
        return sharedPref.getString("languageCode","").toString()
    }

    fun getDefaultLanguage() : String{
        return sharedPref.getString("defaultLanguage","").toString()
    }

    fun getAppId(): String{
        return sharedPref.getString("appId","").toString()
    }

    fun getColorWidget(): Int{
        return sharedPref.getInt(Constant.KeyShared.colorWidget,R.color.color7)
    }

    fun getColorRunningText(): Int{
        return sharedPref.getInt(Constant.KeyShared.colorRunningText, R.color.white)
    }

    fun isContainSong(): Boolean {
        return sharedPref.getBoolean("addSong", false)
    }

    fun initiateSong(status: Boolean){
        val editor = sharedPref.edit()
        editor.putBoolean("addSong", status)
        editor.apply()
    }

    fun setLanguage(language : String){
        val editor = sharedPref.edit()
        editor.putString("languageCode",language)
        editor.apply()
    }

    fun saveColor(color : Int,name : String){
        val editor = sharedPref.edit()
        editor.putInt(name,color)
        editor.apply()
    }

    fun saveFirstLanguage(value : String){
        val editor = sharedPref.edit()
        editor.putString("firstLanguage",value)
        editor.apply()
    }

    fun saveDefaultLanguage(idLanguage : String){
        try {
            val editor = sharedPref.edit()
            editor.putString("defaultLanguage",idLanguage)
            editor.apply()
        }catch (e : Exception){
            setLog(e.message)
        }
    }

    fun saveAnimation(value : Boolean){
        val editor = sharedPref.edit()
        editor.putBoolean(Constant.KeyShared.animation,value)
        editor.apply()
    }

    fun saveVolume(value : Int){
        val editor = sharedPref.edit()
        editor.putInt(Constant.KeyShared.volume,value)
        editor.apply()
    }

    fun getBackgroundColor(): Int{
        return sharedPref.getInt(Constant.KeyShared.backgroundColor, -1)
    }


    fun resetBackgroundColor(){
        val editor = sharedPref.edit()
        editor.putInt(Constant.KeyShared.backgroundColor,-1)
        editor.apply()
    }


    fun updateBackgroundColor(color:Int){
        val editor = sharedPref.edit()
        editor.putInt(Constant.KeyShared.backgroundColor,color)
        editor.apply()
    }



    fun getBannerId(): String {
        return sharedPref.getString(Constant.KeyShared.admobBannerId, "").toString()
    }

    fun getBannerInMobi(): Long {
        return sharedPref.getLong(Constant.KeyShared.inMobiBannerId, 0)
    }

    fun getInMobiInterstitialId(): Long {
        return sharedPref.getLong(Constant.KeyShared.inMobiInterstitialId, 0)
    }

    fun getFanEnable(): Boolean{
        return sharedPref.getBoolean(Constant.KeyShared.fanEnable,false)
    }

    fun getStarAppEnable(): Boolean{
        return sharedPref.getBoolean(Constant.KeyShared.starAppEnable,false)
    }

    fun getStarAppShowBanner(): Boolean{
        return sharedPref.getBoolean(Constant.KeyShared.starAppShowBanner,false)
    }

    fun getStarAppShowInterstitial(): Boolean{
        return sharedPref.getBoolean(Constant.KeyShared.starAppShowInterstitial,false)
    }

    fun getInMobiEnable(): Boolean{
        return sharedPref.getBoolean(Constant.KeyShared.inMobiEnable,false)
    }


    fun getInMobiId(): String{
        return sharedPref.getString(Constant.KeyShared.inMobiId, "").toString()
    }

    fun getFANId(): String{
        return sharedPref.getString(Constant.KeyShared.fanId, "").toString()
    }

    fun getBannerFANId(): String{
        return sharedPref.getString(Constant.KeyShared.fanBannerId, "").toString()
    }

    fun getInterstitialFANId(): String{
        return sharedPref.getString(Constant.KeyShared.fanInterstitialId, "").toString()
    }

    fun getInterstitialId(): String {
        return sharedPref.getString(Constant.KeyShared.admobInterstitialId, "").toString()
    }

    fun getAppOpenId(): String {
        return sharedPref.getString(Constant.KeyShared.admobAppOpenId, "").toString()
    }

    fun getOrientationAds(): Int {
        return sharedPref.getInt(Constant.KeyShared.orientationAds, 2)
    }

    fun getRewardInterstitialId(): String {
        return sharedPref.getString(Constant.KeyShared.admobRewardInterstitialId, "").toString()
    }

    fun getRewardId(): String {
        return sharedPref.getString(Constant.KeyShared.admobRewardId, "").toString()
    }

    fun getStarAppId(): String{
        return sharedPref.getString(Constant.KeyShared.starAppId, "").toString()
    }

    fun getFirstLanguage(): String {
        return sharedPref.getString("firstLanguage", "").toString()
    }

    fun getNativeId(): String {
        return sharedPref.getString(Constant.KeyShared.admobNativeId, "").toString()
    }

    fun getAdmobId(): String {
        return sharedPref.getString(Constant.KeyShared.admobId, "").toString()
    }

    fun setLog(message : String?=null){
        Log.d("error",message.toString()+".")
    }
}
