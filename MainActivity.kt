package com.callscreener.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callscreener.data.ScreenedCall
import com.callscreener.data.ScreenedCallRepository
import com.callscreener.data.ScreeningDecision
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Main screen: shows the full call screening history.
 *
 * Each item displays the caller's number (or name if available), the AI summary,
 * the screening decision with a colour-coded badge, and a human-readable timestamp.
 *
 * The list is reloaded each time the activity resumes so freshly classified calls
 * (written by ClassificationWorker in the background) appear immediately.
 *
 * Also requests POST_NOTIFICATIONS on API 33+ so result notifications can be shown.
 */
class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied — notifications silently skipped if denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        val repo = ScreenedCallRepository(this)
        setContent {
            MaterialTheme {
                CallHistoryScreen(repo)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

// ---------------------------------------------------------------------------
// Composables
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallHistoryScreen(repo: ScreenedCallRepository) {
    var calls by remember { mutableStateOf(emptyList<ScreenedCall>()) }

    LaunchedEffect(Unit) {
        calls = repo.getAll()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Call History") })
        }
    ) { padding ->
        if (calls.isEmpty()) {
            EmptyState(Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(calls, key = { it.id }) { call ->
                    CallHistoryItem(call)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "No calls screened yet",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CallHistoryItem(call: ScreenedCall) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Decision badge
        DecisionBadge(
            decision = call.decision,
            modifier = Modifier.padding(top = 2.dp, end = 12.dp)
        )

        // Call details
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = call.callerName ?: call.phoneNumber,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = call.timestamp.toRelativeTime(),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            if (call.callerName != null) {
                Text(
                    text = call.phoneNumber,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val detail = call.aiSummary ?: call.transcript
            if (detail != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = detail,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DecisionBadge(decision: ScreeningDecision, modifier: Modifier = Modifier) {
    val (label, color) = when (decision) {
        ScreeningDecision.ALLOW            -> "✓" to Color(0xFF2E7D32)   // green
        ScreeningDecision.SEND_TO_VOICEMAIL -> "VM" to Color(0xFFF57F17)  // amber
        ScreeningDecision.BLOCK_SILENTLY   -> "✕" to Color(0xFFC62828)   // red
        ScreeningDecision.SCREENING        -> "…" to Color(0xFF616161)   // grey
    }

    Box(
        modifier = modifier
            .size(32.dp)
            .background(color, shape = MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun Long.toRelativeTime(): String {
    val now = System.currentTimeMillis()
    val diff = now - this
    return when {
        diff < 60_000L -> "Just now"
        diff < 3_600_000L -> "${diff / 60_000}m ago"
        diff < 86_400_000L -> "${diff / 3_600_000}h ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(this))
    }
}
