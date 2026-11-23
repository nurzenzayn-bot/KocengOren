package com.koceng.oren.audio

import android.app.Activity
import android.content.SharedPreferences
import java.util.concurrent.atomic.AtomicBoolean

/**
 * "Kloning suara" ringan: rekam contoh suara user lewat VAD yang sudah ada,
 * ekstrak profil nada/tempo di core, lalu simpan profilnya (bukan audio mentah).
 *
 * Profil dipakai untuk mengatur pitch/speed TTS cadangan dan memilih preset
 * Gemini yang paling mirip. Audio mentah dibuang seketika setelah dianalisis.
 */
class VoiceCloningManager(
    private val activity: Activity,
    private val prefs: SharedPreferences,
    private val bridge: VoiceMimicBridge,
    private val listener: Listener
) {

    interface Listener {
        fun onStatus(message: String)
        fun onProfileChanged(profile: VoiceProfile?)
    }

    companion object {
        const val KEY_PROFILE = "voice_profile_json"
    }

    private val recording = AtomicBoolean(false)

    fun loadProfile(): VoiceProfile? {
        val json = prefs.getString(KEY_PROFILE, null) ?: return null
        return VoiceProfile.fromJson(json)
    }

    fun recordSample() {
        if (recording.get()) {
            listener.onStatus("Rekaman masih berjalan, sabar ya...")
            return
        }

        // Pasang callback dulu supaya penolakan izin mikrofon tetap tersampaikan.
        bridge.setCallback(object : VoiceMimicCallback {
            override fun onSpeechStarted() {
                activity.runOnUiThread {
                    listener.onStatus("Kedengeran suaranya. Terus ngomong ya...")
                }
            }

            override fun onSpeechCaptured(pcmData: ByteArray, sampleRate: Int, durationSeconds: Float) {
                recording.set(false)
                bridge.setCallback(null)
                Thread {
                    val profile = VoiceProfileAnalyzer.analyze(pcmData, sampleRate, durationSeconds)
                    activity.runOnUiThread {
                        if (profile == null) {
                            listener.onStatus("Suara kurang jelas. Coba lagi di tempat yang lebih tenang ya.")
                        } else {
                            prefs.edit().putString(KEY_PROFILE, profile.toJson()).apply()
                            listener.onStatus("Profil suara tersimpan: ${profile.summary()}")
                            listener.onProfileChanged(profile)
                        }
                    }
                }.start()
            }

            override fun onError(errorMessage: String) {
                recording.set(false)
                bridge.setCallback(null)
                activity.runOnUiThread { listener.onStatus(errorMessage) }
            }
        })

        bridge.checkAndRequestPermissions {
            if (!recording.compareAndSet(false, true)) return@checkAndRequestPermissions
            listener.onStatus("Ngomong 2-3 detik ya, Oren lagi dengerin suara lu...")
            bridge.startListening()
        }
    }

    fun clearProfile() {
        prefs.edit().remove(KEY_PROFILE).apply()
        listener.onStatus("Profil suara dihapus. Suara balik ke pengaturan standar.")
        listener.onProfileChanged(null)
    }
}
