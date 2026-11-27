package com.hhh.phoneagent.data.remote.gemini

import com.hhh.phoneagent.BuildConfig
import com.hhh.phoneagent.core.common.AppError.ErrorCode
import com.hhh.phoneagent.core.common.AppResult
import com.hhh.phoneagent.core.common.failure
import com.hhh.phoneagent.core.common.runCatchingApp
import com.hhh.phoneagent.core.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calls generateContent and rotates keys. 429 cools the key down, 401/403 retires it, 5xx
 * and network errors back off and retry. The key goes in a header, never in the URL.
 */
@Singleton
class GeminiApi @Inject constructor(
    private val client: OkHttpClient,
    private val rotator: ApiKeyRotator,
    private val json: Json,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun generateContent(
        model: String,
        body: GenerateContentRequest,
    ): AppResult<GenerateContentResponse> = withContext(io) {
        val payload = json.encodeToString(GenerateContentRequest.serializer(), body)
            .toRequestBody(JSON_MEDIA_TYPE)

        var lastCode = ErrorCode.UNKNOWN
        var lastMessage = "Gemini request failed"

        repeat(MAX_ATTEMPTS) { attempt ->
            val lease = rotator.acquire()
                ?: return@withContext failure(ErrorCode.RATE_LIMITED, "All Gemini API keys are unavailable or cooling down")

            when (val outcome = execute(model, lease.value, payload)) {
                is Outcome.Success -> return@withContext parseResponse(outcome.body)
                is Outcome.Fatal -> return@withContext failure(ErrorCode.API_ERROR, outcome.message)
                is Outcome.Throttled -> {
                    rotator.reportRateLimited(lease, outcome.retryAfterMillis)
                    lastCode = ErrorCode.RATE_LIMITED
                    lastMessage = outcome.message
                }
                is Outcome.Rejected -> {
                    rotator.reportRejected(lease)
                    lastCode = ErrorCode.AUTH_FAILED
                    lastMessage = outcome.message
                }
                is Outcome.Transient -> {
                    lastCode = ErrorCode.NETWORK
                    lastMessage = outcome.message
                    delay(backoffMillis(attempt))
                }
            }
        }
        failure(lastCode, lastMessage)
    }

    private fun execute(model: String, apiKey: String, payload: okhttp3.RequestBody): Outcome {
        val request = Request.Builder()
            .url("${BuildConfig.GEMINI_BASE_URL}/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(payload)
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> Outcome.Success(body)
                    response.code == 429 -> Outcome.Throttled(
                        retryAfterMillis = response.header("Retry-After")?.toLongOrNull()?.times(1_000L),
                        message = errorMessage(body),
                    )
                    response.code == 401 || response.code == 403 -> Outcome.Rejected(errorMessage(body))
                    response.code >= 500 -> Outcome.Transient(errorMessage(body))
                    else -> Outcome.Fatal(errorMessage(body))
                }
            }
        } catch (e: IOException) {
            Outcome.Transient(e.message ?: "Network error")
        }
    }

    private fun parseResponse(body: String): AppResult<GenerateContentResponse> =
        runCatchingApp(ErrorCode.API_ERROR) {
            json.decodeFromString(GenerateContentResponse.serializer(), body)
        }

    private fun errorMessage(body: String): String {
        val fromEnvelope = runCatching {
            json.decodeFromString(ErrorEnvelope.serializer(), body).error?.message
        }.getOrNull()
        return fromEnvelope ?: body.take(300).ifBlank { "HTTP error" }
    }

    private fun backoffMillis(attempt: Int): Long = (500L shl attempt).coerceAtMost(4_000L)

    private sealed interface Outcome {
        data class Success(val body: String) : Outcome
        data class Throttled(val retryAfterMillis: Long?, val message: String) : Outcome
        data class Rejected(val message: String) : Outcome
        data class Transient(val message: String) : Outcome
        data class Fatal(val message: String) : Outcome
    }

    private companion object {
        const val MAX_ATTEMPTS = 4
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
