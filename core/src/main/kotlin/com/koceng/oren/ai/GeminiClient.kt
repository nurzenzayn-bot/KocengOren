package com.koceng.oren.ai

import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Client untuk Gemini API (REST + Streaming SSE).
 * Wrapper OkHttp yang expose suspend functions & streaming channel.
 * Implementasi platform-agnostic, di-inject ke Android bridge.
 */
class GeminiClient(
    private val apiKey: String,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta/models",
    private val model: String = "gemini-1.5-flash-latest"
) {

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    init {
        // Ensure coroutine scope
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    /** Kirim prompt & terima response penuh (non-streaming) */
    suspend fun sendMessage(
        prompt: String,
        systemPrompt: String? = null,
        temperature: Float = 0.8f,
        topP: Float = 0.9f,
        topK: Int = 40,
        maxOutputTokens: Int = 2048
    ): Result<GeminiResponse> {
        return try {
            val requestBody = buildRequestBody(
                prompt = prompt,
                systemPrompt = systemPrompt,
                temperature = temperature,
                topP = topP,
                topK = topK,
                maxOutputTokens = maxOutputTokens,
                stream = false
            )

            val request = Request.Builder()
                .url("$baseUrl/$model:generateContent?key=$apiKey")
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).awaitResponse()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Result.failure(GeminiApiException("HTTP ${response.code}: $errorBody"))
            } else {
                val json = response.body?.string() ?: ""
                val geminiResponse = gson.fromJson(json, GeminiResponse::class.java)
                Result.success(geminiResponse)
            }
        } catch (e: Exception) {
            Result.failure(GeminiApiException("Network error: ${e.message}", e))
        }
    }

    /** Streaming response via Channel (SSE) */
    fun streamMessage(
        prompt: String,
        systemPrompt: String? = null,
        temperature: Float = 0.8f,
        topP: Float = 0.9f,
        topK: Int = 40,
        maxOutputTokens: Int = 2048
    ): ReceiveChannel<StreamChunk> {
        val channel = Channel<StreamChunk>(Channel.UNLIMITED)
        val jobScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        jobScope.launch {
            try {
                val requestBody = buildRequestBody(
                    prompt = prompt,
                    systemPrompt = systemPrompt,
                    temperature = temperature,
                    topP = topP,
                    topK = topK,
                    maxOutputTokens = maxOutputTokens,
                    stream = true
                )

                val request = Request.Builder()
                    .url("$baseUrl/$model:streamGenerateContent?key=$apiKey&alt=sse")
                    .post(requestBody)
                    .build()

                val call = okHttpClient.newCall(request)
                val response = call.execute()

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    channel.send(StreamChunk.Error("HTTP ${response.code}: $errorBody"))
                    channel.close()
                    return@launch
                }

                val source = response.body?.source() ?: run {
                    channel.send(StreamChunk.Error("Empty response body"))
                    channel.close()
                    return@launch
                }

                // Parse SSE stream line by line
                var done = false
                while (!done) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isEmpty()) continue
                    if (line.startsWith("data: ")) {
                        val data = line.substring(6).trim()
                        if (data == "[DONE]") {
                            done = true
                            break
                        }
                        if (data.isNotBlank()) {
                            try {
                                val chunk = gson.fromJson(data, GeminiStreamResponse::class.java)
                                chunk.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.let { text ->
                                    channel.send(StreamChunk.Text(text))
                                }
                                chunk.candidates?.firstOrNull()?.finishReason?.let { reason ->
                                    if (reason != "STOP") {
                                        channel.send(StreamChunk.Finish(reason))
                                    }
                                }
                            } catch (e: Exception) {
                                // Ignore parse errors for partial chunks
                            }
                        }
                    }
                }

                channel.send(StreamChunk.Finish("STOP"))
                channel.close()

            } catch (e: Exception) {
                channel.send(StreamChunk.Error("Stream error: ${e.message}"))
                channel.close()
            }
        }

        return channel
    }

    private fun buildRequestBody(
        prompt: String,
        systemPrompt: String?,
        temperature: Float,
        topP: Float,
        topK: Int,
        maxOutputTokens: Int,
        stream: Boolean
    ): okhttp3.RequestBody {
        val contents = mutableListOf<Content>()

        systemPrompt?.let {
            contents.add(Content(role = "user", parts = listOf(Part(text = it))))
            contents.add(Content(role = "model", parts = listOf(Part(text = "Baik, gue paham. Gue siap jadi temen ngobrol lu yang santai & bantu PR. Gas aja!"))))
        }

        contents.add(Content(role = "user", parts = listOf(Part(text = prompt))))

        val request = GenerateRequest(
            contents = contents,
            generationConfig = GenerationConfig(
                temperature = temperature,
                topP = topP,
                topK = topK,
                maxOutputTokens = maxOutputTokens
            ),
            safetySettings = listOf(
                SafetySetting(category = "HARM_CATEGORY_HARASSMENT", threshold = "BLOCK_MEDIUM_AND_ABOVE"),
                SafetySetting(category = "HARM_CATEGORY_HATE_SPEECH", threshold = "BLOCK_MEDIUM_AND_ABOVE"),
                SafetySetting(category = "HARM_CATEGORY_SEXUALLY_EXPLICIT", threshold = "BLOCK_MEDIUM_AND_ABOVE"),
                SafetySetting(category = "HARM_CATEGORY_DANGEROUS_CONTENT", threshold = "BLOCK_MEDIUM_AND_ABOVE")
            )
        )

        return gson.toJson(request).toRequestBody(jsonMediaType)
    }

    /** Tutup client */
    fun close() {
        okHttpClient.dispatcher.executorService.shutdown()
        okHttpClient.connectionPool.evictAll()
    }

    // ===== Data Classes untuk Request/Response =====

    data class Content(
        val role: String? = null,
        val parts: List<Part>? = null
    )

    data class Part(
        val text: String? = null
    )

    data class GenerateRequest(
        val contents: List<Content>,
        val generationConfig: GenerationConfig,
        val safetySettings: List<SafetySetting>
    ) {
        data class GenerationConfig(
            val temperature: Float,
            val topP: Float,
            val topK: Int,
            val maxOutputTokens: Int
        )
    }

    data class SafetySetting(
        val category: String,
        val threshold: String
    )

    /** Response non-streaming */
    data class GeminiResponse(
        val candidates: List<Candidate>? = null,
        val promptFeedback: PromptFeedback? = null
    ) {
        fun getText(): String = candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
    }

    /** Streaming response chunk */
    data class GeminiStreamResponse(
        val candidates: List<Candidate>? = null
    )

    data class Candidate(
        val content: Content? = null,
        val finishReason: String? = null,
        val index: Int = 0,
        val safetyRatings: List<SafetyRating>? = null
    )

    data class PromptFeedback(
        val blockReason: String? = null,
        val safetyRatings: List<SafetyRating>? = null
    )

    data class SafetyRating(
        val category: String? = null,
        val probability: String? = null
    )

    /** Stream chunk sealed class */
    sealed class StreamChunk {
        data class Text(val text: String) : StreamChunk()
        data class Finish(val reason: String) : StreamChunk()
        data class Error(val message: String) : StreamChunk()
    }

    /** Exception class */
    class GeminiApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /** Result wrapper */
    sealed class Result<out T> {
        data class Success<T>(val value: T) : Result<T>()
        data class Failure<T>(val exception: Exception) : Result<T>()

        companion object {
            fun <T> success(value: T): Result<T> = Success(value)
            fun <T> failure(exception: Exception): Result<T> = Failure(exception)
        }

        fun isSuccess(): Boolean = this is Success
        fun isFailure(): Boolean = this is Failure
        fun getOrNull(): T? = if (this is Success) value else null
        fun getOrThrow(): T = if (this is Success) value else throw exception
    }
}

/** Suspend extension untuk OkHttp Call -> Response */
suspend fun okhttp3.Call.awaitResponse(): okhttp3.Response = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
    enqueue(object : okhttp3.Callback {
        override fun onFailure(call: okhttp3.Call, e: IOException) {
            cont.resumeWithException(e)
        }
        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
            cont.resume(response)
        }
    })
}