package com.callscreener.data

/**
 * The three outcomes a screened call can have.
 * Step 2 will add AI_ANSWERING as a transitional state.
 */
enum class ScreeningDecision {
    ALLOW,
    BLOCK_SILENTLY,
    SEND_TO_VOICEMAIL
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
    val aiSummary: String? = null        // populated in Step 4+ by LLM
)
