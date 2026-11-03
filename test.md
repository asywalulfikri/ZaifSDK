# Daftar Tes ZaifSDK

Terakhir diperbarui: 2 November 2026. Versi SDK: `1.1.162`.

Berisi fitur SDK yang dikerjakan dari tag `1.1.153` sampai `1.1.162`. Semua tes dilakukan lewat app Pianika, karena SDK tidak berjalan sendiri. HP yang dipakai: Realme RMX3261 (Android 11), Samsung SM-G532G (Android 6), dan vivo I2401 (Android 16).

**Status tes**

- **Sudah:** sudah dites sampai selesai.
- **Sebagian:** baru sebagian yang dites. Lihat kolom Catatan.
- **Belum:** belum dites.

**Hasil**

- **Normal:** berjalan sesuai harapan.
- **Tidak normal:** ada masalah.
- **Belum tahu:** belum ada cukup hasil untuk menilai.

## Wajib sebelum tag dipakai app

| No | Yang dites | Status tes | Hasil | Catatan |
|---|---|---|---|---|
| 1 | SDK `1.1.158` tersedia di JitPack | Sudah | Normal | File pom dan aar bisa diunduh |
| 2 | Teks baru ada di 19 bahasa | Sudah | Normal | Dicek untuk teks dialog update |
| 3 | Kode release Pianika berhasil dikompilasi dengan `1.1.158` | Sudah | Normal | |
| 4 | Tes di app selain Pianika | Sebagian | Belum tahu | Lira, Gendang, dan Dholak dites di Realme, 2 November 2026. Darbuka dan lainnya belum |

## BaseGameActivity dan iklan

| No | Yang dites | Status tes | Hasil | Catatan |
|---|---|---|---|---|
| 5 | Banner dan interstitial tampil | Sudah | Normal | Memakai iklan tes |
| 6 | Iklan reward memberi hadiah | Sudah | Normal | Memakai iklan tes |
| 7 | AdMob dimulai setelah consent | Sebagian | Belum tahu | Iklan tampil, tapi urutannya belum dicek di wilayah yang wajib consent |
| 8 | Internet kembali: iklan dimuat ulang | Belum | Belum tahu | |
| 9 | Banner setelah app ke background lalu kembali | Sebagian | Normal | Banner tampil lagi saat kembali ke layar game |
| 10 | Migrasi app lain ke BaseGameActivity | Sebagian | Belum tahu | Lira sudah pindah, belum di-commit. Dengan Wi-Fi, banner, interstitial, dan iklan reward tampil di Realme tanpa crash. Dengan data seluler yang timeout, iklan tidak tampil. Pianika masih memakai kode sendiri |

## Export video

| No  | Yang dites                                                               | Status tes | Hasil      | Catatan                                                                                                                        |
|-----|--------------------------------------------------------------------------|------------|------------|--------------------------------------------------------------------------------------------------------------------------------|
| 11  | Export video dari rekaman                                                | Sudah      | Normal     | Realme 220 frame dalam 7,29 detik. Samsung sekitar 29 fps, vivo sekitar 30 fps                                                 |
| 12  | Export video dari tutorial                                               | Sudah      | Normal     | Dites di vivo lewat `EKSPOR` → `VIDEO`                                                                                         |
| 13  | Tombol biru `VIDEO` hanya di build debug                                 | Sudah      | Normal     | Tanpa dialog dan tanpa watermark besar. Sejak `1.1.159` merekam full layar tanpa banner dan `REC`, dites di Lira               |
| 13a | Pilihan `fullScreen` di `TutorialVideoExport`                            | Sebagian   | Normal     | Sejak `1.1.159`, bawaan mati. Dites di Lira dari `LIST REC`. `EKSPOR` tutorial dan batal saat merekam belum dites              |
| 13b | Preview dialog `Atur tampilan video` sesuai alat musik                   | Sebagian   | Normal     | Dulu selalu tuts piano. Sekarang memakai gambar alat musik yang dibuka. Sejak `1.1.160`. Dites di Gendang                      |
| 13c | Video full layar 30 fps lewat `PixelCopy`, audio berhenti bersama gambar | Sebagian   | Normal     | Dites di Dholak dan Gendang (Realme) Sejak `1.1.162`. Vivo belum. Android 7 ke bawah tetap memakai cara lama |
| 14  | Watermark terbaca                                                        | Sudah      | Normal     | Terbaca di Samsung dan vivo                                                                                                    |
| 15  | Dialog premium pada video kedua                                          | Sebagian   | Normal     | Dites di vivo. `Beli Premium` tidak ditekan                                                                                    |
| 16  | Keluar dari app saat rekam                                               | Sudah      | Normal     | Thread berhenti, file sementara terhapus                                                                                       |
| 17  | Layar mati saat rekam                                                    | Sudah      | Normal     | Dites di vivo. Rekaman batal, jatah tidak terpakai                                                                             |
| 18  | Encoder tidak menahan main thread                                        | Sebagian   | Normal     | Crash 0 dan ANR 0 selama tes. Belum ada data dari Android vitals                                                               |
| 19  | Encoder macet: export gagal setelah 10 detik                             | Belum      | Belum tahu | Tidak bisa dibuat secara sengaja di HP                                                                                         |
| 20  | Rekaman video panjang                                                    | Belum      | Belum tahu |                                                                                                                                |
| 21  | Sinkron suara dan gambar                                                 | Belum      | Belum tahu | Perlu ditonton dan didengar langsung                                                                                           |
| 22  | Jatah gratis kembali keesokan harinya                                    | Belum      | Belum tahu |                                                                                                                                |

## Export suara

| No | Yang dites | Status tes | Hasil | Catatan |
|---|---|---|---|---|
| 23 | Penanda `OFF\|alat\|suara` dikenali export | Sudah | Normal | Sejak `1.1.156`. Volume MP3 diukur di vivo, dan hasil export dites langsung oleh pemilik app |
| 24 | Penanda `OFF` polos tetap dikenali | Sebagian | Normal | Dicek lewat video Basuri di Samsung. Pianika Pro belum diukur |
| 25 | Export MIDI memakai penanda yang sama | Belum | Belum tahu | Kodenya ikut diubah, hasilnya belum dibuka |
| 26 | Export MP3 tersimpan | Sudah | Normal | Dites di vivo |
| 27 | Tombol Bagikan dan WhatsApp | Sebagian | Normal | Keduanya terbuka, tapi tes tidak mengirim apa pun |

## Potong rekaman

| No | Yang dites | Status tes | Hasil | Catatan |
|---|---|---|---|---|
| 28 | Buka Potong saat rekaman diputar | Sudah | Normal | Sejak `1.1.157`. Putaran langsung berhenti, dites di vivo |
| 29 | Pratinjau `Putar bagian ini` | Sudah | Normal | Dites di vivo |
| 30 | Geser ujung saat pratinjau berjalan | Sudah | Normal | Pratinjau berhenti, dan ini memang disengaja |
| 31 | Stop pratinjau saat alat berbeda | Sudah | Normal | Dites langsung oleh pemilik app. Perbaikannya ada di kode Pianika, bukan SDK |
| 32 | Stop saat putaran baru disiapkan | Belum | Belum tahu | Sulit dipicu dengan tangan, baru dicek lewat kode |
| 33 | Simpan hasil potongan | Belum | Belum tahu | Tes tidak menyimpan rekaman |

## Update dalam app

| No | Yang dites | Status tes | Hasil | Catatan |
|---|---|---|---|---|
| 34 | Dialog `Pasang` dan `Nanti` setelah unduhan selesai | Belum | Belum tahu | Sejak `1.1.158`. Butuh versi lebih baru di Play Store |
| 35 | `Nanti`: tidak ditanya lagi sampai app dibuka ulang | Belum | Belum tahu | Sama seperti nomor 34 |
| 36 | App dibuka ulang: dialog muncul lagi | Belum | Belum tahu | Sama seperti nomor 34 |
| 37 | Unduhan selesai saat app di background | Belum | Belum tahu | Dialog seharusnya muncul saat app kembali |

## Misi, koin, dan Premium Pass

| No | Yang dites | Status tes | Hasil | Catatan |
|---|---|---|---|---|
| 38 | Misi, koin, dan iklan reward | Sudah | Normal | Dites langsung oleh pemilik app |
| 39 | Progres misi rekaman dan klaim koin | Sudah | Normal | Koin naik dari 9 ke 14 |
| 40 | Tukar 50 koin dengan Premium Pass | Belum | Belum tahu | Video tetap harus dibatasi 1x sehari |
| 41 | Pembelian asli | Belum | Belum tahu | Tes lewat Internal Testing di Pianika |

## Layar admin

| No  | Yang dites                                            | Status tes | Hasil        | Catatan                                                                        |
|-----|-------------------------------------------------------|------------|--------------|--------------------------------------------------------------------------------|
| 42  | Tombol Salin                                          | Sudah      | Normal       | Dites pemilik app di vivo dan Samsung                                          |
| 43  | Tombol Info Perangkat                                 | Sebagian   | Belum tahu   | Di Kritik Saran normal, di Bug Reports belum ditekan                           |
| 44  | Judul layar Bug Reports                               | Sudah      | Tidak normal | Masih tertulis "Song Request Admin"                                            |
| 45  | Warna tombol Reply dan Done                           | Sudah      | Tidak normal | Sulit dibaca karena menyatu dengan latar                                       |
| 45a | `DelayInfoDialog` dengan tombol `Ingatkan lagi nanti` | Sebagian   | Normal       | Sejak `1.1.161`. Dites di Gendang. Teks baru ada di 19 bahasa |

## Crash dan ANR

| No | Yang dites | Status tes | Hasil | Catatan |
|---|---|---|---|---|
| 46 | Crash dan ANR selama tes di Samsung dan vivo | Sudah | Normal | Crash 0, ANR 0 |
| 47 | Data ANR di Play Console | Belum | Belum tahu | Dicek setelah rilis |
