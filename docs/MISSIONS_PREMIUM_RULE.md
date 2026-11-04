# Aturan: Sembunyikan Misi untuk Pembeli Premium

Berlaku untuk **semua aplikasi instrumen** yang memakai sistem misi/koin dari ZaifSDK
(`DailyRewards`, `RewardsDialog`, `CoinManager`, `PremiumPass`).

## Aturannya

User yang sudah **membeli Remove Ads / premium (permanen)** tidak perlu misi lagi.
Untuk user ini, **tombol Misi (dan jumlah koinnya) di Home harus disembunyikan**.

Alasannya: misi dan koin dipakai untuk mendapat Premium Pass, menukar koin dengan export,
dan menggantikan iklan. Pembeli premium sudah punya semuanya, jadi tombol Misi
cuma membingungkan.

## Siapa yang dihitung "premium"

| User | Tombol Misi |
|---|---|
| Sudah beli Remove Ads / premium (permanen, `hasPurchased`) | **Sembunyikan** |
| Premium Pass aktif (tukar koin, sementara) | **Tetap tampil**: sisa waktu pass ada di bawah tombol, dan user perlu misi untuk memperpanjang |
| User gratis | Tampil |

Jangan pakai `isPremium` untuk aturan ini, karena di banyak aplikasi
`isPremium = hasPurchased || PremiumPass.isActive(...)`, sehingga user Premium Pass ikut
kehilangan tombol Misi.

## Cara menerapkan (contoh: Rebana godev2)

Referensi: `godev2/Rebana`, file `ui/HomeScreen.kt` dan `ui/HomeFragment.kt`.

1. Composable Home menerima parameter baru:
   ```kotlin
   /** Pembeli Remove Ads tidak perlu misi/koin, jadi tombol Misi disembunyikan. */
   showMissions: Boolean,
   ```
2. Tombol Misi (beserta Spacer di atasnya) hanya digambar kalau `showMissions` bernilai true:
   ```kotlin
   if (showMissions) {
       Spacer(Modifier.height(10.dp))
       MissionButton(rewards, onMissions)
   }
   ```
3. Di Fragment/Activity Home, isi dari state pembelian permanen:
   ```kotlin
   showMissions = !hasPurchased,
   ```
   `hasPurchased` harus berupa state Compose (`mutableStateOf`) dan diperbarui di callback
   `setupPayment { ... }` dan di `onResume()`, supaya tombol langsung hilang setelah pembelian
   terbaca (termasuk setelah install ulang / `checkHistory()`).

Untuk Home berbasis XML: `missionButton.isVisible = !hasPurchased` di tempat yang sama
(callback pembayaran dan `onResume`).

## Notifikasi untuk pembeli premium

| Notifikasi | Pembeli premium |
|---|---|
| Klaim koin jam 20:00 (`CoinNotificationWorker`) | **Tidak muncul** |
| Pengingat buka app (`AppReminderWorker`) | **Tetap muncul** |

SDK tidak tahu status beli (disimpan host app di prefs sendiri, mis. `billing_prefs`/`REMOVE_ADS_KEY`),
jadi host app **wajib** memberi tahu SDK:

```kotlin
PremiumPurchase.setPurchased(context, true)   // sound.recorder.widget.rewards
```

Panggil di tempat yang sama dengan `hasPurchased` diisi: saat app dibuka (baca prefs), di callback
`BillingManager` setelah beli, dan setelah `checkHistory()`. Setelah itu SDK:
- membatalkan jadwal notif koin yang sudah ada,
- tidak menjadwalkan ulang saat app dibuka / check-in / klaim bonus,
- tidak menampilkan notif dari jadwal lama yang terlanjur dibuat.

Jangan cuma memanggil `CoinNotificationWorker.cancel()`: SDK menjadwalkannya lagi setiap app dibuka
(`MyApp`, berjalan di background sehingga bisa terjadi sesudah `cancel`).

Premium Pass tidak dihitung sebagai pembelian, jadi notif koin tetap jalan untuk user Premium Pass.

## Export video saat jatah gratis habis

Flag-nya: **`ZaifSDKBuilder.hasPremiumPurchase(...)`** di GameApp. Default `true` (tidak perlu dipanggil).

| App | Saat jatah video gratis habis |
|---|---|
| **Tanpa** pembelian: panggil `.hasPremiumPurchase(false)` (mis. Darbuka) | Tonton iklan atau bayar `RecordingExporter.exportCoinCost` (10) koin → video **dengan** watermark. Atau bayar `TutorialVideoQuota.noWatermarkCoinCost` (50) koin → video **tanpa** watermark. Selama jatah gratis masih ada: switch "Hapus watermark" di dialog custom video seharga `removeWatermarkCoinCost` (30) koin; tetap memakai 1 jatah gratis. Setelah bayar 10 koin, switch tinggal 40 (total 50). Koin dikembalikan bila batal/gagal |
| **Punya** pembelian (default) + `TutorialVideoExport.onPremiumPurchased` diisi | Dialog Premium + tombol beli. **Tidak ada** pilihan iklan/koin |
| **Punya** pembelian (default) tapi `onPremiumPurchased` lupa diisi | Hanya "coba lagi besok" (iklan/koin tetap tidak terbuka) |

Di dalam SDK flag disimpan terbalik (`noPremiumPurchase`, default false), karena Gson mengisi field yang
belum ada di config lama dengan false. Jadi config lama/rusak tetap dianggap app dengan pembelian.

Ini keputusan produk: iklan/koin untuk video **hanya** di app tanpa pembelian, supaya tidak
mengurangi penjualan premium di app lain. Alur lengkap ada di KDoc `RecordingVideoExport`
(`soundrecorder/.../tutorial/RecordingVideoExport.kt`).

Agar pilihan VIDEO muncul sama sekali, app perlu **dua-duanya**: `ZaifSDKBuilder.isVideo(true)` dan
`controlPanel.videoExport = TutorialVideoExport(...)` di setiap layar instrumen
(contoh: `createVideoExport` di BaseFragment Dholak / Darbuka).

## Yang tidak diubah

- Ini hanya perubahan tampilan. Penghitungan progres misi (`DailyRewards.add`, jumlah pukulan,
  lama bermain) tetap berjalan seperti biasa.
- Lencana Premium / tombol Remove Ads tetap mengikuti logika yang sudah ada.

## Checklist per aplikasi

- [x] Rebana (godev2): tombol Misi disembunyikan, 2026-11-03
- [ ] Rebana (godev2): panggil `PremiumPurchase.setPurchased` (butuh versi SDK baru; saat ini 1.1.164)
- [x] Darbuka (godev1): tidak berlaku. Darbuka sengaja tidak menjual Remove Ads (premium hanya
      dari Premium Pass), jadi tombol Misi dan notif koin tetap tampil. Jangan tambahkan pembelian.
- [ ] Aplikasi lain yang punya tombol Misi (Darbuka, Dholak, Pianika, Lira, Gendang, dll.):
      cek satu per satu, terapkan aturan di atas, lalu centang di sini
