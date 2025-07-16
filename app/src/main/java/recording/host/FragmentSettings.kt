package recording.host

import android.annotation.SuppressLint
import android.app.Dialog
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
import androidx.appcompat.widget.AppCompatTextView
import sound.recorder.widget.R
import sound.recorder.widget.base.BaseFragmentWidget
import sound.recorder.widget.databinding.FragmentSettingBinding
import sound.recorder.widget.util.DataSession
import java.util.Locale
import kotlin.let
import kotlin.text.isEmpty
import kotlin.text.isNotEmpty
import kotlin.text.lowercase

open class FragmentSettings : BaseFragmentWidget() {

    private var binding: FragmentSettingBinding? = null
    private var type = ""
    var language = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSettingBinding.inflate(inflater, container, false)
        dataSession = DataSession(requireActivity())

        binding?.let { binding ->

            binding.llRateUs.setOnClickListener {
                val createChooser = Intent.createChooser(
                    Intent(
                        "android.intent.action.VIEW",
                        Uri.parse("https://play.google.com/store/apps/details?id=" + BuildConfig.APPLICATION_ID)
                    ), "Share via"
                )
                createChooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(createChooser)
            }

            binding.llHelp.setOnClickListener {
                showDialogEmail("Melodica",getInfo())

            }


            binding.llLanguage.visibility = View.GONE
            binding.llLanguage.setOnClickListener {
                showDialogLanguage()
            }

            binding.llWhatsapp.visibility =View.GONE

            binding.llShareWithFriends.setOnClickListener {
                val shareIntent = Intent(Intent.ACTION_SEND)
                shareIntent.type = "text/plain"
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, dataSession?.getAppName())
                val shareMessage =
                    "Let me recommend you this application\n\nhttps://play.google.com/store/apps/details?id="+dataSession?.getAppId()
                shareIntent.putExtra(Intent.EXTRA_TEXT, shareMessage)
                startActivity(Intent.createChooser(shareIntent, requireActivity().getString(R.string.choose_one)))
            }

            binding.llMoreApps.setOnClickListener {
                openPlayStoreForMoreApps("sayunara+dev")
            }


        }

        return binding?.root
    }


    private fun getInfo(): String {
        val appInfo = "VC" + BuildConfig.VERSION_CODE
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
        val rbEnglish = dialog.findViewById<View>(R.id.rbEnglish) as RadioButton
        val btnSave = dialog.findViewById<View>(R.id.btn_submit) as AppCompatTextView



        if(language.isEmpty()){
            if(getCurrentLanguage().lowercase()=="indonesia"){
                rbIndonesia.isChecked = true
            }else{
                rbEnglish.isChecked = true
            }
        }else{
            if(dataSession?.getLanguage()=="en"){
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
                dataSession?.setLanguage(type)
                changeLanguage(type)
            }

            dialog.dismiss()
        }

        dialog.show()
    }

    private fun changeLanguage() {
        val locale = Locale(type) // Ganti "en" dengan kode bahasa yang diinginkan
        Locale.setDefault(locale)

        val configuration = Configuration()
        configuration.locale = locale

        resources.updateConfiguration(configuration, resources.displayMetrics)
        requireActivity().recreate()
    }


    private fun changeLanguage(type : String) {
        val locale = Locale(type) // Ganti "en" dengan kode bahasa yang diinginkan
        Locale.setDefault(locale)

        val configuration = Configuration()
        configuration.locale = locale

        resources.updateConfiguration(configuration, resources.displayMetrics)
        requireActivity().recreate()
    }


    fun onBackPressed(): Boolean {
        activity?.supportFragmentManager?.beginTransaction()?.remove(this)?.commit()
        return false
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