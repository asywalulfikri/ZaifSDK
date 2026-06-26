package recording.host

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import recording.host.databinding.FragmentHomeBinding
import sound.recorder.widget.base.BaseFragmentWidget
import sound.recorder.widget.listener.MyAdsListener
import sound.recorder.widget.music.MusicListDialogHelper
import sound.recorder.widget.music.RequestSongDialog
import sound.recorder.widget.music.MusicPlayerManager
import sound.recorder.widget.ui.dialog.KritikSaranDialog
import sound.recorder.widget.ui.dialog.ReportBugDialog
import sound.recorder.widget.util.Constant
import sound.recorder.widget.util.NetworkUtils
import sound.recorder.widget.util.Toastic
import sound.recorder.widget.util.dialog.DialogSDK
import kotlin.toString

class HomeFragment : BaseFragmentWidget() {

    private var binding: FragmentHomeBinding? =null

    companion object {
        fun newInstance(): HomeFragment {
            return HomeFragment()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): RelativeLayout? {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding?.root

    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        onViewAds(false)
        setupButtonClick()
        setupOnBackPressed()


    }

    private fun setupOnBackPressed() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { requireActivity().finishAffinity() }
        })
    }
    private fun setupButtonClick(){

        binding?.btnNext?.setOnClickListener {
            try {

                (activity as? GameActivity)?.showInterstitialIfAllowed(true) {

                    if (!isAdded) return@showInterstitialIfAllowed
                    MyAdsListener.setBanner(true)
                    findNavController().navigate(R.id.action_home_fragment_to_dholak_fragment)
                }

            } catch (e: Exception) {
                setToast(e.message.toString())
            }
        }

        binding?.btnSetting?.setOnClickListener {
            try {
                MyAdsListener.setBanner(false)
                findNavController().navigate(R.id.action_home_fragment_to_setting_fragment)
            } catch (e: Exception) {
                setToastTic(Toastic.ERROR, e.message.toString())
            }
        }

        binding?.btnRating?.setOnClickListener {
            try {

                if (!dataSession.isDoneRating()) {
                    DialogSDK.newInstance(
                        Constant.DialogType.RATING
                    ) { result ->
                        dataSession.saveDoneRating(result)
                        requireActivity().finishAffinity()
                    }.show(parentFragmentManager, "rating_dialog")
                } else {
                    requireActivity().finishAffinity()
                }


            } catch (e: Exception) {
                setToastTic(Toastic.ERROR, e.message.toString())
            }
        }


        binding?.btnVideo?.setOnClickListener {
            NetworkUtils.isInternetConnected(requireContext()) { isConnected ->
                if (isConnected) {
                    try {
                        MyAdsListener.setBanner(false)
                        findNavController().navigate(R.id.action_home_fragment_to_video_fragment)
                    } catch (e: Exception) {
                        setToastTic(Toastic.ERROR,e.message.toString())
                    }
                } else {
                    setToastTic(Toastic.WARNING,getString(sound.recorder.widget.R.string.no_internet_connection))
                }
            }
        }

        binding?.btnListBug?.setOnClickListener {
            NetworkUtils.isInternetConnected(requireContext()) { isConnected ->
                if (isConnected) {
                    try {
                        MyAdsListener.setBanner(false)
                        findNavController().navigate(R.id.action_home_fragment_to_list_bug_fragment)
                    } catch (e: Exception) {
                        setToastTic(Toastic.ERROR,e.message.toString())
                    }
                } else {
                    setToastTic(Toastic.WARNING,getString(sound.recorder.widget.R.string.no_internet_connection))
                }
            }
        }

        binding?.btnRequestSong?.setOnClickListener {
            RequestSongDialog().show(requireContext())
        }

        binding?.btnBugReport?.setOnClickListener {
            KritikSaranDialog().show(requireContext())
        }

        binding?.btnAdminView?.setOnClickListener {
            showAdminViewDialog()
        }

    }

    private fun showAdminViewDialog() {
        val options = arrayOf("Bug Reports", "Song Request", "Note Promotion","Kritik Saran")
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Admin Panel")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> navigateSafe(R.id.action_home_fragment_to_list_bug_fragment)
                    1 -> navigateSafe(R.id.action_home_fragment_to_song_request_admin)
                    2 -> navigateSafe(R.id.action_home_fragment_to_setting_fragment)
                    3 -> navigateSafe(R.id.action_home_fragment_to_kritiksaran_admin)
                }
            }
            .show()
    }

    private fun navigateSafe(actionId: Int) {
        try {
            MyAdsListener.setBanner(false)
            findNavController().navigate(actionId)
        } catch (e: Exception) {
            setToastTic(Toastic.ERROR, e.message.toString())
        }
    }

    fun onViewAds(show : Boolean){
        MyAdsListener.setBanner(show)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onResume() {
        super.onResume()
        registerMusicAsync()
    }


    /*private fun registerMusicAsync() {
        // Gunakan lifecycleScope agar coroutine otomatis batal jika Fragment mati (mencegah leak)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val rawList = listOf(
                R.raw.ampar_ampar_pisang to "Ampar Ampar Pisang"
            )

            val tracks = rawList.map { (resId, title) ->
                MusicPlayerManager.MusicTrack(
                    title = title,
                    duration = getRawDurationSafe(resId),
                    isRaw = true,
                    rawResId = resId
                )
            }

            // Kembali ke Main Thread HANYA untuk meregister datanya
            withContext(Dispatchers.Main) {
                if (isAdded) {
                    MusicListDialogHelper.registerRawTracks(tracks)
                }
            }
        }
    }*/

    private fun registerMusicAsync() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val rawList = listOf(
                R.raw.ampar_ampar_pisang to "Ampar Ampar Pisang"
            )

            val tracks = rawList.map { (resId, title) ->
                MusicPlayerManager.MusicTrack(
                    title = title,
                    duration = getRawDurationSafe(resId),
                    isRaw = true,
                    rawResId = resId,
                    // 🔥 KRUSIAL: Simpan resId sebagai String di field path
                    // Agar saat direkam, database menyimpan angka ID ini.
                )
            }

            withContext(Dispatchers.Main) {
                if (isAdded) {
                    MusicListDialogHelper.registerRawTracks(tracks)
                }
            }
        }
    }


}
