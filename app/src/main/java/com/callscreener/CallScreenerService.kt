package com.callscreener.service

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.callscreener.data.BlocklistRepository
import com.callscreener.data.ScreenedCall
import com.callscreener.data.ScreenedCallRepository
import com.callscreener.data.ScreeningDecision

/**
 * CallScreenerService
 *
 * Registered with Android's Telecom system to intercept every incoming call
 * before it rings. Android calls onScreenCall() and waits for respondToCall()
 * before deciding whether to ring the device.
 *
 * Step 1 scope: number blocklist only.
 * Step 2+ will add ConnectionService audio answering and LLM classification.
 */
class CallScreenerService : CallScreeningService() {

    private val tag = "CallScreenerService"

    // Injected in a real app via Hilt/Koin — kept simple here for clarity
    private val blocklistRepo by lazy { BlocklistRepository(applicationContext) }
    private val screenedCallRepo by lazy { ScreenedCallRepository(applicationContext) }

    override fun onScreenCall(callDetails: Call.Details) {
        val handle = callDetails.handle
        val phoneNumber = handle?.schemeSpecificPart ?: run {
            Log.w(tag, "Call with no number — allowing through")
            allowCall(callDetails)
            return
        }

        Log.d(tag, "Screening call from: $phoneNumber")

        val decision = screenNumber(phoneNumber, callDetails)

        // Persist for the user to review in the call history screen
        screenedCallRepo.save(
            ScreenedCall(
                phoneNumber = phoneNumber,
                decision = decision,
                timestamp = System.currentTimeMillis(),
                callerName = callDetails.callerDisplayName?.toString()
            )
        )

        when (decision) {
            ScreeningDecision.ALLOW -> {
                Log.d(tag, "Allowing call from $phoneNumber")
                allowCall(callDetails)
            }
            ScreeningDecision.BLOCK_SILENTLY -> {
                Log.d(tag, "Silently blocking call from $phoneNumber")
                blockCall(callDetails, sendToVoicemail = false, showMissedCallNotification = false)
            }
            ScreeningDecision.SEND_TO_VOICEMAIL -> {
                Log.d(tag, "Sending $phoneNumber to voicemail")
                blockCall(callDetails, sendToVoicemail = true, showMissedCallNotification = true)
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Screening logic — Step 1: blocklist only
    // Step 2+ will add: unknown caller → answer with AI → LLM classification
    // ---------------------------------------------------------------------------

    private fun screenNumber(
        phoneNumber: String,
        callDetails: Call.Details
    ): ScreeningDecision {
        // 1. Always allow contacts (system populates callerDisplayName for contacts)
        if (isKnownContact(callDetails)) {
            return ScreeningDecision.ALLOW
        }

        // 2. Check hard blocklist (numbers the user has explicitly blocked)
        if (blocklistRepo.isBlocked(phoneNumber)) {
            return ScreeningDecision.BLOCK_SILENTLY
        }

        // 3. Check voicemail list (numbers to silently divert, not hard-block)
        if (blocklistRepo.isSoftBlocked(phoneNumber)) {
            return ScreeningDecision.SEND_TO_VOICEMAIL
        }

        // 4. Unknown caller — allow for now, Step 2 will answer with AI instead
        return ScreeningDecision.ALLOW
    }

    /**
     * Android doesn't give us direct contact lookup here — we check if the
     * caller display name is populated, which the system only sets for contacts.
     * A more robust check would query ContactsContract in a coroutine.
     */
    private fun isKnownContact(callDetails: Call.Details): Boolean {
        return !callDetails.callerDisplayName.isNullOrBlank()
    }

    // ---------------------------------------------------------------------------
    // Response helpers — thin wrappers around the SDK for readability
    // ---------------------------------------------------------------------------

    private fun allowCall(callDetails: Call.Details) {
        respondToCall(
            callDetails,
            CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSilenceCall(false)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build()
        )
    }

    private fun blockCall(callDetails: Call.Details, sendToVoicemail: Boolean, showMissedCallNotification: Boolean) {
        respondToCall(
            callDetails,
            CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(!sendToVoicemail)
                .setSilenceCall(false)
                .setSkipCallLog(false)
                .setSkipNotification(!showMissedCallNotification)
                .build()
        )
    }
}
