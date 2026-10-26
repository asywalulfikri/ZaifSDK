# Changelog — recording-sdk (`com.sound.recorder:recording-sdk`)

## 0.0.65

### ⚠️ Wajib dibaca sebelum update app host
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
  - Untuk merender suara pad ke MP3, host memasang `RecordingExporter.resolver` (lihat `RecordingExportResolver`).
  - Set `resolver` ke `null` di `onDestroyView` supaya tidak terjadi leak.
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
