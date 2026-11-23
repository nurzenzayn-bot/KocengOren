# Koceng Oren 🐱🎤

**AI Companion 3D Virtual Pet** yang bisa ngobrol natural seperti manusia, bantu PR sekolah, dan bicara pakai suara.

## 🎯 Konsep

Koceng Oren adalah aplikasi AI companion dengan avatar kucing 3D berperilaku seperti teman ngobrol.

- **Ngobrol santai**: Bahasa sehari-hari Jakarta (Gue/Lu), witty, sarkas ringan, nggak kaku.
- **Bantu PR**: Mode homework dengan pipeline teks/OCR foto → LLM → structured step-by-step answer.
- **Bicara pakai suara**: Speech-to-Text (VAD) → Gemini AI → Text-to-Speech (Gemini Natural Voice / fallback Android TTS).
- **3D Avatar**: Procedural animation + GLB skeleton, semua ekspresi digenerate via kode.

## 🛠 Tech Stack

- **Engine**: LibGDX 1.12.1 + Kotlin 1.9.22 + gLTF
- **AI**: Gemini API (`gemini-3.5-flash-lite` untuk chat, `gemini-*-tts` untuk suara natural)
- **OCR**: Foto soal PR → teks → LLM (pipeline internal)
- **TTS**: Gemini Natural Voice (Puck/Fenrir/Charon/Aoede/Kore) + fallback Android TextToSpeech
- **Audio**: Procedural SFX (PCM synthesis, no external files)
- **UI**: Scene2D dengan generated texture procedural (no external assets)

## 🚀 Quick Start

```bash
# Set Gemini API Key (wajib untuk fitur AI)
export GEMINI_API_KEY="your_gemini_key_here"

# Build
./gradlew :android:assembleDebug

# Run
./gradlew :android:installDebug
```

## 📁 Struktur

```
core/
  ai/          - AiChatBridge, ChatMode, RoomTheme, PersonaManager, ConversationMemory,
                PromptBuilder, GeminiClient, HomeworkPipeline
  audio/       - VoiceMimicBridge, AndroidVoiceMimicBridge (VAD), VoiceProfileAnalyzer, VoiceCloningManager, ProceduralSfx, WavUtils
  character/   - PetCharacter (3D animation / ekspresi)
  data/        - PetStateManager
  input/       - PetTouchController (raycast)
  screens/     - PetScreen (main game loop + integrasi AI)
  ui/          - PetHud, PetParticleOverlay
  KocengOrenGame.kt

android/
  src/main/kotlin/com/koceng/oren/
    - AndroidLauncher.kt        (LibGDX Android entry)
    - ai/AndroidAiChatBridge.kt  (STT + Gemini + TTS + homework + HUD native)
    - ai/NativePetHudOverlay.kt (HUD bubble/komik di Android)
    - audio/AndroidVoiceMimicBridge.kt (VAD AudioRecord)
    - audio/VoiceCloningManager.kt (rekam + simpan profil suara)
    - notifications/            (sapaan proaktif: alarm, receiver, notifikasi)

assets/
  models/cartoon_cat.glb        (avatar 3D)

.github/workflows/build.yml    (Build APK di GitHub Actions)
```

Persona bawaan (Default, Casual, Sarkas, Formal, Ramah, Gen-Z) didefinisikan di
`core/ai/PersonaManager.kt` dan bisa ditambah dari JSON lewat `loadPersonaFromJson`.

## 🎭 Personas

- **Default**: Oren santai klasik
- **Casual**: Bahasa Jaksel/Jakarta casual (Gue/Lu)
- **Sarkas**: Mode sarkas
- **Formal**: Mode formal/baku
- **Ramah**: Mode ramah/supportif
- **GenZ**: Mode Gen-Z slang

## 🔮 Roadmap

- [x] Core AI abstraction (Persona, Memory, Prompt, Gemini, Homework)
- [x] Android integration (STT/TTS/Gemini/homework/HUD native)
- [x] Memory & personalisasi (memori persisten, fakta user, ringkasan, pilih persona, nama panggilan)
- [x] Proactive notifications (sapaan pagi/malam, kangen, follow-up PR)
- [x] Custom voice cloning (profil nada/tempo dari sampel suara → TTS + preset Gemini)
- [ ] Play Store beta

---

Built with ❤️ using LibGDX & Gemini
