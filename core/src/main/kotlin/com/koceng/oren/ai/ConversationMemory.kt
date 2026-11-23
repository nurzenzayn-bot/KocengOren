package com.koceng.oren.ai

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

/**
 * Manajemen memori percakapan jangka panjang.
 * Menyimpan history, ekstraksi fakta kunci, dan building context untuk LLM.
 * Desain: ringan, serializable ke JSON untuk persistensi.
 */
class ConversationMemory(
    private val maxTurns: Int = 50,           // Max history turns di memori
    private val maxKeyFacts: Int = 30,        // Max key facts disimpan
    private val summaryInterval: Int = 10     // Buat summary tiap N turns
) {

    private val gson = Gson()
    private val history = mutableListOf<ChatTurn>()
    private val keyFacts = mutableMapOf<String, String>()
    private var turnCount = 0
    private var lastSummary: String = ""

    /** Satu turn percakapan (user + assistant) */
    data class ChatTurn(
        val user: String,
        val assistant: String,
        val timestamp: Long = System.currentTimeMillis(),
        val mode: ChatMode = ChatMode.CHAT,
        val personaId: String = "default",
        val metadata: Map<String, String> = emptyMap()
    ) {
        fun toShortString(): String = "User: $user\nOren: $assistant"
    }

    /** Tambah turn baru ke history */
    fun addTurn(
        user: String,
        assistant: String,
        mode: ChatMode = ChatMode.CHAT,
        personaId: String = "default"
    ) {
        val turn = ChatTurn(
            user = user,
            assistant = assistant,
            mode = mode,
            personaId = personaId
        )
        history.add(turn)
        turnCount++

        // Trim history kalau kelebihan
        while (history.size > maxTurns) {
            history.removeAt(0)
        }

        // Extract key facts dari user message
        extractKeyFacts(user, turn)

        // Generate summary periodik
        if (turnCount % summaryInterval == 0) {
            generateSummary()
        }
    }

    /** Extract key facts sederhana dari user message (rule-based, bisa upgrade ke LLM-based nanti) */
    private fun extractKeyFacts(userMessage: String, turn: ChatTurn) {
        val lower = userMessage.lowercase()

        // Pattern: "nama gue [nama]" / "namaku [nama]" / "aku [nama]"
        val namePatterns = listOf(
            "nama gue ", "namaku ", "nama aku ", "panggil aku ", "panggil gue ",
            "aku ", "gue " // hati-hati dengan false positive
        )
        for (pattern in namePatterns) {
            val idx = lower.indexOf(pattern)
            if (idx >= 0) {
                val start = idx + pattern.length
                val end = lower.indexOfFirst { it in " ,.!?\n" }.takeIf { it >= start } ?: lower.length
                val potentialName = userMessage.substring(start, end).trim()
                if (potentialName.length >= 2 && potentialName.length <= 20 && potentialName.all { it.isLetter() || it.isWhitespace() }) {
                    keyFacts["nama"] = potentialName.capitalize()
                    trimKeyFacts()
                    return
                }
            }
        }

        // Pattern: "suka [hal]" / "hobi [hal]" / "minat [hal]"
        val interestPatterns = listOf("suka ", "hobi ", "minat ", "gemar ", "enak ")
        for (pattern in interestPatterns) {
            val idx = lower.indexOf(pattern)
            if (idx >= 0) {
                val start = idx + pattern.length
                val end = lower.indexOfFirst { it in " ,.!?\n" }.takeIf { it >= start } ?: lower.length
                val interest = userMessage.substring(start, end).trim()
                if (interest.length >= 2 && interest.length <= 30) {
                    keyFacts["suka"] = interest
                    trimKeyFacts()
                    return
                }
            }
        }

        // Pattern: "benci [hal]" / "nggak suka [hal]"
        val dislikePatterns = listOf("benci ", "nggak suka ", "ga suka ", "tidak suka ")
        for (pattern in dislikePatterns) {
            val idx = lower.indexOf(pattern)
            if (idx >= 0) {
                val start = idx + pattern.length
                val end = lower.indexOfFirst { it in " ,.!?\n" }.takeIf { it >= start } ?: lower.length
                val dislike = userMessage.substring(start, end).trim()
                if (dislike.length >= 2 && dislike.length <= 30) {
                    keyFacts["benci"] = dislike
                    trimKeyFacts()
                    return
                }
            }
        }

        // Deteksi topik teknis: "PR matematika", "soal fisika", "bahasa inggris", dll
        val subjects = listOf("matematika", "fisika", "kimia", "biologi", "bahasa indonesia", "bahasa inggris", "sejarah", "geografi", "ekonomi", "sosiologi", "informatika", "coding", "programming")
        for (subject in subjects) {
            if (lower.contains(subject)) {
                keyFacts["topik_terakhir"] = subject
                trimKeyFacts()
                break
            }
        }
    }

    private fun trimKeyFacts() {
        while (keyFacts.size > maxKeyFacts) {
            keyFacts.remove(keyFacts.keys.first())
        }
    }

    /** Generate summary ringkasan percakapan (simple heuristic, nanti bisa pakai LLM) */
    private fun generateSummary() {
        if (history.isEmpty()) return

        val recent = history.takeLast(history.size.coerceAtMost(summaryInterval))
        val topics = recent.flatMap { turn ->
            (turn.user + " " + turn.assistant).lowercase().split(" ")
                .filter { it.length > 4 }
                .distinct()
        }.groupingBy { it }.eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(5)
            .map { it.first }

        val modes = recent.map { it.mode }.distinct()
        val userMsgCount = recent.size
        val avgUserLen = recent.map { it.user.length }.average()

        lastSummary = """
            Percakapan ${recent.size} turn terakhir: mode ${modes.joinToString("/")}.
            Topik dominan: ${topics.joinToString(", ")}.
            User rata-rata ${avgUserLen.toInt()} karakter per pesan.
        """.trimIndent()
    }

    /** Get recent N turns untuk context window */
    fun getRecentTurns(count: Int = 10): List<ChatTurn> {
        return history.takeLast(count.coerceAtMost(history.size))
    }

    /** Get semua history (untuk export/debug) */
    fun getAllHistory(): List<ChatTurn> = history.toList()

    /** Get key facts yang diekstrak */
    fun getKeyFacts(): Map<String, String> = keyFacts.toMap()

    /** Get current summary */
    fun getSummary(): String = lastSummary

    /** Build context prompt untuk dikirim ke LLM */
    fun buildContextPrompt(maxTurns: Int = 8): String {
        val recent = getRecentTurns(maxTurns)
        val facts = keyFacts.map { "${it.key}: ${it.value}" }.joinToString(", ")
        val historyStr = recent.joinToString("\n\n") { it.toShortString() }

        return """
            ===== MEMORI PERCAKAPAN =====
            Fakta User: ${if (facts.isEmpty()) "Belum ada" else facts}
            Ringkasan: ${if (lastSummary.isBlank()) "Belum ada" else lastSummary}
            
            Riwayat Terbaru (${recent.size} turn):
            $historyStr
        """.trimIndent()
    }

    /** Build UserContext object untuk PersonaManager */
    fun buildUserContext(personaId: String = "default"): UserContext {
        return UserContext(
            name = keyFacts["nama"],
            preferredTopics = keyFacts.entries.filter { it.key == "topik_terakhir" || it.key == "suka" }.map { it.value }.distinct(),
            conversationStyle = "casual",
            lastActiveTimestamp = history.lastOrNull()?.timestamp ?: 0,
            totalConversations = turnCount,
            favoritePersonaId = personaId,
            keyFacts = keyFacts.toMap(),
            recentSummary = lastSummary
        )
    }

    /** Clear semua memory (reset chat) */
    fun clear() {
        history.clear()
        keyFacts.clear()
        turnCount = 0
        lastSummary = ""
    }

    /** Serialize ke JSON untuk persistensi */
    fun toJson(): String {
        val data = MemoryData(
            history = history,
            keyFacts = keyFacts.toMap(),
            turnCount = turnCount,
            lastSummary = lastSummary
        )
        return gson.toJson(data)
    }

    /** Deserialize dari JSON */
    companion object {
        fun fromJson(json: String): ConversationMemory {
            val gson = Gson()
            val typeToken = object : TypeToken<MemoryData>() {}
            val type: Type = typeToken.type
            val data: MemoryData = gson.fromJson(json, type)
            val memory = ConversationMemory()
            memory.history.addAll(data.history)
            memory.keyFacts.putAll(data.keyFacts)
            memory.turnCount = data.turnCount
            memory.lastSummary = data.lastSummary
            return memory
        }
    }

    /** Internal data class untuk serialisasi */
    @Suppress("UNUSED_PARAMETER")
    private data class MemoryData(
        val history: List<ChatTurn>,
        val keyFacts: Map<String, String>,
        val turnCount: Int,
        val lastSummary: String
    )
}