package com.heimdallr.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.heimdallr.data.ScreenedCall
import com.heimdallr.data.ScreeningDecision
import com.heimdallr.ui.MainActivity

/**
 * Manages the two notifications shown during a screening session:
 *
 *   1. "Screening in progress" — an ongoing notification displayed while
 *      ScreeningInCallService holds the call (replaced once classification finishes).
 *
 *   2. "Screening result" — shown by ClassificationWorker after claude-opus-4-6
 *      returns a decision. Includes action buttons (Call Back, Block).
 *
 * Notification IDs are derived from the call's id field so in-progress and
 * result notifications for the same call share an ID and the result replaces
 * the in-progress one automatically.
 */
class ScreeningNotificationManager(private val context: Context) {

    companion object {
        private const val TAG = "ScreeningNotificationManager"
        const val CHANNEL_ID = "call_screening_results"

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Call Screening Results",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts about screened calls and their AI classification"
                }
                context.getSystemService(NotificationManager::class.java)
                    .createNotificationChannel(channel)
            }
        }

        // Flags shared across all PendingIntents
        private val PI_FLAGS =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }

    // ---------------------------------------------------------------------------
    // In-progress notification (shown while the call is being answered + recorded)
    // ---------------------------------------------------------------------------

    fun showScreeningInProgress(phoneNumber: String, callId: Long) {
        val notifId = callId.toNotifId()

        val openHistory = tapToOpenHistory(notifId)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Screening call…")
            .setContentText(phoneNumber)
            .setOngoing(true)           // can't be dismissed by the user
            .setProgress(0, 0, true)    // indeterminate progress bar
            .setContentIntent(openHistory)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        notify(notifId, notification)
    }

    // ---------------------------------------------------------------------------
    // Result notification (shown after ClassificationWorker finishes)
    // ---------------------------------------------------------------------------

    fun showClassificationResult(call: ScreenedCall) {
        val notifId = call.id.toNotifId()
        val number = call.phoneNumber
        val summary = call.aiSummary ?: number

        val (title, icon) = when (call.decision) {
            ScreeningDecision.ALLOW ->
                "Legitimate caller" to android.R.drawable.ic_dialog_info
            ScreeningDecision.SEND_TO_VOICEMAIL ->
                "Possible solicitor — sent to voicemail" to android.R.drawable.ic_dialog_alert
            ScreeningDecision.BLOCK_SILENTLY ->
                "Blocked — likely spam or scam" to android.R.drawable.ic_delete
            ScreeningDecision.SCREENING ->
                "Call screened" to android.R.drawable.ic_dialog_info
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setContentIntent(tapToOpenHistory(notifId))
            .setAutoCancel(true)
            .setOngoing(false)          // replaces the in-progress ongoing notification
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        when (call.decision) {
            ScreeningDecision.ALLOW -> {
                builder.addAction(
                    android.R.drawable.ic_menu_call,
                    "Call Back",
                    callBackIntent(number, notifId)
                )
            }
            ScreeningDecision.SEND_TO_VOICEMAIL,
            ScreeningDecision.BLOCK_SILENTLY -> {
                builder.addAction(
                    android.R.drawable.ic_delete,
                    "Block number",
                    blockIntent(number, notifId)
                )
            }
            ScreeningDecision.SCREENING -> { /* no action */ }
        }

        notify(notifId, builder.build())
    }

    fun cancel(callId: Long) {
        NotificationManagerCompat.from(context).cancel(callId.toNotifId())
    }

    // ---------------------------------------------------------------------------
    // PendingIntent helpers
    // ---------------------------------------------------------------------------

    private fun tapToOpenHistory(notifId: Int): PendingIntent =
        PendingIntent.getActivity(
            context, notifId,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PI_FLAGS
        )

    private fun callBackIntent(phoneNumber: String, notifId: Int): PendingIntent =
        PendingIntent.getActivity(
            context, notifId + 1,
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")),
            PI_FLAGS
        )

    private fun blockIntent(phoneNumber: String, notifId: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, notifId + 2,
            Intent(NotificationActionReceiver.ACTION_BLOCK).apply {
                setPackage(context.packageName)
                putExtra(NotificationActionReceiver.EXTRA_PHONE_NUMBER, phoneNumber)
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notifId)
            },
            PI_FLAGS
        )

    // ---------------------------------------------------------------------------
    // Internal
    // ---------------------------------------------------------------------------

    private fun notify(id: Int, notification: android.app.Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted — user skipped the permission dialog
            Log.w(TAG, "POST_NOTIFICATIONS not granted, skipping notification $id")
        }
    }

    /** Notification IDs must be Int; callId is a millisecond timestamp so truncate. */
    private fun Long.toNotifId(): Int = (this % Int.MAX_VALUE).toInt()
}
