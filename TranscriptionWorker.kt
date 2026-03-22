package com.callscreener.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.callscreener.BuildConfig
import com.callscreener.data.ScreenedCallRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * WorkManager task that transcribes a recorded screening call.
 *
 * Triggered by ScreeningInCallService after the recording is saved. Runs on an
 * IO dispatcher so the blocking HTTP call doesn't stall the worker thread pool.
 *
 * Retry policy: WorkManager retries automatically (exponential back-off) when
 * Result.retry() is returned — used for transient network errors.
 *
 * Required input data keys:
 *   KEY_AUDIO_PATH  — absolute path to the WAV file written by AudioCaptureManager
 *   KEY_CALL_ID     — id of the ScreenedCall record to update with the transcript
 */
class TranscriptionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "TranscriptionWorker"
        const val KEY_AUDIO_PATH = "audio_path"
        const val KEY_CALL_ID = "call_id"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val audioPath = inputData.getString(KEY_AUDIO_PATH) ?: return@withContext run {
            Log.e(TAG, "Missing audio_path input")
            Result.failure()
        }
        val callId = inputData.getLong(KEY_CALL_ID, -1L)
        if (callId == -1L) {
            Log.e(TAG, "Missing call_id input")
            return@withContext Result.failure()
        }

        val apiKey = BuildConfig.GOOGLE_STT_API_KEY
        if (apiKey.isBlank()) {
            Log.w(TAG, "GOOGLE_STT_API_KEY not configured — add it to local.properties")
            return@withContext Result.failure()
        }

        Log.i(TAG, "Transcribing call $callId from $audioPath")

        val transcript = try {
            SpeechToTextClient().transcribe(File(audioPath), apiKey)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during transcription: ${e.message}")
            return@withContext Result.retry()
        }

        if (transcript == null) {
            // Could be silence, unintelligible audio, or a transient API error.
            // Retry once; Step 4 can tolerate a null transcript if it still fails.
            Log.w(TAG, "Null transcript for call $callId — scheduling retry")
            return@withContext Result.retry()
        }

        Log.i(TAG, "Transcript for call $callId: \"$transcript\"")
        ScreenedCallRepository(applicationContext).updateTranscript(callId, transcript)
        Result.success()
    }
}
