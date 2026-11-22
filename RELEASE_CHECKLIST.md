# J.A.R.V.I.S. (PrivateAgent) — Release Readiness Checklist

**Application ID:** `com.aistudio.jarvisagent.vpxkrm`  
**Namespace:** `com.example`  
**Target / Min SDK:** `minSdk = 24`, `targetSdk = 36`, `compileSdk = 36.1`

---

## 1. Automated Verification Gates

- [x] `compile_applet` succeeds cleanly with zero Kotlin/Compose/KSP errors.
- [x] `gradle :app:assembleDebug` succeeds cleanly.
- [x] `gradle :app:lintDebug` completes with `BUILD SUCCESSFUL`.
- [x] `gradle :app:testDebugUnitTest` passes **24/24** tests across **10 test suites** (`0` failures, `0` skipped, `0` errors).
- [x] `app/src/main/` scan for `TODO`, `FIXME`, `PLACEHOLDER`, and `unimplemented` returns **0 matches**.
- [x] No hardcoded API keys (`sk-`, `AIza`, `nvapi-`, `ghp_`) or raw secret dumps in source code or `.env.example`.

## 2. Security, Privacy & Play Policy Compliance

- [x] `android:usesCleartextTraffic="false"` in `app/src/main/AndroidManifest.xml`.
- [x] `app/src/main/res/xml/network_security_config.xml` enforces HTTPS globally and permits cleartext strictly on explicit local dev loopback hosts (`127.0.0.1`, `localhost`, `10.0.2.2`).
- [x] `backup_rules.xml` & `data_extraction_rules.xml` exclude `jarvis_secure_keys.xml`, `jarvis_legacy_keys.xml`, DataStore preferences, and `sentry_captures/`.
- [x] Broad storage permissions (`READ_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`) and `SYSTEM_ALERT_WINDOW` are **not** requested; multimodal photos use `ActivityResultContracts.PickVisualMedia`.
- [x] Single launcher `Activity` (`MainActivity`, `android:exported="true"`); no unintended exported components.
- [x] Sensitive AI tool actions (`arm_sentry_mode`, `open_system_settings`, `web_search`) require explicit operator confirmation.
- [x] Hermes memory & export pipelines reject/redact API keys, Bearer tokens, PEM blocks, and OTP/password strings.
- [x] App icon is a custom adaptive vector icon (no default template green icon).
- [x] `signingConfigs` (`release` and `debugConfig`), `debug.keystore`, and `MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API` in `metadata.json` remain untouched.

## 3. External & Operator Pre-Release Actions (Blocked / Pending Outside Container)

- [ ] **Git VCS Hygiene:** `.git` repository is not initialized in `/app/applet`; verify repository state and `.gitignore` before pushing upstream.
- [ ] **Release Signing Credentials:** Provide `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` in CI/release environment and run `gradle :app:bundleRelease` / `assembleRelease`.
- [ ] **Real-Device & Emulator Smoke Testing:** Verify microphone (`RECORD_AUDIO`), Sentry front-camera (`CAMERA`), gyroscope Air Gestures, and SAF export (`CreateDocument`) on physical Android devices (minSdk 24 through targetSdk 36).
- [ ] **Live Provider Verification:** Configure a personal OpenAI/DeepSeek/OpenRouter/Ollama endpoint (or authorized AgentRouter gateway) in the **Agent** tab and run **Test Connection**.
- [ ] **Firebase Production Console:** Confirm App Check enforcement (Play Integrity / reCAPTCHA) and Gemini model quotas in Firebase Console (`gen-lang-client-0642776716`) prior to public Play rollout.
