package sound.recorder.widget.ui.bottomSheet

import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.view.WindowCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import sound.recorder.widget.RecordingSDK
import sound.recorder.widget.databinding.BottomSheetSettingBinding
import sound.recorder.widget.util.Constant
import sound.recorder.widget.util.DataSession


class BottomSheetSetting : BottomSheetDialogFragment(),SharedPreferences.OnSharedPreferenceChangeListener {

    private var binding : BottomSheetSettingBinding? = null
    private var sharedPreferences : SharedPreferences? =null

    companion object {
        fun newInstance(): BottomSheetDialogFragment {
            return BottomSheetDialogFragment()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): RelativeLayout? {
        binding = BottomSheetSettingBinding.inflate(layoutInflater)

        if(activity!=null&&context!=null){
            (dialog as? BottomSheetDialog)?.behavior?.state = STATE_EXPANDED
            (dialog as? BottomSheetDialog)?.behavior?.isDraggable = false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                dialog?.window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
            } else {
                @Suppress("DEPRECATION")
                dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }


            sharedPreferences = DataSession(requireActivity()).getShared()
            sharedPreferences?.registerOnSharedPreferenceChangeListener(this)

            binding?.layoutBackground?.setOnClickListener {
                RecordingSDK.showDialogColorPicker(requireActivity())
                dismiss()
            }

            binding?.btnColor?.setBackgroundColor(DataSession(requireActivity()).getBackgroundColor())

            binding?.cbAnimation?.isChecked = DataSession(requireActivity()).getAnimation()

            binding?.cbAnimation?.setOnCheckedChangeListener { _, b ->
                if (b) {
                    DataSession(requireActivity()).saveAnimation(true)
                } else {
                    DataSession(requireActivity()).saveAnimation(false)
                }
            }

            binding?.rlAnimation?.setOnClickListener {
                if(binding?.cbAnimation?.isChecked == true){
                    binding?.cbAnimation?.isChecked = false
                    DataSession(requireActivity()).saveAnimation(false)
                }else{
                    binding?.cbAnimation?.isChecked = true
                    DataSession(requireActivity()).saveAnimation(true)
                }
            }

            setupSeekBar()
            binding?.seekBar?.progress = DataSession(requireActivity()).getVolume()

        }
        return binding?.root

    }

    private fun setupSeekBar(){
        binding?.seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                DataSession(requireActivity()).saveVolume(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {

            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {

            }
        })
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if(key== Constant.KeyShared.backgroundColor){
            sharedPreferences?.let {
                it.getInt(Constant.KeyShared.backgroundColor,-1)
                    .let { it1 -> binding?.btnColor?.setBackgroundColor(it1) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }


}