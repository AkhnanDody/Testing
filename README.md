# Venice Seedream — Aplikasi Android (Kotlin)

Aplikasi Android untuk generate gambar pakai Venice Seedream, langsung nyambung ke server Venice. Nggak perlu `server.py` lagi.

- Bahasa: Kotlin + Jetpack Compose (Material 3)
- Tema: dark mode, aksen kuning `#FFC400`
- Min. Android: 8.0 (API 26) • Target: Android 14 (API 34)

## Fitur

1. **Tombol hamburger (kiri atas)** — membuka drawer Pengaturan:
   - Input API key Venice (ada tombol mata buat lihat/sembunyikan)
   - Tombol **Simpan** dan **Hapus Key**
   - Key disimpan lokal di HP pakai DataStore, tidak di-hardcode di kode
2. **Layar utama (Beranda)**:
   - Tab switch di atas kotak prompt: **Text2Image** (tetap seperti dulu) dan **Img2Img**
   - **Text2Image**: kotak input prompt + hitungan karakter, Resolusi (1K, 1.5K, 2K), Aspect Ratio, tombol **Generate**
   - **Img2Img**: pilih gambar referensi dari galeri HP — tampil preview lengkap dengan info ukuran, tombol **Ganti Gambar** dan **Hapus**. Gambar otomatis dikompresi di HP (sisi terpanjang maks 1024px, JPEG kualitas 85) sebelum dikirim. Prompt-nya berisi instruksi edit, contoh: "ganti langit jadi sunset". Tombol **Edit Gambar**, resolusi edit 1K/2K (rasio ngikutin gambar referensi)
   - Begitu generate/edit berhasil, kotak prompt langsung kosong (prompt-nya sudah ikut tersimpan di Galeri) dan aplikasi langsung pindah ke tab **Galeri** — Beranda sengaja dibiarkan kosong di bawah tombol, biar hasilnya nggak tampil dobel
   - Pas lagi proses, tombol menampilkan timer detiknya
3. **Navbar bawah: Beranda | Galeri**:
   - Galeri grid 2 kolom, tiap kartu ada gambar + prompt + info (Text2Image: resolusi • ratio, Img2Img: "Img2Img • resolusi")
   - Klik kartu buat lihat besar: ada Download, Pakai Prompt Lagi, dan Hapus
   - Ada tombol Hapus Semua di atas galeri
   - Galeri disimpan lokal di HP, jadi tetap ada setelah aplikasi ditutup

Download menyimpan gambar ke folder `Download/VeniceSeedream` di HP.

## Cara membuka & menjalankan

1. Ekstrak ZIP ini.
2. Buka **Android Studio**, pilih **Open**, arahkan ke folder hasil ekstrak ini (folder yang ada file `settings.gradle.kts`-nya).
3. Tunggu Gradle sync selesai (butuh internet buat unduh dependency pertama kali).
   - Proyek ini pakai Gradle 8.7, AGP 8.5.2, Kotlin 2.0.20 — Android Studio versi terbaru sudah cocok.
   - Kalau Android Studio minta install SDK Platform 34, klik setuju aja.
4. Sambungkan HP (aktifkan USB debugging) atau pakai emulator, lalu klik **Run ▶**.
5. Di aplikasi: buka hamburger > isi API key Venice kamu (dari venice.ai/settings/api) > Simpan.
6. Balik ke Beranda, tulis prompt, pilih resolusi & ratio, pencet **Generate**.

Buat file APK yang bisa di-install langsung: menu **Build > Build App Bundle(s) / APK(s) > Build APK(s)**, hasilnya ada di `app/build/outputs/apk/debug/app-debug.apk`.

## Cara build pakai GitHub Actions (tanpa Android Studio)

Proyek ini sudah termasuk file workflow di `.github/workflows/build-apk.yml`, jadi bisa di-build otomatis di GitHub:

1. Buat repo baru di GitHub, sebaiknya **Private**.
2. Upload / push **isi folder proyek ini** ke repo itu — pastikan file `settings.gradle.kts` ada di root repo, dan folder `.github/workflows/build-apk.yml` ikut ter-upload. Kalau upload lewat web GitHub, folder `.github` kadang tidak ikut; cara paling aman pakai `git push` dari Termux/PC:
   ```
   git init
   git add .
   git commit -m "Venice Seedream Android"
   git branch -M main
   git remote add origin https://github.com/<username>/<repo>.git
   git push -u origin main
   ```
3. Push ke branch `main` akan otomatis menjalankan build. Bisa juga dijalankan manual: buka tab **Actions** di repo > pilih **Build APK** > **Run workflow**.
4. Tunggu build selesai (build pertama biasanya 5–10 menit).
5. Di halaman run yang selesai, download artifact **`app-debug`** di bagian bawah, ekstrak, lalu install `app-debug.apk` di HP. Kalau HP minta izin install dari sumber tak dikenal, nyalakan dulu izinnya.

Catatan: workflow ini pakai `gradle assembleDebug` langsung (bukan `./gradlew`, karena file `gradlew` tidak ikut di ZIP ini), dengan Java 17 dan Gradle 8.7 — sama seperti versi proyek ini (AGP 8.5.2). API key tidak ada di kode, jadi aman di-push; key-nya diisi sendiri di dalam aplikasi setelah APK ter-install.

## Cara kerja ke Venice

**Text2Image** (sama persis kayak server.py):

```
POST https://api.venice.ai/api/v1/image/generate
Header: Authorization: Bearer <API key kamu>
Body JSON:
{
  "model": "seedream-v5-5",
  "prompt": "...",
  "resolution": "1K",
  "aspect_ratio": "1:1",
  "format": "png",
  "safe_mode": false
}
```

Response dibaca kayak di `server.py`: ambil `images[0]` (strip awalan data-URI kalau ada), fallback ke `data[0].b64_json`, lalu decode base64 jadi PNG.
Error 402 ditampilkan sebagai pesan saldo credits habis.

**Img2Img** (edit gambar, endpoint terpisah):

```
POST https://api.venice.ai/api/v1/image/edit
Header: Authorization: Bearer <API key kamu>
Body JSON:
{
  "model": "firered-image-edit",
  "image": "<base64 gambar referensi, sudah dikompresi maks 1024px>",
  "prompt": "ganti langit jadi sunset",
  "resolution": "1K",
  "safe_mode": false
}
```

Bedanya: response `/image/edit` adalah **gambar mentah (binary)**, bukan JSON base64 — jadi langsung dibaca bytes-nya. Harga edit $0,04 per edit (generate ~$0,03/gambar 1K, harga bisa berubah, cek venice.ai). Error 422 berarti Venice nolak gambar/prompt-nya.

## Struktur proyek (singkat)

```
app/src/main/java/com/golda/veniceseedream/
├── MainActivity.kt          # Rangka aplikasi: TopBar + hamburger, drawer, navbar bawah
├── data/
│   ├── VeniceApi.kt         # Panggilan langsung ke Venice API (OkHttp + org.json): generate + edit
│   ├── ImageCompressor.kt   # Kompresi gambar referensi maks 1024px (JPEG 85) + base64
│   ├── SettingsStore.kt     # Simpan API key (DataStore Preferences)
│   ├── GalleryStore.kt      # Galeri lokal: PNG + index.json di filesDir/gallery/
│   └── Downloader.kt        # Simpan ke Download/VeniceSeedream (MediaStore)
└── ui/
    ├── MainViewModel.kt     # State & logika generate/edit, timer, galeri
    ├── HomeScreen.kt        # Tab Text2Image/Img2Img, prompt, picker referensi, tombol Generate
    ├── GalleryScreen.kt     # Grid galeri + dialog detail
    ├── SettingsDrawer.kt    # Isi drawer Pengaturan API key
    ├── Components.kt        # Dropdown tutup-buka & penampil gambar lokal
    ├── Options.kt           # Daftar resolusi & aspect ratio (+ resolusi edit)
    └── theme/Theme.kt       # Tema dark mode + aksen kuning
```

## Catatan

- API key cuma disimpan di HP dan cuma dikirim ke `api.venice.ai` pas generate. Jangan bagikan APK yang sudah kamu isi key-nya ke orang lain kalau key-nya mau tetap privat — sebenarnya key tidak ikut di APK, key tersimpan di data aplikasi di HP masing-masing.
- Generate butuh internet dan credits Venice yang cukup.
- Proyek ini belum dicoba build di mesin pembuatnya (tidak ada Android SDK di sana), jadi sync pertama di Android Studio kamu adalah uji build pertamanya. Kalau ada error merah pas sync, kirim screenshot/pesan error-nya biar bisa dibenerin.
