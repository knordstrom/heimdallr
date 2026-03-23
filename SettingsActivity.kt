package com.callscreener.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.callscreener.data.Strictness
import com.callscreener.data.UserPreferencesRepository
import com.callscreener.service.GreetingEngine

/**
 * Settings screen — lets the user configure how the LLM screens calls.
 *
 * Changes are written to UserPreferencesRepository on every UI interaction
 * (SharedPreferences writes are fast and non-blocking on the main thread).
 * All settings take effect on the next incoming call with no restart required.
 *
 * Reachable from:
 *   - The app launcher (via OnboardingActivity → MainActivity → overflow menu, Step 6+)
 *   - The system telecom settings deep-link (declared in call_screening_service.xml)
 */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = UserPreferencesRepository(this)
        setContent {
            MaterialTheme {
                SettingsScreen(repo)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Composables
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(repo: UserPreferencesRepository) {
    // Mirror prefs into Compose state; write back to repo on every change.
    var aiEnabled by remember { mutableStateOf(repo.aiScreeningEnabled) }
    var strictness by remember { mutableStateOf(repo.strictness) }
    var userContext by remember { mutableStateOf(repo.userContext) }
    var greeting by remember { mutableStateOf(repo.customGreeting) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Screener Settings") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            // ── AI screening toggle ──────────────────────────────────────────
            SectionCard(title = "AI Screening") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable AI classification", fontWeight = FontWeight.Medium)
                        Text(
                            if (aiEnabled) "Unknown callers are answered and classified by Claude"
                            else "Blocklist-only mode — unknown callers ring through",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = aiEnabled,
                        onCheckedChange = { aiEnabled = it; repo.aiScreeningEnabled = it }
                    )
                }
            }

            // ── Strictness ───────────────────────────────────────────────────
            SectionCard(title = "Strictness") {
                Text(
                    "Controls how Claude classifies ambiguous callers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Strictness.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = strictness == option,
                            onClick = { strictness = option; repo.strictness = option },
                            enabled = aiEnabled
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                option.label,
                                fontWeight = if (strictness == option) FontWeight.SemiBold
                                             else FontWeight.Normal,
                                color = if (aiEnabled) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── User context ─────────────────────────────────────────────────
            SectionCard(title = "About You") {
                Text(
                    "Tell Claude who you are so it can make smarter decisions. " +
                    "This text is sent to the AI with every call.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = userContext,
                    onValueChange = { userContext = it; repo.userContext = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = aiEnabled,
                    placeholder = {
                        Text(
                            "e.g. I'm a freelance photographer; clients often call about bookings.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    minLines = 3,
                    maxLines = 6,
                    label = { Text("Context (optional)") }
                )
            }

            // ── Custom greeting ──────────────────────────────────────────────
            SectionCard(title = "Caller Greeting") {
                Text(
                    "The message spoken to callers when the app answers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = greeting,
                    onValueChange = { greeting = it; repo.customGreeting = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = aiEnabled,
                    label = { Text("Greeting") },
                    minLines = 3,
                    maxLines = 6
                )
                TextButton(
                    onClick = {
                        repo.resetGreeting()
                        greeting = GreetingEngine.GREETING_TEXT
                    },
                    enabled = aiEnabled && greeting != GreetingEngine.GREETING_TEXT
                ) {
                    Text("Reset to default")
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}
