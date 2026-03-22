package com.callscreener.service

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Records the caller's audio during a screening session and writes it as a WAV file.
 *
 * Uses VOICE_COMMUNICATION as the audio source — this captures the microphone path
 * used for VoIP-style calls. Full downlink-only capture (caller's voice, no mic)
 * requires system/carrier privileges not available to third-party apps; Step 3
 * STT will work with whatever is captured here.
 *
 * Recording happens on a dedicated background thread; call [stopRecording] from
 * any thread to finish gracefully.
 */
class AudioCaptureManager(private val outputDir: File) {

    companion object {
        private const val TAG = "AudioCaptureManager"
        private const val SAMPLE_RATE = 16_000          // Hz — matches most STT APIs
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BYTES_PER_SAMPLE = 2          // 16-bit
    }

    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    @Volatile private var recording = false

    /**
     * Starts recording. Returns the absolute path of the output WAV file,
     * or null if the AudioRecord could not be initialized.
     */
    fun startRecording(callId: Long): String? {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBuf <= 0) {
            Log.e(TAG, "Invalid min buffer size: $minBuf")
            return null
        }

        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            minBuf * 4
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            record.release()
            return null
        }

        outputDir.mkdirs()
        val outputFile = File(outputDir, "screening_$callId.wav")

        audioRecord = record
        recording = true
        record.startRecording()

        captureThread = Thread({ captureLoop(record, outputFile, minBuf) }, "AudioCapture")
            .also { it.start() }

        Log.i(TAG, "Recording started → ${outputFile.absolutePath}")
        return outputFile.absolutePath
    }

    fun stopRecording() {
        recording = false
        captureThread?.join(3_000)
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        Log.i(TAG, "Recording stopped")
    }

    // ---------------------------------------------------------------------------
    // Internal
    // ---------------------------------------------------------------------------

    private fun captureLoop(record: AudioRecord, outputFile: File, bufSize: Int) {
        val pcmChunks = mutableListOf<ByteArray>()
        val buf = ByteArray(bufSize)

        try {
            while (recording) {
                val n = record.read(buf, 0, bufSize)
                if (n > 0) pcmChunks.add(buf.copyOf(n))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Capture loop error: ${e.message}")
        }

        try {
            writeWav(outputFile, pcmChunks)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write WAV: ${e.message}")
        }
    }

    private fun writeWav(file: File, chunks: List<ByteArray>) {
        val pcmBytes = chunks.sumOf { it.size }
        FileOutputStream(file).use { out ->
            out.write(wavHeader(pcmBytes))
            chunks.forEach { out.write(it) }
        }
        Log.d(TAG, "Wrote ${file.length()} bytes to ${file.name}")
    }

    private fun wavHeader(pcmBytes: Int): ByteArray {
        val byteRate = SAMPLE_RATE * BYTES_PER_SAMPLE   // mono
        return ByteArray(44).also { h ->
            ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray())
                putInt(pcmBytes + 36)   // total file size − 8
                put("WAVE".toByteArray())
                put("fmt ".toByteArray())
                putInt(16)              // PCM chunk size
                putShort(1)             // PCM format
                putShort(1)             // mono
                putInt(SAMPLE_RATE)
                putInt(byteRate)
                putShort(BYTES_PER_SAMPLE.toShort())    // block align
                putShort(16)            // bits per sample
                put("data".toByteArray())
                putInt(pcmBytes)
            }
        }
    }
}
