# J.A.R.V.I.S. (PrivateAgent) — Engineering Audit Report

**Audit Scope & Environment Transparency:**
This audit was executed inside the AI Studio Android Gradle/Kotlin workspace (`/app/applet`). The upstream Flutter/Dart source tree (`lib/`, `pubspec.yaml`, `pubspec.lock`) from `AbuZar-Ansarii/Jarvis` is **not present on disk** in this container; therefore, findings below distinguish strictly between **(A) Verified & Fixed in the on-disk Android Kotlin/Compose implementation** and **(B) External/Blocked items requiring access to the Flutter repository or live provider credentials**.

---

## 1. Confirmed Findings & Fixes (On-Disk Android Codebase)

### [CONFIRMED SECURITY WEAKNESS] Global Cleartext HTTP Traffic Permitted
- **Location:** `app/src/main/AndroidManifest.xml` (pre-audit `android:usesCleartextTraffic="true"`).
- **Risk:** Allowed unencrypted HTTP traffic to any host, exposing Bearer API keys and conversation payloads to on-path interception.
- **Fix Applied:** Disabled global cleartext (`android:usesCleartextTraffic="false"`), added `app/src/main/res/xml/network_security_config.xml` enforcing HTTPS globally while permitting HTTP strictly for explicit loopback dev hosts (`127.0.0.1`, `localhost`, `10.0.2.2`), and enforced HTTPS checks inside `EndpointUrlNormalizer`.

### [CONFIRMED SECURITY WEAKNESS] Default Auto-Backup Could Include Preferences/Captures
- **Location:** `app/src/main/res/xml/backup_rules.xml`, `app/src/main/res/xml/data_extraction_rules.xml`.
- **Risk:** Default empty templates allowed Android Auto Backup / device transfer to back up preference files and local Sentry camera captures.
- **Fix Applied:** Explicitly excluded `jarvis_secure_keys.xml`, `jarvis_legacy_keys.xml`, `datastore/jarvis_preferences.preferences_pb`, and `sentry_captures/`.

### [CONFIRMED BUG] Missing OpenAI-Compatible & AgentRouter Endpoint Normalization
- **Location:** `app/src/main/java/com/example/data/ai/JarvisAiRepository.kt` (pre-audit only supported Gemini and Ollama).
- **Risk:** Users could not connect OpenAI, DeepSeek, OpenRouter, NVIDIA NIM, AgentRouter, or Custom providers; naive URL concatenation risks `/v1/v1` or `/chat/completions/chat/completions` duplication.
- **Fix Applied:** Created `EndpointUrlNormalizer.kt` and `OpenAiCompatibleClient.kt` supporting trailing-slash normalization, `/v1` & `/chat/completions` deduplication, malformed URL rejection, and remote HTTPS enforcement.

### [CONFIRMED BUG] Missing Encrypted API Key Storage & Provider Isolation
- **Location:** `app/src/main/java/com/example/data/local/JarvisPreferences.kt`.
- **Fix Applied:** Created `SecureKeyStore.kt` (Android Keystore AES-256-GCM encryption + legacy plaintext migration + blank-overwrite guard + `SecretRedactor`) and refactored `JarvisPreferences` to store isolated per-provider `ProviderConfig` objects so switching providers never destroys custom Base URLs or Model IDs.

### [CONFIRMED BUG] Unstructured HTTP Errors, Missing Reasoning-Model Parameter Handling & SSE Chunk Hazards
- **Location:** `HttpErrorHandler.kt`, `AiProviderRegistry.kt`, `SseStreamParser.kt`.
- **Fix Applied:**
  - Implemented structured HTTP error mapping (`400, 401, 403, 404, 408, 413, 429, 500, 502, 503, 504`) with bounded exponential backoff + jitter + `Retry-After` support and strict non-retry on `400/401/403/404/413`.
  - Added reasoning-model awareness (`o1`, `o3`, `o4`, `gpt-5` use `max_completion_tokens` and omit `temperature`; `deepseek-reasoner` omits `temperature`).
  - Replaced naive line parsing with Okio `BufferedSource` UTF-8 safe `SseStreamParser` supporting split multi-byte UTF-8 boundaries, multiple events per buffer, array content parts, `reasoning_content`, and `finish_reason=length` truncation notices.

### [CONFIRMED BUG] Unconfirmed Device Actions & Voice Duplicates
- **Location:** `HudScreen.kt`, `JarvisViewModel.kt`, `VoiceController.kt`.
- **Fix Applied:** Added interactive operator confirmation dialog (`AlertDialog`) for sensitive/external AI-proposed actions (`ARM_SENTRY`, `OPEN_WIFI`, `OPEN_BLUETOOTH`, `OPEN_SETTINGS`, `WEB_SEARCH:*`), added a 1500ms voice command deduplication window, and added workflow step cancellation + `maxAgentSteps` enforcement.

### [CONFIRMED BUG] Stale Unit Test Assertion & Android JSON Stub / Offline Test Failures
- **Location:** `app/src/test/java/com/example/ExampleRobolectricTest.kt`, `HttpErrorHandler.kt`, `SseStreamParser.kt`, `OpenAiCompatibleClient.kt`, `JarvisPreferences.kt`.
- **Risk:** Stale `"My Application"` string assertion, `org.json.JSONObject` throwing `RuntimeException: Stub!` in local JVM unit tests, and Robolectric `@Config(sdk = [36])` failing in an offline sandbox while trying to download `android-all-instrumented`.
- **Fix Applied:** Migrated core AI/settings JSON parsing to Moshi (`JsonCodec.kt`), injected test-friendly storage providers in `SecureKeyStore.kt`, and implemented pure-JVM + local `com.sun.net.httpserver.HttpServer` + offline manifest/XML verification tests.

### [CONFIRMED BUG — PHASES 16–30] Missing Tool Execution Loop Guard, Hermes Persistence, Prompt Fencing, Usage & DB Migration
- **Locations:** `OpenAiCompatibleClient.kt`, `JarvisAiRepository.kt`, `JarvisDatabase.kt`, `JarvisPreferences.kt`, `DeviceTelemetryManager.kt`, `DeviceActionExecutor.kt`, `JarvisViewModel.kt`.
- **Risks Identified:**
  1. Legacy `org.json` usage remained in Ollama/workflow paths inside `JarvisAiRepository` and `JarvisViewModel.parseSteps` (JVM unit test stub risk and inconsistent schema parsing).
  2. No structured OpenAI function-calling schema validation, recursion/loop depth cap (`MAX_TOOL_ROUNDS = 3`), or capability limitation disclosure when switching to non-tool models/providers.
  3. Unstructured conversation summaries risked prompt injection and lacked persistent deduplicated Hermes memory or Room `MIGRATION_1_2` upgrade path.
  4. High-frequency `SensorManager` callbacks triggered unthrottled Compose `StateFlow` emissions and stayed registered when backgrounded even with Sentry Disarmed.
  5. Unsafe `as` system service casts and absent vibrator/URL-encoding guards in `DeviceActionExecutor`.
- **Fixes Applied:**
  - Created `AiToolRegistry.kt` (allowlisted tools, strict JSON argument validation, `ToolExecutionGuard` loop/depth protection) and updated `OpenAiCompatibleClient` + `SseStreamParser` for OpenAI tool-call loops, sensitivity gating, and token usage capture.
  - Added `supportsToolCalling` / `toolCallingLimitationReason` in `AiProviderRegistry` and `HttpErrorCategory.QUOTA_EXHAUSTED` (`402` / `insufficient_quota`) in `HttpErrorHandler` with zero auto-recharge.
  - Implemented `PromptSecurityGuard.kt` (control/bidi char stripping, secret/OTP refusal, `<untrusted_data>` XML fencing), `HermesMemoryManager.kt`, Room `version = 2` (`MIGRATION_1_2`, `PRAGMA quick_check`), `JarvisUsageSummary.kt`, and atomic sanitized backup export (`JarvisDataPortability.kt`).
  - Added 150ms sensor UI throttling + `ON_START`/`ON_STOP` lifecycle pause in `DeviceTelemetryManager` & `JarvisApp`, null-safe casts in `DeviceActionExecutor`, `SavedStateHandle` draft + interrupted-AI recovery in `JarvisViewModel`, and the **Vault** UI (`VaultScreen.kt`).

---

## 2. AgentRouter `HTTP 401: unauthorized client detected` Investigation

- **Classification:** Confirmed external provider restriction (verified via official documentation & issue disclosures).
- **Root Cause:** AgentRouter (`https://agentrouter.org/v1`) enforces server-side client fingerprinting / allowlisting on `/v1/chat/completions`, permitting only approved CLI integrations (such as Claude Code, Codex CLI, Qwen Code). Generic mobile/custom clients receive `HTTP 401` (`unauthorized_client_error`: `"unauthorized client detected"`) even with a valid Bearer API key.
- **Compliance Decision:** This app **does not** spoof third-party `User-Agent` / `Originator` headers or bypass AgentRouter policies. Instead, `HttpErrorHandler` detects this exact `401` signature and surfaces actionable guidance (use a user-owned authorized gateway or switch to OpenRouter, DeepSeek, OpenAI, Gemini, or local Ollama).

---

## 3. Feature, Verification & Repository Parity Status

- **On-disk Kotlin/Compose unit, mock-HTTP, SQLite migration & security tests (`gradle :app:testDebugUnitTest`):** `PASS` (**24/24 tests across 10 test suites**, 0 failures, 0 skipped).
- **Android APK compilation (`compile_applet` & `gradle :app:assembleDebug`):** `PASS`.
- **Dart/Flutter files (`lib/`, `pubspec.yaml`, `pubspec.lock`, `test/`):** `BLOCKED` — not on disk in this workspace.
- **Live authenticated AgentRouter / remote provider API call:** `BLOCKED` — requires real user API credentials outside this container.
- **Signed Release APK/AAB (`gradle :app:assembleRelease`):** `BLOCKED` — requires external upload keystore env vars (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`).
- **On-device Emulator / ADB interactive UI & sensor/camera/mic tests:** `BLOCKED` — no ADB/emulator access in this execution container.
- **Upstream features not implemented in this native Compose build** (Floating Co-Pilot overlay requiring `SYSTEM_ALERT_WINDOW`, Telegram Remote Control, Arc Reactor Charging Telemetry, Polyglot Diplomat, Code Black, Airspace Radar, Multi-Task Dump, Smart Home Integration): `NOT PRESENT` / `BLOCKED` in this Compose build; implemented features are **HUD (Voice/Wake-Word & Photo Picker Vision)**, **Zero-Touch Air Gestures**, **Tactical Sentry Mode**, **Agent Config & Autonomous Workflows**, and **Vault (Token Usage, Hermes Memory, Export & Wipe)**.
- **Note on Security & Policy Compliance:** Prompt injection defenses (`PromptSecurityGuard`, `<untrusted_data>` fencing, confirmation dialogs) reduce risk via defense-in-depth but cannot mathematically guarantee 100% immunity against adversarial LLM inputs. Simulated lockout screens ("Code Black") and unauthorized network scanners are intentionally excluded.

