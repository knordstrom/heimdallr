package com.heimdallr.service

import android.util.Log
import com.heimdallr.data.Strictness
import com.heimdallr.data.ScreeningDecision
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class ClassificationResult(
    val decision: ScreeningDecision,
    val summary: String
)

/**
 * User-configurable parameters forwarded to ClaudeClassificationClient.
 * Read from UserPreferencesRepository by ClassificationWorker at job-run time.
 */
data class ClassificationConfig(
    val strictness: Strictness = Strictness.BALANCED,
    val userContext: String = ""
)

/**
 * Classifies a screening call transcript using the Claude API (claude-opus-4-6).
 *
 * Sends the caller's spoken response to a prompt that asks Claude to decide whether
 * the call should be allowed, sent to voicemail, or silently blocked, and to write
 * a one-sentence summary of who called and why.
 *
 * Uses the Claude Messages REST API directly (no SDK) so no additional dependencies
 * are needed beyond what the project already has.
 *
 * Prerequisite: add  anthropic.api.key=sk-ant-...  to local.properties.
 * The key is injected into BuildConfig.ANTHROPIC_API_KEY at build time.
 *
 * Synchronous — call only from a background thread (ClassificationWorker handles this).
 */
class ClaudeClassificationClient {

    companion object {
        private const val TAG = "ClaudeClassificationClient"
        private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        private const val MODEL = "claude-opus-4-6"
        private const val MAX_TOKENS = 1024
        private const val TIMEOUT_MS = 30_000
    }

    /**
     * Classifies [transcript] using [config] to shape the system prompt.
     * Returns a [ClassificationResult], or null on failure.
     */
    fun classify(
        transcript: String,
        apiKey: String,
        config: ClassificationConfig = ClassificationConfig()
    ): ClassificationResult? {
        val body = buildRequestBody(transcript, config)

        return try {
            val responseJson = post(body, apiKey)
            val text = extractText(responseJson) ?: return null
            parseResult(text)
        } catch (e: IOException) {
            Log.e(TAG, "Claude API network error: ${e.message}")
            null
        }
    }

    // ---------------------------------------------------------------------------
    // Request building
    // ---------------------------------------------------------------------------

    private fun buildRequestBody(transcript: String, config: ClassificationConfig) =
        JSONObject().apply {
            put("model", MODEL)
            put("max_tokens", MAX_TOKENS)
            put("system", buildSystemPrompt(config))
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "Caller said: \"${transcript.replace("\"", "\\\"")}\"")
                })
            })
        }.toString()

    private fun buildSystemPrompt(config: ClassificationConfig): String {
        val contextSection = if (config.userContext.isNotBlank())
            "\nContext about the phone's owner: ${config.userContext}\n"
        else ""

        val strictnessInstruction = when (config.strictness) {
            Strictness.LENIENT ->
                "Default to ALLOW when uncertain. " +
                "Only use BLOCK_SILENTLY for clear robocalls or scams."
            Strictness.BALANCED ->
                "Use your best judgment. Route likely solicitors to voicemail; " +
                "block clear spam and scams."
            Strictness.STRICT ->
                "Default to SEND_TO_VOICEMAIL or BLOCK_SILENTLY when uncertain. " +
                "Only ALLOW if the caller's purpose is clearly legitimate."
        }

        return """
            You are a call screening assistant for a mobile phone app.
            Classify an incoming phone call based on the caller's spoken response to the greeting
            "Please say your name and the reason for your call."
            $contextSection
            Classify the call as exactly one of:
            - ALLOW: A legitimate caller with a genuine reason to speak with the phone owner
              (friend, family, colleague, scheduled appointment, relevant business inquiry).
            - SEND_TO_VOICEMAIL: A solicitor, telemarketer, or unwanted but non-threatening
              caller who could reasonably leave a voicemail.
            - BLOCK_SILENTLY: A spammer, scammer, robocall, or potential fraudster.

            $strictnessInstruction

            Respond with ONLY a JSON object — no preamble, no markdown fences:
            {"decision": "<ALLOW|SEND_TO_VOICEMAIL|BLOCK_SILENTLY>", "summary": "<one sentence>"}
        """.trimIndent()
    }

    // ---------------------------------------------------------------------------
    // HTTP
    // ---------------------------------------------------------------------------

    private fun post(body: String, apiKey: String): String {
        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", "2023-06-01")
            setRequestProperty("content-type", "application/json; charset=utf-8")
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
        }

        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            val error = conn.errorStream?.bufferedReader()?.readText() ?: "(no body)"
            throw IOException("Claude API returned HTTP $code: $error")
        }

        return conn.inputStream.bufferedReader().readText()
    }

    // ---------------------------------------------------------------------------
    // Parsing
    // ---------------------------------------------------------------------------

    private fun extractText(json: String): String? {
        return try {
            val content = JSONObject(json).getJSONArray("content")
            (0 until content.length())
                .map { content.getJSONObject(it) }
                .firstOrNull { it.getString("type") == "text" }
                ?.getString("text")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract text from response: ${e.message}")
            null
        }
    }

    private fun parseResult(raw: String): ClassificationResult? {
        // Strip optional markdown code fences Claude might add despite the prompt
        val json = raw.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()

        return try {
            val obj = JSONObject(json)
            val decision = ScreeningDecision.valueOf(obj.getString("decision"))
            val summary = obj.getString("summary")
            ClassificationResult(decision, summary)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse classification JSON: $json — ${e.message}")
            null
        }
    }
}
