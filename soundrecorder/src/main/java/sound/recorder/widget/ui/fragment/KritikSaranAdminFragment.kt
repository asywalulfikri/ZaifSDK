package sound.recorder.widget.ui.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import sound.recorder.widget.R
import sound.recorder.widget.builder.ZaifSDKBuilder
import sound.recorder.widget.builder.ZaifSDKConfig
import sound.recorder.widget.databinding.FragmentKritiksaranAdminBinding
import sound.recorder.widget.databinding.FragmentSongRequestAdminBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KritikSaranAdminFragment : Fragment() {

    private var binding: FragmentKritiksaranAdminBinding? = null

    data class KritikSaranRequest(
        val docId: String,
        val name: String,
        val description : String,
        val requestedAt: Long
    )

    private val allRequests = mutableListOf<KritikSaranRequest>()

    private lateinit var adapter: KritikSaranAdapter
    var zaifSDKConfig : ZaifSDKConfig? =null

    private var lastVisible: DocumentSnapshot? = null
    private var isLastPage = false
    private var isLoading = false
    private val PAGE_SIZE = 100L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        binding = FragmentKritiksaranAdminBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = KritikSaranAdapter(
            onDelete   = { req -> deleteRequest(req) },
            onClick = { req -> showDetailDialog(req) }
        )

        binding?.rvRequests?.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 2)
        binding?.rvRequests?.adapter = adapter

        binding?.rvRequests?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as androidx.recyclerview.widget.GridLayoutManager
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
            .collection("suggest")
            .orderBy("requested_at", Query.Direction.DESCENDING)
            .whereEqualTo("app_id", zaifSDKConfig?.applicationId)
            .limit(PAGE_SIZE)

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
                    KritikSaranRequest(
                        docId = doc.id,
                        name = d["name"] as? String ?: "-",
                        description = d["description"] as? String ?: "",
                        requestedAt = d["requested_at"] as? Long ?: 0L,
                    )
                }

                allRequests.addAll(newItems)
                lastVisible = snapshot.documents[snapshot.size() - 1]
                
                if (snapshot.size() < PAGE_SIZE) {
                    isLastPage = true
                }

                adapter.updateData(allRequests)
                binding?.tvCount?.text = "${allRequests.size} items"
            }
            .addOnFailureListener {
                isLoading = false
                binding?.progressContainer?.visibility = View.GONE
                Toast.makeText(requireContext(), "Gagal memuat data: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showDetailDialog(req: KritikSaranRequest) {
        AlertDialog.Builder(requireContext())
            .setTitle(req.name)
            .setMessage(req.description)
            .setPositiveButton("Tutup", null)
            .show()
    }

    private fun deleteRequest(req: KritikSaranRequest) {
        FirebaseFirestore.getInstance()
            .collection("suggest")
            .document(req.docId)
            .delete()
            .addOnSuccessListener {
                allRequests.removeAll { it.docId == req.docId }
                adapter.updateData(allRequests)
                binding?.tvEmpty?.visibility = if (allRequests.isEmpty()) View.VISIBLE else View.GONE
                binding?.tvCount?.text = "${allRequests.size} items"
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

    private inner class KritikSaranAdapter(
        private val onDelete: (KritikSaranRequest) -> Unit,
        private val onClick: (KritikSaranRequest) -> Unit
    ) : RecyclerView.Adapter<KritikSaranAdapter.VH>() {

        private val items = mutableListOf<KritikSaranRequest>()

        @SuppressLint("NotifyDataSetChanged")
        fun updateData(newItems: List<KritikSaranRequest>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvName)
            val tvDescription: TextView    = view.findViewById(R.id.tvDescription)
            val tvDate: TextView      = view.findViewById(R.id.tvDate)
            val btnDelete: TextView   = view.findViewById(R.id.btnDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_kritik_saran, parent, false)
            return VH(v)
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val sdf  = SimpleDateFormat("dd MMM yyyy  HH:mm", Locale.getDefault())

            holder.tvName.text = item.name
            holder.tvDescription.text = item.description
            holder.tvDate.text      = sdf.format(Date(item.requestedAt))
            holder.btnDelete.setOnClickListener   { onDelete(item) }
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
