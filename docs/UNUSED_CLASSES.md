# Class yang Tidak Dipakai (kandidat hapus)

Dicek **2026-11-04**. **Belum dihapus**, menunggu keputusan pemilik SDK.

## Cara mengecek

- Pemakaian dicari berdasarkan nama class di SDK (`soundrecorder/src/main`: kode, layout XML, manifest)
  dan di semua app `godev1–godev5/*/app/src/main` (kode, layout, manifest). Ke-13 app yang memakai
  ZaifSDK ikut dicek; tidak ada app di luar folder godev yang memakainya.
- Berantai: class yang hanya dipakai oleh class mati ikut dihitung mati.
- File dianggap mati bila class utamanya (nama sama dengan nama file) tidak dipakai di file lain.
- Class pembantu di dalam file yang masih dipakai (mis. `MusicAdapter` di `MusicListDialogHelper`,
  `Voice` di `Mp3Renderer`) **tidak** dimasukkan.
- Tes unit (`src/test`) tidak dihitung sebagai pemakaian.

Batasan: pencarian berdasarkan nama, jadi pemakaian lewat reflection atau dari app di luar folder
godev tidak terdeteksi. Sebelum menghapus, ulangi pengecekan (app bisa saja mulai memakai class ini).

## Daftar (36 file, ±3.700 baris)

Path relatif terhadap `soundrecorder/src/main/java/sound/recorder/widget/`.

### Tampilan / layar besar
| File | Baris | Catatan |
|---|---|---|
| `recording/InstrumentControlPanelNewDesign.kt` | 906 | Versi lain `InstrumentControlPanel`; semua app memakai yang lama |
| `ui/bottomSheet/BottomSheetListSong.kt` | 358 | Sudah diganti `MusicListDialogHelper` |
| `util/ColorChooserDialog.java` | 336 | |
| `ui/bottomSheet/BottomSheetVideo.kt` | 215 | |
| `ui/fragment/NoteFragmentFirebase.kt` | 214 | |
| `util/SnowFlakesLayout.java` | 213 | Efek salju |
| `ui/bottomSheet/BottomSheetSetting.kt` | 123 | |
| `ui/bottomSheet/ActionsDialogFragment.kt` | 100 | |

### Internet / iklan / sistem
| File | Baris | Catatan |
|---|---|---|
| `internet/InternetAvailabilityChecker.java`, `CheckInternetTask.java`, `InternetConnectivityListener.java`, `TaskFinished.java` | 345 | Pengecek internet lama; sekarang `isInternetTrulyAvailable` |
| `ads/GoogleMobileAdsConsentManager.kt` | 80 | Consent sudah lewat `setupGDPR` di `BaseActivityWidget` |
| `util/NetworkWatcher.kt` | 63 | |
| `service/BackgroundService.kt` | 49 | Tidak terdaftar di manifest mana pun |
| `tools/NativeLib.kt` | 16 | Memuat library native `widget` yang tidak ada di SDK: kalau dipanggil, app crash |

### Listener lama (±130 baris)
`listener/ColorListener.java`, `CompleteSoundListener.kt`, `MyCompleteSoundListener.kt`,
`FragmentListener.kt`, `MyFragmentListener.kt`, `PauseListener.kt`, `MyPauseListener.kt`,
`StopMusicListener.kt`, `MyStopMusicListener.kt`, `StopSDKMusicListener.kt`, `MyStopSDKMusicListener.kt`
(dua terakhir hanya dipakai `BottomSheetListSong`).

### Util kecil
| File | Baris |
|---|---|
| `util/HFRecyclerViewAdapter.java` | 149 |
| `notes/utils/MyDividerItemDecoration.kt` | 97 |
| `db/StorageManager.kt` | 88 |
| `util/BaseRecyclerViewAdapter.java` | 86 |
| `tools/TimerHandler.kt` | 52 |
| `util/KeyboardUtils.kt` | 38 |
| `util/PermissionManager.kt` | 32 |
| `util/LanguageManager.kt` | 30 |
| `animation/modifiers/AccelerationModifier.java` | 23 |

## Kalau nanti dihapus

1. Ulangi pengecekan pemakaian di SDK dan semua app.
2. Hapus bertahap, mulai dari yang paling aman: listener lama, `NativeLib`, `InternetAvailabilityChecker`.
3. Setelah itu cek layout, drawable, dan teks yang hanya dipakai class-class ini (mis. layout bottom sheet).
4. Build SDK + tes unit, lalu build minimal satu app sebelum rilis jitpack.
5. Catat di CHANGELOG (class publik yang dihapus bisa membuat kode app gagal dikompilasi saat update).
