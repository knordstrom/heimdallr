package com.callscreener.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Manages two lists of phone numbers:
 *
 *  - Blocklist:     hard block, call dropped silently, no notification
 *  - Soft blocklist: diverted to voicemail, missed call notification shown
 *
 * Storage: SharedPreferences for Step 1 simplicity.
 * Migrate to Room in a later step when call history is added.
 *
 * Numbers are stored in E.164 format where possible (+12025551234)
 * but the matcher also strips formatting for comparison robustness.
 */
class BlocklistRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------------------
    // Read
    // ---------------------------------------------------------------------------

    fun isBlocked(phoneNumber: String): Boolean {
        val normalized = normalize(phoneNumber)
        return getBlocklist().any { normalize(it) == normalized }
    }

    fun isSoftBlocked(phoneNumber: String): Boolean {
        val normalized = normalize(phoneNumber)
        return getSoftBlocklist().any { normalize(it) == normalized }
    }

    fun getBlocklist(): Set<String> =
        prefs.getStringSet(KEY_BLOCKLIST, emptySet()) ?: emptySet()

    fun getSoftBlocklist(): Set<String> =
        prefs.getStringSet(KEY_SOFT_BLOCKLIST, emptySet()) ?: emptySet()

    // ---------------------------------------------------------------------------
    // Write
    // ---------------------------------------------------------------------------

    fun addToBlocklist(phoneNumber: String) {
        val current = getBlocklist().toMutableSet()
        current.add(normalize(phoneNumber))
        prefs.edit { putStringSet(KEY_BLOCKLIST, current) }
    }

    fun removeFromBlocklist(phoneNumber: String) {
        val current = getBlocklist().toMutableSet()
        current.remove(normalize(phoneNumber))
        prefs.edit { putStringSet(KEY_BLOCKLIST, current) }
    }

    fun addToSoftBlocklist(phoneNumber: String) {
        val current = getSoftBlocklist().toMutableSet()
        current.add(normalize(phoneNumber))
        prefs.edit { putStringSet(KEY_SOFT_BLOCKLIST, current) }
    }

    fun removeFromSoftBlocklist(phoneNumber: String) {
        val current = getSoftBlocklist().toMutableSet()
        current.remove(normalize(phoneNumber))
        prefs.edit { putStringSet(KEY_SOFT_BLOCKLIST, current) }
    }

    // ---------------------------------------------------------------------------
    // Normalization
    // Strips spaces, dashes, parentheses for comparison.
    // Does not attempt full E.164 conversion (needs country code context).
    // ---------------------------------------------------------------------------

    private fun normalize(number: String): String =
        number.replace(Regex("[\\s\\-().+]"), "")

    companion object {
        private const val PREFS_NAME = "callscreener_blocklist"
        private const val KEY_BLOCKLIST = "blocklist"
        private const val KEY_SOFT_BLOCKLIST = "soft_blocklist"
    }
}
