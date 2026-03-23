package com.callscreener.service

import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Transcribes a WAV audio file using the Google Cloud Speech-to-Text v1 REST API.
 *
 * Prerequisites:
 *   1. Enable "Cloud Speech-to-Text API" in Google Cloud Console.
 *   2. Create an API key and add it to local.properties:
 *        google.stt.api.key=AIza...
 *   3. The key is injected into BuildConfig.GOOGLE_STT_API_KEY at build time.
 *
 * Audio requirements:
 *   - Format: raw LINEAR16 (16-bit PCM, no container)
 *   - Sample rate: 16 000 Hz
 *   - Channels: mono
 *   Our WAV files have a 44-byte RIFF header that is stripped before sending.
 *
 * Synchronous — call only from a background thread (WorkManager handles this).
 */
class SpeechToTextClient {

    companion object {
        private const val TAG = "SpeechToTextClient"
        private const val ENDPOINT = "https://speech.googleapis.com/v1/speech:recognize"
        private const val WAV_HEADER_SIZE = 44
        private const val TIMEOUT_MS = 30_000
    }

    /**
     * Sends [wavFile] to Google STT and returns the top transcript, or null on error.
     */
    fun transcribe(wavFile: File, apiKey: String): String? {
        if (!wavFile.exists()) {
            Log.w(TAG, "Audio file not found: ${wavFile.absolutePath}")
            return null
        }
        if (wavFile.length() <= WAV_HEADER_SIZE) {
            Log.w(TAG, "Audio file too small to contain speech: ${wavFile.length()} bytes")
            return null
        }

        val pcm = wavFile.readBytes().drop(WAV_HEADER_SIZE).toByteArray()
        val audioBase64 = Base64.encodeToString(pcm, Base64.NO_WRAP)

        val body = JSONObject().apply {
            put("config", JSONObject().apply {
                put("encoding", "LINEAR16")
                put("sampleRateHertz", 16_000)
                put("languageCode", "en-US")
                put("model", "phone_call")   // telephony-optimized model
                put("useEnhanced", true)
            })
            put("audio", JSONObject().apply {
                put("content", audioBase64)
            })
        }.toString()

        return try {
            val responseJson = post("$ENDPOINT?key=$apiKey", body)
            parseTranscript(responseJson)
        } catch (e: IOException) {
            Log.e(TAG, "STT network error: ${e.message}")
            null
        }
    }

    // ---------------------------------------------------------------------------
    // HTTP + parsing
    // ---------------------------------------------------------------------------

    private fun post(urlString: String, body: String): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
        }

        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            val error = conn.errorStream?.bufferedReader()?.readText() ?: "(no body)"
            throw IOException("STT API returned HTTP $code: $error")
        }

        return conn.inputStream.bufferedReader().readText()
    }

    private fun parseTranscript(json: String): String? {
        return try {
            val results = JSONObject(json).optJSONArray("results")
            if (results == null || results.length() == 0) {
                Log.d(TAG, "STT returned no results (silence or unintelligible audio)")
                return null
            }
            results
                .getJSONObject(0)
                .getJSONArray("alternatives")
                .getJSONObject(0)
                .getString("transcript")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse STT response: ${e.message}")
            null
        }
    }
}
