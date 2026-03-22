package com.callscreener.data

/**
 * The outcomes a screened call can have.
 * SCREENING is a transitional state while the call is being actively answered and recorded.
 */
enum class ScreeningDecision {
    ALLOW,
    BLOCK_SILENTLY,
    SEND_TO_VOICEMAIL,
    SCREENING    // Step 2+: call is being answered by the app for AI classification
}

/**
 * A record of a call that passed through the screener.
 * Stored locally so the user can review what was blocked.
 */
data class ScreenedCall(
    val id: Long = 0,
    val phoneNumber: String,
    val callerName: String?,
    val decision: ScreeningDecision,
    val timestamp: Long,
    val transcript: String? = null,      // populated in Step 3+ by STT
    val aiSummary: String? = null,       // populated in Step 4+ by LLM
    val audioFilePath: String? = null    // Step 2+: path to recorded caller audio
)
