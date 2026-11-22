package com.koceng.oren.ai

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CoroutineScope

/**
 * Pipeline untuk memproses homework dari foto:
 * 1. OCR extract text dari gambar
 * 2. Parse & klasifikasi soal
 * 3. Kirim ke LLM dengan prompt terstruktur
 * 4. Parse response jadi structured steps
 * 5. Return HomeworkResult
 */
class HomeworkPipeline(
    private val aiBridge: AiChatBridge,
    private val ocrHelper: OcrHelper,
    private val scope: CoroutineScope
) {

    private val gson = Gson()

    /**
     * Process foto homework end-to-end.
     * Returns Deferred untuk async processing.
     */
    fun processPhotoHomework(imageUri: String): Deferred<HomeworkResult> {
        return scope.async {
            try {
                // Step 1: OCR
                val ocrText = ocrHelper.extractText(imageUri)
                if (ocrText.isBlank()) {
                    return@async HomeworkResult.NeedClarification(
                        "Gue nggak bisa baca fotonya, coba ambil yang lebih jelas ya. " +
                        "Pastikan pencahayaan cukup dan teks nggak blur."
                    )
                }

                // Step 2: Klasifikasi jenis soal
                val problemType = classifyProblem(ocrText)

                // Step 3: Build prompt terstruktur
                val prompt = buildHomeworkPrompt(ocrText, problemType)

                // Step 4: Kirim ke AI & parse response
                val response = sendToAIAndWait(prompt)

                // Step 5: Parse response jadi structured
                parseStructuredResponse(response, problemType)
            } catch (e: Exception) {
                HomeworkResult.Error("Error memproses PR: ${e.message}. Coba ketik soalnya aja ya.")
            }
        }
    }

    /** Klasifikasi tipe soal berdasarkan teks OCR (simple keyword-based) */
    private fun classifyProblem(text: String): ProblemType {
        val lower = text.lowercase()
        return when {
            lower.contains("integral") || lower.contains("turunan") || lower.contains("limit") || lower.contains("kalkulus") -> ProblemType.CALCULUS
            lower.contains("persamaan kuadrat") || lower.contains("fungsi kuadrat") -> ProblemType.QUADRATIC
            lower.contains("sistem persamaan") || lower.contains("spl") -> ProblemType.LINEAR_SYSTEM
            lower.contains("trigonometri") || lower.contains("sin") || lower.contains("cos") || lower.contains("tan") -> ProblemType.TRIGONOMETRY
            lower.contains("peluang") || lower.contains("statistika") || lower.contains("mean") || lower.contains("median") -> ProblemType.STATISTICS
            lower.contains("geometri") || lower.contains("segitiga") || lower.contains("lingkaran") || lower.contains("bangun") -> ProblemType.GEOMETRY
            lower.contains("kimia") || lower.contains("reaksi") || lower.contains("mol") || lower.contains("stoikiometri") -> ProblemType.CHEMISTRY
            lower.contains("fisika") || lower.contains("gaya") || lower.contains("energi") || lower.contains("gerak") -> ProblemType.PHYSICS
            lower.contains("bahasa inggris") || lower.contains("translate") || lower.contains("tenses") -> ProblemType.ENGLISH
            else -> ProblemType.GENERAL
        }
    }

    /** Build prompt terstruktur untuk homework */
    private fun buildHomeworkPrompt(ocrText: String, problemType: ProblemType): String {
        val typeInstruction = when (problemType) {
            ProblemType.CALCULUS -> "Fokus pada langkah kalkulus: aturan rantai, substitusi, parsial, dll."
            ProblemType.QUADRATIC -> "Gunakan rumus ABC atau faktorisasi. Jelaskan diskriminan."
            ProblemType.LINEAR_SYSTEM -> "Gunakan eliminasi/substitusi/matriks. Tunjukkan langkah eliminasi."
            ProblemType.TRIGONOMETRY -> "Gunakan identitas trigonometri. Tunjukkan transformasi sudut."
            ProblemType.STATISTICS -> "Tampilkan rumus & substitusi nilai. Jelaskan arti hasil."
            ProblemType.GEOMETRY -> "Gambar skema mental. Gunakan teorema (Pythagoras, dll)."
            ProblemType.CHEMISTRY -> "Seimbangkan reaksi. Hitung mol/massa. Tunjukkan stoikiometri."
            ProblemType.PHYSICS -> "Tulis diketahui & ditanya. Pilih rumus fisika yang tepat."
            ProblemType.ENGLISH -> "Jelaskan grammar/tenses. Berikan terjemahan & alasan."
            ProblemType.GENERAL -> "Analisis soal secara umum. Pecah jadi sub-masalah."
        }

        return """
            Kamu adalah tutor sabar & jelas. Soal dari foto PR:
            
            === TEKS SOAL (OCR) ===
            $ocrText
            
            === Tipe Soal: ${problemType.displayName} ===
            $typeInstruction
            
            === FORMAT JAWABAN WAJIB ===
            **JENIS SOAL:** [${problemType.displayName}]
            
            **LANGKAH 1:** [deskripsi langkah]
            **RUMUS/TEOREMA:** [jika ada]
            **PERHITUNGAN:** [detail hitungan]
            
            **LANGKAH 2:** [deskripsi langkah]
            ...
            
            **JAWABAN AKHIR:** [hasil final, bold angka utama]
            
            **PENJELASAN KONSEP:** [2-3 kalimat jelaskan konsep intinya biar lu paham, nggak cuma hafal]
            
            === GAYA BAHASA ===
            - Jakarta casual, encouraging, nggak kaku
            - Gunakan "Gue", "Lu", "Santai", "Gas"
            - Contoh: "Nah, ini langkah paling penting ya...", "Gampang kan?", "Yaudah, lanjut ke langkah berikut"
            - JANGAN gunakan markdown **bold** kecuali label di atas (JENIS SOAL, LANGKAH, JAWABAN AKHIR, PENJELASAN KONSEP)
        """.trimIndent()
    }

    /** Kirim prompt ke AI bridge & tunggu response penuh (blocking untuk simplicity, nanti bisa streaming) */
    private fun sendToAIAndWait(prompt: String): String {
        // Karena AiChatBridge async callback-based, kita pakai suspendCoroutine
        // Tapi di sini kita return dummy - implementasi nyata di Android side
        // Method ini akan di-override di Android implementation
        throw UnsupportedOperationException("Harus diimplementasi di Android side via bridge")
    }

    /** Parse response LLM jadi structured HomeworkResult */
    private fun parseStructuredResponse(response: String, problemType: ProblemType): HomeworkResult {
        // Simple parsing berdasarkan marker **LABEL:**
        val steps = mutableListOf<Step>()
        var finalAnswer = ""
        var explanation = ""

        val lines = response.lines().toList()
        var currentStepDesc = ""
        var currentFormula = ""
        var currentCalculation = ""

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("**LANGKAH") -> {
                    // Simpan step sebelumnya kalau ada
                    if (currentStepDesc.isNotBlank()) {
                        steps.add(Step(currentStepDesc, currentFormula, currentCalculation))
                    }
                    currentStepDesc = trimmed.substringAfter(":").trim()
                    currentFormula = ""
                    currentCalculation = ""
                }
                trimmed.startsWith("**RUMUS") || trimmed.startsWith("**TEOREMA") -> {
                    currentFormula = trimmed.substringAfter(":").trim()
                }
                trimmed.startsWith("**PERHITUNGAN") -> {
                    currentCalculation = trimmed.substringAfter(":").trim()
                }
                trimmed.startsWith("**JAWABAN AKHIR") -> {
                    finalAnswer = trimmed.substringAfter(":").trim()
                }
                trimmed.startsWith("**PENJELASAN KONSEP") -> {
                    explanation = trimmed.substringAfter(":").trim()
                }
            }
        }
        // Simpan step terakhir
        if (currentStepDesc.isNotBlank()) {
            steps.add(Step(currentStepDesc, currentFormula, currentCalculation))
        }

        if (steps.isEmpty() && finalAnswer.isBlank()) {
            // Fallback: treat whole response as explanation
            return HomeworkResult.Success(
                steps = listOf(Step(response, "", "")),
                explanation = "Gue jawabin secara umum ya.",
                finalAnswer = "Lihat penjelasan di atas"
            )
        }

        return HomeworkResult.Success(steps, explanation, finalAnswer)
    }

    /** Process text message directly (bukan foto) untuk homework mode */
    fun processTextHomework(userText: String): Deferred<HomeworkResult> {
        return scope.async {
            val problemType = classifyProblem(userText)
            val prompt = buildHomeworkPrompt(userText, problemType)
            val response = sendToAIAndWait(prompt)
            parseStructuredResponse(response, problemType)
        }
    }
}

/** Tipe soal untuk customizing prompt */
enum class ProblemType(
    val displayName: String
) {
    GENERAL("Umum"),
    CALCULUS("Kalkulus"),
    QUADRATIC("Persamaan Kuadrat"),
    LINEAR_SYSTEM("Sistem Persamaan Linear"),
    TRIGONOMETRY("Trigonometri"),
    STATISTICS("Statistika & Peluang"),
    GEOMETRY("Geometri"),
    CHEMISTRY("Kimia"),
    PHYSICS("Fisika"),
    ENGLISH("Bahasa Inggris"),
    BIOLOGY("Biologi"),
    HISTORY("Sejarah"),
    GEOGRAPHY("Geografi"),
    ECONOMICS("Ekonomi")
}

/** Helper interface untuk OCR (implementasi di Android side) */
interface OcrHelper {
    fun extractText(imageUri: String): String
}

/** Structured step dalam jawaban homework */
data class Step(
    val description: String,
    val formula: String = "",
    val calculation: String = "",
    @SerializedName("latex") val latex: String? = null,
    @SerializedName("imageUrl") val imageUrl: String? = null
)

/** Result sealed class untuk homework */
sealed class HomeworkResult {
    data class Success(
        val steps: List<Step>,
        val explanation: String,
        val finalAnswer: String
    ) : HomeworkResult()

    data class NeedClarification(
        val question: String
    ) : HomeworkResult()

    data class Error(
        val message: String
    ) : HomeworkResult()
}