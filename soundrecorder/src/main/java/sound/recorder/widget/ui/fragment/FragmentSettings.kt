package sound.recorder.widget.ui.fragment

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.AppCompatTextView
import androidx.navigation.fragment.findNavController
import sound.recorder.widget.BuildConfig
import sound.recorder.widget.R
import sound.recorder.widget.base.BaseFragmentWidget
import sound.recorder.widget.builder.ZaifSDKBuilder
import sound.recorder.widget.databinding.FragmentSettingBinding
import sound.recorder.widget.listener.MyAdsListener
import sound.recorder.widget.util.Constant
import sound.recorder.widget.util.DataSession
import sound.recorder.widget.util.Toastic
import sound.recorder.widget.util.dialog.DialogSDK
import java.util.Locale
import kotlin.let
import kotlin.text.isEmpty
import kotlin.text.isNotEmpty
import kotlin.text.lowercase
import kotlin.toString

open class FragmentSettings : BaseFragmentWidget() {

    private var binding: FragmentSettingBinding? = null
    var language = ""

    @SuppressLint("UseKtx")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSettingBinding.inflate(inflater, container, false)
        dataSession = DataSession(requireActivity())

        MyAdsListener.setHideAllBanner()

        requireActivity().onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                try {
                    MyAdsListener.setBannerHome(false)
                    findNavController().navigateUp()
                }catch (e : Exception){
                    setToast(e.message.toString())
                }
            }
        })
        binding?.let { binding ->

            binding.llRateUs.setOnClickListener {
                setLog("wkwkw ="+zaifSDKBuilder?.applicationId)
                val createChooser = Intent.createChooser(
                    Intent(
                        "android.intent.action.VIEW",
                        Uri.parse("https://play.google.com/store/apps/details?id=" + zaifSDKBuilder?.applicationId)
                    ), "Share via"
                )
                createChooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(createChooser)
            }

            binding.llHelp.setOnClickListener {
                showDialogEmail(dataSession.getAppName().toString(),getInfo())

            }

            binding.llMoreApps.setOnClickListener {
                openPlayStoreForMoreApps(zaifSDKBuilder?.developerName)
            }


            binding.llAddSong.setOnClickListener {
                try {
                    val dialog = DialogSDK(requireContext(), Constant.DialogType.ADD_SONG) {
                        // clickAction (it)
                    }
                    dialog.show(parentFragmentManager,  Constant.DialogType.toString())
                } catch (e: Exception) {
                    setToastTic(Toastic.ERROR, e.message.toString())
                }
            }

            binding.llWhatsapp.visibility = View.GONE
            binding.llWhatsapp.setOnClickListener {
                openWhatsApp()
            }


            binding.llLanguage.visibility = View.GONE
            binding.llLanguage.setOnClickListener {
                showDialogLanguage()
            }

            binding.llShareWithFriends.setOnClickListener {
                val shareIntent = Intent(Intent.ACTION_SEND)
                shareIntent.type = "text/plain"
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, dataSession.getAppId())
                val shareMessage =
                    "Let me recommend you this application\n\nhttps://play.google.com/store/apps/details?id="+zaifSDKBuilder?.applicationId
                shareIntent.putExtra(Intent.EXTRA_TEXT, shareMessage)
                startActivity(Intent.createChooser(shareIntent, requireActivity().getString(R.string.choose_one)))
            }


        }

        return binding?.root
    }

    @SuppressLint("UseKtx")
    private fun openWhatsApp() {
        val phoneNumber = "6285158829034" // Nomor telepon tujuan dengan kode negara
        val message = "Bellyra"+" " + getString(R.string.help_suggest)

        // Format URL untuk membuka WhatsApp dengan nomor telepon dan pesan tertentu
        val url = "https://api.whatsapp.com/send?phone=$phoneNumber&text=${Uri.encode(message)}"

        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // WhatsApp tidak terinstall, atau tidak ada aplikasi yang bisa menangani intent ini
            Toast.makeText(activity, "WhatsApp Not Found", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }


    private fun getInfo(): String {
        val appInfo = "VC" + dataSession.getVersionCode()
        val androidVersion = "SDK" + Build.VERSION.SDK_INT
        val androidOS = "OS" + Build.VERSION.RELEASE

        return Build.MANUFACTURER + " " + Build.MODEL + " , " + androidOS + ", " + appInfo + ", " + androidVersion
    }



    @SuppressLint("SetTextI18n")
    fun showDialogLanguage() {

        // custom dialog
        var type = ""
        val dialog = Dialog(requireActivity())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_choose_language)
        dialog.setCancelable(true)

        // set the custom dialog components - text, image and button
        val rbIndonesia = dialog.findViewById<View>(R.id.rbDefaultLanguage) as RadioButton
        rbIndonesia.text = "Indonesia"
        val rbEnglish = dialog.findViewById<View>(R.id.rbEnglish) as RadioButton
        val btnSave = dialog.findViewById<View>(R.id.btn_submit) as AppCompatTextView



        if(language.isEmpty()){
            if(getCurrentLanguage().lowercase()=="indonesia"){
                rbIndonesia.isChecked = true
            }else{
                rbEnglish.isChecked = true
            }
        }else{
            if(dataSession.getLanguage() =="en"){
                rbEnglish.isChecked = true
            }else{
                rbIndonesia.isChecked = true
            }
        }


        // if button is clicked, close the custom dialog
        btnSave.setOnClickListener {

            if(rbIndonesia.isChecked){
                type = "id"
            }

            if(rbEnglish.isChecked){
                type = "en"
            }


            if(type.isNotEmpty()){
                dataSession.setLanguage(type)
                changeLanguage(type)
            }

            dialog.dismiss()
        }

        dialog.show()
    }

   /* private fun changeLanguage(type : String) {
        val locale = Locale(type)
        Locale.setDefault(locale)

        val configuration = Configuration()
        configuration.locale = locale

        resources.updateConfiguration(configuration, resources.displayMetrics)
        requireActivity().recreate()
    }*/


    private fun changeLanguage(type: String) {
        val locale = Locale.forLanguageTag(type)
        Locale.setDefault(locale)

        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        // update resource ke Activity
        requireActivity().resources.updateConfiguration(
            config,
            requireActivity().resources.displayMetrics
        )

        requireActivity().recreate()   // refresh UI
    }




    private fun getCurrentLanguage(): String {
        val locale: Locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            this.resources.configuration.locales[0]
        } else {
            this.resources.configuration.locale
        }
        return locale.displayLanguage
    }


}