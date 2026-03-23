package com.heimdallr.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.heimdallr.service.GreetingEngine

/**
 * How aggressively unknown callers are classified.
 * Injected into the Claude system prompt as a classification instruction.
 */
enum class Strictness(val label: String, val description: String) {
    LENIENT(
        "Lenient",
        "Allow most calls; only block obvious robocalls and scams"
    ),
    BALANCED(
        "Balanced",
        "Block clear spam and route likely solicitors to voicemail"
    ),
    STRICT(
        "Strict",
        "Block anything that isn't clearly a known or expected caller"
    )
}

/**
 * Persists user-configurable preferences that shape how calls are screened.
 *
 * All four settings are read by the services at call-time so changes take
 * effect on the next incoming call with no restart required.
 *
 * Storage: SharedPreferences (same pattern as the other repositories).
 */
class UserPreferencesRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Master switch for AI classification.
     * When false, unknown callers fall through to ALLOW (blocklist-only mode).
     */
    var aiScreeningEnabled: Boolean
        get() = prefs.getBoolean(KEY_AI_ENABLED, true)
        set(v) = prefs.edit { putBoolean(KEY_AI_ENABLED, v) }

    /** How aggressively unknown callers are classified. */
    var strictness: Strictness
        get() = try {
            Strictness.valueOf(prefs.getString(KEY_STRICTNESS, null) ?: "")
        } catch (_: IllegalArgumentException) {
            Strictness.BALANCED
        }
        set(v) = prefs.edit { putString(KEY_STRICTNESS, v.name) }

    /**
     * Free-text context the user provides about themselves. Injected verbatim into
     * the Claude system prompt so the model can make context-aware decisions.
     *
     * Examples:
     *   "I'm a freelance photographer; clients often call about bookings."
     *   "I'm expecting calls from a mechanic about my car this week."
     *   "I never receive legitimate calls in Mandarin."
     */
    var userContext: String
        get() = prefs.getString(KEY_USER_CONTEXT, "") ?: ""
        set(v) = prefs.edit { putString(KEY_USER_CONTEXT, v.trim()) }

    /**
     * TTS greeting text spoken to callers when the app answers.
     * Defaults to [GreetingEngine.GREETING_TEXT] when blank or unset.
     */
    var customGreeting: String
        get() = prefs.getString(KEY_GREETING, null)
            ?.takeIf { it.isNotBlank() }
            ?: GreetingEngine.GREETING_TEXT
        set(v) = prefs.edit {
            putString(KEY_GREETING, v.trim().ifBlank { GreetingEngine.GREETING_TEXT })
        }

    fun resetGreeting() = prefs.edit { remove(KEY_GREETING) }

    companion object {
        private const val PREFS_NAME = "screener_user_prefs"
        private const val KEY_AI_ENABLED = "ai_screening_enabled"
        private const val KEY_STRICTNESS = "strictness"
        private const val KEY_USER_CONTEXT = "user_context"
        private const val KEY_GREETING = "custom_greeting"
    }
}
