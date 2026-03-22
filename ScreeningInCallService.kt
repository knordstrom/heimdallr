package com.callscreener.service

import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.util.Log
import com.callscreener.data.ScreenedCall
import com.callscreener.data.ScreenedCallRepository
import com.callscreener.data.ScreeningDecision

/**
 * InCallService that intercepts calls flagged by CallScreenerService, answers them
 * programmatically, plays a TTS greeting, and records the caller's response.
 *
 * Requires this app to be the default phone app (ROLE_DIALER), which OnboardingActivity
 * requests alongside ROLE_CALL_SCREENING. Without the default-dialer role, Android will
 * not bind this service and flagged calls will ring normally without being screened.
 *
 * After recording, the call is disconnected. Step 3 will transcribe the audio;
 * Step 4 will reclassify the decision based on the transcript.
 */
class ScreeningInCallService : InCallService() {

    companion object {
        private const val TAG = "ScreeningInCallService"

        // Maximum time we keep the caller on the line after the greeting finishes.
        // Step 4 (LLM classification) will end the call sooner once it has enough.
        private const val MAX_RESPONSE_MS = 20_000L
    }

    private var greetingEngine: GreetingEngine? = null
    private var audioCaptureManager: AudioCaptureManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        greetingEngine = GreetingEngine(applicationContext)
        audioCaptureManager = AudioCaptureManager(
            getExternalFilesDir("recordings") ?: filesDir.resolve("recordings")
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        greetingEngine?.shutdown()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)

        val number = call.details?.handle?.schemeSpecificPart
        if (number == null) {
            Log.w(TAG, "onCallAdded: no number on call, ignoring")
            return
        }

        if (!ScreeningStateManager.isPendingScreening(number)) return

        Log.i(TAG, "Screening call from $number (state=${call.state})")
        call.registerCallback(buildCallback(call, number))

        // Answer immediately if already ringing; callback handles late state transitions.
        if (call.state == Call.STATE_RINGING) {
            call.answer(VideoProfile.STATE_AUDIO_ONLY)
        }
    }

    // ---------------------------------------------------------------------------
    // Call state machine
    // ---------------------------------------------------------------------------

    private fun buildCallback(call: Call, number: String) = object : Call.Callback() {
        override fun onStateChanged(c: Call, state: Int) {
            when (state) {
                Call.STATE_RINGING -> {
                    Log.d(TAG, "Call ringing — answering")
                    call.answer(VideoProfile.STATE_AUDIO_ONLY)
                }
                Call.STATE_ACTIVE -> onCallActive(call, number)
                Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> {
                    audioCaptureManager?.stopRecording()
                    mainHandler.removeCallbacksAndMessages(null)
                    call.unregisterCallback(this)
                }
            }
        }
    }

    private fun onCallActive(call: Call, number: String) {
        ScreeningStateManager.consumeScreeningRequest(number)

        val callId = System.currentTimeMillis()
        val audioPath = audioCaptureManager?.startRecording(callId)

        greetingEngine?.playGreeting {
            // Greeting finished — caller is now speaking.
            // Enforce a hard cap; Step 4 will terminate sooner once it classifies.
            mainHandler.postDelayed({
                finishScreening(call, number, callId, audioPath)
            }, MAX_RESPONSE_MS)
        }
    }

    private fun finishScreening(call: Call, number: String, callId: Long, audioPath: String?) {
        audioCaptureManager?.stopRecording()

        ScreenedCallRepository(applicationContext).save(
            ScreenedCall(
                phoneNumber = number,
                callerName = null,          // no display name available at this point
                decision = ScreeningDecision.SCREENING,
                timestamp = callId,
                audioFilePath = audioPath
                // transcript and aiSummary filled in by Steps 3 and 4
            )
        )

        Log.i(TAG, "Screening complete for $number — audio: $audioPath — disconnecting")
        call.disconnect()
    }
}
