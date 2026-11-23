# Play Store Beta — Checklist & Materi Listing

Dokumen ini menyiapkan rilis **closed beta (uji coba tertutup)** di Google Play Console.

## Artefak rilis

CI (GitHub Actions) sekarang menghasilkan tiga artefak tiap push ke `main`:

| Artefak | Isi | Untuk apa |
| --- | --- | --- |
| `koceng-oren-debug-apk` | APK debug | Testing cepat / sideload |
| `koceng-oren-release-apk` | APK release bertanda tangan | Sideload versi beta |
| `koceng-oren-release-aab` | **Android App Bundle** | **Upload ke Play Console** |

> Play Console menerima **AAB**, bukan APK. Ambil artefak `koceng-oren-release-aab`
> dari run Actions terbaru.

## Signing

- Keystore: `android/release.keystore` (alias `kocengoren`, RSA 2048, valid 10.000 hari).
- Konfigurasi: `android/keystore.properties`.
- **PENTING:** keystore di repo ini adalah *demo key* supaya CI bisa signing otomatis.
  Sebelum rilis publik sungguhan:
  1. Buat keystore baru milik sendiri dan simpan **di luar repo** (mis. GitHub Secrets).
  2. Ganti `keystore.properties` / injeksi via env di CI.
  3. Jangan pernah kehilangan keystore rilis — update aplikasi wajib pakai key yang sama
     (atau aktifkan Play App Signing saat upload AAB pertama; itu cara paling aman).
- Disarankan: aktifkan **Play App Signing** di Play Console agar Google mengelola key upload.

## Materi listing (Bahasa Indonesia)

- **Nama aplikasi:** Koceng Oren
- **Deskripsi singkat (≤80 karakter):**
  `Kucing oranye AI yang ketus tapi sayang. Ngobrol, bantu PR, dan nemenin harimu.`
- **Deskripsi lengkap:**

  > Koceng Oren bukan sekadar chatbot — dia kucing oranye virtual yang tinggal di HP kamu,
  > lengkap dengan ruangan 3D, suara alami, dan kepribadian yang bisa kamu pilih.
  >
  > 🐱 **Ngobrol pakai suara atau ketikan** — jawabannya diucapkan dengan suara natural.
  > 📚 **Mode PR** — bantu ngerjain PR dengan gaya yang kamu suka: formal, sarkas, atau gen-z.
  > 🧠 **Ingat kamu** — nama panggilan, kesukaan, dan topik obrolan disimpan di HP kamu.
  > 🔔 **Sapaan proaktif** — Oren nyapa pagi/malam, kangen kalau kamu lama nggak buka,
  > dan follow-up PR yang belum selesai.
  > 🎤 **Kloning suara ringan** — rekam suaramu sebentar, Oren menyesuaikan nada & tempo
  > bicaranya biar mirip kamu. Audio mentah tidak disimpan.
  >
  > Semua data pribadi tersimpan di perangkat kamu. Tidak ada iklan, tidak ada akun.

- **Kategori:** Aplikasi → Hiburan (atau Pendidikan, sesuai positioning)
- **Kontak email:** isi email developer di Play Console
- **Kebijakan privasi:** `https://github.com/nurzenzayn-bot/KocengOren/blob/main/PRIVACY.md`
  (atau aktifkan GitHub Pages untuk URL yang lebih rapi)

## Data safety form (jawaban yang sesuai kode saat ini)

- Data dibagikan ke pihak ketiga: **Ya** — teks obrolan dikirim ke Google Gemini API
  (kategori: pesan dalam aplikasi / konten buatan pengguna; tujuan: fungsi aplikasi;
  diproses sementara, tidak disimpan oleh aplikasi di server).
- Data dikumpulkan & disimpan di perangkat saja: riwayat obrolan, preferensi, profil suara
  (angka). Tidak ada lokasi, kontak, identifier iklan, atau analytics.
- Enkripsi in transit: **Ya** (HTTPS ke Gemini API).
- Pengguna bisa minta penghapusan data: **Ya** (hapus data aplikasi / uninstall).
- Mikrofon: dipakai atas aksi pengguna, audio tidak disimpan/diunggah.

## Langkah upload beta

1. Buat aplikasi di [Play Console](https://play.google.com/console) (bahasa default: Indonesia).
2. Aktifkan **Play App Signing**.
3. Isi listing + privacy policy URL di atas, upload ikon 512×512 & feature graphic 1024×500.
4. **Testing → Closed testing → Create new release**, upload `koceng-oren-release-aab`.
5. Isi release notes, misalnya:
   `Beta pertama: chat suara, mode PR, memori personal, sapaan proaktif, kloning suara ringan.`
6. Buat daftar tester (email) dan bagikan link opt-in.
7. Lengkapi content rating questionnaire & data safety form sebelum rollout.

## Versi saat ini

- `versionCode 2`, `versionName 1.0.0-beta.1`
- targetSdk 34, minSdk 21
