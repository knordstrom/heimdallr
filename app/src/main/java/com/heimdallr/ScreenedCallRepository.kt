package com.heimdallr.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores a log of screened calls so the user can review what the app blocked.
 *
 * Storage: SharedPreferences + JSON for Step 1 simplicity.
 * Migrate to Room when the call history UI is built in a later step.
 *
 * Keeps the 100 most recent records to avoid unbounded growth.
 */
class ScreenedCallRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(call: ScreenedCall) {
        val calls = getAll().toMutableList()
        val withId = call.copy(id = System.currentTimeMillis())
        calls.add(0, withId)                     // newest first

        val trimmed = calls.take(MAX_RECORDS)
        prefs.edit { putString(KEY_CALLS, serialize(trimmed)) }
    }

    fun getAll(): List<ScreenedCall> {
        val json = prefs.getString(KEY_CALLS, null) ?: return emptyList()
        return deserialize(json)
    }

    fun getBlocked(): List<ScreenedCall> =
        getAll().filter { it.decision != ScreeningDecision.ALLOW }

    fun clear() {
        prefs.edit { remove(KEY_CALLS) }
    }

    // ---------------------------------------------------------------------------
    // Serialization — simple JSON, no Gson/Moshi dependency for Step 1
    // ---------------------------------------------------------------------------

    private fun serialize(calls: List<ScreenedCall>): String {
        val array = JSONArray()
        calls.forEach { call ->
            val obj = JSONObject().apply {
                put("id", call.id)
                put("phoneNumber", call.phoneNumber)
                put("callerName", call.callerName ?: JSONObject.NULL)
                put("decision", call.decision.name)
                put("timestamp", call.timestamp)
                put("transcript", call.transcript ?: JSONObject.NULL)
                put("aiSummary", call.aiSummary ?: JSONObject.NULL)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun deserialize(json: String): List<ScreenedCall> {
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                ScreenedCall(
                    id = obj.getLong("id"),
                    phoneNumber = obj.getString("phoneNumber"),
                    callerName = obj.optString("callerName").ifBlank { null },
                    decision = ScreeningDecision.valueOf(obj.getString("decision")),
                    timestamp = obj.getLong("timestamp"),
                    transcript = obj.optString("transcript").ifBlank { null },
                    aiSummary = obj.optString("aiSummary").ifBlank { null }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val PREFS_NAME = "callscreener_history"
        private const val KEY_CALLS = "screened_calls"
        private const val MAX_RECORDS = 100
    }
}
