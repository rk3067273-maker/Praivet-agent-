# J.A.R.V.I.S. (PrivateAgent) — System Architecture Report

**Scope & Runtime Basis:** Native Android **Kotlin + Jetpack Compose** codebase (`com.aistudio.jarvisagent.vpxkrm`) audited in `/app/applet`. The upstream Flutter/Dart tree (`lib/`, `pubspec.yaml`, `pubspec.lock`) and any Flutter-to-native `MethodChannel`/`EventChannel` bindings are **not present on disk** in this workspace (`BLOCKED` / `NOT PRESENT`).

---

## 1. Layered Package Topology

```text
app/src/main/java/com/example/
├── MainActivity.kt                      # Single Activity: AppCheck init, edge-to-edge, JarvisTheme
├── core/
│   └── AppCheckInit.kt                  # Firebase App Check (Debug / Play Integrity / reCAPTCHA fallback)
├── data/
│   ├── ai/
│   │   ├── AiProviderRegistry.kt        # Provider presets, capability profiles & tool-calling limitations
│   │   ├── AiToolRegistry.kt            # Allowlisted tool schemas, strict arg validator, loop/depth guard
│   │   ├── EndpointUrlNormalizer.kt     # HTTPS enforcement, loopback dev allowance, /v1 & suffix dedup
│   │   ├── HttpErrorHandler.kt          # HTTP 400–504 mapping, 402/429 QUOTA_EXHAUSTED, AgentRouter 401 notice
│   │   ├── JarvisAiRepository.kt        # Unified AI facade (Gemini, OpenAI-compatible, Ollama, webhooks)
│   │   ├── JsonCodec.kt                 # Moshi adapter helpers (JVM-testable, zero org.json runtime stubs)
│   │   ├── OpenAiCompatibleClient.kt    # OkHttp client, bounded retries, tool-call roundtrip, testConnection
│   │   └── SseStreamParser.kt           # Okio UTF-8 safe SSE parser, tool_calls deltas & TokenUsageSnapshot
│   ├── hardware/
│   │   ├── DeviceActionExecutor.kt      # Null-safe system service actions (Torch, Media, Search, Settings)
│   │   ├── DeviceTelemetryManager.kt    # Sensor listeners, hardware capability probe, 150ms UI flow throttle
│   │   ├── SentryCameraHelper.kt        # CameraX silent front-camera capture + rotation & file output
│   │   └── VoiceController.kt           # SpeechRecognizer, wake-word arming, 1500ms dedup, TextToSpeech
│   ├── local/
│   │   ├── JarvisDao.kt                 # Room DAO + transactional deduplicated Hermes memory upsert + wipe
│   │   ├── JarvisDataPortability.kt     # Sanitized JSON export builder + atomic temp-file-then-rename write
│   │   ├── JarvisDatabase.kt            # Room DB v2, MIGRATION_1_2, PRAGMA quick_check helper
│   │   ├── JarvisEntities.kt            # Messages, Workflows, SentryEvents, MemoryEntryEntity, AiUsageLogEntity
│   │   ├── JarvisPreferences.kt         # DataStore per-provider config, usage limit, AI in-flight crash marker
│   │   └── JarvisUsageSummary.kt        # Real provider/model token aggregation & rate-limit snapshot
│   ├── memory/
│   │   └── HermesMemoryManager.kt       # MemoryStore interface, RoomMemoryStore, secret block, prompt wrapping
│   └── security/
│       ├── PromptSecurityGuard.kt       # Control-char strip, secret detector, <untrusted_data> XML fencing
│       └── SecureKeyStore.kt            # Android Keystore AES-256-GCM encryption + SecretRedactor
└── ui/
    ├── JarvisApp.kt                     # Adaptive NavigationBar / NavigationRail + ON_START/ON_STOP lifecycle hooks
    ├── JarvisViewModel.kt               # AndroidViewModel + SavedStateHandle draft/crash state + Coroutine state flows
    ├── components/LiquidGlassComponents.kt
    ├── screens/
    │   ├── HudScreen.kt                 # AI command HUD, Photo Picker, voice, tool confirmation dialog
    │   ├── SensorsScreen.kt             # Air Gestures + Runtime Hardware Compatibility Matrix
    │   ├── SentryScreen.kt              # Tactical Sentry Mode, intruder captures, webhook URL config
    │   ├── VaultScreen.kt               # Token Usage, Hermes Memory CRUD/Import, Export & Recovery Controls
    │   └── WorkflowsScreen.kt           # Agent provider config, Test Connection diagnostic, Autonomous Workflows
    └── theme/ (Color.kt, Theme.kt, Type.kt)
```

---

## 2. Service Boundaries & Ownership

1. **Single-Source State Ownership (`JarvisViewModel`):**
   - `JarvisViewModel` constructs and owns one instance each of `JarvisPreferences`, `SecureKeyStore`, `JarvisDatabase` (via Room singleton), `HermesMemoryManager`, `DeviceTelemetryManager`, `VoiceController`, `DeviceActionExecutor`, and `JarvisAiRepository`.
   - Zero circular dependencies: `data/security` and `data/local` have no dependency on `data/ai`, `data/hardware`, or `ui`.
   - `ui/screens/*` receive immutable `StateFlow` values collected via `collectAsStateWithLifecycle()` and dispatch events exclusively through `JarvisViewModel`.

2. **Thread & Coroutine Discipline:**
   - **Network & File I/O (`Dispatchers.IO`):** All OkHttp requests (`OpenAiCompatibleClient`, `JarvisAiRepository`), atomic JSON export writes (`JarvisDataPortability`), and Room `PRAGMA quick_check` calls run on `Dispatchers.IO` with explicit `withTimeout` guards (60s streaming turn, 15s connection probe, 12s webhook dispatch).
   - **Sensor High-Frequency Throttling (`DeviceTelemetryManager`):** Gyroscope/accelerometer events from `SensorManager` trigger unthrottled Sentry spike detection for security fidelity, while UI `StateFlow` updates are throttled (`>= 150ms`) to prevent excessive Compose recompositions and battery drain.
   - **Resource Lifecycle:** `JarvisApp` observes `LocalLifecycleOwner.current.lifecycle` (`ON_START` -> `onAppForeground()`, `ON_STOP` -> `onAppBackground()`). When backgrounded, `DeviceTelemetryManager.pauseForBackground(keepMotionForSentry)` unregisters sensors unless Sentry Mode is actively armed, and `VoiceController` stops continuous wake-word listening; `JarvisViewModel.onCleared()` releases both telemetry listeners and speech/TTS resources cleanly.

3. **State Recovery & Persistence Safety:**
   - **Draft & Crash Recovery (`SavedStateHandle` + `JarvisPreferences`):** Unsent HUD command drafts survive process death via `SavedStateHandle`. `setAiInFlight(true)` marks active AI execution in DataStore before dispatch and clears on completion/cancellation; if a previous session terminated mid-request, `onAppForeground()` restores an informational recovery message without auto-retrying paid API calls.
   - **Database Schema Evolution (`JarvisDatabase` v2):** Upgraded from `v1` to `v2` via explicit non-destructive `MIGRATION_1_2` (`JarvisDatabaseMigrationSpec`), creating `hermes_memories` (unique `(category, normalized_key)` composite index) and `ai_usage_logs` while preserving existing `chat_messages`, `workflows`, and `sentry_events`.
   - **Atomic Export:** `JarvisDataPortability.writeTextAtomically` writes to a sibling temporary file, flushes, and renames atomically (with stream copy fallback for SAF document URIs).

---

## 3. Architectural Limitations & Non-Present Upstream Layers

- **Flutter / Native Method & Event Channels:** Absent because this workspace is a pure native Kotlin/Compose build (`minSdk = 24`, `targetSdk = 36`).
- **Background Foreground Service / System Overlay (`SYSTEM_ALERT_WINDOW`):** Intentionally omitted in alignment with least-privilege permissions and Google Play policy; Sentry monitoring operates within active lifecycle scope and explicit operator consent.
- **Telegram Remote Control & Cloud Sync (`Firebase Firestore`):** Not enabled in this offline-first local vault architecture (`firebase-firestore` and `firebase-auth` remain commented out in `app/build.gradle.kts`).
