package com.koceng.oren.audio

import com.google.gson.Gson

/**
 * Profil suara hasil "kloning ringan".
 *
 * Catatan jujur: ini bukan kloning neural penuh. Kami mengukur nada dasar
 * (pitch) dan tempo bicara user, lalu memetakannya ke parameter TTS dan
 * suara Gemini preset yang paling mirip. Audio mentah TIDAK disimpan.
 */
data class VoiceProfile(
    val pitchHz: Float,
    val speakingRate: Float,
    val ttsPitch: Float,
    val ttsSpeed: Float,
    val voiceClass: VoiceClass,
    val sampleCount: Int,
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Suara Gemini preset yang paling dekat dengan profil ini. */
    fun recommendedGeminiVoice(): String = when (voiceClass) {
        VoiceClass.LOW -> "Fenrir"
        VoiceClass.MEDIUM -> "Puck"
        VoiceClass.HIGH -> "Kore"
    }

    fun summary(): String = buildString {
        append("Nada ${pitchHz.toInt()} Hz, ")
        append("tempo ${String.format("%.1f", speakingRate)} suku kata/detik, ")
        append("kelas ${when (voiceClass) { VoiceClass.LOW -> "rendah"; VoiceClass.MEDIUM -> "sedang"; VoiceClass.HIGH -> "tinggi" }}")
    }

    fun toJson(): String = Gson().toJson(this)

    companion object {
        fun fromJson(json: String): VoiceProfile? = try {
            Gson().fromJson(json, VoiceProfile::class.java)
        } catch (e: Exception) {
            null
        }
    }
}

enum class VoiceClass { LOW, MEDIUM, HIGH }
