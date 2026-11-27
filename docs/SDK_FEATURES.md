# Fitur ZaifSDK — katalog & status

Titik awal untuk melanjutkan pekerjaan di session baru. Isinya: semua fitur SDK, cara menyalakannya
di app host, pengaturan dan angkanya, app mana memakai apa, dan pekerjaan yang masih terbuka.

- Terakhir diperbarui: **16 November 2026**, SDK **`1.1.176`** (overlay video portrait lebih kecil; app lain masih `1.1.175`).
- **27 November 2026:** ada perubahan SDK yang sudah di-commit (`1e3a0f1`, branch `feature/recording-sdk-0.0.65`) tapi **belum dirilis**
  (versi di `soundrecorder/build.gradle` masih `1.1.187`, belum ada tag): daftar lagu untuk layar sempit, export video admin
  60 fps tanpa watermark, tombol YouTube, hook export. Rinciannya di tabel "Perubahan SDK" dan `godev2/PianikaBaru/docs/PORTRAIT_RECORD.md`.
- Detail perubahan per versi: `CHANGELOG.md`. Daftar tes: `test.md`.
- Modul: `soundrecorder` (package `sound.recorder.widget`).
- Rilis: JitPack `com.github.asywalulfikri:ZaifSDK:<tag>`. Tes lokal tanpa tag:
  `./gradlew :soundrecorder:publishToMavenLocal` → `com.sound.recorder:recording-sdk:0.0.65`.
- Tes SDK yang belum dirilis di app host **tanpa mengubah file app**: `publishReleasePublicationToMavenLocal` (versi `1.1.187`), lalu build app
  dengan init script yang mengganti `com.github.asywalulfikri:ZaifSDK` menjadi `com.sound.recorder:recording-sdk:1.1.187` dari `mavenLocal()`
  (`beforeSettings { dependencyResolutionManagement.repositories { mavenLocal() } }` + `dependencySubstitution`), jalankan `./gradlew -I <init.gradle> :app:assembleDebug`.
  Dipakai untuk Pianika; tambah `--refresh-dependencies` setelah SDK dipublikasikan ulang.
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
| `docs/STRESS_TEST.md` | Stress test ANR: cakupan fitur SDK (sudah/belum diuji), hasil per app dan HP, ANR dari Play Console |

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
  Tombol **YouTube** muncul bila host mengisi `TutorialVideoExport.youtubeShare` (hanya tombol VIDEO admin, build debug):
  membuka aplikasi YouTube dengan video terlampir, judul/deskripsi lewat `EXTRA_SUBJECT/TITLE/TEXT`, dan disalin ke clipboard sebagai cadangan
  (apakah YouTube mengisinya otomatis tergantung versi aplikasinya, belum dipastikan).
- Tombol **VIDEO** admin di daftar lagu (hanya `isAppDebuggable`): **60 fps** (bitrate naik sebanding, 4 Mbps di 30 fps), **tanpa watermark**.
  Export user biasa tetap 30 fps dan aturan watermark tidak berubah (`startVideoExport(fps = ...)`, default 30).
- Opsi baru `TutorialVideoExport` (semua default = perilaku lama): `playbackLeadMs` (layar yang menahan not, mis. tiles jatuh dulu: suara dan penghitung not
  digeser sebesar itu dan rekaman diperpanjang), `screenShowsTitleAndNote` (pil judul/kotak not export tidak digambar), `keepBannerHidden`,
  `onRecordingScreen(recording)` (host menyembunyikan tombolnya selama merekam; dipulihkan saat selesai/gagal/batal), `adminFrameRate`, `youtubeShare`.
- Video tersimpan di `Movies/<App>/`.

### Tutorial & lagu — `tutorial/*`, `music/*`
- `InstrumentTutorialDialog`: daftar lagu (PLAY / LEARN / EXPORT), cari, filter bahasa, lagu terkunci koin.
- Daftar lagu di **layar sempit** (`screenWidthDp < 600`, mis. portrait HP): `layout/item_song_tutorial.xml` dua baris (nama di atas, 4 tombol sama lebar),
  search selebar layar dan tombol filter/admin di baris yang bisa digeser. Layar lebar memakai `layout-w600dp/item_song_tutorial.xml` (= layout lama, id harus sama).
- Query Firestore daftar lagu yang gagal kini dicatat (`Log.e("TutorialList")`) dan di build debug muncul toast. Dulu ditelan dan daftar terlihat macet.
  Filter status PUBLISHED/DRAFT atau bahasa "ALL" butuh composite index `category + status + submitted_at` (lihat Pekerjaan terbuka no. 10).
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
- `AppReminderWorker` + `AppReminderMessages`: pengingat buka app (multi-bahasa). Host bisa mengatur teks/jadwal lewat
  `ReminderCustomizer` (diimplementasikan di Application; jeda minimal dipaksa >= 20 jam). Dipakai Darbuka (streak belajar).
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
| Real Gayageum | `godev2/RealGayageum` | 1.1.176 | ✅ | ✅ | ✅ | `RewardsDialog` (tanpa misi terdaftar) | Tanpa pembelian (`hasPremiumPurchase(false)`). Dimigrasi dari 1.0.66: `BaseGameActivity`, `InstrumentControlPanel` di kiri (menggantikan `VoiceRecordFragmentHorizontalZaif`), `GayageumExportResolver` (`sound_N` → `senar_N`, pentatonik C D E G A, program 107 koto). `GayageumFragment` sebelumnya salah paket (`real.tumbi`) dan belum memanggil `GayageumView`. Geser pitch slider belum ikut terekam. Navigasi setelah interstitial memakai `runWhenStarted` (tanpa itu log: "Ignoring navigate() call: FragmentManager has already saved its state" dan app tertahan di Home). Dites di Samsung SM-G532G: Home, layar alat, REC, simpan, putar ulang. Home Compose (gambar `bgc` + tombol Compose). `GayageumView` dioptimalkan: lapisan diam di-cache ke bitmap, tanpa `LAYER_TYPE_SOFTWARE`/`BlurMaskFilter`, badan `roxy.png` dimuat sekali sesuai ukuran layar. Ukur di Samsung SM-G532G (Android 6, RAM 1,4 GB, debug, banner uji aktif; 6 geseran x 12 senar + 6 ketukan): sebelum 72,75% tersendat, p90 121 ms (400 frame); sesudah 18,52%, p90 19 ms, p99 26 ms (799 frame). Belum dites: export MP3/MIDI/video, volume, panel Misi. Branch `feature/sdk-1.1.176` (belum di-commit) |
| Harmonica | `godev2/Harmonica` | 1.1.69 | — | — | — | — | Versi lama |
| Darbuka Electric Beat, Real Carango, Virtual Shaker, Taiko | `godev2/*` | 1.0.62–1.0.68 | — | — | — | — | Versi sangat lama |
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
- **Gambar yang dipanggil lewat nama hilang di build shrink** (RealGayageum): `getIdentifier("roxy", "drawable", ...)` tidak terlihat oleh shrinker resource, jadi `roxy.png` dan `hand.png` dibuang saat `isShrinkResources = true` (APK 35 MB → 17,7 MB, badan alat kosong). Pakai `R.drawable.x` langsung; `keep.xml` hanya menjaga `@raw/*`, tambahkan `@drawable/...` bila memang harus lewat nama.
- **Catatan uji**: pemasangan baru memunculkan dialog izin (notifikasi, media); koordinat tombol tolak berbeda
  per HP, jadi jadikan parameter skrip. `install -r` ditolak bila app terpasang dari Play Store (tanda tangan beda):
  uninstall hanya dengan izin pemilik HP. Interstitial uji sering butuh beberapa kali Back dengan jeda.
- Build **release** memakai ID iklan produksi dan memuat iklan asli di HP uji. Ukur performa dengan build
  debug (iklan uji) dan jangan menekan iklan.

## Perubahan SDK untuk rollout ke banyak app (1.1.177 – 1.1.187)

Dipakai app host yang naik versi. Darbuka jadi canary (branch `feature/sdk-1.1.177`); app lain menunggu hasil CTR 1–2 minggu.
Detail lengkap tiap poin ada di `CHANGELOG.md`.

| Versi | Perubahan SDK | Yang harus dicek di app host |
|---|---|---|
| 1.1.177 | Status/navigation bar tetap tersembunyi (`FLAG_FULLSCREEN`, dipasang ulang saat fokus, dialog `immersive()`), banner tidak tertutup status bar di Android 10 ke bawah | Banner menutupi elemen paling atas? Ukur jarak banner (±160 px di layar 1080×2376) ke tombol/elemen teratas. Darbuka: margin atas dinaikkan `_50sdp` → `_58sdp` di Home Back/Close Mixer, Single (drum), dan Studio |
| 1.1.177 | Interstitial menunggu layar tenang 0,8 dtk (maks 2,5 dtk, lalu dilewati; `onFinished` tetap jalan), jeda 90 dtk antar iklan tidak berubah | Tidak perlu kode di host. Tujuannya menurunkan klik tidak sengaja (CTR Darbuka interstitial 11%, banner 9,8%) |
| 1.1.179 | `MyApp.workManagerConfiguration` memasang `setInitializationExceptionHandler`: crash WorkManager `SQLITE_CANTOPEN` / "file system on the device is in a bad state" kini dicatat ke Crashlytics sebagai non-fatal, app tidak crash | Host harus memakai `MyApp` (atau turunannya) sebagai Application agar handler berlaku. Pengingat terjadwal di HP bermasalah tidak jalan, fitur lain normal |
| 1.1.179 | Dialog daftar musik (`MusicListDialogHelper`) muat di layar landscape: kartu pemutar satu baris di bawah daftar, header/tab/kolom cari lebih ramping (3 lagu terlihat saat memutar di Mi Max 2, sebelumnya 1). `MusicSeekBar`: track/thumb di 40% tinggi supaya tidak menimpa label waktu (semua layar) | Buka daftar musik di landscape saat lagu diputar: tidak ada yang tertutup atau berdempetan. Cek juga tempat lain yang memakai `MusicSeekBar` |
| 1.1.180 | `SafeEditText`, `SafeTextInputEditText`, `EditText.makeTouchSafe()`: menelan crash sistem `setSpan (-1 ... -1)` saat EditText di dialog disentuh (OPPO Android 8.1). Semua EditText SDK sudah memakainya | EditText buatan host sendiri tidak terlindungi: ganti `<EditText>` jadi `<sound.recorder.widget.ui.SafeEditText>` atau panggil `makeTouchSafe()`. Pantau Crashlytics: crash ini harus hilang dari app yang memakai SDK baru |
| 1.1.181 | Retry banner game naik bertahap: retry lambat satu percobaan dengan jeda 60 dtk, 2 mnt, 5 mnt (maks), reset saat berhasil (dulu ±2 permintaan/menit selamanya saat no-fill) | Tidak perlu kode di host. Bila banner game tidak muncul di HP no-fill, pastikan permintaan iklan di log (`BannerAd`) makin jarang |
| 1.1.187 | `<uses-feature android.hardware.wifi required="false">` di manifest SDK: perangkat tanpa Wi-Fi tidak lagi diblokir Play karena izin `CHANGE_WIFI_STATE`. Versi publikasi `0.0.65` → `1.1.187` | Tidak perlu kode di host. Setelah update, cek di Play Console (Perangkat didukung) bahwa jumlah perangkat yang tidak didukung berkurang |

Belum dirilis (setelah 1.1.187, commit `1e3a0f1`):

| Perubahan SDK | Yang harus dicek di app host |
|---|---|
| Daftar lagu tutorial dua baris di layar sempit + `layout-w600dp` untuk layar lebar; "Loading..." tidak menimpa spinner | Landscape: daftar harus sama seperti sebelumnya (layout-w600dp = layout lama). App yang memakai layar portrait: nama lagu terbaca dan tombol muat |
| Export video admin 60 fps tanpa watermark; opsi `TutorialVideoExport` baru; tombol YouTube; error query daftar lagu dicatat | Hanya build debug. Export user biasa harus tetap 30 fps dengan watermark seperti dulu. Tidak perlu kode di host bila tidak memakai opsi baru |

**1.1.178 gagal build di JitPack** (checksum Gradle wrapper salah, diperbaiki di `6703d09`); pakai 1.1.179.

Catatan WorkManager (1.1.179): error dilempar di thread internal WorkManager (`WM.task-1`), jadi `try/catch` di `MyApp` tidak menangkapnya.
Dicoba ulang di Realme C21Y dengan mencabut izin folder `no_backup` (`chmod 000`): SDK lama crash, SDK baru tidak.
Penyebab aslinya di perangkat (penyimpanan penuh/rusak, ruang kedua), jadi tidak bisa dihilangkan dari app.

## Checklist host dengan library native (Oboe): `libnative-lib.so` hilang

Crashlytics Rebana (23 Nov 2026): `UnsatisfiedLinkError: dlopen failed: library "libnative-lib.so" not found` dari
`SoundPlayUtils.<clinit>`, lalu `RuntimeException: Cannot create an instance of class <SoundViewModel diacak R8>`.
Keduanya satu penyebab: `SoundViewModel` membaca field `SoundPlayUtils` (mis. `loadingProgress`), jadi gagal muat library
ikut menjatuhkan pembuatan ViewModel. Ini kode host, bukan SDK, tapi pola `SoundPlayUtils` + `System.loadLibrary` disalin ke
hampir semua app Oboe, jadi cek tiap app:

1. **ABI**: `ndk { abiFilters }` sebaiknya menyertakan `x86`. Hanya Stylophone dan Belira yang punya; Rebana sudah ditambah
   (branch `fix/native-lib-missing`). Pianika, Santoor, Gendang, Tabla, Taiko, TongueDrum, Dholak/Darbuka belum dicek/ditambah
   (Dholak/Darbuka: periksa dulu).
2. **Muat library aman**: jangan `System.loadLibrary` polos di `init {}` object. Bungkus try/catch `UnsatisfiedLinkError`
   ke flag (mis. `nativeAvailable`), lewati panggilan native bila `false` (`init`, `setVolume`, `release`), dan beri tahu
   user (toast "perangkat tidak didukung") supaya app tidak crash.
3. **ViewModel jangan memicu object native lewat field**: `val x = SoundPlayUtils.liveData` di `SoundViewModel` memicu
   `<clinit>` saat ViewModel dibuat. Dengan langkah 2 aman; tanpa itu, crash tampil sebagai "Cannot create an instance".
4. Sesudah rilis, cek Crashlytics: kunci ABI/model perangkat pada event itu. Bila datang dari HP ARM biasa, penyebabnya
   instalasi/paket rusak, bukan ABI.

Berlaku untuk: BeliraMarchingBells, GendangElectronik, PianikaBasuri, RealSantoor, Rebana, TablaSimulator, Taiko,
TongueDrum, Stylophone (JNI di `AudioEngineJNI`), Melodica (`MelodicaAudioEngine`). Status per app: Rebana ✔ (belum
dirilis/diuji di perangkat), lainnya belum.

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

10. **Index Firestore** (project `pianika-31e58`, collection `pianica.music.instrument`): buat composite index `category ASC, status ASC, submitted_at DESC`
    lewat tautan `create_composite` dari log (`W/Firestore ... requires an index`). Tanpa itu, status PUBLISHED/DRAFT di admin dan filter bahasa "ALL"
    (juga di build release) tidak memuat apa-apa. Index dibuat per collection: app lain akan memunculkan error yang sama dengan tautan berbeda.
11. **Rilis SDK** untuk perubahan 27 November 2026 (tag + naikkan versi di app host), lalu gabungkan branch Pianika `feature/portrait-record-screen`.
    Branch itu memakai API SDK baru, jadi belum bisa dibuild dengan JitPack `1.1.186`.
12. Panel LEARN (belajar per langkah) dan dialog export belum dicek di layar portrait; daftar lagu belum dilihat di landscape setelah perubahan layout.

## Aturan kerja yang dipakai
- Teks baru wajib ada di 19 bahasa (`values`, `values-en/id/in/ar/de/es/fil/fr/hi/ja/ko/ms/nl/pt/ru/th/vi/zh`).
- Tes hanya di HP yang tersambung (vivo I2401 = HP kelas atas; Realme RMX3261 = HP kelas bawah untuk ukur performa); tanpa emulator. Dua entri adb wireless untuk satu HP muncul sebagai dua perangkat: pakai `adb -t <transport_id>` atau `-s <serial>`. Setiap tap dicek fokus app,
  iklan tidak pernah ditekan (tutup interstitial dengan BACK).
- Jangan checkout/pull `main` tanpa bertanya. Commit/push hanya bila diminta.
- try-catch bukan perbaikan; cari akar masalah dulu.
