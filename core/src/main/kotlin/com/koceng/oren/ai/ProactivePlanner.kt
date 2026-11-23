package com.koceng.oren.ai

/**
 * Phase 4: otak untuk notifikasi proaktif.
 *
 * Semua keputusan "kapan Oren boleh nyapa duluan" ada di sini supaya
 * bisa diuji tanpa Android dan tidak spam notifikasi.
 */
data class ProactiveContext(
    val nowMillis: Long,
    val localHour: Int,
    val lastActiveMillis: Long,
    val lastMode: ChatMode? = null,
    val homeworkSubject: String? = null,
    val lastMorningSentMillis: Long = 0L,
    val lastNightSentMillis: Long = 0L,
    val lastSilenceSentMillis: Long = 0L,
    val lastHomeworkSentMillis: Long = 0L,
    val lastAnySentMillis: Long = 0L,
    val silenceThresholdHours: Int = 24,
    val homeworkFollowUpHours: Int = 18,
    val greetingMinIdleHours: Int = 4,
    val greetingCooldownHours: Int = 20,
    val globalCooldownHours: Int = 8,
    val morningStartHour: Int = 6,
    val morningEndHour: Int = 9,
    val nightStartHour: Int = 19,
    val nightEndHour: Int = 22
)

object ProactivePlanner {

    private const val HOUR_MS = 60L * 60L * 1000L

    /**
     * Tentukan trigger yang paling relevan, atau null kalau belum waktunya.
     * Prioritas: follow-up PR -> kangen (long silence) -> sapaan pagi -> sapaan malam.
     */
    fun decide(context: ProactiveContext): ProactiveTrigger? {
        if (context.lastActiveMillis <= 0L) return null

        val idleHours = hoursBetween(context.lastActiveMillis, context.nowMillis)
        if (idleHours < context.greetingMinIdleHours) return null

        // Jangan pernah kirim dua notifikasi proaktif berdekatan.
        if (hoursBetween(context.lastAnySentMillis, context.nowMillis) < context.globalCooldownHours) {
            return null
        }

        val subject = context.homeworkSubject?.takeIf { it.isNotBlank() }
        if (context.lastMode == ChatMode.HOMEWORK && subject != null &&
            idleHours >= context.homeworkFollowUpHours &&
            hoursBetween(context.lastHomeworkSentMillis, context.nowMillis) >= context.greetingCooldownHours
        ) {
            return ProactiveTrigger.HomeworkFollowUp(subject)
        }

        if (idleHours >= context.silenceThresholdHours &&
            hoursBetween(context.lastSilenceSentMillis, context.nowMillis) >= context.silenceThresholdHours
        ) {
            return ProactiveTrigger.LongSilence(idleHours)
        }

        val hour = context.localHour
        if (hour in context.morningStartHour until context.morningEndHour &&
            hoursBetween(context.lastMorningSentMillis, context.nowMillis) >= context.greetingCooldownHours
        ) {
            return ProactiveTrigger.MorningGreeting
        }

        if (hour in context.nightStartHour until context.nightEndHour &&
            hoursBetween(context.lastNightSentMillis, context.nowMillis) >= context.greetingCooldownHours
        ) {
            return ProactiveTrigger.NightGreeting
        }

        return null
    }

    private fun hoursBetween(fromMillis: Long, nowMillis: Long): Int {
        if (fromMillis <= 0L) return Int.MAX_VALUE
        return ((nowMillis - fromMillis) / HOUR_MS).toInt().coerceAtLeast(0)
    }
}

/** Isi notifikasi yang siap ditampilkan. */
data class ProactiveNotification(
    val title: String,
    val body: String
)

/**
 * Membuat teks notifikasi yang sudah dipersonalisasi (nama + gaya persona).
 * Tanpa network, jadi tetap jalan walau HP offline.
 */
object ProactiveMessageGenerator {

    fun build(
        trigger: ProactiveTrigger,
        userName: String?,
        keyFacts: Map<String, String> = emptyMap(),
        persona: PersonaConfig = PersonaConfig.default()
    ): ProactiveNotification {
        val name = userName?.trim()?.takeIf { it.isNotEmpty() }
            ?: keyFacts["nama"]?.trim()?.takeIf { it.isNotEmpty() }

        val body = when (trigger) {
            is ProactiveTrigger.MorningGreeting -> morningBody(persona, name)
            is ProactiveTrigger.NightGreeting -> nightBody(persona, name)
            is ProactiveTrigger.LongSilence -> silenceBody(persona, name, trigger.hours)
            is ProactiveTrigger.HomeworkFollowUp -> homeworkBody(persona, name, trigger.subject)
        }

        return ProactiveNotification(title = "Koceng Oren 🐱", body = body)
    }

    private fun morningBody(persona: PersonaConfig, name: String?): String = when (persona.id) {
        "formal" -> "Selamat pagi${withName(name)}. Semoga hari Anda lancar. Ada PR yang perlu dibantu?"
        "sarkas" -> "Pagi${withName(name)}. Baru bangun ya? Ada PR numpuk nggak tuh?"
        "genz" -> "Pagi${withName(name)}! Gas hari ini 🔥 Ada PR yang mau dibantuin?"
        else -> "Pagi${withName(name)}! Semangat jalanin hari ini. Ada PR yang mau dibantu?"
    }

    private fun nightBody(persona: PersonaConfig, name: String?): String = when (persona.id) {
        "formal" -> "Selamat malam${withName(name)}. Sudah selesai aktivitas? Jika ada PR sisa, saya siap membantu."
        "sarkas" -> "Malam${withName(name)}. Belum tidur? PR-nya udah kelar belum tuh?"
        "genz" -> "Malam${withName(name)}! Udah kelar semua? Kalau ada PR sisa, gas gue bantu."
        else -> "Malam${withName(name)}! Udah selesai aktivitas? Kalau ada PR sisa, gue bantu sebentar."
    }

    private fun silenceBody(persona: PersonaConfig, name: String?, hours: Int): String {
        val safeHours = hours.coerceAtLeast(1)
        return when (persona.id) {
            "formal" -> "Sudah $safeHours jam kita tidak mengobrol${withName(name)}. Saya di sini jika Anda butuh bantuan."
            "sarkas" -> "Udah $safeHours jam nggak nongol${withName(name)}. Sibuk atau lupa sama gue nih? Hehe."
            "genz" -> "Udah $safeHours jam nggak mabar chat${withName(name)}. Kangen nggak? Gas balik yuk!"
            else -> "Udah $safeHours jam nggak ngobrol nih${withName(name)}. Kangen ya? Hehe. Gue di sini kalau butuh apa-apa."
        }
    }

    private fun homeworkBody(persona: PersonaConfig, name: String?, subject: String): String = when (persona.id) {
        "formal" -> "Apakah PR $subject sudah selesai${withName(name)}? Saya siap membantu bila masih ada kesulitan."
        "sarkas" -> "Eh${withName(name)}, PR $subject tadi udah kelar belum? Jangan bilang lupa ya."
        "genz" -> "Yo${withName(name)}! PR $subject udah kelar? Kalau belum, gas gue bantu."
        else -> "Eh${withName(name)}, PR $subject tadi udah selesai belum? Butuh bantuan lagi nggak?"
    }

    private fun withName(name: String?): String = if (name.isNullOrBlank()) "" else ", $name"
}
