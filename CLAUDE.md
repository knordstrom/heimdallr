# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Run unit tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.callscreener.BlocklistRepositoryTest"

# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Clean build
./gradlew clean assembleDebug
```

## Architecture

Heimdallr is an Android call screening app (Kotlin, min SDK 28) that hooks into the Android Telecom framework to intercept calls before they ring.

**Call flow:**
1. Android Telecom calls `CallScreenerService.onScreenCall()` for every incoming call
2. The service checks: known contact → hard blocklist → soft blocklist → allow (future: AI answering)
3. The screening decision and call metadata are persisted to `ScreenedCallRepository`
4. The service responds with allow, silent block, or send-to-voicemail

**Key components:**

- `CallScreenerService` — extends Android's `CallScreeningService`; contains the 4-tier screening logic
- `BlocklistRepository` — manages hard/soft blocklists in SharedPreferences; normalizes phone numbers before matching
- `ScreenedCallRepository` — append-only call history log in SharedPreferences (capped at 100 entries, manual JSON serialization, no Gson/Moshi)
- `OnboardingActivity` — handles the one-time flow to grant the app the `CALL_SCREENING` role (uses `RoleManager` on API 29+, falls back to `TelecomManager` on API 28)
- `Models.kt` — `ScreeningDecision` enum and `ScreenedCall` data class (includes `transcript` and `aiSummary` fields reserved for future steps)

**Storage:** Both repositories use SharedPreferences with manual JSON serialization. Comments throughout mark where Room DB migration will happen in a later phase.

**Permissions:** The service requires `BIND_SCREENING_SERVICE` (enforced by the system) plus `READ_CALL_LOG` and `READ_PHONE_STATE` as runtime permissions.

## Multi-Phase Roadmap

The codebase is explicitly structured around phases:
- **Step 1 (current):** Blocklist-only filtering
- **Step 2:** AI audio answering + LLM classification at the callout point in `screenNumber()`
- **Step 3:** STT transcript generation (field reserved in `ScreenedCall`)
- **Step 4:** LLM summarization (field reserved in `ScreenedCall`)
- **Later:** Room DB migration, Compose UI for call history and settings

Compose is already enabled in `build.gradle` and dependencies are included in anticipation of future UI work.
