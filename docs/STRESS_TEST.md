# Stress test ZaifSDK (pencarian ANR di semua app host)

Dibuat 2026-11-18. SDK dipakai ±50 app, jadi satu ANR di SDK muncul di banyak app sekaligus. Laporan terbanyak
di Play Console adalah **ANR**, paling sering dari HP OPPO. Dokumen ini adalah **daftar induk**: fitur SDK mana yang
harus di-stress test, apa yang sudah terbukti, apa yang **belum diuji**, dan hasil per perangkat.

- Dholak: `godev2/Dholak/docs/test-results/STRESS_TEST.md` (kode tes `StressTest.kt`, interstitial dilewati lewat `DebugAds` hanya debug).
- Hasil rinci per HP dan tes yang sudah berjalan (app **Darbuka**, canary): `godev1/Darbuka/docs/test-results/STRESS_TEST.md`.
  Kode tesnya: `godev1/Darbuka/app/src/androidTest/.../StressTest.kt`.
- Katalog fitur SDK: `docs/SDK_FEATURES.md`. Daftar tes SDK lain: `test.md`.
- Tiap kali stress test dijalankan di app/HP baru: tambahkan baris ke tabel "Hasil" di bawah, dan centang butir
  di "Cakupan fitur SDK". Jangan menyimpulkan "bebas ANR" dari satu app.

## Cara kerja stress test

SDK tidak punya UI sendiri; stress test dijalankan **lewat app host** (`androidTest`, di dalam proses app):

1. `Watchdog`: tiap ±25 ms mengirim pesan ke main thread dan mencatat berapa lama baru dijalankan
   (`maxStallMs`, jumlah jeda > 100/500/1000 ms). ANR input terjadi bila main thread macet > 5 dtk.
2. Beban: loop navigasi antar layar, ketukan acak beruntun (`Instrumentation.sendPointerSync`, hanya masuk ke jendela
   app sendiri), siklus background/foreground dengan `am send-trim-memory`.
3. Cek: `adb logcat -d | grep -E "ANR in|FATAL EXCEPTION"`, `ActivityManager.processesInErrorState`.
4. **Batas tes ini:** `Watchdog` hanya melihat main thread, bukan RenderThread. ANR "menunggu render" (lihat di bawah)
   tidak terlihat di `maxStallMs` sebelum macet total.

Syarat HP: layar menyala dan tidak terkunci, tidak ada pembaruan Play Services berjalan (sistem mematikan proses app
dan tes berhenti dengan `Process crashed` tanpa FATAL), tidak ada overlay game yang menutupi tepi layar.

## ANR yang dilaporkan Play Console (dicatat 2026-11-18)

Stack thread `main` tanpa satu pun frame kode app atau SDK:

```
libc syscall → futex_wait → pthread_cond_wait → std::condition_variable::wait → std::future<void>::get
libhwui RenderProxy::setStopped
android.graphics.HardwareRenderer.nSetStopped / setStopped
android.view.ViewRootImpl.performDraw → performTraversals → doTraversal → Choreographer.doFrame
```

Main thread **menunggu RenderThread** (GPU/driver/tekstur/WebView iklan/memori), jadi masalahnya di sisi render.
Penyebab belum diketahui dan belum bisa ditiru. Yang perlu dicari di Play Console: model, Android, GPU yang paling
banyak kena; terjadi saat app terbuka atau di background; hubungannya dengan iklan. Fitur SDK yang menyentuh render:
banner dan interstitial (`base/*`, `ads/*`), pratinjau/perekam video (`tutorial/*`, PixelCopy), dialog bertema
(`ui/*`), animasi dan view kustom (`animation/*`, `TrimRangeView`).

## Cakupan fitur SDK

Status: ✅ sudah ada buktinya, ◐ tersentuh sebagian, ☐ belum diuji. Kolom "Risiko ANR" = alasan fitur ini masuk daftar.

### Start app dan inisialisasi — `MyApp`, `ZaifSDKBuilder`
| Fitur | Risiko ANR | Cara stress | Status |
|---|---|---|---|
| `MyApp.onCreate` + Firebase + WorkManager di latar | kerja berat di main thread saat start | start dingin berulang, `am start -W`, `Choreographer: Skipped` | ◐ 12× OPPO (±4 dtk), 8× Realme (±3,4 dtk), build debug; belum rilis |
| WorkManager gagal init (penyimpanan rusak) | crash dari thread `WM.task-1` | `chmod 000 no_backup` lalu start | ✅ diperbaiki di 1.1.179, terbukti di Realme |
| Splash/pemuatan awal host (suara) | splash 18–31 dtk di HP lambat | ukur sampai Home di tiap HP | ◐ ukur kasar OPPO ±28–31 dtk, Realme ±18 dtk |
| Pemasangan baru / update dari Play Store | database, consent, izin baru terbentuk | pasang bersih lalu start pertama | ☐ |
| `GoogleMobileAdsConsentManager` (UMP) | dialog consent di start pertama | start pertama dengan consent aktif | ☐ |

### Iklan — `base/BaseActivityWidget`, `base/BaseGameActivity`, `ads/*`
| Fitur | Risiko ANR | Cara stress | Status |
|---|---|---|---|
| Banner (WebView iklan) | render berat, penyebab terdekat ANR render thread | banner produksi tampil saat navigasi dan ketukan beruntun | ☐ (iklan uji `No fill`, banner belum pernah dirender di tes) |
| Interstitial `showInterstitialIfAllowed` (tunggu sentuhan tenang 0,8–2,5 dtk) | polling di main thread, transisi AdActivity | ketukan beruntun saat iklan menunggu | ◐ logika benar dengan iklan tiruan (`InterstitialTimingTest`); iklan asli tampil + stress belum |
| Iklan reward, dialog "Iklan sedang disiapkan" | menunggu pemuatan, dialog | reward berulang, internet lambat | ☐ |
| Muat ulang iklan saat internet kembali (`NetworkWatcher`, `NetworkChangeReceiver`) | receiver menjalankan kerja di main thread | nyalakan/matikan internet berulang | ☐ |

### Koin, misi, hadiah — `util/CoinManager`, `rewards/*`
| Fitur | Risiko ANR | Cara stress | Status |
|---|---|---|---|
| `CoinManager` (`@Synchronized`), `DailyRewards`, `RewardsDialog` | kunci + SharedPreferences di main thread | `addCoins`/`spendCoins` dari banyak thread, buka dialog berulang | ☐ |
| `PremiumPass` (nonaktif), `PremiumPurchase`, `BillingManager` | koneksi Billing saat start | start dengan/tanpa Play Store | ☐ |

### Rekam, panel kontrol, audio — `recording/*`
| Fitur | Risiko ANR | Cara stress | Status |
|---|---|---|---|
| `InstrumentControlPanel` (REC, LIST REC, MUSIC, NOTE, VOLUME) | dialog berat, daftar besar | ketuk tombol panel berulang, buka/tutup dialog | ☐ (burst di Darbuka sengaja menghindari tombol panel) |
| `InstrumentRecorderManager`, `AudioEngine`, `RecordingAutosave` | rekam lama, autosave tulis disk | rekam 10+ menit dengan ketukan beruntun | ☐ |
| `RecordingListDialogHelper`, `RecordingTrim`, `TrimRangeView` | daftar + view kustom | 200+ rekaman, potong berulang | ☐ |
| `db/AppDatabase` (Room v6), migrasi | query di main thread, migrasi saat update | update dari versi lama dengan data besar | ☐ |
| Fokus audio (telepon, headset, Bluetooth) | callback audio | simulasikan telepon masuk saat main | ☐ |

### Export — `recording/export/*`, `tutorial/*`
| Fitur | Risiko ANR | Cara stress | Status |
|---|---|---|---|
| `RecordingExporter` MP3/MIDI (jump3r) | encoder CPU berat | export rekaman panjang, 3 kali beruntun, kembali ke app | ☐ |
| `TutorialVideoRecorder` (PixelCopy 30 fps) + `RecordingVideoExport` | PixelCopy dan encoder menekan RenderThread, **kandidat kuat ANR render** | export video panjang saat banner aktif | ☐ |
| `TutorialVideoOptionsDialog` (preview), `RecordingVideoResultDialog` | preview render | buka/tutup berulang | ☐ |

### Lagu, tutorial, musik — `tutorial/*`, `music/*`
| Fitur | Risiko ANR | Cara stress | Status |
|---|---|---|---|
| `InstrumentTutorialDialog` (daftar lagu, cari, filter) | daftar besar, unduh | gulir cepat, cari, unduh banyak lagu | ☐ |
| `MusicPlayerManager` (daftar MP3), `VolumeDialogHelper`, `RequestSongDialog` | pemutaran + dialog | putar lama, ganti lagu cepat | ☐ |

### Notifikasi dan latar belakang — `notification/*`, `service/*`
| Fitur | Risiko ANR | Cara stress | Status |
|---|---|---|---|
| `AppReminderWorker`, `CoinNotificationWorker` | Worker lambat | picu Worker dengan `cmd jobscheduler run` | ☐ |
| `FirebaseMessageReceiver` (FCM) | `onMessageReceived` tidak boleh lama | kirim banyak pesan FCM | ☐ |
| `service/BackgroundService` (`startForeground`) | `startForeground` harus dipanggil cepat setelah `startForegroundService`, bila tidak: ANR/crash | mulai dari background di Android 12+ | ☐ |
| `NotificationThrottle` | — | — | ☐ |

### Tampilan lain — `ui/*`, util
| Fitur | Risiko ANR | Cara stress | Status |
|---|---|---|---|
| Dialog `immersive()`, `setupHideStatusBar`, fullscreen (1.1.177) | transisi bar dan insets berulang | buka/tutup dialog 100× | ◐ dialog tidak diuji berulang; navigasi 48× lulus |
| `ReportBugDialog`, `KritikSaranDialog`, layar admin | Firestore/jaringan di dialog | kirim berulang tanpa internet | ☐ |
| `LanguageManager` (19 bahasa), font besar, mode gelap, multi-window | layout ulang | ganti bahasa saat app terbuka | ☐ |
| `InAppUpdateHelper` | koneksi Play Core | buka app saat update tersedia | ☐ |
| `ZaifAnalytics` | event banyak | ratusan event cepat | ☐ |

### Kondisi sistem (berlaku untuk semua fitur)
- ☐ Memori hampir penuh sungguhan; ☐ proses dimatikan lalu dibuka lagi (process death); ☐ tanpa internet atau lambat;
  ☐ HP panas / mode hemat daya; ☐ pemakaian 30 menit sampai beberapa jam; ☐ **build rilis** (semua angka sekarang debug).

## Hasil per app dan HP

Gunakan format yang sama di tiap baris. Rincian lengkap ada di dokumen stress test app yang bersangkutan.

| App | HP | Android | Build | Tes | Hasil | Jeda main thread terpanjang | Tanggal |
|---|---|---|---|---|---|---|---|
| Darbuka | OPPO CPH2591 | 15 | debug | navigasi 48×, 1800 ketukan, 10 siklus bg/fg | lulus, tanpa ANR | 733 ms | 2026-11-18 |
| Darbuka | Realme RMX3261 | 11 | debug | sama | lulus, tanpa ANR | 433 ms | 2026-11-18 |
| Darbuka | vivo I2401 (Snapdragon, 15 GB) | 16 | debug | navigasi, 900 ketukan, bg/fg | lulus, tanpa ANR | 227 ms | 2026-11-18 |
| Darbuka | Xiaomi Mi Max 2 | 7.1.1 | debug | navigasi, 1800 ketukan, bg/fg | lulus, tanpa ANR | 1614 ms (ketukan) | 2026-11-18 |
| Darbuka | Samsung J2 Prime SM-G532G (MT6737T / Mali-T720, 1,4 GB) | 6.0.1 | debug | navigasi, 900 ketukan, bg/fg, heap 20× | lulus, tanpa ANR, **jeda 3097 ms** | **3097 ms (terburuk)** | 2026-11-18 |
| Darbuka | HP MediaTek/Mali kelas bawah | — | — | — | ☐ belum | — | — |
| Darbuka | OPPO CPH2591 | 15 | **rilis** | semua | ☐ belum | — | — |
| Belira | OPPO A18 CPH2591 (MT6769V / Mali-G52 MC2, 3,7 GB) | 15 | debug, SDK 1.1.183 | navigasi 60× (6 layar), 2700 ketukan, 10 siklus bg/fg | lulus, tanpa ANR | 399 ms | 2026-11-19 |
| Belira | OPPO A18 CPH2591 | 15 | debug, cabang `main` (SDK 1.1.171, versi Play 1.166) | sama | lulus, tanpa ANR (hampir sama dengan SDK 1.1.183) | 373 ms | 2026-11-19 |
| Belira | OPPO A18 CPH2591 | 15 | **rilis** | semua | ☐ belum | — | — |
| Dholak | OPPO A18 CPH2591 | 15 | debug, SDK 1.1.186, interstitial dilewati | navigasi 60×, 900+300 ketukan, Theka 30×, 10 siklus bg/fg | lulus, tanpa ANR | 470 ms | 2026-11-22 |
| Dholak | OPPO A18 CPH2591 | 15 | **rilis** (R8, SDK 1.1.186) | cek manual: start, Main, Theka, 5× bg/fg, Belajar | ◐ lulus tanpa crash/ANR; bukan stress test otomatis | tidak diukur | 2026-11-22 |
| app lain yang memakai SDK (±48) | — | — | — | — | ☐ belum (Darbuka dan Belira dulu) | — | — |

## Temuan yang berasal dari SDK atau berlaku untuk semua app (dari stress test Darbuka)

1. **Crash WorkManager saat penyimpanan rusak** (`SQLITE_CANTOPEN` dari `WM.task-1`): diperbaiki di 1.1.179
   (`MyApp.workManagerConfiguration`). Berlaku untuk semua app yang memakai `MyApp`.
2. **Overlay Game Assistant ColorOS (`GamesFloatBar`)** menelan sentuhan di ±48 px kiri layar (24 dp di 320 dpi) di
   OPPO/Realme untuk app yang dikenali sebagai game. Tombol di tepi kiri tidak bisa diketuk. Darbuka sudah diberi
   margin 28sdp. **Berlaku untuk semua app instrumen:** cek tombol di tepi kiri (Kembali, MUSIK, REC, LIST REC, VOLUME)
   dan beri jarak ≥ 28sdp, atau pindahkan dari 48 px kiri. Panel kontrol SDK (`InstrumentControlPanel`) menempel di
   tepi kiri dan **belum dicek**.
3. **Toast debug "Banner gagal"** (`setToast(true)`) menutupi tengah layar dan menolak ketukan injeksi; hanya build debug.
   Pastikan `setToast(true)` tidak ikut ke rilis (sudah tercatat di `SDK_FEATURES.md`, DarbukaPad).
4. **Heap "bocor" di tes bg/fg = artefak tes, bukan SDK** (Mi Max 2, 2026-11-18): kembali ke app dengan `startActivity` membuat
   Activity baru tiap siklus (heap 36 → 138 MB, 11 `GameActivity` di heap dump). Dengan `AppTask.moveToFront()` heap datar
   (46 → 49 MB). **Untuk tes di app lain:** pakai `moveToFront()`, bukan `startActivity`, dan bandingkan heap dump
   (`Debug.dumpHprofData`) per kelas sebelum menyimpulkan ada kebocoran. Tes `heapProbeSingle` ada di StressTest Darbuka.
5. **HP MediaTek/Mali RAM kecil paling dekat ke ANR**: Samsung J2 Prime (Android 6, 1,4 GB) macet main thread hingga 3,1 dtk
   (navigasi) dan 2,4 dtk (bg/fg), 17 dan 32 kali > 1 dtk, 4× lebih buruk dari HP lain; batas ANR 5 dtk. Semua app SDK
   perlu diuji di HP sekelas ini. Penyebab belum ditelusuri; build rilis belum diukur.
6. Gangguan lingkungan: Play Services yang sedang update membuat sistem mematikan proses app; ulangi tes setelah selesai.
7. **Belira juga kena ANR render thread di Play** (2026-11-19): Oppo A18 (CPH2591), Android 15, versi 166 (SDK 1.1.171), stack
   sama persis dengan Darbuka (`RenderProxy::setStopped`, main thread menunggu RenderThread). Dua app berbeda, HP sama, jadi
   penyebab ada di sisi SDK/iklan/render atau HP itu, bukan di kode satu app. Chipset HP itu kini tercatat: **MediaTek
   MT6769V/CZ (Helio G85 kelas) + Mali-G52 MC2, GLES 3.2** (sebelumnya "GPU belum dicatat"). Stress test Belira di HP yang
   sama (debug, SDK 1.1.183) **tidak menirunya**: jeda terburuk 399 ms. Cabang `main` Belira (SDK 1.1.171, = versi Play) di HP yang sama juga lulus (373 / 296 / 345 ms), jadi SDK baru bukan penyebab bedanya. Frame pertama sesudah kembali dari latar/masuk
   layar bermain 350–650 ms (build debug), pola yang cocok dengan trace tapi masih jauh dari 5 dtk. Rincian dan daftar
   yang belum diuji ada di `Belira/docs/test-results/STRESS_TEST.md`.
8. **Harness lintas app:** `StressTest.kt` Belira berdiri sendiri (tanpa kelas dasar UI app) dan bisa jadi contoh untuk ±48
   app lain: `ActivityScenario` + `NavController.navigate` + `Watchdog` + `AppTask.moveToFront()`. Cukup ganti daftar layar.
   Bila app memakai Compose untuk Home, navigasi lewat NavController tidak menguji klik kartu dan interstisial di jalur klik.
9. **Peluncur ikon build debug bisa membuka LeakCanary**, bukan app (Belira: `LeakLauncherActivity`); buka
   `<paket>/.ui.activity.GameActivity` langsung. ColorOS menampilkan layar "Pengujian keamanan" (com.heytap.market) setelah
   `adb install`; tutup dengan BACK sebelum tes.
