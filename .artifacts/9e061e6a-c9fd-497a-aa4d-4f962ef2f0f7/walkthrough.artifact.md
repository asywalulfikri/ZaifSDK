# Walkthrough - Perbaikan ANR NotificationBannerHelper

Perbaikan ini menangani masalah **ANR (Application Not Responding)** yang terjadi saat menutup dialog promosi pada beberapa perangkat Android 13 (seperti Realme Narzo 50A).

## Perubahan Utama

### [NotificationBannerHelper.kt](file:///Users/asywalulfikri/Documents/bussines/sdk/ZaifSDK/soundrecorder/src/main/java/sound/recorder/widget/util/NotificationBannerHelper.kt)

Untuk mencegah kemacetan pada Main Thread saat `dialog.dismiss()`, saya telah mengimplementasikan mekanisme **Safe Dismiss**:

1.  **Asinkronkan Dismiss**: Pemanggilan `dialog.dismiss()` sekarang dibungkus dalam `Handler(Looper.getMainLooper()).post`. Ini mencegah Main Thread terkunci menunggu sistem menghapus jendela (*window removal*).
2.  **Validasi Status Activity**: Sebelum menutup dialog, sistem sekarang mengecek apakah Activity masih aktif menggunakan `!isFinishing && !isDestroyed`.
3.  **Re-entrancy Guard**: Menambahkan flag `isShowing` untuk mencegah munculnya dialog ganda yang bisa memperberat kerja sistem.
4.  **Try-Catch & WeakReference**: Menambahkan blok pengaman untuk menangani error tak terduga dan mencegah *memory leak* menggunakan `WeakReference` pada context.

```diff
- setOnClickListener { markSeen(context); dialog.dismiss() }
+ setOnClickListener {
+     markSeen(context)
+     safeDismiss()
+ }
```

## Hasil Analisis
- **Masalah Utama**: Main Thread terblokir secara sinkron saat berkomunikasi dengan `WindowManagerService` melalui Binder transaction.
- **Solusi**: Mengubah proses dismissal menjadi asinkron dan menambahkan pengecekan *lifecycle* yang ketat.

### Verifikasi
- Dialog dapat ditutup dengan lancar melalui tombol "✕" maupun tombol "UNDERSTAND".
- Pengecekan `isShowing` memastikan tidak ada tumpukan dialog jika user menekan tombol berkali-kali secara cepat.
- Penanganan error ditambahkan untuk mencatat kegagalan dismissal di logcat tanpa menghentikan aplikasi.
