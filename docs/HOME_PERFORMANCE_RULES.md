# Plan & Aturan Home Ringan dan Smooth

Untuk semua app instrumen yang memakai ZaifSDK (Belira, Lira, Dholak, Darbuka,
Kendang, Pianika, Rebana, Angklung, …). Khusus layar **Home**.

Tujuan: Home tetap lancar di HP kelas bawah (acuan: Samsung J2 Prime, Android 6,
960x540), termasuk setelah redesign.

---

## 1. Kenapa aturan ini ada

Hasil ukur nyata di Samsung J2 Prime (build release):

| Home | Frame tersendat | p90 | p99 | Slow issue draw commands |
|---|---|---|---|---|
| Belira, desain lama (navy) | 4,2% | 15 ms | 21 ms | 9 |
| Belira, redesign anak v1 (kartu permen) | 20,7% | 18 ms | 36 ms | 73 |

Redesign v1 kelihatan sederhana, tapi 5x lebih berat. Beban ada di GPU
(slow issue draw commands naik). **Perkiraan "secara teori ringan" tidak cukup;
setiap perubahan Home wajib diukur.**

---

## 2. Aturan desain Home

### 2.1 Gambar (beban GPU)

1. **Tidak ada clip bertumpuk.** Jangan `clip()` di dalam `clip()`
   (mis. kartu luar untuk bibir 3D + muka kartu yang di-clip lagi).
2. **`Modifier.background(color, shape)`, bukan `clip(shape).background(color)`**
   untuk badge, pil, lingkaran, halo di belakang ilustrasi.
3. **Kartu dekoratif digambar sekaligus** dengan `drawWithCache`:
   ```kotlin
   Modifier.drawWithCache {
       val r = CornerRadius(radius.toPx())
       val brush = Brush.verticalGradient(listOf(top, bottom), endY = faceHeight)
       val stroke = Stroke(2.dp.toPx())
       onDrawBehind {
           drawRoundRect(lipColor, cornerRadius = r)                         // bibir 3D
           drawRoundRect(brush, size = faceSize, cornerRadius = r)           // muka
           drawRoundRect(borderColor, topLeft = ..., size = ..., cornerRadius = ..., style = stroke) // tepi
       }
   }
   ```
   Brush, Stroke, ukuran dihitung di blok cache, bukan setiap frame.
4. **Tidak ada bayangan teks buram** (`Shadow(blurRadius > 0)`) di banyak teks.
   Kontras didapat dari warna kartu. Maksimal satu judul dengan bayangan tanpa blur.
5. **Umpan balik tekan = skala**, via `graphicsLayer { scaleX = s; scaleY = s }` dan
   `indication = null`. Ripple pada kartu bersudut bulat butuh clip, hindari.
6. **Latar = gradien + bentuk sederhana di `drawBehind`** (awan/titik warna).
   Jangan gambar PNG layar penuh + overlay gelap bertumpuk. Kalau harus pakai gambar:
   satu lapis, ukuran sesuai layar.
7. **Tidak ada animasi yang jalan terus** di Home (infinite transition, partikel,
   awan bergerak, kilau berulang).
8. Ilustrasi instrumen: vector drawable atau PNG kecil seukuran tampilannya.

### 2.2 Recompose (beban CPU)

1. State yang berubah tiap detik (countdown koin, timer Premium) dibaca di
   **composable sekecil mungkin**, dikirim sebagai lambda `() -> State`.
   Yang digambar ulang hanya chip koin, bukan seluruh Home.
2. Warna, `TextStyle`, `Shape` yang tetap → `private val` di level file.
3. Objek turunan parameter (Brush dari warna kartu) → `remember(key)` atau `drawWithCache`.
4. Jangan baca `SharedPreferences` / hitung data di dalam composable;
   siapkan di Fragment/ViewModel lalu kirim sebagai state.

### 2.3 Banner iklan (Unity/AdMob)

1. Banner diletakkan **di lapisan belakang konten**: setelah latar, sebelum konten
   dalam `Box`. Tidak perlu `Spacer` selebar banner di top bar.
2. Judul, tombol koin, dan setting **tidak boleh terpotong** di layar 640dp.
3. Konten tidak boleh menutupi isi banner (aturan iklan); cek di layar sempit.

### 2.4 Teks & emoji

1. **Tidak ada string hardcode.** Teks baru masuk `values/` dan semua folder bahasa
   app itu. String SDK: 19 locale.
2. **Tidak ada emoji Unicode 13+** (🪙, 🪘, …) bila minSdk < 30, karena tampil kotak di
   Android lama. Pakai ikon vektor atau `CoinManager.icon` / `CoinManager.withIcon()`.

### 2.5 Logika

Redesign Home **hanya mengubah tampilan**. Aksi tombol, alur koin/misi/Premium, dan
iklan tidak diubah. Bug yang ditemukan dilaporkan terpisah, bukan diam-diam diganti.

---

## 3. Cara mengukur (wajib)

1. Build **release** (debug Compose jauh lebih lambat).
   Release tanpa signing → `zipalign -p 4` + `apksigner` dengan
   `~/.android/debug.keystore` agar bisa `install -r` di atas build debug.
2. HP uji kelas bawah (J2 Prime). HP flagship tidak dipakai untuk keputusan.
3. Buka app, **tunggu 45–60 detik** (dexopt latar + loading iklan mengacaukan angka).
   Tutup dialog yang muncul.
4. `adb shell dumpsys gfxinfo <package> reset`
5. Gerakan berskrip yang **tidak pindah layar**: tekan kartu lalu geser jari keluar,
   `adb shell input swipe <x> <y> 480 520 600`, 8 kartu berbeda, jeda 0,8 s.
   - Cek fokus app sebelum setiap gerakan.
   - Jangan pernah mengetuk iklan.
6. `adb shell dumpsys gfxinfo <package>` → catat Janky %, p90, p99,
   Slow UI thread, Slow issue draw commands.
7. **A/B adil:** pembanding = kode yang sama, hanya file Home dikembalikan ke versi lama
   (`git show HEAD:<HomeScreen.kt> > <HomeScreen.kt>`), build, ukur, lalu kembalikan file baru.
8. Hasil aneh → ulangi minimal 2x.

### Target lulus

- Janky% ≤ versi lama + 2 poin, **dan**
- p90 ≤ 16 ms, p99 ≤ 25 ms di J2 Prime.

---

## 4. Plan kerja redesign Home (urutan)

1. **Baca Home lama**: catat semua aksi, state, string, dan posisi banner.
2. **Ukur baseline** Home lama (bagian 3), simpan angkanya.
3. **Desain** mengikuti bagian 2: palet di `private val`, kartu `drawWithCache`,
   latar `drawBehind`, banner di belakang konten.
4. Build release → **screenshot di 960x540**: tidak ada teks penting terpotong.
5. **Ukur A/B**. Tidak lulus target → perbaiki dulu, urutan pilihan:
   1. hapus clip bertumpuk / bayangan buram / overlay yang tersisa,
   2. kurangi elemen dekoratif per kartu,
   3. cache muka kartu jadi bitmap (seperti cache bel di Lira).
6. Lulus → laporkan angka lama vs baru ke user, baru dianggap selesai.

---

## 5. Checklist sebelum selesai

- [ ] Hanya tampilan yang berubah.
- [ ] Tidak ada clip bertumpuk; pil/lingkaran pakai `background(color, shape)`.
- [ ] Kartu digambar via `drawWithCache`.
- [ ] Tidak ada bayangan teks buram berulang.
- [ ] Latar tanpa gambar besar bertumpuk; tidak ada animasi terus-menerus.
- [ ] Countdown hanya me-recompose chip koin.
- [ ] Banner di belakang konten; judul/koin tidak terpotong; banner tetap terlihat.
- [ ] Tidak ada string hardcode; semua bahasa terisi.
- [ ] Tidak ada emoji yang tampil kotak di Android 6.
- [ ] A/B gfxinfo di J2 Prime (release) lulus target.
