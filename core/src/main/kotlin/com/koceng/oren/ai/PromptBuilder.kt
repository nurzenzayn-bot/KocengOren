package com.koceng.oren.ai

/**
 * Builder untuk构建 prompt yang dikirim ke LLM.
 * Menggabungkan system prompt + conversation memory + current input.
 * Dipisah agar testable & reusable.
 */
class PromptBuilder(
    private val personaManager: PersonaManager,
    private val conversationMemory: ConversationMemory
) {

    /** Build prompt lengkap untuk chat mode */
    fun buildChatPrompt(
        userInput: String,
        mode: ChatMode = ChatMode.CHAT,
        personaId: String = "default"
    ): PromptBundle {
        val systemPrompt = personaManager.buildSystemPrompt(
            mode = mode,
            userContext = conversationMemory.buildUserContext(personaId)
        )

        val memoryContext = conversationMemory.buildContextPrompt(maxTurns = 8)

        val fullPrompt = """
            $systemPrompt
            
            $memoryContext
            
            ===== INPUT USER SAAT INI =====
            User: $userInput
            
            ===== JAWABAN LU (Oren) =====
        """.trimIndent()

        return PromptBundle(
            systemPrompt = systemPrompt,
            memoryContext = memoryContext,
            userInput = userInput,
            fullPrompt = fullPrompt,
            mode = mode,
            personaId = personaId
        )
    }

    /** Build prompt untuk homework (text-based) */
    fun buildHomeworkPrompt(
        userInput: String,
        problemType: ProblemType = ProblemType.GENERAL,
        personaId: String = "default"
    ): PromptBundle {
        val systemPrompt = personaManager.buildSystemPrompt(
            mode = ChatMode.HOMEWORK,
            userContext = conversationMemory.buildUserContext(personaId)
        )

        val memoryContext = conversationMemory.buildContextPrompt(maxTurns = 6)

        // Homework-specific instruction
        val homeworkInstruction = """
            ===== MODE PR SEKOLAH ===
            Soal: $userInput
            Tipe: ${problemType.displayName}
            
            Format jawaban WAJIB:
            **JENIS SOAL:** [${problemType.displayName}]
            
            **LANGKAH 1:** [deskripsi]
            **RUMUS/TEOREMA:** [jika ada]
            **PERHITUNGAN:** [detail]
            
            **LANGKAH 2:** [...]
            ...
            
            **JAWABAN AKHIR:** [hasil]
            
            **PENJELASAN KONSEP:** [konsep inti 2-3 kalimat]
        """.trimIndent()

        val fullPrompt = """
            $systemPrompt
            
            $memoryContext
            
            $homeworkInstruction
            
            ===== JAWABAN LU (Oren) =====
        """.trimIndent()

        return PromptBundle(
            systemPrompt = systemPrompt,
            memoryContext = memoryContext,
            userInput = userInput,
            fullPrompt = fullPrompt,
            mode = ChatMode.HOMEWORK,
            personaId = personaId
        )
    }

    /** Build prompt untuk homework dari OCR result */
    fun buildOcrHomeworkPrompt(
        ocrText: String,
        problemType: ProblemType,
        personaId: String = "default"
    ): PromptBundle {
        val systemPrompt = personaManager.buildSystemPrompt(
            mode = ChatMode.HOMEWORK,
            userContext = conversationMemory.buildUserContext(personaId)
        )

        val memoryContext = conversationMemory.buildContextPrompt(maxTurns = 4)

        val ocrInstruction = """
            ===== MODE PR SEKOLAH (DARI FOTO) ===
            Teks OCR: $ocrText
            Tipe soal terdeteksi: ${problemType.displayName}
            
            Instruksi khusus:
            - OCR bisa ada typo, gunakan konteks untuk memperbaiki
            - Kalau soal nggak lengkap, tanya balik ke user
            - Format jawaban sama seperti homework biasa
        """.trimIndent()

        val fullPrompt = """
            $systemPrompt
            
            $memoryContext
            
            $ocrInstruction
            
            ===== JAWABAN LU (Oren) =====
        """.trimIndent()

        return PromptBundle(
            systemPrompt = systemPrompt,
            memoryContext = memoryContext,
            userInput = "[FOTO PR] $ocrText",
            fullPrompt = fullPrompt,
            mode = ChatMode.HOMEWORK,
            personaId = personaId
        )
    }

    /** Build prompt untuk proactive suggestion / follow-up */
    fun buildProactivePrompt(
        trigger: ProactiveTrigger,
        personaId: String = "default"
    ): PromptBundle {
        val systemPrompt = personaManager.buildSystemPrompt(
            mode = ChatMode.CHAT,
            userContext = conversationMemory.buildUserContext(personaId)
        )

        val triggerPrompt = when (trigger) {
            is ProactiveTrigger.HomeworkFollowUp -> """
                User tadi nanya PR ${trigger.subject}. 
                Kirim pesan singkat tanya progress: "Eh, PR ${trigger.subject} tadi udah selesai nggak? Butuh bantuan lagi nggak?"
            """.trimIndent()
            is ProactiveTrigger.LongSilence -> """
                User diam ${trigger.hours} jam. 
                Kirim pesan ringan: "Lagi sibuk ya? Santai aja, gue di sini kalo butuh apa-apa."
            """.trimIndent()
            is ProactiveTrigger.MorningGreeting -> """
                Pagi! Kirim greeting ringan: "Pagi! Semangat jalanin hari ini ya. Ada PR nggak? Gue bantu."
            """.trimIndent()
            is ProactiveTrigger.NightGreeting -> """
                Malam! Kirim: "Malam! Udah selesai aktivitas? Kalau ada PR sisa, gue bantu sebentar."
            """.trimIndent()
        }

        val fullPrompt = """
            $systemPrompt
            
            ===== PROACTIVE MESSAGE ===
            $triggerPrompt
            
            ===== JAWABAN LU (Oren) =====
        """.trimIndent()

        return PromptBundle(
            systemPrompt = systemPrompt,
            memoryContext = "",
            userInput = "[PROACTIVE] ${trigger.javaClass.simpleName}",
            fullPrompt = fullPrompt,
            mode = ChatMode.CHAT,
            personaId = personaId
        )
    }

    /** Data class untuk bundle prompt */
    data class PromptBundle(
        val systemPrompt: String,
        val memoryContext: String,
        val userInput: String,
        val fullPrompt: String,
        val mode: ChatMode,
        val personaId: String
    ) {
        fun getEstimatedTokens(): Int {
            // Rough estimation: 1 token ≈ 4 chars (Indonesian)
            return fullPrompt.length / 4
        }
    }
}

/** Sealed class untuk proactive triggers */
sealed class ProactiveTrigger {
    data class HomeworkFollowUp(val subject: String) : ProactiveTrigger()
    data class LongSilence(val hours: Int) : ProactiveTrigger()
    object MorningGreeting : ProactiveTrigger()
    object NightGreeting : ProactiveTrigger()
}