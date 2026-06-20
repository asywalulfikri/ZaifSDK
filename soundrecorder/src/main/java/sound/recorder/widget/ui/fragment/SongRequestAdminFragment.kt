package sound.recorder.widget.ui.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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

        binding?.rvRequests?.layoutManager = LinearLayoutManager(requireContext())
        binding?.rvRequests?.adapter = adapter

        binding?.btnBack?.setOnClickListener { findNavController().navigateUp() }
        binding?.btnFilterAll?.setOnClickListener     { applyFilter("all") }
        binding?.btnFilterPending?.setOnClickListener { applyFilter("pending") }
        binding?.btnFilterDone?.setOnClickListener    { applyFilter("done") }

        val config = ZaifSDKBuilder.load(requireContext())

        loadRequests()
    }

    private fun loadRequests() {
        binding?.progressContainer?.visibility = View.VISIBLE
        binding?.tvEmpty?.visibility = View.GONE

        FirebaseFirestore.getInstance()
            .collection("song_request")
            .orderBy("requested_at", Query.Direction.DESCENDING)
            .whereEqualTo("app_id",zaifSDKConfig?.applicationId)
            .limit(100)
            .get()
            .addOnSuccessListener { snapshot ->
                binding?.progressContainer?.visibility = View.GONE
                allRequests.clear()
                allRequests.addAll(snapshot.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    SongRequest(
                        docId      = doc.id,
                        songTitle  = d["song_title"]   as? String ?: "-",
                        requestedAt = d["requested_at"] as? Long   ?: 0L,
                        status     = d["status"]       as? String ?: "pending"
                    )
                })
                applyFilter(currentFilter)
            }
            .addOnFailureListener {
                binding?.progressContainer?.visibility = View.GONE
                Toast.makeText(requireContext(), "Gagal memuat data: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun applyFilter(filter: String) {
        currentFilter = filter
        val filtered = when (filter) {
            "pending" -> allRequests.filter { it.status == "pending" }
            "done"    -> allRequests.filter { it.status == "done" }
            else      -> allRequests.toList()
        }
        updateFilterUI(filter)
        adapter.updateItems(filtered)
        binding?.tvCount?.text = "${allRequests.size} requests"
        binding?.tvEmpty?.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
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
                if (idx >= 0) allRequests[idx] = allRequests[idx].copy(status = newStatus)
                applyFilter(currentFilter)
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
                allRequests.removeAll { it.docId == req.docId }
                applyFilter(currentFilter)
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Gagal hapus: ${it.message}", Toast.LENGTH_SHORT).show()
            }
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
