package sound.recorder.widget.ui.fragment

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import sound.recorder.widget.R
import sound.recorder.widget.builder.ZaifSDKBuilder
import sound.recorder.widget.builder.ZaifSDKConfig
import sound.recorder.widget.databinding.FragmentSongRequestAdminBinding
import sound.recorder.widget.encrypt.CryptoManager
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BugReportAdminFragment : Fragment() {

    private var binding: FragmentSongRequestAdminBinding? = null

    data class BugRequest(
        val docId: String,
        val bugTitle: String,
        val bugDescription: String,
        val requestedAt: Long,
        val status: String,
        val firebaseToken: String = ""
    )

    private val allRequests = mutableListOf<BugRequest>()
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
            onDelete   = { req -> deleteRequest(req) },
            onReply    = { req -> showReplyDialog(req) }
        )

        binding?.rvRequests?.layoutManager = LinearLayoutManager(requireContext())
        binding?.rvRequests?.adapter = adapter

        binding?.btnBack?.setOnClickListener { findNavController().navigateUp() }
        binding?.btnFilterAll?.setOnClickListener     { applyFilter("all") }
        binding?.btnFilterPending?.setOnClickListener { applyFilter("pending") }
        binding?.btnFilterDone?.setOnClickListener    { applyFilter("done") }

        zaifSDKConfig = ZaifSDKBuilder.load(requireContext())

        loadRequests()
    }

    private fun loadRequests() {
        binding?.progressContainer?.visibility = View.VISIBLE
        binding?.tvEmpty?.visibility = View.GONE

        FirebaseFirestore.getInstance()
            .collection("bug_reports")
            .orderBy("requested_at", Query.Direction.DESCENDING)
            .whereEqualTo("app_id",zaifSDKConfig?.applicationId)
            .limit(100)
            .get()
            .addOnSuccessListener { snapshot ->
                binding?.progressContainer?.visibility = View.GONE
                allRequests.clear()
                allRequests.addAll(snapshot.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    BugRequest(
                        docId      = doc.id,
                        bugTitle  = d["bug_title"]   as? String ?: "-",
                        bugDescription  = d["bug_description"]   as? String ?: "-",
                        requestedAt = d["requested_at"] as? Long   ?: 0L,
                        status     = d["status"]       as? String ?: "pending",
                        firebaseToken = d["firebaseToken"] as? String ?: ""
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

    private fun updateStatus(req: BugRequest, newStatus: String) {
        FirebaseFirestore.getInstance()
            .collection("bug_reports")
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

    private fun deleteRequest(req: BugRequest) {
        FirebaseFirestore.getInstance()
            .collection("bug_reports")
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

    private fun showReplyDialog(req: BugRequest) {
        val context = requireContext()
        val etReply = EditText(context).apply {
            hint = "Masukkan pesan balasan..."
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
            addView(etReply)
        }

        AlertDialog.Builder(context)
            .setTitle("Balas Bug Report")
            .setMessage("Kirim notifikasi ke user untuk: ${req.bugTitle}")
            .setView(layout)
            .setPositiveButton("Kirim") { _, _ ->
                val message = etReply.text.toString().trim()
                if (message.isNotEmpty()) {
                    sendReplyNotification(req, message)
                } else {
                    Toast.makeText(context, "Pesan tidak boleh kosong", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun sendReplyNotification(req: BugRequest, replyMessage: String) {
        val encryptedKey = zaifSDKConfig?.fcmKey.orEmpty()
        val encryptionKey = zaifSDKConfig?.applicationId.orEmpty()
        if (encryptedKey.isBlank() || encryptionKey.isBlank()) {
            Toast.makeText(requireContext(), "FCM Key atau App ID belum dikonfigurasi", Toast.LENGTH_SHORT).show()
            return
        }

        val serviceAccountJson = try {
            CryptoManager(requireContext(), encryptionKey).decrypt(encryptedKey)
        } catch (_: Exception) {
            Toast.makeText(requireContext(), "Gagal dekripsi FCM key", Toast.LENGTH_SHORT).show()
            return
        }

        val projectId = try {
            JSONObject(serviceAccountJson).getString("project_id")
        } catch (_: Exception) {
            Toast.makeText(requireContext(), "Project ID tidak ditemukan di JSON", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val accessToken = getOAuthToken(serviceAccountJson)
                if (accessToken == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Gagal mendapatkan OAuth token", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val conn = (URL("https://fcm.googleapis.com/v1/projects/$projectId/messages:send")
                    .openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    doOutput = true
                }

                val payload = JSONObject().apply {
                    put("message", JSONObject().apply {
                        put("token", req.firebaseToken)
                        put("notification", JSONObject().apply {
                            put("title", "Balasan Bug Report")
                            put("body", replyMessage)
                        })
                        put("data", JSONObject().apply {
                            put("type", "bug_reply")
                            put("bug_id", req.docId)
                        })
                    })
                }

                OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
                val success = conn.responseCode in 200..299

                withContext(Dispatchers.Main) {
                    val msg = if (success) "Balasan berhasil dikirim!" else "Gagal kirim notifikasi: ${conn.responseCode}"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun getOAuthToken(serviceAccountJson: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val json = JSONObject(serviceAccountJson)
                val privateKeyPem = json.getString("private_key")
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("\n", "")
                    .trim()
                val clientEmail = json.getString("client_email")

                val now = System.currentTimeMillis() / 1000L
                val headerJson = """{"alg":"RS256","typ":"JWT"}"""
                val payloadJson = JSONObject().apply {
                    put("iss", clientEmail)
                    put("scope", "https://www.googleapis.com/auth/firebase.messaging")
                    put("aud", "https://oauth2.googleapis.com/token")
                    put("iat", now)
                    put("exp", now + 3600L)
                }.toString()

                val header  = Base64.encodeToString(headerJson.toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE)
                val payload = Base64.encodeToString(payloadJson.toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE)
                val signingInput = "$header.$payload"

                val keyBytes   = Base64.decode(privateKeyPem, Base64.DEFAULT)
                val privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
                val sig = Signature.getInstance("SHA256withRSA").apply {
                    initSign(privateKey)
                    update(signingInput.toByteArray())
                }.sign()

                val jwt = "$signingInput.${Base64.encodeToString(sig, Base64.NO_WRAP or Base64.URL_SAFE)}"

                val tokenConn = (URL("https://oauth2.googleapis.com/token")
                    .openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    doOutput = true
                    connectTimeout = 10_000
                    readTimeout    = 10_000
                }

                val body = "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=$jwt"
                OutputStreamWriter(tokenConn.outputStream).use { it.write(body) }

                if (tokenConn.responseCode == 200) {
                    val response = tokenConn.inputStream.bufferedReader().readText()
                    JSONObject(response).getString("access_token")
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    // ─── Adapter ──────────────────────────────────────────────────

    private inner class SongRequestAdapter(
        private val onMarkDone: (BugRequest) -> Unit,
        private val onDelete: (BugRequest) -> Unit,
        private val onReply: (BugRequest) -> Unit
    ) : RecyclerView.Adapter<SongRequestAdapter.VH>() {

        private val items = mutableListOf<BugRequest>()

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvSongTitle: TextView? = view.findViewById(R.id.tvTitle)
            val tvStatus: TextView?    = view.findViewById(R.id.tvStatus)
            val tvDescription: TextView? = view.findViewById(R.id.tvDescription)
            val tvDate: TextView?      = view.findViewById(R.id.tvDate)
            val btnMarkDone: TextView? = view.findViewById(R.id.btnMarkDone)
            val btnDelete: TextView?   = view.findViewById(R.id.btnDelete)
            val btnReply: TextView?    = view.findViewById(R.id.btnReply)
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

            holder.tvSongTitle?.text = item.bugTitle
            holder.tvDescription?.text = item.bugDescription
            holder.tvDescription?.visibility = View.VISIBLE
            holder.tvDate?.text      = sdf.format(Date(item.requestedAt))

            if (item.status == "done") {
                holder.tvStatus?.text = "Done"
                holder.tvStatus?.setTextColor(0xFF00C853.toInt())
                holder.btnMarkDone?.visibility = View.GONE
            } else {
                holder.tvStatus?.text = "Pending"
                holder.tvStatus?.setTextColor(0xFFFFAA00.toInt())
                holder.btnMarkDone?.visibility = View.VISIBLE
            }

            holder.btnMarkDone?.setOnClickListener { onMarkDone(item) }
            holder.btnDelete?.setOnClickListener   { onDelete(item) }

            if (item.firebaseToken.isNotEmpty()) {
                holder.btnReply?.visibility = View.VISIBLE
                holder.btnReply?.setOnClickListener { onReply(item) }
            } else {
                holder.btnReply?.visibility = View.GONE
            }
        }

        override fun getItemCount() = items.size

        @SuppressLint("NotifyDataSetChanged")
        fun updateItems(newItems: List<BugRequest>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }
    }
}
