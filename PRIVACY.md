# Kebijakan Privasi — Koceng Oren

_Terakhir diperbarui: 23 November 2026_

Koceng Oren ("aplikasi") adalah aplikasi companion kucing virtual untuk Android.
Kebijakan ini menjelaskan data apa yang dipakai aplikasi dan ke mana data itu pergi.

## Ringkasan singkat

- **Tidak ada akun, tidak ada login, tidak ada iklan, tidak ada analytics.**
- Semua data pribadi (riwayat obrolan, memori, profil suara) disimpan **hanya di perangkat kamu**.
- Satu-satunya data yang keluar dari perangkat adalah **teks obrolan yang dikirim ke Google Gemini API** untuk menghasilkan balasan dan suara.

## Data yang dikumpulkan & disimpan di perangkat

| Data | Tujuan | Penyimpanan |
| --- | --- | --- |
| Riwayat obrolan & fakta tentang kamu (nama panggilan, topik, kesukaan) | Personalisasi balasan & sapaan | SharedPreferences di perangkat, bisa dihapus lewat "Reset Obrolan" |
| Profil suara (angka nada/tempo hasil analisis) | Menyesuaikan pitch & tempo suara Oren | SharedPreferences di perangkat, bisa dihapus lewat Pengaturan → Kloning Suara → Hapus profil |
| Pengaturan (persona, suara, tema, level ketus, notifikasi) | Menyimpan preferensi kamu | SharedPreferences di perangkat |

**Audio mentah dari mikrofon tidak pernah disimpan atau dikirim ke server mana pun.**
Rekaman hanya dianalisis sesaat di perangkat untuk menghasilkan angka profil suara, lalu dibuang.

## Data yang dikirim ke pihak ketiga

- **Google Gemini API** (`generativelanguage.googleapis.com`): teks pesan kamu, ringkasan konteks obrolan, dan teks yang akan diucapkan dikirim untuk menghasilkan balasan AI dan audio suara. Pemrosesan ini tunduk pada [Kebijakan Privasi Google](https://policies.google.com/privacy) dan [Ketentuan Layanan Gemini API](https://ai.google.dev/gemini-api/terms).
- Aplikasi tidak mengirim data ke server lain, tidak memakai SDK iklan, dan tidak memakai layanan analytics.

## Izin yang dipakai

| Izin | Alasan |
| --- | --- |
| `INTERNET` | Menghubungi Gemini API |
| `RECORD_AUDIO` | Input suara (push-to-talk) dan sampel untuk kloning suara; hanya aktif saat kamu memakainya |
| `POST_NOTIFICATIONS` | Sapaan proaktif (pagi/malam, kangen, pengingat PR); bisa dimatikan |
| `RECEIVE_BOOT_COMPLETED` | Memasang ulang jadwal sapaan setelah HP restart |
| `VIBRATE`, `MODIFY_AUDIO_SETTINGS` | Efek getaran & pengaturan audio playback |

Semua izin bersifat opsional untuk fungsi inti; aplikasi tetap bisa dipakai lewat tombol ketik tanpa mikrofon.

## Penghapusan data

- Hapus riwayat & memori: menu **Reset Obrolan** di aplikasi.
- Hapus profil suara: **Pengaturan → Kloning Suara → Hapus profil**.
- Hapus semua data: hapus data aplikasi lewat pengaturan Android, atau uninstall aplikasi.

## Anak-anak

Aplikasi ini ditujukan untuk pengguna umum (remaja ke atas). Kami tidak dengan sengaja mengumpulkan data pribadi anak-anak di bawah 13 tahun.

## Kontak

Pertanyaan tentang privasi: buka issue di repositori GitHub proyek ini
(`https://github.com/nurzenzayn-bot/KocengOren`).

## Perubahan kebijakan

Perubahan kebijakan akan diperbarui di dokumen ini beserta tanggal revisinya.
