package sound.recorder.widget.ui.fragment

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.DocumentSnapshot
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
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.Signature
import sound.recorder.widget.encrypt.CryptoManager
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.intuit.sdp.R as SdpR

class NotePromotionAdminFragment : Fragment() {

    companion object {
        private const val PAGE_SIZE      = 20L
        private var COLLECTION           = "note_promotions"
        private const val STATUS_DRAFT   = "draft"
        private const val STATUS_PUBLISH = "published"

        fun newInstance() = NotePromotionAdminFragment()
    }

    // ─── Model ───────────────────────────────────────────────────────────────

    data class PromotionNote(
        val docId: String,
        val recordName: String,
        val category: String,
        val senderName: String,
        val submittedAt: Long,
        val firebaseToken: String,
        var status: String,
        val jsonNote: String
    )

    // ─── State ───────────────────────────────────────────────────────────────

    private val db        = FirebaseFirestore.getInstance()
    private val notes     = mutableListOf<PromotionNote>()
    private var lastDoc   : DocumentSnapshot? = null
    private var isLoading = false
    private var isLastPage = false

    private lateinit var recyclerView : RecyclerView
    private lateinit var progressBar  : ProgressBar
    private lateinit var emptyView    : TextView
    private lateinit var adapter      : NoteAdapter
    var zaifSDKConfig : ZaifSDKConfig? =null

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = buildRoot()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadPage()
    }

    // ─── UI Build ────────────────────────────────────────────────────────────

    private fun buildRoot(): View {
        val ctx = requireContext()

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F0A1A"))
        }


        root.addView(buildHeader(ctx))
        val config = ZaifSDKBuilder.load(ctx)
        zaifSDKConfig = config
        COLLECTION = config?.applicationId ?: "note_promotions"

        adapter = NoteAdapter()

        recyclerView = RecyclerView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            layoutManager = LinearLayoutManager(ctx)
            setBackgroundColor(Color.TRANSPARENT)
            clipToPadding = false
            val pad = ctx.sdp(SdpR.dimen._8sdp)
            setPadding(pad, pad, pad, pad)
        }
        recyclerView.adapter = adapter
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as LinearLayoutManager
                if (!isLoading && !isLastPage && lm.findLastVisibleItemPosition() >= adapter.itemCount - 3) {
                    loadPage()
                }
            }
        })
        root.addView(recyclerView)

        progressBar = ProgressBar(ctx).apply { visibility = View.GONE }
        root.addView(LinearLayout(ctx).apply {
            gravity = Gravity.CENTER
            val vPad = ctx.sdp(SdpR.dimen._12sdp)
            setPadding(0, vPad, 0, vPad)
            addView(progressBar)
        })

        emptyView = TextView(ctx).apply {
            text = "∅  Belum ada promosi masuk"
            setTextColor(Color.parseColor("#4A3020"))
            textSize = 13f
            gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = ctx.sdp(SdpR.dimen._32sdp)
            }
        }
        root.addView(emptyView)

        return root
    }

    private fun buildHeader(ctx: Context): View {
        val headerBg = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.parseColor("#1A0F09"), Color.parseColor("#2D1B10"))
        )

        val header = FrameLayout(ctx).apply {
            background = headerBg
        }

        val textCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = ctx.sdp(SdpR.dimen._16sdp)
            setPadding(pad, pad, pad, ctx.sdp(SdpR.dimen._12sdp))
        }
        textCol.addView(TextView(ctx).apply {
            text = "★  NOTE PROMOTIONS"
            setTextColor(Color.parseColor("#D2B48C"))
            textSize = 16f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        })
        textCol.addView(TextView(ctx).apply {
            text = "Kelola nota yang dikirim pengguna"
            setTextColor(Color.parseColor("#8A7456"))
            textSize = 10f
            val topPad = ctx.sdp(SdpR.dimen._4sdp)
            setPadding(0, topPad, 0, 0)
        })

        val refreshBtn = TextView(ctx).apply {
            text = "⟳ Refresh"
            setTextColor(Color.parseColor("#D2B48C"))
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            val padH = ctx.sdp(SdpR.dimen._12sdp)
            val padV = ctx.sdp(SdpR.dimen._8sdp)
            setPadding(padH, padV, padH, padV)
            background = RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#30FFFFFF")),
                GradientDrawable().apply {
                    setColor(Color.parseColor("#202D1B10"))
                    cornerRadius = ctx.resources.getDimension(SdpR.dimen._10sdp)
                    setStroke(ctx.sdp(SdpR.dimen._1sdp), Color.parseColor("#44D2B48C"))
                },
                null
            )
            layoutParams = FrameLayout.LayoutParams(-2, -2).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                marginEnd = ctx.sdp(SdpR.dimen._16sdp)
            }
            setOnClickListener { resetAndReload() }
        }

        header.addView(textCol)
        header.addView(refreshBtn)
        return header
    }

    // ─── Pagination ──────────────────────────────────────────────────────────

    private fun resetAndReload() {
        notes.clear()
        adapter.notifyDataSetChanged()
        lastDoc = null
        isLoading = false
        isLastPage = false
        emptyView.visibility = View.GONE
        loadPage()
    }

    private fun loadPage() {
        if (isLoading || isLastPage) return
        isLoading = true
        progressBar.visibility = View.VISIBLE


        var query: Query = db.collection(COLLECTION)
            .orderBy("submitted_at", Query.Direction.DESCENDING)
            .limit(PAGE_SIZE)

        lastDoc?.let { query = query.startAfter(it) }

        query.get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded) return@addOnSuccessListener
                progressBar.visibility = View.GONE
                isLoading = false

                if (snapshot.isEmpty) {
                    isLastPage = true
                    if (notes.isEmpty()) emptyView.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                lastDoc = snapshot.documents.last()
                if (snapshot.size() < PAGE_SIZE) isLastPage = true

                val newItems = snapshot.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    PromotionNote(
                        docId         = doc.id,
                        recordName    = d["record_name"]    as? String ?: "",
                        category      = d["category"]       as? String ?: "",
                        senderName    = d["sender_name"]    as? String ?: "",
                        submittedAt   = d["submitted_at"]   as? Long   ?: 0L,
                        firebaseToken = d["firebase_token"] as? String ?: "",
                        status        = d["status"]         as? String ?: STATUS_DRAFT,
                        jsonNote      = d["json_note"]      as? String ?: ""
                    )
                }

                val startPos = notes.size
                notes.addAll(newItems)
                adapter.notifyItemRangeInserted(startPos, newItems.size)
                if (notes.isNotEmpty()) emptyView.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                progressBar.visibility = View.GONE
                isLoading = false
                Toast.makeText(context, "Gagal memuat: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ─── Actions ─────────────────────────────────────────────────────────────

    private fun showDeleteConfirm(note: PromotionNote, position: Int) {
        if (!isAdded) return
        val ctx = requireContext()
        val dialog = AlertDialog.Builder(ctx).create()

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A0F09"))
                cornerRadius = ctx.resources.getDimension(SdpR.dimen._14sdp)
                setStroke(ctx.sdp(SdpR.dimen._1sdp), Color.parseColor("#22D2B48C"))
            }
            val pad = ctx.sdp(SdpR.dimen._16sdp)
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(ctx).apply {
            text = "Hapus rekaman ini?"
            setTextColor(Color.parseColor("#F5F5DC"))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        })

        root.addView(TextView(ctx).apply {
            text = "\"${note.recordName}\" oleh ${note.senderName}"
            setTextColor(Color.parseColor("#8A7456"))
            textSize = 11f
            val topPad = ctx.sdp(SdpR.dimen._8sdp)
            setPadding(0, topPad, 0, topPad)
        })

        val btnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            val topPad = ctx.sdp(SdpR.dimen._8sdp)
            setPadding(0, topPad, 0, 0)
        }
        btnRow.addView(buildActionBtn(ctx, "Batal", "8A7456") { dialog.dismiss() })
        btnRow.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(ctx.sdp(SdpR.dimen._8sdp), 1)
        })
        btnRow.addView(buildActionBtn(ctx, "Hapus", "CF6679") {
            deleteNote(note, position)
            dialog.dismiss()
        })
        root.addView(btnRow)

        dialog.setView(root)
        dialog.show()
        val dm = ctx.resources.displayMetrics
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout((dm.widthPixels * 0.85).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun showEditNameDialog(note: PromotionNote, position: Int) {
        if (!isAdded) return
        val ctx = requireContext()
        val dialog = AlertDialog.Builder(ctx).create()

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A0F09"))
                cornerRadius = ctx.resources.getDimension(SdpR.dimen._14sdp)
                setStroke(ctx.sdp(SdpR.dimen._1sdp), Color.parseColor("#22D2B48C"))
            }
            val pad = ctx.sdp(SdpR.dimen._16sdp)
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(ctx).apply {
            text = "Edit Nama Rekaman"
            setTextColor(Color.parseColor("#F5F5DC"))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        })

        val input = EditText(ctx).apply {
            setText(note.recordName)
            setTextColor(Color.parseColor("#F5F5DC"))
            setHintTextColor(Color.parseColor("#4A3020"))
            hint = "Nama rekaman"
            textSize = 12f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2D1B10"))
                cornerRadius = ctx.resources.getDimension(SdpR.dimen._8sdp)
                setStroke(ctx.sdp(SdpR.dimen._1sdp), Color.parseColor("#44D2B48C"))
            }
            val padH = ctx.sdp(SdpR.dimen._12sdp)
            val padV = ctx.sdp(SdpR.dimen._10sdp)
            setPadding(padH, padV, padH, padV)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = ctx.sdp(SdpR.dimen._12sdp)
                bottomMargin = ctx.sdp(SdpR.dimen._8sdp)
            }
            setSelection(text.length)
        }
        root.addView(input)

        val btnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            val topPad = ctx.sdp(SdpR.dimen._8sdp)
            setPadding(0, topPad, 0, 0)
        }
        btnRow.addView(buildActionBtn(ctx, "Batal", "8A7456") { dialog.dismiss() })
        btnRow.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(ctx.sdp(SdpR.dimen._8sdp), 1)
        })
        btnRow.addView(buildActionBtn(ctx, "Simpan", "D2B48C") {
            val newName = input.text.toString().trim()
            if (newName.isNotEmpty()) {
                updateRecordName(note, position, newName)
                dialog.dismiss()
            } else {
                input.error = "Nama tidak boleh kosong"
            }
        })
        root.addView(btnRow)

        dialog.setView(root)
        dialog.show()
        val dm = ctx.resources.displayMetrics
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout((dm.widthPixels * 0.85).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun updateRecordName(note: PromotionNote, position: Int, newName: String) {
        db.collection(COLLECTION).document(note.docId)
            .update("record_name", newName)
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                notes[position] = note.copy(recordName = newName)
                adapter.notifyItemChanged(position)
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                Toast.makeText(context, "Gagal update nama: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteNote(note: PromotionNote, position: Int) {
        db.collection(COLLECTION).document(note.docId)
            .delete()
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                notes.removeAt(position)
                adapter.notifyItemRemoved(position)
                adapter.notifyItemRangeChanged(position, notes.size - position)
                if (notes.isEmpty()) emptyView.visibility = View.VISIBLE
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                Toast.makeText(context, "Gagal hapus: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun toggleStatus(note: PromotionNote, position: Int) {
        val newStatus = if (note.status == STATUS_PUBLISH) STATUS_DRAFT else STATUS_PUBLISH
        db.collection(COLLECTION).document(note.docId)
            .update("status", newStatus)
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                note.status = newStatus
                adapter.notifyItemChanged(position)
                if (newStatus == STATUS_PUBLISH && note.firebaseToken.isNotEmpty()) {
                    sendPublishNotification(note)
                }
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                Toast.makeText(context, "Gagal update: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ─── FCM Notification (HTTP v1 API) ──────────────────────────────────────

    private fun sendPublishNotification(note: PromotionNote) {
        val encryptedKey = zaifSDKConfig?.fcmKey.orEmpty()
        if (encryptedKey.isBlank()) {
            Toast.makeText(context, "FCM Service Account belum diset", Toast.LENGTH_SHORT).show()
            return
        }
        val encryptionKey = zaifSDKConfig?.applicationId.orEmpty()
        val serviceAccountJson = try {
            CryptoManager(requireContext(), encryptionKey).decrypt(encryptedKey)
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal dekripsi FCM key", Toast.LENGTH_SHORT).show()
            return
        }
        if (serviceAccountJson.isBlank()) {
            Toast.makeText(context, "FCM Service Account belum diset", Toast.LENGTH_SHORT).show()
            return
        }

        val projectId = try {
            JSONObject(serviceAccountJson).getString("project_id")
        } catch (e: Exception) {
            Toast.makeText(context, "Format Service Account JSON tidak valid", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val accessToken = getOAuthToken(serviceAccountJson)
                if (accessToken == null) {
                    withContext(Dispatchers.Main) {
                        if (!isAdded) return@withContext
                        Toast.makeText(context, "Gagal mendapatkan OAuth token", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val conn = (URL("https://fcm.googleapis.com/v1/projects/$projectId/messages:send")
                    .openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    doOutput = true
                    connectTimeout = 10_000
                    readTimeout    = 10_000
                }

                val payload = JSONObject().apply {
                    put("message", JSONObject().apply {
                        put("token", note.firebaseToken)
                        put("notification", JSONObject().apply {
                            put("title", context?.getString(R.string.not_approved))
                            put("body", "Rekaman \"${note.recordName}\" kini tampil di aplikasi. Terima kasih ${note.senderName}!")
                        })
                        put("data", JSONObject().apply {
                            put("type", "note_published")
                            put("record_name", note.recordName)
                            put("category", note.category)
                        })
                    })
                }

                OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
                val success = conn.responseCode in 200..299

                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    val msg = if (success)
                        "Notifikasi terkirim ke ${note.senderName} ✓"
                    else
                        "Notifikasi gagal (kode: ${conn.responseCode})"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    Toast.makeText(context, "Error notifikasi: ${e.message}", Toast.LENGTH_SHORT).show()
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
                    val err = tokenConn.errorStream?.bufferedReader()?.readText()
                    Log.e("FCM", "Token error ${tokenConn.responseCode}: $err")
                    null
                }
            } catch (e: Exception) {
                Log.e("FCM", "OAuth token exception: ${e.message}")
                null
            }
        }
    }

    // ─── Adapter ─────────────────────────────────────────────────────────────

    inner class NoteAdapter : RecyclerView.Adapter<NoteAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context
            val card = CardView(ctx).apply {
                radius = ctx.resources.getDimension(SdpR.dimen._10sdp)
                cardElevation = 0f
                setCardBackgroundColor(Color.parseColor("#2D1B10"))
                layoutParams = RecyclerView.LayoutParams(-1, -2).apply {
                    val m = ctx.sdp(SdpR.dimen._4sdp)
                    setMargins(0, m, 0, m)
                }
            }
            card.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                val pad = ctx.sdp(SdpR.dimen._12sdp)
                setPadding(pad, pad, pad, pad)
            })
            return VH(card)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            bindCard(holder.itemView as CardView, notes[position], position)
        }

        override fun getItemCount() = notes.size
    }

    // ─── Card Binding ────────────────────────────────────────────────────────

    private fun bindCard(card: CardView, note: PromotionNote, position: Int) {
        val ctx   = card.context
        val inner = card.getChildAt(0) as LinearLayout
        inner.removeAllViews()

        val isPublished = note.status == STATUS_PUBLISH
        val statusColor = if (isPublished) "#4CAF50" else "#FFB347"

        // ── Row 1: name + status badge ──
        val row1 = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row1.addView(TextView(ctx).apply {
            text = note.recordName.uppercase()
            setTextColor(Color.parseColor("#F5F5DC"))
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        row1.addView(buildStatusBadge(ctx, note.status, statusColor))
        inner.addView(row1)

        // ── Row 2: meta info ──
        inner.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 4.dp(ctx))
        })
        val sdf = SimpleDateFormat("dd/MM/yy  HH:mm", Locale.getDefault())
        inner.addView(TextView(ctx).apply {
            text = "📂 ${note.category}   ·   👤 ${note.senderName}   ·   🕐 ${sdf.format(Date(note.submittedAt))}"
            setTextColor(Color.parseColor("#8A7456"))
            textSize = 9f
        })

        // ── Divider ──
        inner.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 1).apply {
                topMargin = 8.dp(ctx)
                bottomMargin = 8.dp(ctx)
            }
            setBackgroundColor(Color.parseColor("#22D2B48C"))
        })

        // ── Row 3: actions ──
        val actionRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        actionRow.addView(buildActionBtn(ctx, "Edit", "7BAFD4") {
            showEditNameDialog(note, position)
        })

        actionRow.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(8.dp(ctx), 1)
        })

        actionRow.addView(buildActionBtn(ctx, "Hapus", "CF6679") {
            showDeleteConfirm(note, position)
        })

        actionRow.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(8.dp(ctx), 1)
        })

        actionRow.addView(buildActionBtn(ctx, "Export WA", "25D366") {
            shareJsonViaWhatsApp(note)
        })

        actionRow.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(8.dp(ctx), 1)
        })

        val toggleLabel = if (isPublished) "→ Draft" else "Publish ★"
        actionRow.addView(buildActionBtn(ctx, toggleLabel, statusColor.removePrefix("#")) {
            toggleStatus(note, position)
        })

        inner.addView(actionRow)
    }

    private fun shareJsonViaWhatsApp(note: PromotionNote) {
        if (note.jsonNote.isBlank()) {
            Toast.makeText(context, "json_note kosong", Toast.LENGTH_SHORT).show()
            return
        }
        val ctx = requireContext()
        try {
            val fileName = "${note.recordName.replace(" ", "_")}_note.json"
            val file = File(ctx.cacheDir, fileName).apply {
                writeText(note.jsonNote)
            }
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", file)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val waIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                setPackage("com.whatsapp")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            if (waIntent.resolveActivity(ctx.packageManager) != null) {
                startActivity(waIntent)
            } else {
                startActivity(Intent.createChooser(shareIntent, "Export JSON via..."))
            }
        } catch (e: Exception) {
            Toast.makeText(ctx, "Gagal export: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildStatusBadge(ctx: Context, status: String, colorHex: String) = TextView(ctx).apply {
        val cleanHex = colorHex.removePrefix("#")
        text = if (status == STATUS_PUBLISH) "PUBLISHED" else "DRAFT"
        setTextColor(Color.parseColor("#$cleanHex"))
        textSize = 8f
        typeface = Typeface.DEFAULT_BOLD
        val padH = ctx.sdp(SdpR.dimen._6sdp)
        val padV = 3.dp(ctx)
        setPadding(padH, padV, padH, padV)
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#20$cleanHex"))
            cornerRadius = ctx.resources.getDimension(SdpR.dimen._8sdp)
            setStroke(1.dp(ctx), Color.parseColor("#$cleanHex"))
        }
    }

    private fun buildActionBtn(ctx: Context, label: String, colorHex: String, onClick: () -> Unit) =
        TextView(ctx).apply {
            val cleanHex = colorHex.removePrefix("#")
            text = label
            setTextColor(Color.parseColor("#$cleanHex"))
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val padH = ctx.sdp(SdpR.dimen._10sdp)
            val padV = ctx.sdp(SdpR.dimen._6sdp)
            setPadding(padH, padV, padH, padV)
            background = RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#40FFFFFF")),
                GradientDrawable().apply {
                    setColor(Color.parseColor("#20$cleanHex"))
                    cornerRadius = ctx.resources.getDimension(SdpR.dimen._10sdp)
                    setStroke(1.dp(ctx), Color.parseColor("#$cleanHex"))
                },
                null
            )
            setOnClickListener { onClick() }
        }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun Context.sdp(id: Int) = resources.getDimensionPixelSize(id)
    private fun Int.dp(ctx: Context) = (this * ctx.resources.displayMetrics.density).toInt()
}
