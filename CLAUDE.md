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
## Project
Android call screener app. Kotlin + Jetpack Compose.
Screens spam and cold sales calls using CallScreeningService + LLM.

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

## Build steps completed
- [x] Step 1: CallScreeningService + blocklist
- [x] Step 2: InCallService + TTS greeting + audio capture
- [x] Step 3: STT transcription (Google Speech-to-Text)
- [x] Step 4: Claude API classification (claude-opus-4-6)
- [ ] Step 5: User notification + accept/dismiss UI
- [ ] Step 6: User preferences for LLM context

## Key files
- service/CallScreenerService.kt — main screening logic
- data/BlocklistRepository.kt — hard/soft blocklists
- data/ScreenedCallRepository.kt — call history log

Compose is already enabled in `build.gradle` and dependencies are included in anticipation of future UI work.
