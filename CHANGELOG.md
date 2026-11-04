# Changelog — recording-sdk (`com.sound.recorder:recording-sdk`)

## 0.0.65

### ⚠️ Wajib dibaca sebelum update app host
- **Premium Pass dimatikan secara bawaan** (`PremiumPass.isEnabled = false`). Kartu Premium Pass hilang dari
  dialog Misi dan `PremiumPass.redeem()` selalu gagal, jadi koin tidak bisa lagi ditukar dengan 2 hari tanpa iklan.
  **Alasan:** terlalu murah dibanding pendapatan iklan yang hilang. Pass yang sudah ditukar tetap berlaku sampai
  habis (kartu hanya menampilkan sisa waktu, tanpa tombol perpanjang). Untuk menyalakan lagi: `PremiumPass.isEnabled = true`.
  App yang dulu punya Premium Pass sendiri (Belira) pindah ke `PremiumPass` SDK dan memanggil
  `PremiumPass.importFrom(context, "premium_pass")` sekali di thread background: pass yang masih berjalan,
  termasuk hitungan penukaran, disalin ke SDK lalu data lama dihapus. Data SDK yang sudah ada tidak ditimpa.
- **`CoinManager.addCoins` kini `@Synchronized`** (sama dengan `spendCoins`), supaya koin yang ditambah dan dipotong
  bersamaan tidak saling menimpa saldo. Belira pindah dari `CoinManager` dan `CoinNotificationWorker` miliknya ke versi SDK
  (data saldo sama: `zaif_coins` / `balance`).
- **Kartu "Koin untuk apa?"** (`CoinUses.list(context)`, data `CoinUse`): buka lagu (`CoinManager.UNLOCK_COST`),
  export lagu (`RecordingExporter.exportCoinCost`), dan video tanpa watermark (30–50, hanya app tanpa pembelian dengan
  `isVideo`). Harga dibaca dari pengaturan SDK. Menggantikan kartu "Export pakai koin" di dialog Misi SDK; panel Misi
  buatan host (Belira, Compose) memakai daftar yang sama. Teks baru 19 bahasa: `coin_uses_title`, `coin_use_unlock_song`,
  `coin_use_export`, `coin_use_video`.
- **Database rekaman naik ke versi 6** (sebelumnya 5). Migration otomatis dan aman:
  - **v5 → v6:** hanya menambah kolom `musicJson` (nullable), data lama tidak disentuh.
  - **v1–v4 → v5:** tabel dibangun ulang dan rekaman lama disalin, tidak dihapus lagi seperti versi SDK sebelumnya.
  - Sudah diuji di device: update dari v5 → semua rekaman identik.
  - **Downgrade** (APK lama dipasang di atas versi baru) tetap me-reset database, sama seperti sebelumnya. Lewat Play Store hal ini tidak terjadi.
- **App host wajib dikompilasi ulang:** `RecordingEntity` mendapat field baru `musicJson` (default `null`). Kode sumber host tidak perlu diubah.
- **Sebelum build SDK, commit file-file ini:**
  - `soundrecorder/consumer-rules.pro` (dirujuk `build.gradle`; tanpa file ini build gagal);
  - skema Room `soundrecorder/schemas/sound.recorder.widget.recording.database.AppDatabase/5.json` dan `6.json`.
- Dependency baru: `de.sciss:jump3r:1.0.5` (encoder MP3, LGPL).
- Permission baru: `WRITE_EXTERNAL_STORAGE` dengan `maxSdkVersion=28`, untuk menyimpan hasil export di Android 9 ke bawah.

### 🎁 Jatah export gratis & premium
- **Export gratis naik dari 1x jadi 3x per hari**, untuk export suara (`RecordingExporter.freeExportsPerDay = 3`)
  dan video (`TutorialVideoQuota.freePerDay = 3`, sebelumnya angka 1 ditulis langsung di kode).
  - **Alasan:** sampai SDK 1.1.136 export suara gratis tanpa batas. Setelah export diberi iklan reward
    (gratis hanya 1x sehari), pendapatan turun ±20% padahal eCPM dan rate iklan tetap bagus. Artinya
    jumlah tayangan yang turun, kemungkinan karena user kesal lalu lebih jarang main. Rekaman pertama
    sering belum bagus, jadi 1x sehari terlalu sedikit. Video tutorial memakai watermark nama app,
    jadi tiap video yang dibagikan sekaligus jadi promosi gratis.
  - Awalnya direncanakan 2, lalu diputuskan pemilik app jadi 3 (2026-11-04). Pantau event
    `reward_ad_result` dan retensi 1–2 minggu; kalau pendapatan iklan reward turun banyak, turunkan per app.
  - Tiap app tetap bisa mengatur sendiri, misalnya `freeExportsPerDay = 1`. 0 = selalu wajib iklan.
  - Baru: `RecordingExporter.freeExportsLeft(context)` untuk sisa jatah hari ini. Dialog export SDK
    sekarang menampilkan sisanya.
  - Jatah video yang sudah dipakai hari ini dengan SDK lama dihitung 1 (data lama tetap terbaca).
- **Teks:** `export_first_free` kini tanpa angka ("Export gratis hari ini, tanpa iklan") karena dipakai
  langsung oleh Dholak dan Gendang Elektronik. Teks baru `export_free_left` (dengan angka sisa).
  `tutorial_video_free_hint` sekarang berisi `%1$d`, jadi panggil dengan angka jatah. Semua di 19 bahasa.
- **Notif klaim koin tidak muncul lagi untuk pembeli premium (Remove Ads).** Baru: `PremiumPurchase`
  (`sound.recorder.widget.rewards`).
  - **Alasan:** pembeli premium tidak memakai misi/koin, jadi notif "klaim koin" hanya mengganggu.
    Pengingat buka app (`AppReminderWorker`) **tetap muncul** untuk semua user.
  - **App host wajib memanggil** `PremiumPurchase.setPurchased(context, true)` saat pembelian terbaca
    (saat app dibuka, di callback `BillingManager`, setelah `checkHistory()`). SDK tidak tahu status beli
    karena disimpan di prefs milik app. Tanpa panggilan ini perilakunya sama seperti sebelumnya.
  - Cukup memanggil `CoinNotificationWorker.cancel()` tidak cukup: SDK menjadwalkannya lagi tiap app dibuka.
  - Premium Pass (tukar koin) tidak dihitung sebagai pembelian.
- Aturan lengkap (termasuk menyembunyikan tombol Misi untuk pembeli): `docs/MISSIONS_PREMIUM_RULE.md`.
- **Video saat jatah gratis habis — HANYA untuk app tanpa pembelian Remove Ads/premium.** Flag baru
  `ZaifSDKBuilder.hasPremiumPurchase(false)` (default `true`, jadi app lain tidak perlu diubah). Darbuka memanggilnya.
  App dengan pembelian yang lupa mengisi `onPremiumPurchased` hanya melihat "coba lagi besok", bukan iklan/koin.
  Detail:
  dialog kini menawarkan **tonton iklan** dan **bayar koin** (`RecordingExporter.exportCoinCost`, bila
  `isCoin`). Hasilnya tetap versi gratis (watermark). Tanpa iklan dan koin, dialog bilang "coba lagi besok".
  - **Alasan:** sebelumnya dialog "Premium" muncul tanpa tombol beli, jadi user mentok.
  - App yang punya pembelian tidak berubah (tetap dialog Premium + tombol beli).
  - Baru: parameter `onRequestReward` di `RecordingVideoExport.option`; diisi otomatis oleh
    `InstrumentControlPanel` (lewat `adRequestListener`, tipe `"export"`) dan `InstrumentTutorialDialog`.
    User Premium Pass juga boleh menonton iklan untuk video, karena pass tidak membuka video.
  - Teks baru di 19 bahasa: `tutorial_video_limit_title`, `tutorial_video_limit_message`, `tutorial_video_limit_tomorrow`.
  - **Pilihan tanpa watermark: 50 koin** (`TutorialVideoQuota.noWatermarkCoinCost`, 0 = disembunyikan), di dialog yang
    sama dan hanya untuk app tanpa pembelian. **Alasan:** koin dari misi jadi lebih berharga, sementara pilihan
    10 koin / iklan tetap memakai watermark (promosi gratis). Video berbayar ini tidak memakai jatah gratis.
    Teks baru: `tutorial_video_no_watermark` (19 bahasa).
  - **Switch "Hapus watermark" di dialog custom video** (app tanpa pembelian + `isCoin`). Selama jatah gratis
    masih ada: `TutorialVideoQuota.removeWatermarkCoinCost` (default 30, 0 = disembunyikan). Setelah jatah habis
    (video dibuka lewat iklan): harga `noWatermarkCoinCost` (50); sudah bayar 10 koin → switch tinggal 40,
    jadi total tanpa watermark selalu 50. Koin switch baru dipotong saat tombol Mulai (tombol menampilkan
    harganya); saldo kurang → toast dan switch kembali mati. Switch tidak diingat, selalu mati saat dialog dibuka.
    **Video 30 koin tetap memakai satu jatah gratis** (`TutorialVideoStyle.usesFreeSlot`): koin hanya membayar
    hapus watermark. Tanpa ini user yang selalu bayar 30 tidak pernah menghabiskan jatah, jadi harga 50 tidak berlaku.
  - **Koin video dikembalikan bila batal / gagal** (10, 50, maupun switch): dialog custom video ditutup tanpa
    Mulai, rekaman dibatalkan, atau gagal dimulai / disimpan. Analytics: `coin_earned` dengan source `refund`.
    Teks baru: `tutorial_video_remove_watermark_coins` (19 bahasa).
  - **Teks petunjuk dialog custom video tidak lagi menyebut Premium di app tanpa pembelian**:
    `tutorial_video_free_hint_coins` ("…Hapus pakai koin") bila switch tampil, `tutorial_video_free_hint_basic`
    bila koin mati. App dengan pembelian tetap `tutorial_video_free_hint`. 19 bahasa.

### ✨ Fitur baru
- **Lagu (MP3) ikut terekam.** Saat merekam sambil memutar lagu dari daftar musik, yang dicatat adalah lagu, posisi, volume, serta kapan lagu diputar, dijeda, digeser, atau diganti.
  - **Saat rekaman diputar ulang:** lagu berbunyi sinkron dengan ketukan. Lagu di daftar musik dijeda otomatis supaya tidak bertabrakan.
  - **Saat export MP3:** lagu di-mix langsung dari file aslinya, sehingga jernih.
  - **Mode yang menyimpan lagu:** *alat musik saja* dan *mic + earphone*. Mic lewat speaker tidak menyimpan lagu karena mic sudah menangkapnya (supaya tidak dobel).
  - **Lagu bawaan app** disimpan dengan nama resource, bukan ID, karena ID berubah tiap build. Lagu yang sudah tidak ada atau izinnya dicabut dilewati, dan rekaman tetap diputar.
- **Potong rekaman (trim):** di daftar rekaman ada tombol *Potong*. Geser dua pegangan untuk memilih bagian yang disimpan; posisi ketukan dan lagu terlihat di penggesernya.
  - Hasilnya disimpan sebagai **rekaman baru** "<nama> (potongan)". Rekaman asli tidak berubah.
  - Audio mic dipotong dengan remux (tanpa encode ulang): cepat, kualitas tetap, dan file 3GP lama ikut menjadi `.m4a`.
  - Ketukan dan lagu ikut dipangkas serta digeser.
  - **Tombol "▶ Putar bagian ini"** di dialog Potong: hanya bagian yang dipilih yang diputar (ketukan, audio mic, dan lagu), dengan garis posisi yang bergerak di penggeser. Pratinjau berhenti sendiri di titik akhir, dan juga berhenti saat pegangan digeser, Simpan ditekan, atau dialog ditutup. Panel memasangnya lewat parameter opsional baru `onPreview` / `onStopPreview` di `RecordingListDialogHelper.show()`. Kalau `null`, tombol tidak ditampilkan, jadi pemanggil lama tidak berubah.
- **Rekam lapis (overdub):** di daftar rekaman ada tombol *Rekam lapis*. Rekaman dasar diputar (ketukan, audio mic, dan lagu), lalu ketukan baru direkam di atasnya.
  - Hasilnya disimpan sebagai **rekaman baru** "<nama> + lapis". Rekaman dasar tidak berubah.
  - Audio mic rekaman dasar **disalin** ke file sendiri, sehingga menghapus salah satu rekaman tidak merusak yang lain. BATAL tidak pernah menghapus audio rekaman dasar.
  - Ketukan dari pemutaran ulang tidak ikut terekam, walaupun host memanggil `recordEvent` dari `onPlaybackEvent`.
  - **Rekaman dasar lewat speaker:** suara pad sudah ada di audio mic, jadi hanya ketukan baru yang disimpan dan pad tidak di-mute.
  - Versi ini baru mendukung lapisan berupa ketukan alat musik; rekam lapis dengan mic belum didukung.
  - `RecordingListDialogHelper.show()` mendapat parameter opsional `onOverdub`. Kalau `null`, tombolnya disembunyikan. Overload lama tetap ada.
- **Nada panjang (pianika, suling, dan instrumen lain yang memakai `OFF`)** tidak lagi hilang atau menggantung:
  - **potong:** nada yang ditahan melewati titik awal dibunyikan ulang di awal potongan, dan nada yang masih ditahan di titik akhir diberi `OFF`;
  - **stop rekam saat nada masih ditahan:** diberi `OFF` otomatis (termasuk rekaman yang dipulihkan dari draft);
  - **Stop saat putar ulang / akhir rekam lapis / akhir pemutaran rekaman lama:** host dikirimi `OFF` untuk nada yang masih berbunyi. Saat layar ditutup tidak ada callback ke host;
  - **perkusi (tanpa `OFF`) tidak berubah sama sekali.**
- **Ikon lagu di daftar rekaman:** rekaman yang berisi lagu menampilkan "🎵 judul lagu".
- **Autosave rekaman:**
  - draft ditulis tiap 5 detik selama merekam; kalau app mati mendadak, rekaman dipulihkan saat layar instrumen dibuka lagi (dengan toast);
  - dialog simpan yang ditutup lewat Back atau tap di luar, atau keluar dari layar saat merekam, sekarang **disimpan otomatis** dengan nama "Simpan otomatis <tanggal>";
  - tombol **BATAL** tetap membuang rekaman.
- **Export rekaman ke MP3 dan MIDI**, lalu bisa dibagikan (chooser atau WhatsApp) atau disimpan ke perangkat.
  - **1x export gratis per hari tanpa iklan**; export berikutnya di hari yang sama meminta rewarded ad. Jatah di-reset setiap ganti tanggal (waktu lokal HP) dan baru terpakai kalau export berhasil (export yang gagal atau dibatalkan tetap gratis). Atur dengan `RecordingExporter.freeExportsPerDay` (default `1`, `0` = selalu pakai iklan).
  - Untuk merender suara pad ke MP3, host memasang `RecordingExporter.resolver` (lihat `RecordingExportResolver`).
  - Set `resolver` ke `null` di `onDestroyView` supaya tidak terjadi leak.
  - **Tanpa `resolver`, MP3 tidak bisa berisi suara pad.** Rekaman *alat musik saja* hanya bisa diekspor ke MIDI (dibunyikan synth HP, jadi suaranya beda); rekaman *mic + earphone* hanya berisi suara mic.
  - `ExportSample.rate` (default `1`): untuk host yang menggeser pitch sampel (`rate` di `SoundPool.play()` atau engine native), isi dengan `2^(semitone/12)` dikali pitch global.
  - Samakan `RecordingExporter.maxPolyphony` dengan batas suara di engine host.
- **Notifikasi pengingat buka app** tiap 2 hari, pukul 20:30–21:30.
  - Teks ucapan mengikuti bahasa HP (17 bahasa) dan jam.
  - Tidak muncul di jam tenang (22:00–08:00).
- Komponen `EqualizerBarsView` untuk dialog volume dan daftar musik.

### 🎙️ Peningkatan fitur rekam
- **Kualitas mic:** AAC 44,1 kHz / 128 kbps `.m4a` (sebelumnya 3GP sekitar 8 kHz). Device yang tidak mendukung otomatis kembali ke setelan lama.
- **Sinkron mic dan ketukan:** ketukan disejajarkan dengan saat mic benar-benar mulai, dan saat diputar ulang ketukan menunggu audio siap. Jam rekaman memakai `elapsedRealtime`, jadi tidak lompat walau jam HP berubah.
- **Durasi rekaman mic** memakai panjang audio aslinya, bukan hanya ketukan terakhir.
- **File mic dari rekaman yang dibatalkan** (atau rekaman tanpa ketukan) dihapus, supaya penyimpanan tidak menumpuk.

### 🎨 Tampilan
- **Dialog volume** lebih hidup: tile ikon bergradasi dan equalizer yang bergerak saat slider digeser.
- **Dialog daftar MP3** didesain ulang (header, tab, pencarian, kartu player) dan tampil immersive, tanpa nav bar. Logika tidak berubah.
- **Dialog daftar rekaman** dan **bottom sheet catatan** mendapat tampilan baru.

### 🐛 Perbaikan
- **Iklan reward tidak perlu ditekan ulang.** Kalau iklan belum siap, muncul dialog "Iklan sedang disiapkan…" (dengan tombol Batal), lalu iklan tampil otomatis begitu selesai dimuat. Kalau AdMob gagal dimuat atau lebih dari 8 detik padahal internet hidup, reward tetap diberikan. Kalau internet mati atau terjadi error jaringan (termasuk karena pemblokir iklan), **tidak ada reward** dan muncul pesan "tidak ada koneksi internet". Batal, Back, atau meninggalkan app membatalkan tanpa reward. Berlaku untuk semua pemakai `showRewardedAd` (export, buka lagu, dan lain-lain); API tidak berubah.
- **Export di daftar tutorial** (lagu lokal dan online), memakai dialog export yang sama:
  - premium tanpa iklan;
  - lagu yang baru dibuka dengan iklan atau koin tidak diminta iklan lagi;
  - metadata lagu lokal kosong, sama seperti saat lagu dimainkan;
  - `OFF` di akhir not hanya untuk `isSustained = true`. Default `showLocal(isSustained)` kini `false`; sebelumnya parameter ini tidak dipakai.
- **Terjemahan:** toast "Recording failed" dan label sumber lagu (ASSET / STORAGE) di daftar musik kini mengikuti bahasa HP (19 folder bahasa).
- **Notifikasi koin:**
  - tidak lagi gagal saat dijadwalkan tanpa data input;
  - hanya muncul kalau bonus harian memang bisa diklaim dan notifikasi diizinkan;
  - dijadwalkan ulang setelah klaim;
  - hanya aktif untuk app dengan `isCoin = true`.
- **Iklan:** callback interstitial dan rewarded tidak lagi menahan referensi kuat ke Activity (memory leak).
- **Catatan lokal:** `DatabaseHelper` kini singleton (`getInstance`), memperbaiki error "attempt to re-open an already-closed object". Konstruktor lama tetap ada, tapi deprecated.

### 🧪 Pengujian
- **Unit test:** 24 test lolos (`MidiFileWriterTest`, `StreamResamplerTest`, `MusicRecordTrackerTest`, `RecordingTrimTest`, `SustainNotesTest`).
- **Diuji di device (Android 16):**
  - migration database;
  - autosave dan pemulihan setelah app dimatikan (force-stop);
  - Batal dan Simpan;
  - rekam dengan lagu, putar ulang, dan export MP3 dengan lagu;
  - mic HQ, termasuk fallback ke rekaman lama;
  - potong rekaman (ketukan + lagu, m4a, 3GP) dan pemutaran hasilnya;
  - rekam lapis di atas rekaman ketukan + lagu, mic + earphone, dan mic lewat speaker; BATAL; pemulihan setelah app mati di tengah rekam lapis.
- **Diuji di HP low-end Realme C21Y (2,8 GB, Android 11):**
  - update dari build lama (DB v5 → v6);
  - lagu dari penyimpanan HP (content URI);
  - mic HQ, potong, rekam lapis;
  - frame tersendat < 0,6%, p99 ≤ 13 ms;
  - memori stabil.
- **Belum diuji:**
  - nada panjang di app pianika/suling sungguhan;
  - device Android 5–8;
  - update dari versi Play Store pada app host.
