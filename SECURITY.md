# Security Architecture & Policy

## 1. Credential Protection
- **Android Keystore AES-256-GCM:** User-supplied AI provider API keys are encrypted using a hardware/TEE-backed `AndroidKeyStore` AES-256-GCM key (`jarvis_api_key_master_aes256`) and persisted solely in `MODE_PRIVATE` storage (`jarvis_secure_keys.xml`).
- **Legacy Migration:** Any legacy plaintext keys are migrated safely into encrypted storage and removed only after decryption verification succeeds. Blank inputs never overwrite valid saved keys.
- **Backup Exclusion:** `backup_rules.xml` and `data_extraction_rules.xml` explicitly exclude `jarvis_secure_keys.xml`, DataStore preferences, and `sentry_captures/` from Android Auto Backup and device-to-device transfer.
- **Log & Error Redaction:** `SecretRedactor` strips `Bearer ...`, `sk-...`, `AIza...`, `nvapi-...`, and active secret values from exception strings and UI error banners.

## 2. Transport Security
- **HTTPS Enforced:** `AndroidManifest.xml` sets `android:usesCleartextTraffic="false"` with `network_security_config.xml` denying cleartext HTTP globally.
- **Scoped Local Development Exception:** Cleartext `http://` is permitted strictly for explicit local loopback hosts (`127.0.0.1`, `localhost`, `10.0.2.2`) so local Ollama inference works without exposing remote API traffic.
- **Remote Webhook Validation:** Optional Sentry webhook URLs require `https://`.

## 3. Permissions & User Control
- **Microphone (`RECORD_AUDIO`):** Requested at runtime only when tapping voice input or arming wake-word listening. Includes a 1500ms duplicate-command debounce.
- **Camera (`CAMERA`):** Requested at runtime only for Sentry Mode silent intruder snapshot capture; optional (`required="false"`).
- **Media Selection:** Uses Android's zero-permission `PickVisualMedia` Photo Picker (`READ_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES` are never requested).
- **Operator Confirmation:** Sensitive or external AI-proposed actions (`arm_sentry_mode`, `open_system_settings`, `web_search`) require explicit confirmation via an in-app dialog before execution.
- **Least-Privilege Architecture:** Broad storage permissions, `SYSTEM_ALERT_WINDOW` overlays, arbitrary shell command execution, and unauthorized network scanners are excluded.

## 4. Prompt Injection & AI Tool Defense-in-Depth
- **Passive Context Fencing (`PromptSecurityGuard`):** Persistent Hermes memories, device telemetry snapshots, and tool outputs are stripped of zero-width/bidi override control characters and wrapped inside explicit `<untrusted_data source="...">...</untrusted_data>` delimiters rather than injected raw into system instructions.
- **Allowlisted Schema Validation (`AiToolRegistry`):** Model-generated tool calls must match an allowlisted tool name, exact JSON schema types, and finite recursion/loop bounds (`MAX_TOOL_ROUNDS = 3`, identical-call loop detection). Arbitrary commands or unvalidated parameters are rejected.
- **Quota & Billing Safety (`HttpErrorHandler`):** `HTTP 402` and `insufficient_quota` `HTTP 429` responses stop cleanly (`QUOTA_EXHAUSTED`) with zero automatic recharge or silent paid fallback.
- **Residual Risk Limitation:** **Prompt injection cannot be eliminated with 100% certainty in any large language model system.** Untrusted text can still attempt semantic manipulation; therefore, this app combines strict tool allowlisting, argument validation, and mandatory human confirmation before sensitive device or external actions execute.

## 5. Memory Privacy, Sanitized Export & Data Wipe
- **Secret Refusal (`HermesMemoryManager`):** Persistent memory storage and JSON imports refuse API keys (`sk-`, `AIza`, `nvapi-`, `ghp_`), `Bearer` tokens, PEM private keys, and password/OTP strings.
- **Sanitized Backup Export (`JarvisDataPortability`):** Exported JSON bundles carry an explicit external-transmission warning banner, omit all API keys (`apiKeysIncluded = false`), redact known secrets, and write atomically via temp-file-then-rename.
- **One-Tap Revocation & Wipe (`VaultScreen`):** Users can clear temporary chat/event caches, revoke all encrypted API keys, or perform a confirmed full local data wipe across Room, DataStore, Keystore, and Sentry capture files.

## 6. Responsible Disclosure
If you discover a security issue in this build, do not open a public issue with live credentials or exploit payloads. Report the affected component, reproduction steps, and impact privately to the project maintainer.

