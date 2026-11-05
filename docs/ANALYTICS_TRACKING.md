# Analytics Tracking (ZaifSDK)

Semua event dikirim lewat satu pintu: `soundrecorder/src/main/java/sound/recorder/widget/util/ZaifAnalytics.kt`
→ `FirebaseAnalytics.logEvent(...)`.

- Dependency: `com.google.firebase:firebase-analytics` di `soundrecorder/build.gradle` (pakai Firebase BoM).
- Host app **tanpa** `google-services.json` → tidak ada event yang terkirim (error hanya di-log, fitur tetap jalan).
- Tidak ada data pribadi: hanya jenis kejadian, jumlah koin, format, dan nama kelas error
  (bukan pesan error, karena pesan bisa berisi nama file user).
- Nilai parameter dipotong maks. 100 karakter (batas Firebase).
- Belum ada `setUserId` / user property — semua anonim.
- Firebase otomatis mencatat versi app host (`app_version`), versi Android, model/merek HP, bahasa,
  negara, dan event bawaan (`first_open`, `session_start`, `screen_view`, …) — bisa langsung difilter
  di laporan tanpa didaftarkan.

---

## Daftar event

| Event | Parameter | Arti |
|---|---|---|
| `coin_earned` | `source`, `amount` | User dapat koin |
| `coin_spent` | `purpose`, `amount` | User memakai koin |
| `coin_ad_watched` | – | Iklan reward untuk koin selesai ditonton |
| `premium_pass_redeemed` | `cost` | Premium Pass ditukar pakai koin |
| `remove_ads_purchased` | – | Pembelian Remove Ads berhasil (acknowledged) |
| `premium_offer_shown` | `place` | Tawaran premium tampil |
| `premium_offer_clicked` | `place` | User tekan tombol beli |
| `export_started` | `format`, `source` | Export dimulai |
| `export_finished` | `format`, `source` | Export berhasil |
| `export_failed` | `format`, `source`, `reason` | Export gagal / dibatalkan |
| `export_unlock` | `method` | Cara user membuka kunci export |
| `reward_ad_requested` | – | User minta iklan reward |
| `reward_ad_result` | `result` | Hasil akhir permintaan iklan reward |

### Nilai parameter

| Parameter | Nilai |
|---|---|
| `source` (koin) | `mission`, `check_in`, `ad`, `daily_bonus`, `refund`, `other` |

`refund` (sejak 1.1.171): koin video berbayar dikembalikan karena rekaman batal atau gagal. Saat menghitung koin yang *didapat* user, kecualikan `refund`; itu bukan koin baru.
| `purpose` | `premium_pass`, `export`, `note_unlock`, `other` |
| `place` | `remove_ads`, `video` |
| `format` | `video`, atau nama `ExportFormat` huruf kecil (mis. `wav`, `mp3`, …) |
| `source` (export) | `recording`, `tutorial` |
| `method` | `free`, `ad`, `coin` |
| `reason` | nama kelas error (mis. `IOException`), `cancelled`, `mp3_source_unavailable` |
| `result` | `earned`, `closed_early`, `show_failed_granted`, `load_failed_granted`, `timeout_granted`, `not_started_granted`, `no_internet`, `cancelled`, `left_app` |

> Catatan: `source` dipakai di dua event berbeda (koin & export) dengan arti berbeda.
> Saat membuat laporan di Firebase, selalu filter per event.

---

## Di mana saja tracking dipanggil

Path relatif dari `soundrecorder/src/main/java/sound/recorder/widget/`.

### 1. Koin — `util/CoinManager.kt`

| Fungsi | Event |
|---|---|
| `addCoins(context, amount, source)` | `coin_earned` (source default `other`) |
| `spendCoin(context)` | `coin_spent` purpose `note_unlock` |
| `spendCoins(context, amount, purpose)` | `coin_spent` (purpose default `other`) |
| `claimDailyBonus(context)` | lewat `addCoins` → `coin_earned` source `daily_bonus` |

Karena semua koin lewat `CoinManager`, **host app yang memanggil `addCoins` / `spendCoins` sendiri
juga ikut tercatat**. Supaya tidak masuk `other`, host sebaiknya mengisi `source` / `purpose`.

### 2. Misi & check-in — `rewards/DailyRewards.kt`

| Fungsi | Event |
|---|---|
| `claim(mission)` | `coin_earned` source `mission` |
| `rewardAd()` | `coin_earned` source `ad` + `coin_ad_watched` |
| `checkIn()` | `coin_earned` source `check_in` |

### 3. Premium Pass — `rewards/PremiumPass.kt`

| Lokasi | Event |
|---|---|
| tukar pass (± baris 60) | `coin_spent` purpose `premium_pass` (via `spendCoins`) |
| setelah pass aktif (± baris 69) | `premium_pass_redeemed` |

### 4. Pembelian — `payment/BillingManager.kt`

| Lokasi | Event |
|---|---|
| callback `acknowledgePurchase` OK (± baris 168) | `remove_ads_purchased` |

Hanya dicatat saat pembelian baru di-acknowledge, jadi pembelian lama yang dipulihkan (restore) tidak dihitung ulang.

### 5. Tawaran premium

| File | Event | `place` |
|---|---|---|
| `music/InstrumentDialogHelper.kt` (dialog Remove Ads) | `premium_offer_shown` saat dialog tampil, `premium_offer_clicked` saat tombol Beli | `remove_ads` |
| `tutorial/TutorialVideoPremiumDialog.kt` | `premium_offer_shown` saat dialog tampil, `premium_offer_clicked` saat tombol Beli | `video` |

### 6. Export audio rekaman — `recording/export/RecordingExportDialog.kt`

| Kejadian | Event |
|---|---|
| mulai export (bukan file cache) | `export_started` format audio, source `recording` |
| export selesai (bukan file cache) | `export_finished` |
| dibatalkan | `export_failed` reason `cancelled` |
| MP3 tidak bisa dibuat (host belum pasang resolver) | `export_failed` reason `mp3_source_unavailable` |
| error lain | `export_failed` reason = nama kelas error |
| kuota gratis terpakai | `export_unlock` method `free` |
| buka kunci setelah iklan | `export_unlock` method `ad` |
| buka kunci pakai koin | `coin_spent` purpose `export` + `export_unlock` method `coin` |

File yang sudah dibuat di dialog yang sama dipakai ulang dan **tidak** dihitung sebagai export baru.

### 7. Export video rekaman — `tutorial/RecordingVideoExport.kt`

| Kejadian | Event |
|---|---|
| recorder mulai | `export_started` format `video`, source `recording` |
| gagal mulai | `export_failed` |
| `complete()` berhasil | `export_finished` |
| `complete()` gagal | `export_failed` |
| `cancel()` (user batal / pindah layar saat merekam) | `export_failed` reason `cancelled` |

### 8. Export video tutorial — `tutorial/InstrumentTutorialDialog.kt` (± baris 1930–1975)

Sama seperti nomor 7, tapi source `tutorial`: `export_started`, `export_finished`, `export_failed`.
Batal saat merekam (`cancelVideoExport()`, dipanggil dari `stopAll()`) → `export_failed` reason `cancelled`.

### 9. Iklan reward — `base/BaseActivityWidget.kt`

| Lokasi | Event / `result` |
|---|---|
| `showRewardedAd()` | `reward_ad_requested` (sekali per permintaan, tidak dobel saat sedang menunggu) |
| `showRewardedAd()` tanpa internet | `no_internet` |
| `presentRewardedAd` → iklan ditutup | `earned` atau `closed_early` |
| `presentRewardedAd` → gagal tayang | `show_failed_granted` (reward tetap diberi) |
| load gagal, error jaringan | `no_internet` |
| load gagal, error AdMob lain | `load_failed_granted` (reward tetap diberi) |
| tunggu iklan terlalu lama | `timeout_granted` (reward tetap diberi) |
| load tidak bisa dimulai (ID kosong / consent) | `not_started_granted` (reward tetap diberi) |
| user tekan Batal / dialog di-cancel | `cancelled` |
| app ditinggalkan saat menunggu | `left_app` |
| `finishRewardWait`, internet mati saat menunggu | `no_internet` |

Hasil `*_granted` = keputusan produk: kalau internet ada tapi AdMob gagal, user tetap dapat reward.

---

## Celah yang diketahui

- Setiap `export_started` sekarang berpasangan dengan tepat satu `export_finished` atau `export_failed`
  (termasuk batal, reason `cancelled`), untuk audio maupun video.
- Belum ada event untuk: buka instrumen, mulai/selesai rekam, mainkan tutorial, buka layar misi.

---

## Cara menambah event baru

1. Tambahkan konstanta event/parameter + fungsi publik di `ZaifAnalytics.kt`.
2. Panggil dari tempat kejadian; jangan kirim teks bebas dari user (nama file, nama rekaman, pesan error).
3. Nama event: `snake_case`, maks. 40 karakter; nilai parameter maks. 100 karakter.
4. Update tabel di file ini.
5. Cek di HP: `adb shell setprop debug.firebase.analytics.app <package.host>` lalu lihat Firebase → DebugView.
