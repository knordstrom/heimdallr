package com.callscreener.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.callscreener.BuildConfig
import com.callscreener.data.ScreenedCallRepository
import com.callscreener.data.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager task that classifies a screened call using the Claude API.
 *
 * Runs after TranscriptionWorker succeeds. Reads the transcript from WorkManager's
 * chained input data (TranscriptionWorker outputs KEY_TRANSCRIPT), sends it to
 * ClaudeClassificationClient, and updates the ScreenedCall record with the final
 * ScreeningDecision and a one-sentence AI summary.
 *
 * After this worker completes, the call record has a human-readable decision
 * (ALLOW, SEND_TO_VOICEMAIL, or BLOCK_SILENTLY) and an aiSummary the user can
 * read in the call history screen (Step 5).
 *
 * Input data keys (provided by TranscriptionWorker output via WorkManager chain):
 *   KEY_CALL_ID    — id of the ScreenedCall record to update
 *   KEY_TRANSCRIPT — caller's spoken text from Step 3 transcription
 */
class ClassificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ClassificationWorker"
        const val KEY_CALL_ID = "call_id"
        const val KEY_TRANSCRIPT = "transcript"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val callId = inputData.getLong(KEY_CALL_ID, -1L)
        if (callId == -1L) {
            Log.e(TAG, "Missing call_id — was TranscriptionWorker output forwarded?")
            return@withContext Result.failure()
        }

        val transcript = inputData.getString(KEY_TRANSCRIPT) ?: run {
            Log.e(TAG, "Missing transcript for call $callId")
            return@withContext Result.failure()
        }

        val apiKey = BuildConfig.ANTHROPIC_API_KEY
        if (apiKey.isBlank()) {
            Log.w(TAG, "ANTHROPIC_API_KEY not configured — add anthropic.api.key to local.properties")
            return@withContext Result.failure()
        }

        Log.i(TAG, "Classifying call $callId: \"${transcript.take(80)}…\"")

        val userPrefs = UserPreferencesRepository(applicationContext)
        val config = ClassificationConfig(
            strictness = userPrefs.strictness,
            userContext = userPrefs.userContext
        )

        val result = try {
            ClaudeClassificationClient().classify(transcript, apiKey, config)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during classification: ${e.message}")
            return@withContext Result.retry()
        }

        if (result == null) {
            Log.w(TAG, "Null classification for call $callId — retrying")
            return@withContext Result.retry()
        }

        Log.i(TAG, "Call $callId classified as ${result.decision}: \"${result.summary}\"")

        val repo = ScreenedCallRepository(applicationContext)
        repo.updateClassification(callId, result.decision, result.summary)

        // Show result notification — replaces the "screening in progress" notification
        // that ScreeningInCallService posted while the call was active.
        repo.getById(callId)?.let { updatedCall ->
            ScreeningNotificationManager(applicationContext)
                .showClassificationResult(updatedCall)
        }

        Result.success()
    }
}
