package sound.recorder.widget.ui.fragment

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.intuit.sdp.R as SdpR
import sound.recorder.widget.R
import sound.recorder.widget.builder.ZaifSDKBuilder
import sound.recorder.widget.builder.ZaifSDKConfig
import sound.recorder.widget.databinding.FragmentSongRequestAdminBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SongRequestAdminFragment : Fragment() {

    private var binding: FragmentSongRequestAdminBinding? = null

    data class SongRequest(
        val docId: String,
        val songTitle: String,
        val requestedAt: Long,
        val status: String
    )

    private val allRequests = mutableListOf<SongRequest>()
    private var currentFilter = "all"
    private lateinit var adapter: SongRequestAdapter
    var zaifSDKConfig : ZaifSDKConfig? =null

    private var lastVisible: DocumentSnapshot? = null
    private var isLastPage = false
    private var isLoading = false
    private val PAGE_SIZE = 100L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSongRequestAdminBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = SongRequestAdapter(
            onMarkDone = { req -> updateStatus(req, "done") },
            onDelete   = { req -> deleteRequest(req) }
        )

        binding?.rvRequests?.layoutManager = GridLayoutManager(requireContext(), 2)
        binding?.rvRequests?.adapter = adapter

        binding?.rvRequests?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as GridLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if (!isLoading && !isLastPage) {
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                        && firstVisibleItemPosition >= 0
                        && totalItemCount >= PAGE_SIZE
                    ) {
                        loadRequests()
                    }
                }
            }
        })

        binding?.btnBack?.setOnClickListener { findNavController().navigateUp() }
        binding?.btnFilterAll?.setOnClickListener     { applyFilter("all") }
        binding?.btnFilterPending?.setOnClickListener { applyFilter("pending") }
        binding?.btnFilterDone?.setOnClickListener    { applyFilter("done") }

        setupAdminTools(view)

        zaifSDKConfig = ZaifSDKBuilder.load(requireContext())

        loadRequests()
    }

    private fun loadRequests() {
        if (isLoading) return
        isLoading = true

        binding?.progressContainer?.visibility = View.VISIBLE
        if (lastVisible == null) {
            binding?.tvEmpty?.visibility = View.GONE
        }

        var query = FirebaseFirestore.getInstance()
            .collection("song_request")
            .orderBy("requested_at", Query.Direction.DESCENDING)
            .whereEqualTo("app_id", zaifSDKConfig?.applicationId)

        if (currentFilter != "all") {
            query = query.whereEqualTo("status", currentFilter)
        }

        query = query.limit(PAGE_SIZE)

        lastVisible?.let {
            query = query.startAfter(it)
        }

        query.get()
            .addOnSuccessListener { snapshot ->
                isLoading = false
                binding?.progressContainer?.visibility = View.GONE

                if (snapshot.isEmpty) {
                    isLastPage = true
                    if (allRequests.isEmpty()) {
                        binding?.tvEmpty?.visibility = View.VISIBLE
                    }
                    return@addOnSuccessListener
                }

                if (lastVisible == null) {
                    allRequests.clear()
                }

                val newItems = snapshot.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    SongRequest(
                        docId      = doc.id,
                        songTitle  = d["song_title"]   as? String ?: "-",
                        requestedAt = d["requested_at"] as? Long   ?: 0L,
                        status     = d["status"]       as? String ?: "pending"
                    )
                }

                allRequests.addAll(newItems)
                lastVisible = snapshot.documents[snapshot.size() - 1]

                if (snapshot.size() < PAGE_SIZE) {
                    isLastPage = true
                }

                adapter.updateItems(allRequests)
                binding?.tvCount?.text = "${allRequests.size} requests"
                binding?.tvEmpty?.visibility = if (allRequests.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener {
                isLoading = false
                binding?.progressContainer?.visibility = View.GONE
                Toast.makeText(requireContext(), "Gagal memuat data: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun applyFilter(filter: String) {
        if (currentFilter == filter) return
        currentFilter = filter
        updateFilterUI(filter)
        
        // Reset pagination
        lastVisible = null
        isLastPage = false
        allRequests.clear()
        adapter.updateItems(allRequests)
        
        loadRequests()
    }

    private fun updateFilterUI(active: String) {
        val b = binding ?: return
        listOf(
            "all"     to b.btnFilterAll,
            "pending" to b.btnFilterPending,
            "done"    to b.btnFilterDone
        ).forEach { (key, btn) ->
            if (key == active) {
                btn.setTextColor(0xFFFFFFFF.toInt())
                btn.setBackgroundResource(R.drawable.bg_btn_tutorial_learn)
            } else {
                btn.setTextColor(0xFFCCCCCC.toInt())
                btn.setBackgroundResource(R.drawable.bg_dialog_game)
            }
        }
    }

    private fun updateStatus(req: SongRequest, newStatus: String) {
        FirebaseFirestore.getInstance()
            .collection("song_request")
            .document(req.docId)
            .update("status", newStatus)
            .addOnSuccessListener {
                val idx = allRequests.indexOfFirst { it.docId == req.docId }
                if (idx >= 0) {
                    if (currentFilter == "all") {
                        allRequests[idx] = allRequests[idx].copy(status = newStatus)
                    } else {
                        allRequests.removeAt(idx)
                    }
                    adapter.updateItems(allRequests)
                    binding?.tvCount?.text = "${allRequests.size} requests"
                    binding?.tvEmpty?.visibility = if (allRequests.isEmpty()) View.VISIBLE else View.GONE
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Gagal update: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteRequest(req: SongRequest) {
        FirebaseFirestore.getInstance()
            .collection("song_request")
            .document(req.docId)
            .delete()
            .addOnSuccessListener {
                val idx = allRequests.indexOfFirst { it.docId == req.docId }
                if (idx >= 0) {
                    allRequests.removeAt(idx)
                    adapter.updateItems(allRequests)
                    binding?.tvCount?.text = "${allRequests.size} requests"
                    binding?.tvEmpty?.visibility = if (allRequests.isEmpty()) View.VISIBLE else View.GONE
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Gagal hapus: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupAdminTools(view: View) {
        val context = requireContext()
        val root = view as? LinearLayout ?: return
        
        // Temukan index rvRequests untuk menyisipkan tools di atasnya
        val rvIndex = root.indexOfChild(binding?.rvRequests)
        if (rvIndex == -1) return

        val adminLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.sdp(SdpR.dimen._12sdp), 0, context.sdp(SdpR.dimen._12sdp), context.sdp(SdpR.dimen._12sdp))
        }

        val btnCheck = TextView(context).apply {
            text = "CHECK IDENTIK"
            textSize = 10f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(context.sdp(SdpR.dimen._10sdp), context.sdp(SdpR.dimen._6sdp), context.sdp(SdpR.dimen._10sdp), context.sdp(SdpR.dimen._6sdp))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#448AFF"))
                cornerRadius = context.sdp(SdpR.dimen._4sdp).toFloat()
            }
            setOnClickListener { checkIdenticalRequests() }
        }

        val btnClean = TextView(context).apply {
            text = "CLEAN DUPLICATES"
            textSize = 10f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(context.sdp(SdpR.dimen._10sdp), context.sdp(SdpR.dimen._6sdp), context.sdp(SdpR.dimen._10sdp), context.sdp(SdpR.dimen._6sdp))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FF5252"))
                cornerRadius = context.sdp(SdpR.dimen._4sdp).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
                marginStart = context.sdp(SdpR.dimen._8sdp)
            }
            setOnClickListener { cleanIdenticalRequests() }
        }

        adminLayout.addView(btnCheck)
        adminLayout.addView(btnClean)
        root.addView(adminLayout, rvIndex)
    }

    private fun checkIdenticalRequests() {
        val duplicates = allRequests.groupBy { it.songTitle.trim().lowercase() }
            .filter { it.value.size > 1 }

        if (duplicates.isEmpty()) {
            Toast.makeText(requireContext(), "Tidak ada request identik ditemukan.", Toast.LENGTH_SHORT).show()
            return
        }

        val sb = StringBuilder("Daftar Request Identik:\n\n")
        duplicates.forEach { (title, list) ->
            sb.append("• ${title.uppercase()} (${list.size}x)\n")
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Identical Requests Report")
            .setMessage(sb.toString())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun cleanIdenticalRequests() {
        val duplicatesMap = allRequests.groupBy { it.songTitle.trim().lowercase() }
            .filter { it.value.size > 1 }

        if (duplicatesMap.isEmpty()) {
            Toast.makeText(requireContext(), "Tidak ada duplikat untuk dihapus.", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Clean Duplicates?")
            .setMessage("Sistem akan menghapus request duplikat dan hanya menyisakan satu untuk setiap judul yang sama. Lanjutkan?")
            .setPositiveButton("Hapus") { _, _ ->
                var totalDeleted = 0
                val db = FirebaseFirestore.getInstance()
                
                duplicatesMap.forEach { (_, list) ->
                    // Sisakan satu (yang paling lama/awal atau yang mana saja)
                    // Kita ambil semua kecuali item pertama
                    val toDelete = list.drop(1)
                    toDelete.forEach { req ->
                        db.collection("song_request").document(req.docId).delete()
                            .addOnSuccessListener {
                                totalDeleted++
                                allRequests.remove(req)
                                if (totalDeleted == list.size - 1) {
                                     // Not ideal for multiple groups but okay for UI update
                                     adapter.updateItems(allRequests)
                                     binding?.tvCount?.text = "${allRequests.size} requests"
                                }
                            }
                    }
                }
                Toast.makeText(requireContext(), "Proses penghapusan dimulai...", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun Context.sdp(id: Int): Int {
        return this.resources.getDimensionPixelSize(id)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    // ─── Adapter ──────────────────────────────────────────────────

    private inner class SongRequestAdapter(
        private val onMarkDone: (SongRequest) -> Unit,
        private val onDelete: (SongRequest) -> Unit
    ) : RecyclerView.Adapter<SongRequestAdapter.VH>() {

        private val items = mutableListOf<SongRequest>()

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvSongTitle: TextView = view.findViewById(R.id.tvTitle)
            val tvStatus: TextView    = view.findViewById(R.id.tvStatus)
            val tvDate: TextView      = view.findViewById(R.id.tvDate)
            val btnMarkDone: TextView = view.findViewById(R.id.btnMarkDone)
            val btnDelete: TextView   = view.findViewById(R.id.btnDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_song_request, parent, false)
            return VH(v)
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val sdf  = SimpleDateFormat("dd MMM yyyy  HH:mm", Locale.getDefault())

            holder.tvSongTitle.text = item.songTitle
            holder.tvDate.text      = sdf.format(Date(item.requestedAt))

            if (item.status == "done") {
                holder.tvStatus.text = "Done"
                holder.tvStatus.setTextColor(0xFF00C853.toInt())
                holder.btnMarkDone.visibility = View.GONE
            } else {
                holder.tvStatus.text = "Pending"
                holder.tvStatus.setTextColor(0xFFFFAA00.toInt())
                holder.btnMarkDone.visibility = View.VISIBLE
            }

            holder.btnMarkDone.setOnClickListener { onMarkDone(item) }
            holder.btnDelete.setOnClickListener   { onDelete(item) }
        }

        override fun getItemCount() = items.size

        @SuppressLint("NotifyDataSetChanged")
        fun updateItems(newItems: List<SongRequest>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }
    }
}
