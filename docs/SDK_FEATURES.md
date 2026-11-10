# Fitur ZaifSDK — katalog & status

Titik awal untuk melanjutkan pekerjaan di session baru. Isinya: semua fitur SDK, cara menyalakannya
di app host, pengaturan dan angkanya, app mana memakai apa, dan pekerjaan yang masih terbuka.

- Terakhir diperbarui: **5 November 2026**, SDK **`1.1.171`**.
- Detail perubahan per versi: `CHANGELOG.md`. Daftar tes: `test.md`.
- Modul: `soundrecorder` (package `sound.recorder.widget`).
- Rilis: JitPack `com.github.asywalulfikri:ZaifSDK:<tag>`. Tes lokal tanpa tag:
  `./gradlew :soundrecorder:publishToMavenLocal` → `com.sound.recorder:recording-sdk:0.0.65`.
- App contoh di repo ini memakai package `balera.music.android`, sama dengan Belira: memasang yang satu
  menimpa yang lain di HP.

## Dokumen lain

| File | Isi |
|---|---|
| `CHANGELOG.md` | Perubahan per versi, termasuk yang wajib dibaca sebelum update app host |
| `test.md` | Daftar tes SDK dan hasilnya |
| `docs/HOST_APP_UPDATE_1.1.171.md` | Langkah update app ke 1.1.171 + status per app |
| `docs/MISSIONS_PREMIUM_RULE.md` | Aturan Misi disembunyikan untuk pembeli, Premium Pass |
| `docs/ANALYTICS_TRACKING.md` | Event analytics, `source`/`purpose` koin |
| `docs/HOME_PERFORMANCE_RULES.md` | Aturan redesign Home + protokol tes gfxinfo |
| `docs/UNUSED_CLASSES.md` | 36 file SDK yang tidak dipakai (hanya dicatat, tidak dihapus) |

## Menyalakan fitur di app host (`ZaifSDKBuilder`, di `GameApp`)

| Flag | Bawaan | Fungsi |
|---|---|---|
| `isCoin(true)` | false | Sistem koin: buka lagu, export pakai koin, switch watermark video |
| `isVideo(true)` | false | Pilihan VIDEO di export (rekaman & tutorial) |
| `hasPremiumPurchase(false)` | true | **Hanya app tanpa pembelian.** Jatah video habis → tonton iklan / bayar koin, bukan dialog Premium. Disimpan sebagai `noPremiumPurchase` |
| `videoAdWithPurchase(true)` | false | **App dengan pembelian.** Dialog Premium video ditambah tombol "Tonton iklan" (satu video berwatermark); tombol beli tetap ada. Dipakai Pianika |
| `HostRecordingVideo.option(...)` | — | Pilihan VIDEO di `RecordingExportDialog` untuk layar host yang BUKAN panel instrumen (membuat `RecordingEntity` sendiri). Aturan kuota/iklan/koin sama dengan panel. Dipakai Gamelan Studio |
| `isLockRec`, `isLockMusic`, `isDownload`, `isFilterTutorial` | — | Kunci rekaman/musik, unduh lagu, filter bahasa tutorial |
| `showNote`, `showChangeColor`, `showListSong`, `showVolume`, `showTooltip` | — | Tombol di panel kontrol |
| `setAppName`, `setVersionCode/Name`, `setApplicationId`, `setDeveloperName`, `setFcmKey`, `setBackgroundWidgetColor` | — | Identitas app |

## Katalog fitur

### Koin — `util/CoinManager`
- Prefs `zaif_coins`, key `balance`. `getBalance`, `addCoins(ctx, n, source)`, `spendCoin` (1 koin),
  `spendCoins(ctx, n, purpose)`. `addCoins` dan `spendCoins` `@Synchronized`.
- `UNLOCK_COST = 1` (buka lagu), `DAILY_COINS = 2`, bonus harian tiap **5 jam** (`claimDailyBonus`,
  `canClaimDaily`, `nextClaimInMs`).
- Setiap tambah/kurang tercatat di analytics (lihat `ANALYTICS_TRACKING.md`).

### Misi harian — `rewards/DailyRewards` + `rewards/RewardsDialog`
- Host mengisi `DailyRewards.missions` (list `RewardMission`). Kosong = fitur mati (`isEnabled`).
- Progres: `DailyRewards.add(ctx, key, n)`. Check-in beruntun `streakRewards = 2,3,4,5,6,8,10`.
- Iklan reward: `adReward = 3` koin, `adDailyLimit = 3` per hari. **Jangan dinaikkan**: dulu dicoba 10,
  dibatalkan karena koin jadi terlalu mudah.
- `RewardsDialog.show(activity, style) { onClose }`: panel Misi bawaan SDK.
- Di app yang **punya pembelian**, Misi disembunyikan untuk pembeli (`MISSIONS_PREMIUM_RULE.md`).

### Kartu "Koin untuk apa?" — `rewards/CoinUses` (1.1.171)
- `CoinUses.list(ctx)` → `List<CoinUse(icon, label, price)>`; harga dibaca dari pengaturan SDK.
  Isi: buka lagu (1), export lagu (`exportCoinCost`, bila > 0), video tanpa watermark (30–50, hanya
  `isVideo` + `noPremiumPurchase`).
- Dipakai otomatis oleh `RewardsDialog`; panel Misi buatan host (Belira, Compose) memanggilnya sendiri.

### Premium Pass — `rewards/PremiumPass` (disembunyikan sejak 1.1.171)
- `isEnabled = false` bawaan: kartu tukar hilang, `redeem()` selalu gagal. Alasan: 2 hari tanpa iklan
  merugikan pendapatan iklan. **Jangan beri tahu user bahwa fitur ini dihapus.**
- Pass yang sudah ditukar tetap jalan sampai habis (`isActive`, `remainingMs`); iklan hilang selama aktif.
- Harga bila dinyalakan lagi: `firstCostCoins = 50`, `costCoins = 100`, `durationDays = 2`.
- `importFrom(ctx, "<prefs lama>")`: pindahkan pass dari sistem lama app (dipakai Belira).
- Pass **tidak** membuka video tanpa batas: `unlimited` hanya untuk pembeli asli.

### Pembelian — `rewards/PremiumPurchase`, `payment/BillingManager`
- `PremiumPurchase.setPurchased(...)` / `hasPurchased` dari host. Hanya app yang menjual Remove Ads.
- **Darbuka dan Belira tidak punya pembelian, dan tidak boleh ditambah.**

### Rekam & panel kontrol — `recording/*`
- `InstrumentControlPanel` / `InstrumentControlPanelNewDesign`: tombol REC, LIST REC, MUSIC, NOTE, VOLUME.
  `controlPanel.videoExport = ...` menyalakan VIDEO dari LIST REC.
- `InstrumentRecorderManager`, `MusicRecordTracker`, `RecordingAutosave`, `AudioEngine`, `SustainNotes`.
- Potong rekaman: `RecordingTrim`, `TrimRangeView`. Daftar rekaman: `RecordingListDialogHelper`.

### Export suara — `recording/export/*`
- `RecordingExporter`: MP3 dan MIDI, simpan ke `Music/<App>` atau `Download/<App>`, bagikan / WhatsApp.
- `freeExportsPerDay = 3`, `exportCoinCost = 10`, `maxPolyphony = 15`.
- MP3 butuh `RecordingExporter.resolver` dari host (contoh: Belira, pitch Oboe; `ExportSample.rate`).

### Export video — `tutorial/*`
- `TutorialVideoExport(...)` dipasang host (biasanya `createVideoExport { view }` di `BaseFragment`).
- `RecordingVideoExport`: video dari rekaman; `InstrumentTutorialDialog`: video dari tutorial (EXPORT → VIDEO).
- `TutorialVideoRecorder`: perekam (PixelCopy 30 fps sejak 1.1.162; Android 7 ke bawah cara lama).
- `TutorialVideoOptionsDialog` ("Customize video"): judul lagu, not, warna, teks sendiri, preview.
- `TutorialVideoQuota`: `freePerDay = 3` (dengan watermark), `noWatermarkCoinCost = 50`,
  `removeWatermarkCoinCost = 30`.
- Switch **"Hapus watermark"** (app tanpa pembelian + `isCoin`): 30 koin selama jatah masih ada (tetap
  memakai 1 jatah), 50 setelah habis; sudah bayar 10 → switch 40. Koin kembali bila dialog ditutup,
  rekaman batal, atau gagal (analytics source `refund`).
- `TutorialVideoPremiumDialog`: setelah jatah habis → app tanpa pembelian: tonton iklan / bayar koin;
  app dengan pembelian: dialog Premium.
- `RecordingVideoResultDialog`: hasil (Bagikan / WhatsApp), `VideoRecordingIndicator`: tanda merekam.
- Video tersimpan di `Movies/<App>/`.

### Tutorial & lagu — `tutorial/*`, `music/*`
- `InstrumentTutorialDialog`: daftar lagu (PLAY / LEARN / EXPORT), cari, filter bahasa, lagu terkunci koin.
- `InstrumentSong`, `HighScoreManager`. `MusicPlayerManager`, `RequestSongDialog`, `VolumeDialogHelper`.

### Activity dasar & iklan — `base/*`, `ads/*`
- `BaseGameActivity`: banner, interstitial, iklan reward, consent (`GoogleMobileAdsConsentManager`),
  muat ulang iklan saat internet kembali. App pindah satu per satu.
- Iklan gagal padahal internet nyala → hadiah **tetap** diberikan (disengaja).
- Audiens 13+: tidak memakai tag iklan anak.
- `InAppUpdateHelper`: update dalam app, dialog Pasang / Nanti (sejak 1.1.158).

### Notifikasi — `notification/*`
- `CoinNotificationWorker`: pengingat koin jam **20:00**, WORK_TAG `coin_claim_reminder`. App yang dulu
  punya worker sendiri dengan tag yang sama otomatis tergantikan.
- `AppReminderWorker` + `AppReminderMessages`: pengingat buka app (multi-bahasa).
- `NotificationThrottle`: tidak mengirim bila baru muncul / app baru dibuka. `FirebaseMessageReceiver`: FCM.

### Dialog lain & util
- `ui/dialog`: `ReportBugDialog`, `KritikSaranDialog`, `DelayInfoDialog` (tombol "Ingatkan lagi nanti").
- Layar admin (Bug Reports, Kritik Saran) — lihat `test.md` no. 42–45 untuk masalah yang diketahui.
- `ZaifAnalytics`, `LanguageManager`, `AppRatingHelper`, `PermissionManager`, `NetworkWatcher`.

## App yang memakai SDK (dicek 5 November 2026)

| App | Folder | Versi SDK | Koin | Video | Tanpa pembelian | Panel Misi | Catatan |
|---|---|---|---|---|---|---|---|
| Darbuka | `godev1/Darbuka` | 1.1.171 | ✅ | ✅ | ✅ | `RewardsDialog` | Branch `feature/sdk-1.1.171` sudah di-push, belum di-merge, belum dites di HP |
| Belira (Marching Bells) | GitHub `BeliraMarchingBells` | 1.1.171 | ✅ | ✅ | ✅ | Compose sendiri + `CoinUses` | Merge ke `main` (PR #2). Folder lokal sudah tidak ada di disk |
| Rebana | `godev2/Rebana` | 1.1.164 | ✅ | ✅ | — | `RewardsDialog` | Punya pembelian |
| Dholak | `godev2/Dholak` | 1.1.162 | ✅ | ✅ | — | `RewardsDialog` | Punya pembelian. Studio harus beda dari Darbuka |
| Gendang | `godev2/GendangElectronik` | 1.1.161 | ✅ | ✅ | — | `RewardsDialog` | Punya pembelian. Versi di `gradle/libs.versions.toml` |
| Pianika | `godev2/PianikaBaru` | 1.1.158 | flag build | ✅ | — | `RewardsDialog` | Punya pembelian. Masih ada `CoinManager.kt` sendiri (prefs sama), tapi kode yang dicek meng-import milik SDK — perlu dicek lalu dihapus |
| Angklung | `godev2/AngklungNew` | 1.1.104 | — | — | — | — | Versi lama |
| Harmonica | `godev2/Harmonica` | 1.1.69 | — | — | — | — | Versi lama |
| Darbuka Electric Beat, Real Gayageum, Real Carango, Virtual Shaker, Talempong, Taiko | `godev2/*` | 1.0.62–1.0.68 | — | — | — | — | Versi sangat lama |
| Lira | tidak ditemukan di disk | ? | | | | | Pernah dites dengan BaseGameActivity |

`godev4` (GendangBeatStudio, Neon, RealRebana) dan `godev5` tidak memakai ZaifSDK lewat Gradle.

## Pekerjaan terbuka

1. **Darbuka**: tes 1.1.171 di HP (dialog Misi tanpa tombol Premium Pass, kartu "Koin untuk apa?",
   switch watermark), lalu merge `feature/sdk-1.1.171` dan naikkan versi (sekarang 1.94 / 94).
   Belum ada `local.properties`; build lewat terminal pakai `ANDROID_HOME=$HOME/Library/Android/sdk`.
   Cek dulu apakah Darbuka di HP terpasang dari Play Store — build debug memaksa uninstall (data hilang).
2. **Belira**: rilis 1.166 ke Play Store (AAB dengan kunci rilis, rilis bertahap). Belum ada video dari
   LIST REC (`controlPanel.videoExport` belum dipasang). `DailyMissions` masih milik Belira sendiri.
3. **Rebana, Dholak, Gendang, Pianika**: naikkan ke 1.1.171 (lihat `HOST_APP_UPDATE_1.1.171.md`).
   Karena punya pembelian, switch koin video tidak muncul; yang berubah hanya Premium Pass & kartu koin.
4. Tes yang belum: `test.md` no. 22d (iklan → switch 50), 22e (refund di export tutorial).
5. Kartu "Koin untuk apa?" di `RewardsDialog` SDK belum pernah dilihat langsung di layar (baru di Belira).

## Aturan kerja yang dipakai
- Teks baru wajib ada di 19 bahasa (`values`, `values-en/id/in/ar/de/es/fil/fr/hi/ja/ko/ms/nl/pt/ru/th/vi/zh`).
- Tes hanya di HP yang tersambung (vivo I2401, adb wireless); tanpa emulator. Setiap tap dicek fokus app,
  iklan tidak pernah ditekan (tutup interstitial dengan BACK).
- Jangan checkout/pull `main` tanpa bertanya. Commit/push hanya bila diminta.
- try-catch bukan perbaikan; cari akar masalah dulu.
