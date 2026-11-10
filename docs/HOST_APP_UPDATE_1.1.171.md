# Update app host ke ZaifSDK 1.1.171

Ringkasan perubahan SDK yang memengaruhi app instrumen, dan apa yang harus dilakukan tiap app.
Detail teknis lengkap ada di `CHANGELOG.md`.

## Apa yang berubah

### 1. Premium Pass disembunyikan
- `PremiumPass.isEnabled` bawaannya **`false`**: kartu Premium Pass hilang dari dialog Misi dan
  `PremiumPass.redeem()` selalu gagal. Koin tidak bisa lagi ditukar dengan 2 hari tanpa iklan.
- **Alasan:** 2 hari tanpa iklan terlalu murah dibanding pendapatan iklan yang hilang.
- Pass yang **sudah** ditukar tetap berlaku sampai habis. Selama masih berjalan, kartunya hanya
  menampilkan sisa waktu, tanpa tombol perpanjang.
- Untuk menyalakan lagi di satu app: `PremiumPass.isEnabled = true` (di `GameApp`).
- Jangan tampilkan teks ke user bahwa Premium Pass dihapus.

### 2. Kartu "Koin untuk apa?"
- `CoinUses.list(context)` mengembalikan daftar kegunaan koin beserta harga dari pengaturan SDK:
  buka lagu (`CoinManager.UNLOCK_COST`), export lagu (`RecordingExporter.exportCoinCost`), dan
  video tanpa watermark (30–50, hanya app tanpa pembelian dengan `isVideo`).
- Dialog Misi SDK (`RewardsDialog`) memakainya otomatis, menggantikan kartu "Export pakai koin".
- App dengan panel Misi buatan sendiri (Compose, mis. Belira) bisa menampilkan daftar yang sama.

### 3. Video: hapus watermark pakai koin (app tanpa pembelian)
- Dialog custom video punya switch **"Hapus watermark"**: 30 koin selama jatah gratis masih ada
  (tetap memakai 1 jatah), 50 koin setelah jatah habis. Sudah bayar 10 koin → switch tinggal 40.
- Koin dikembalikan bila dialog ditutup, rekaman dibatalkan, atau rekaman gagal.
- Teks petunjuk tidak lagi menyebut "Premium" di app tanpa pembelian.
- Butuh `ZaifSDKBuilder.hasPremiumPurchase(false)` + `isVideo(true)` + `isCoin(true)` dan
  `controlPanel.videoExport` di layar instrumen.

### 4. Lainnya
- `PremiumPass.importFrom(context, "<prefs lama>")`: untuk app yang dulu punya Premium Pass sendiri.
- `CoinManager.addCoins` sekarang `@Synchronized`.
- Analytics: source koin baru `refund` (lihat `ANALYTICS_TRACKING.md`).

## Yang harus dilakukan tiap app

| App | Cara pakai koin/misi | Yang dilakukan | Status |
|---|---|---|---|
| Belira (godev1) | Dulu punya CoinManager, PremiumPass, worker sendiri | Pindah ke SDK + `importFrom("premium_pass")` + kartu CoinUses di panel Compose | ✅ 2026-11-04, PR #2 |
| Darbuka (godev1) | Sudah SDK penuh (`DailyRewards`, `RewardsDialog`) | Naikkan versi SDK saja | ✅ kode 2026-11-05, branch `feature/sdk-1.1.171` sudah di-push, belum di-merge, belum dites di HP |
| Rebana (godev2, SDK 1.1.164) | SDK (`RewardsDialog`, `PremiumPass.isActive`), punya pembelian | Naikkan versi SDK saja | ✅ kode 2026-11-07, ke 1.1.172, branch `feature/sdk-1.1.172` (belum di-commit, belum dites di HP) |
| Dholak (godev2, SDK 1.1.162) | SDK, punya pembelian | Naikkan versi SDK saja | ✅ kode 2026-11-07, ke 1.1.172, branch `feature/sdk-1.1.172` (belum di-commit, belum dites di HP) |
| Gendang (godev2/GendangElectronik, SDK 1.1.161) | SDK, punya pembelian. Versi di `gradle/libs.versions.toml` | Naikkan versi SDK saja | ✅ kode 2026-11-07, ke 1.1.172, branch `feature/sdk-1.1.172` (belum di-commit, belum dites di HP) |
| Pianika (godev2/PianikaBaru, SDK 1.1.158) | SDK, punya pembelian. Masih ada `CoinManager.kt` sendiri (prefs `zaif_coins` yang sama); kode yang dicek meng-import milik SDK | Naikkan versi SDK, cek lalu hapus `CoinManager.kt` lokal | ✅ kode 2026-11-06, branch `feature/sdk-1.1.171` (belum di-commit/merge, belum dites di HP) |

App lain (Angklung 1.1.104, Harmonica 1.1.69, dan beberapa app 1.0.x) belum memakai koin/misi. Lihat `SDK_FEATURES.md`.

App yang **punya** pembelian (Remove Ads) tidak mendapat switch koin video; perilaku videonya tidak berubah.

## Cek setelah update
- [ ] Dialog Misi: tidak ada tombol tukar Premium Pass, ada kartu "Koin untuk apa?".
- [ ] User dengan pass yang masih berjalan: badge sisa waktu tetap tampil, iklan tetap hilang.
- [ ] App tanpa pembelian: export VIDEO → switch "Hapus watermark" muncul, saldo terpotong saat Mulai.
- [ ] Batalkan rekaman berbayar → koin kembali.
