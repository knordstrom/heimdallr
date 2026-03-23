# Heimdallr

An Android call screener that answers unknown calls on your behalf, asks callers to identify themselves, transcribes their response, and uses Claude to decide whether to let the call through, send it to voicemail, or block it silently.

## How it works

When an unknown call arrives, Heimdallr intercepts it before your phone rings:

1. **Screening** — `CallScreeningService` checks the caller against your contacts, hard blocklist, and soft blocklist
2. **Answering** — If the caller is unknown and AI screening is enabled, the app answers the call and plays a TTS greeting: *"Please say your name and the reason for your call"*
3. **Recording** — The caller's response is recorded as a WAV file
4. **Transcription** — Google Speech-to-Text converts the audio to text
5. **Classification** — Claude (`claude-opus-4-6`) reads the transcript and decides: `ALLOW`, `SEND_TO_VOICEMAIL`, or `BLOCK_SILENTLY`
6. **Result** — The decision is applied to the live call, a notification appears with a one-sentence AI summary, and the call is logged in the call history screen

If AI screening is disabled, the app falls back to blocklist-only mode: known contacts are allowed, blocklisted numbers are blocked, and everything else passes through.

## Requirements

- Android API 28+ (Android 9 Pie or later)
- Google Cloud Speech-to-Text API key
- Anthropic API key
- The app must be granted two Android roles during onboarding:
  - **Call Screening** role — required to intercept calls
  - **Default Phone App (Dialer)** role — required to answer and record the call

## Setup

1. Clone the repo
2. Add your API keys to `local.properties` (never checked in):
   ```
   google.stt.api.key=YOUR_GOOGLE_STT_KEY
   anthropic.api.key=YOUR_ANTHROPIC_KEY
   ```
3. Build and install:
   ```bash
   ./gradlew installDebug
   ```
4. Open the app and follow the onboarding steps to grant both roles

## Project structure

```
src/
  CallScreenerApplication.kt       — creates notification channel at startup
  data/
    Models.kt                       — ScreeningDecision enum, ScreenedCall data class
    BlocklistRepository.kt          — hard/soft blocklist in SharedPreferences
    ScreenedCallRepository.kt       — append-only call history log (capped at 100)
    UserPreferencesRepository.kt    — user settings (AI toggle, strictness, context, greeting)
  service/
    CallScreenerService.kt          — 4-tier screening logic (contacts → blocklists → AI)
    ScreeningStateManager.kt        — in-process singleton bridging the two services
    ScreeningInCallService.kt       — answers flagged calls, plays greeting, records audio
    GreetingEngine.kt               — TextToSpeech wrapper with queuing
    AudioCaptureManager.kt          — AudioRecord → WAV file (16 kHz mono PCM16)
    SpeechToTextClient.kt           — Google STT v1 REST client
    TranscriptionWorker.kt          — WorkManager worker: STT job
    ClaudeClassificationClient.kt   — Anthropic Messages API client
    ClassificationWorker.kt         — WorkManager worker: Claude classification + notification
    ScreeningNotificationManager.kt — two-phase notifications (in-progress → result)
    NotificationActionReceiver.kt   — handles "Block number" notification action
  ui/
    OnboardingActivity.kt           — grants Call Screening and Dialer roles
    MainActivity.kt                 — Compose call history with colour-coded verdicts
    SettingsActivity.kt             — Compose settings screen
test/
  BlocklistRepositoryTest.kt
```

## Settings

Accessible from the call history screen:

| Setting | Description |
|---|---|
| AI screening | Master toggle. Off = blocklist-only mode |
| Strictness | **Lenient** (block obvious spam only) / **Balanced** / **Strict** (block anything unclear) |
| About you | Free text injected into the Claude prompt — e.g. *"I'm expecting calls from a mechanic this week"* |
| Greeting | Custom TTS text spoken to callers (default: *"Please say your name and the reason for your call"*) |

## Build commands

```bash
# Run unit tests
./gradlew test

# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Clean build
./gradlew clean assembleDebug
```

## Architecture notes

- Both `CallScreenerService` and `ScreeningInCallService` must run simultaneously but are bound by different Android system components. `ScreeningStateManager` (in-process singleton) coordinates which calls should be answered.
- STT and classification run as a chained WorkManager job pair (`TranscriptionWorker` → `ClassificationWorker`) so they survive process death.
- All storage uses SharedPreferences with manual JSON serialization — no ORM dependencies.
- API keys are injected from `local.properties` into `BuildConfig` at build time and never stored in source control.
