package com.heimdallr.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.heimdallr.data.BlocklistRepository

/**
 * Handles notification action buttons tapped by the user.
 *
 * Currently handles one action:
 *   ACTION_BLOCK — adds the caller to the hard blocklist and dismisses the notification.
 *                  Future actions (e.g. ACK, add to soft blocklist) can be added here.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NotificationActionReceiver"
        const val ACTION_BLOCK = "com.heimdallr.action.BLOCK"
        const val EXTRA_PHONE_NUMBER = "phone_number"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_BLOCK -> handleBlock(context, intent)
            else -> Log.w(TAG, "Unknown action: ${intent.action}")
        }
    }

    private fun handleBlock(context: Context, intent: Intent) {
        val number = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: run {
            Log.e(TAG, "ACTION_BLOCK received with no phone number")
            return
        }
        val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        BlocklistRepository(context).addToBlocklist(number)
        Log.i(TAG, "Added $number to blocklist from notification action")

        if (notifId != -1) {
            NotificationManagerCompat.from(context).cancel(notifId)
        }
    }
}
