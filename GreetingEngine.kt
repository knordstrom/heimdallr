package com.callscreener.service

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * Plays a TTS greeting to the caller over the voice call audio stream.
 *
 * Uses USAGE_VOICE_COMMUNICATION so the audio is routed through the call path
 * rather than the media speaker, sending it to the caller's ear.
 *
 * TTS initialization is asynchronous; if playGreeting() is called before init
 * completes the greeting is queued and played once ready.
 */
class GreetingEngine(context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "GreetingEngine"
        private const val UTTERANCE_ID = "screening_greeting"
        const val GREETING_TEXT =
            "Hi, you've reached an automated call screener. " +
            "Please say your name and the reason for your call after the tone."
    }

    private val tts = TextToSpeech(context, this)
    private var initialized = false
    private var pendingCallback: (() -> Unit)? = null

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.e(TAG, "TTS init failed (status=$status)")
            pendingCallback?.invoke()
            pendingCallback = null
            return
        }

        tts.language = Locale.US
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {}

            override fun onDone(utteranceId: String) {
                if (utteranceId == UTTERANCE_ID) pendingCallback?.invoke()
                pendingCallback = null
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                Log.e(TAG, "TTS error for utterance $utteranceId — proceeding anyway")
                pendingCallback?.invoke()
                pendingCallback = null
            }
        })

        initialized = true

        // Play any greeting that was requested before init completed
        pendingCallback?.let { speak(it) }
    }

    /**
     * Plays the greeting, then invokes [onComplete] when the utterance finishes.
     * Safe to call before TTS is initialized.
     */
    fun playGreeting(onComplete: () -> Unit) {
        if (!initialized) {
            pendingCallback = onComplete
            return
        }
        speak(onComplete)
    }

    private fun speak(onComplete: () -> Unit) {
        pendingCallback = onComplete
        tts.speak(GREETING_TEXT, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
