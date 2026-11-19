package sound.recorder.widget.notification

import android.content.Context

/**
 * Pengait opsional agar app host mengatur pengingat buka aplikasi ([AppReminderWorker]).
 * Implementasikan di kelas Application; app yang tidak mengimplementasikannya tidak berubah sama sekali.
 *
 * Dipanggil di thread background (Worker) tepat sebelum notifikasi dibuat. Jangan melakukan kerja berat.
 * Pengaman bawaan tetap berlaku: izin notifikasi, jam sunyi 22:00–08:00, dan tidak muncul bila app baru dibuka.
 */
interface ReminderCustomizer {
    /** Null = pakai pengingat bawaan SDK. */
    fun customizeReminder(context: Context): ReminderOverride?
}

/**
 * @param title judul notifikasi; null = teks bawaan SDK.
 * @param body isi notifikasi; null = teks bawaan SDK. Judul dan isi harus diisi bersama atau dikosongkan bersama.
 * @param skip true = tidak ada notifikasi kali ini (misalnya pengguna sudah berlatih hari ini).
 * @param nextInDays pemeriksaan berikutnya berapa hari lagi (1..[MAX_NEXT_DAYS]); null = bawaan SDK (2 hari).
 * @param minGapHours jarak minimal dari notifikasi SDK sebelumnya; null = bawaan (48 jam). Nilai di bawah
 * [MIN_GAP_HOURS_FLOOR] dinaikkan ke batas itu supaya app tidak bisa membanjiri pengguna.
 */
data class ReminderOverride(
    val title: String? = null,
    val body: String? = null,
    val skip: Boolean = false,
    val nextInDays: Int? = null,
    val minGapHours: Int? = null
) {
    companion object {
        const val MAX_NEXT_DAYS = 7
        const val MIN_GAP_HOURS_FLOOR = 20
    }
}
