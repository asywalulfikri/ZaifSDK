package recording.host

import android.os.Bundle
import androidx.navigation.fragment.NavHostFragment
import recording.host.databinding.ActivityGameBinding
import sound.recorder.widget.base.BaseActivityWidget

class ActivityGame : BaseActivityWidget(){
    private lateinit var binding: ActivityGameBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupHideStatusBar(binding.root,false)

        // Menyiapkan NavHostFragment dan Navigation Controller
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        val navController = navHostFragment.navController

        // Menyiapkan NavGraph
        val navGraph = navController.navInflater.inflate(R.navigation.nav_game)

        // Menambahkan data ke Bundle untuk dikirim ke Fragment awal
        val bundle = Bundle().apply {
            putBoolean("isSearch",true)
        }

        // Atur fragment awal dalam NavGraph
        navGraph.setStartDestination(R.id.FragmentTesting) // Ubah sesuai dengan ID fragment awal Anda
        navController.setGraph(navGraph,bundle)

    }

}
