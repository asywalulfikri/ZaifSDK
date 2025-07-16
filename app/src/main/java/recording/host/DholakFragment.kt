package recording.host

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import recording.host.databinding.ActivityDholakBinding
import sound.recorder.widget.listener.MusicListener
import sound.recorder.widget.listener.MyAdsListener
import sound.recorder.widget.listener.MyMusicListener
import sound.recorder.widget.ui.viewmodel.MusicViewModel
import sound.recorder.widget.util.Constant
import sound.recorder.widget.util.DataSession
import sound.recorder.widget.util.ProgressDialogUtil
import kotlin.toString

class DholakFragment : BaseFragment(), MusicListener,SharedPreferences.OnSharedPreferenceChangeListener{

    private var binding : ActivityDholakBinding? =null

    private val viewModel: MusicViewModel      by activityViewModels()

    private val soundViewModel: SoundViewModel by activityViewModels()

    private var volumeAudio: Float = 1.0f

    private var type = "dholak"



    companion object {
        fun newInstance(): DholakFragment{
            return DholakFragment()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = ActivityDholakBinding.inflate(inflater,container,false)
        return binding?.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if(activity !=null&& context !=null){
            permissionSong()
            observeViewModel()
            myAnim = AnimationUtils.loadAnimation(activity, R.anim.button)

            requireActivity().onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    try {
                        val consumed = onBackPressed()
                        if (!consumed) {
                            isEnabled = false
                            MyAdsListener.setBannerHome(false)
                            MyAdsListener.setOnShowInterstitial()
                            findNavController().navigateUp()
                        }
                    }catch (e : Exception){
                        setToast(e.message.toString())
                    } }
            })
            try {
                sharedPreferences = DataSession(requireContext()).getShared()
                sharedPreferences?.registerOnSharedPreferenceChangeListener(this)

                volumeAudio = sharedPreferences?.getFloat(Constant.KeyShared.volumeAudio, 1.0f) ?: 1.0f
                soundViewModel.setVolume(volumeAudio)


            }catch (e : Exception){
                setLog(e.message.toString())
            }

            val background = DataSession(requireActivity()).getBackgroundColor()
            if (background != -1) {
                binding?.layoutBackground?.setBackgroundColor(getSharedPreferenceUpdate().getInt(
                    Constant.KeyShared.backgroundColor, -1))
            }


            try {
                initAnim(binding?.ivStop)
            } catch (e: Exception) {
                setLog(e.message.toString())
            }

            MyMusicListener.setMyListener(this)

            buttonClick()


            viewModel.isPlaying.observe(viewLifecycleOwner) { isPlaying->
                if(isPlaying){
                    binding?.ivStop?.visibility = View.VISIBLE
                    startAnimation()
                }else{
                    stopAnimation()
                }
            }

            binding?.ivStop?.setOnClickListener {
                try {
                    viewModel.stopMusic()
                } catch (e: Exception) {
                    setToastError(activity, e.message.toString())
                }
            }

        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buttonClick(){


        binding?.rlDholak1?.setOnTouchListener { _, motionEvent ->
            binding?.rlDholak1?.startAnimation(myAnim)
            if (motionEvent == null || motionEvent.action == MotionEvent.ACTION_DOWN) {
                soundViewModel.playSound(type, "dholak1")
            }
            true
        }


        binding?.dohakTengah1?.setOnTouchListener { _, motionEvent ->
            binding?.rlDholak1?.startAnimation(myAnim)
            if (motionEvent == null || motionEvent.action == MotionEvent.ACTION_DOWN) {
                soundViewModel.playSound(type, "dholak2")
            }
            true
        }


        binding?.dohakAtas1?.setOnTouchListener { _, motionEvent ->
            binding?.rlDholak1?.startAnimation(myAnim)
            if (motionEvent == null || motionEvent.action == MotionEvent.ACTION_DOWN) {
                soundViewModel.playSound(type, "dholak3")
            }
            true
        }

        binding?.dohakKiri1?.setOnTouchListener { _, motionEvent ->
            binding?.rlDholak1?.startAnimation(myAnim)
            if (motionEvent == null || motionEvent.action == MotionEvent.ACTION_DOWN) {
                soundViewModel.playSound(type, "dholak4")
            }
            true
        }


        binding?.dohakKanan1?.setOnTouchListener { _, motionEvent ->
            binding?.rlDholak1?.startAnimation(myAnim)
            if (motionEvent == null || motionEvent.action == MotionEvent.ACTION_DOWN) {
                soundViewModel.playSound(type, "dholak5")
            }
            true
        }

        binding?.dohakBawah1?.setOnTouchListener { _, motionEvent ->
            binding?.rlDholak1?.startAnimation(myAnim)
            if (motionEvent == null || motionEvent.action == MotionEvent.ACTION_DOWN) {
                soundViewModel.playSound(type, "dholak6")
            }
            true
        }


        binding?.rlDholak2?.setOnTouchListener { _, motionEvent ->
            binding?.rlDholak2?.startAnimation(myAnim)
            if (motionEvent == null || motionEvent.action == MotionEvent.ACTION_DOWN) {
                soundViewModel.playSound(type, "dholak7")
            }
            true
        }

        binding?.dohakTengah2?.setOnTouchListener { _, motionEvent ->
            binding?.rlDholak2?.startAnimation(myAnim)
            if (motionEvent == null || motionEvent.action == MotionEvent.ACTION_DOWN) {
                soundViewModel.playSound(type, "dholak8")
            }
            true
        }



        binding?.dohakAtas2?.setOnTouchListener { _, motionEvent ->
            binding?.rlDholak2?.startAnimation(myAnim)
            if (motionEvent == null || motionEvent.action == MotionEvent.ACTION_DOWN) {
                soundViewModel.playSound(type, "dholak9")
            }
            true
        }

        binding?.dohakKiri2?.setOnTouchListener { _, motionEvent ->
            binding?.rlDholak2?.startAnimation(myAnim)
            if (motionEvent == null || motionEvent.action == MotionEvent.ACTION_DOWN) {
                soundViewModel.playSound(type, "dholak10")
            }
            true
        }



        binding?.dohakKanan2?.setOnTouchListener { _, motionEvent ->
            binding?.rlDholak2?.startAnimation(myAnim)
            if (motionEvent == null || motionEvent.action == MotionEvent.ACTION_DOWN) {
                soundViewModel.playSound(type, "dholak11")
            }
            true
        }



        binding?.dohakBawah2?.setOnTouchListener { _, motionEvent ->
            binding?.rlDholak2?.startAnimation(myAnim)
            if (motionEvent == null || motionEvent.action == MotionEvent.ACTION_DOWN) {
                soundViewModel.playSound(type, "dholak12")
            }
            true
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        releaseAll()
        super.onDestroy()
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    fun onBackPressed(): Boolean {
        val ret = false
        return  ret
    }

    private fun releaseAll(){
        try {
            MyMusicListener.setMyListener(null)
        } catch (e: Exception) {
            setLog(e.message.toString())
        }
    }

    private fun startAnimation() {
        binding?.ivStop?.startAnimation(mPanAnim)
    }

    private fun stopAnimation() {
        try {
            binding?.ivStop?.clearAnimation()
            binding?.ivStop?.visibility = View.GONE
        } catch (e: Exception) {
            setLog(e.message.toString())
        }
    }

    override fun onVolumeAudio(volume: Float?) {
        soundViewModel.setVolume(volume)
    }

    override fun onResume() {
        super.onResume()
        sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun getSharedPreferenceUpdate(): SharedPreferences {
        return DataSession(requireContext()).getSharedUpdate()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == Constant.KeyShared.backgroundColor) {
            binding?.layoutBackground?.setBackgroundColor(getSharedPreferenceUpdate().getInt(Constant.KeyShared.backgroundColor, -1))
        }
    }

    @SuppressLint("SetTextI18n")
    private fun observeViewModel() {
        try {
            ProgressDialogUtil.show(requireContext())

            soundViewModel.loadingProgress.observe(viewLifecycleOwner) { progress ->
                ProgressDialogUtil.update(progress)
            }

            soundViewModel.isAllSoundsLoaded.observe(viewLifecycleOwner) { allLoaded ->
                if (allLoaded && isAdded) {
                    ProgressDialogUtil.dismiss()
                }
            }
        }catch (e : Exception){
            println(e.message)
        }
    }
}