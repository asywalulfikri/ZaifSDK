package sound.recorder.widget.notes

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import sound.recorder.widget.R
import sound.recorder.widget.recording.RecordingDialogPalette
import sound.recorder.widget.recording.RecordingDialogViews.rounded
import com.intuit.sdp.R as SdpR

/** Pewarnaan baris `note_list_row` sesuai [RecordingDialogPalette]. Hanya tampilan, tanpa logic. */
internal object NoteRowStyle {

    fun apply(row: View, c: RecordingDialogPalette) {
        val context = row.context
        row.findViewById<View>(R.id.noteRowCard)?.background = RippleDrawable(
            ColorStateList.valueOf(c.ripple),
            rounded(context, c.surface, SdpR.dimen._10sdp, c.outline),
            null
        )
        row.findViewById<View>(R.id.noteIconBg)?.background = rounded(context, c.accentSoft, SdpR.dimen._8sdp)
        row.findViewById<ImageView>(R.id.ivIcon)?.imageTintList = ColorStateList.valueOf(c.accent)
        row.findViewById<TextView>(R.id.tvNoteTitle)?.setTextColor(c.text)
        row.findViewById<TextView>(R.id.tvNoteDesc)?.setTextColor(c.textSecondary)
        row.findViewById<TextView>(R.id.timestamp)?.setTextColor(c.textSecondary)
        row.findViewById<ImageView>(R.id.ivChevron)?.imageTintList = ColorStateList.valueOf(c.outline)
        row.findViewById<TextView>(R.id.tvStatus)?.apply {
            setTextColor(c.textSecondary)
            background = rounded(context, Color.TRANSPARENT, SdpR.dimen._6sdp, c.outline)
        }
        // Tombol admin: warna teks tetap diatur adapter, di sini cukup bentuknya
        row.findViewById<View>(R.id.btnApprove)?.background =
            RippleDrawable(ColorStateList.valueOf(c.ripple), rounded(context, c.surfaceAlt, SdpR.dimen._12sdp), null)
        row.findViewById<View>(R.id.btnDeleteOnline)?.background =
            RippleDrawable(ColorStateList.valueOf(c.ripple), rounded(context, c.surfaceAlt, SdpR.dimen._12sdp), null)
    }
}
