# Fitur ZaifSDK — katalog & status

Titik awal untuk melanjutkan pekerjaan di session baru. Isinya: semua fitur SDK, cara menyalakannya
di app host, pengaturan dan angkanya, app mana memakai apa, dan pekerjaan yang masih terbuka.

- Terakhir diperbarui: **13 November 2026**, SDK **`1.1.175`**.
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

## App yang memakai SDK (baris lama dicek 5 November 2026; Gendang Setting Sendiri, DarbukaPad, RealKoto, Talempong dicek 13 November 2026)

| App | Folder | Versi SDK | Koin | Video | Tanpa pembelian | Panel Misi | Catatan |
|---|---|---|---|---|---|---|---|
| Darbuka | `godev1/Darbuka` | 1.1.171 | ✅ | ✅ | ✅ | `RewardsDialog` | Branch `feature/sdk-1.1.171` sudah di-push, belum di-merge, belum dites di HP |
| Belira (Marching Bells) | GitHub `BeliraMarchingBells` | 1.1.171 | ✅ | ✅ | ✅ | Compose sendiri + `CoinUses` | Merge ke `main` (PR #2). Folder lokal sudah tidak ada di disk |
| Rebana | `godev2/Rebana` | 1.1.164 | ✅ | ✅ | — | `RewardsDialog` | Punya pembelian |
| Dholak | `godev2/Dholak` | 1.1.162 | ✅ | ✅ | — | `RewardsDialog` | Punya pembelian. Studio harus beda dari Darbuka |
| Gendang | `godev2/GendangElectronik` | 1.1.161 | ✅ | ✅ | — | `RewardsDialog` | Punya pembelian. Versi di `gradle/libs.versions.toml` |
| Pianika | `godev2/PianikaBaru` | 1.1.158 | flag build | ✅ | — | `RewardsDialog` | Punya pembelian. Masih ada `CoinManager.kt` sendiri (prefs sama), tapi kode yang dicek meng-import milik SDK — perlu dicek lalu dihapus |
| Gendang Setting Sendiri | `godev2/GendangSettingSendiri` (folder lokal sudah tidak ada di disk) | 1.1.175 | ✅ | ✅ | — | `RewardsDialog` | Punya pembelian (`videoAdWithPurchase`). Home Compose, geser jari antar pad, UI emas, `GendangExportResolver`. Branch `feature/sdk-1.1.175` sudah di-push |
| DarbukaPad | `godev2/DarbukaPad` | 1.1.175 | ✅ | ✅ | — | `RewardsDialog` (chip koin di Home XML) | Punya pembelian Remove Ads. `DarbukaExportResolver` (alias suara Classic vs Pad), UI emas-tembaga. Branch `feature/sdk-1.1.175` sudah di-push. Belum dites: rekam, export, panel Misi |
| RealKoto | `godev2/RealKoto` | 1.1.175 | ✅ | ✅ | — | `RewardsDialog` (chip koin di Home Compose) | Belum ada kode billing walau `GameApp` memanggil `videoAdWithPurchase(true)`. Panel kontrol di kiri, `KotoExportResolver`, KotoView di-cache. ID iklan rewarded masih kosong. Branch `feature/sdk-1.1.175` |
| Talempong | `godev2/Talempong` | 1.1.175 | ✅ | ✅ | ✅ | `RewardsDialog` (chip koin di Home Compose) | Tanpa pembelian (`hasPremiumPurchase(false)`). Home Compose bergaya Minang (merah marawa, pita songket, Rumah Gadang), panel kontrol di Pacik & Deret lewat `PadPanelHelper`, `TalempongExportResolver`. Branch `feature/sdk-1.1.175` |
| Angklung | `godev2/AngklungNew` | 1.1.175 | ✅ | ✅ | — | `RewardsDialog` (chip koin di Home Compose) | Punya pembelian Remove Ads (`videoAdWithPurchase`). Home Compose bertema Sunda (kawung, tumpal, hero angklung digambar sekali). Angklung Nada dibuat ulang mengikuti angklung asli: 8 angklung diatonis (do–do'), rangka bambu + tabung besar & tabung oktaf, digoyang (geser jari = kurulung/tremolo, ketuk = centok, goyang HP untuk angklung yang dicentang). `AngklungView` memicu `onShakePulse` ±10x/detik; tiap getaran = satu `RecordedTap` (nama suara `long_*`), `AngklungExportResolver`. Angklung Susun dibuat ulang: 30 angklung kromatik (`d_*`) dua deret di `AngklungSusunView` (lapisan diam di-cache ke bitmap), memakai `InstrumentControlPanel`. Diukur di HP: Realme debug Susun 0,4% / Nada 0% jank, Home release Home↔Sejarah 5,8% (p99 57 ms, satu frame komposisi saat kembali); vivo debug Susun 0,8% / Nada 0%. Tes release hanya di Realme dengan DNS diblok (iklan produksi). Rekam, putar ulang, export MP3 dan MIDI sudah dites di Realme (debug, iklan uji): `AngklungExportResolver` mengisi `resolveMidiNote` (Nada diatonis do = C5, program 12 marimba) karena tanpa itu SDK menulis 60 + nomor pad (kromatik). Layar ke-3 **Angklung Lengkap** (`AngklungLengkapView`, `LengkapPads`) memakai paket suara `angklung_new_package` (62 `.ogg` jadi `res/raw/pk_*`, dimuat hanya saat layar dibuka lewat `SoundPlayUtils.loadPack`, ±13 MB): 24 nada kromatik 2 oktaf, ketuk = centok 0,5 dtk, tahan = goyang `tekan` 2 dtk yang diulang; pad Baru C/D/E/F dan Crash C/D/F (fungsinya masih tebakan). Contoh pemakaian nada tahan: rekam suara `*tekan` lalu event `OFF` (`RecordingExporter.OFF_METADATA`), resolver mengembalikan `ExportSample(loop = true)`; hasil MP3 sesuai lama tahan. Catatan SDK: bila rekaman berisi `OFF`, `MidiFileWriter` membiarkan ketukan tanpa `OFF` berbunyi sampai akhir berkas, dan `Mp3Renderer` memotong sampel non-loop (dengan fade) jika diberi `OFF`, jadi ketukan pendek tidak boleh diberi `OFF`. Dialog ekspor: tombol Tutup terpotong di layar landscape kecil setelah pesan sukses. Belum dites: export Video, panel Misi, dan pitch MIDI Lengkap (c1 diasumsikan C4). Branch `feature/sdk-1.1.175` |
| Harmonica | `godev2/Harmonica` | 1.1.69 | — | — | — | — | Versi lama |
| Darbuka Electric Beat, Real Gayageum, Real Carango, Virtual Shaker, Talempong, Taiko | `godev2/*` | 1.0.62–1.0.68 | — | — | — | — | Versi sangat lama |
| Lira | tidak ditemukan di disk | ? | | | | | Pernah dites dengan BaseGameActivity |

`godev4` (GendangBeatStudio, Neon, RealRebana) dan `godev5` tidak memakai ZaifSDK lewat Gradle.

## Migrasi app lama ke 1.1.175 (pelajaran RealKoto dan Talempong, dari 1.0.62 / 1.0.66)

- **Toolchain wajib**: AGP ≥ 9.1 (dipakai 9.2.1), Gradle 9.4.1, `compileSdk = 37`. AGP 9 sudah membawa Kotlin:
  buang plugin `org.jetbrains.kotlin.android` dan `kapt` (kapt tanpa dependensi aman dibuang). Compose butuh
  `org.jetbrains.kotlin.plugin.compose` (dipakai 2.3.10). Tanpa `local.properties`, Gradle tidak menemukan Android SDK.
- **Compose butuh Java 11**: ubah `compileOptions` dari `VERSION_1_8` ke `VERSION_11`, kalau tidak build gagal
  dengan "Cannot inline bytecode built with JVM target 11".
- **Proyek tanpa `gradle-wrapper.jar`** (Talempong): pakai wrapper proyek lain tanpa mengubah repo, mis.
  `DarbukaPad/gradlew -p Talempong :app:assembleDebug`.
- **Warna layar SDK** (Pengaturan, dll.) dari `@color/colorPrimaryDark` milik SDK (`#B83106`): timpa di `colors.xml`
  app (`<color name="colorPrimaryDark">…</color>`) agar senada tema app. `setBackgroundWidgetColor` bukan sumbernya.
- **API yang hilang**: `VoiceRecordFragmentVerticalZaif` (ganti `InstrumentControlPanel`; hapus juga
  `FragmentContainerView`-nya di XML, kalau tidak app crash saat layar dibuka), `UnityBannerController`,
  `initUnityAdsOnce`, `RecordingSDK.addSong` (ganti `MusicListDialogHelper.registerRawTracks`),
  `setBannerHome` / `setHideAllBanner` / `onViewBannerHome` (satu banner: `MyAdsListener.setBanner(show)`).
- **Signature berubah**: `showInterstitialIfAllowed(isPremium)`, `loadInterstitialIfNeeded(isPremium)`,
  `NetworkUtils.isInternetConnected(context, scope)`.
- **Lint release**: tambahkan provider `androidx.startup.InitializationProvider` dengan
  `WorkManagerInitializer` ber-`tools:node="remove"` di manifest app (manifest SDK tidak dihitung lint).
- **Ikuti pola Gendang / DarbukaPad**: `BaseGameActivity` (`bannerContainer`, `isPremiumUser`, `startAds()`,
  `onAdBuildersReady()`), `DailyRewards.missions` didaftarkan SEBELUM `super.onCreate()`,
  `RecordingExporter.resolver`, `PremiumPurchase` untuk pembelian.
- **Performa View custom** (RealKoto, ukur di Realme RMX3261 Android 11 / RAM 2,8 GB): jangan pakai
  `LAYER_TYPE_SOFTWARE`; gambar bagian statis sekali ke bitmap; tanpa alokasi `Paint`/`Shader` per frame;
  `SoundPool.play` membuat `AudioTrack` (beberapa ms di HP lama), jalankan di thread audio sendiri.
  Hasil: frame tersendat 100% → 0,16%, p99 117 ms → 9 ms. Detail ukur: `HOME_PERFORMANCE_RULES.md` bagian 3.
- **App pad berbasis `View` biasa** (Talempong): satu kelas pembantu (`PadPanelHelper`) memasang `InstrumentControlPanel`
  untuk dua layar: `bindPads(listOf(view to namaSuara))` merekam tiap pukulan lewat `recordEvent(index, nama)`,
  playback lewat `onPlaybackEvent`, plus volume, mute, rewarded, dan `videoExport`. Pemetaan nama suara → `res/raw`
  untuk export ada di `*ExportResolver` (per set, karena Pacik dan Deret memakai sampel berbeda).
- **Ukur Talempong** (48 ketukan pada 16 gong, Realme RMX3261): suara di UI thread + animasi dimuat ulang tiap
  sentuhan 3,58% tersendat (p99 20 ms) → suara di thread audio + animasi dimuat sekali 0,00% (p99 11 ms).
  Di vivo (HP kelas atas) keduanya 0% — perbedaannya hanya terlihat di HP kelas bawah.
- **Catatan uji**: pemasangan baru memunculkan dialog izin (notifikasi, media); koordinat tombol tolak berbeda
  per HP, jadi jadikan parameter skrip. `install -r` ditolak bila app terpasang dari Play Store (tanda tangan beda):
  uninstall hanya dengan izin pemilik HP. Interstitial uji sering butuh beberapa kali Back dengan jeda.
- Build **release** memakai ID iklan produksi dan memuat iklan asli di HP uji. Ukur performa dengan build
  debug (iklan uji) dan jangan menekan iklan.

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
6. **RealKoto**: isi ID iklan rewarded (`admobRewardId` kosong, tombol "tonton iklan" tidak menghasilkan koin);
   putuskan pembelian (tambah billing + `hasPurchased`, atau `hasPremiumPurchase(false)`); tes rekam, putar ulang,
   export MP3/video dan volume (belum dites); pitch bend slider tidak ikut terekam (butuh dukungan SDK);
   masuk layar Koto di HP lama masih ±170–280 ms di build debug (inflate + `rebuildCache`), bisa dipindah ke thread latar.
7. **DarbukaPad**: tes rekam, export, dan panel Misi di HP; `setToast(true)` di `GameApp` memunculkan toast
   "Banner sukses dimuat" ke user bila ikut ke rilis (belum diubah).
8. **Gendang Setting Sendiri**: folder lokal hilang dari `godev2/`; sinkronkan dari GitHub bila perlu dilanjutkan.
9. **Talempong**: tes memutar ulang rekaman, LIST REC, export MP3/video, tombol MUSIK dan VOLUME (belum dites);
   ukur biaya masuk layar instrumen; izin `com.google.android.gms.permission_AD_ID` di manifest salah ketik
   (garis bawah, bukan titik; aman karena SDK membawa izin yang benar); `app/release/app-release.aab` ikut
   dilacak git; pertimbangkan apakah tetap tanpa pembelian.

## Aturan kerja yang dipakai
- Teks baru wajib ada di 19 bahasa (`values`, `values-en/id/in/ar/de/es/fil/fr/hi/ja/ko/ms/nl/pt/ru/th/vi/zh`).
- Tes hanya di HP yang tersambung (vivo I2401 = HP kelas atas; Realme RMX3261 = HP kelas bawah untuk ukur performa); tanpa emulator. Dua entri adb wireless untuk satu HP muncul sebagai dua perangkat: pakai `adb -t <transport_id>` atau `-s <serial>`. Setiap tap dicek fokus app,
  iklan tidak pernah ditekan (tutup interstitial dengan BACK).
- Jangan checkout/pull `main` tanpa bertanya. Commit/push hanya bila diminta.
- try-catch bukan perbaikan; cari akar masalah dulu.
