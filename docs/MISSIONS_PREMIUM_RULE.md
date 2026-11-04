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

## Yang tidak diubah

- Ini hanya perubahan tampilan. Penghitungan progres misi (`DailyRewards.add`, jumlah pukulan,
  lama bermain) tetap berjalan seperti biasa.
- Lencana Premium / tombol Remove Ads tetap mengikuti logika yang sudah ada.

## Checklist per aplikasi

- [x] Rebana (godev2): tombol Misi disembunyikan, 2026-11-03
- [ ] Rebana (godev2): panggil `PremiumPurchase.setPurchased` (butuh versi SDK baru; saat ini 1.1.164)
- [ ] Aplikasi lain yang punya tombol Misi (Darbuka, Dholak, Pianika, Lira, Gendang, dll.):
      cek satu per satu, terapkan aturan di atas, lalu centang di sini
