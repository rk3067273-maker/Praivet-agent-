# Changelog

## [1.2.0] - 2026-11-22
### Added
- **Structured AI Tool Calling (`AiToolRegistry`, `OpenAiCompatibleClient`):** OpenAI-compatible multi-turn tool-call loop with allowlisted tool schemas, strict argument validation, depth limit (`MAX_TOOL_ROUNDS = 3`), identical-call loop detection, sensitivity confirmation, and explicit capability notices for non-tool providers/models.
- **Hermes Persistent Memory Engine (`HermesMemoryManager`, Room v2):** Deduplicated `(category, normalized_key)` SQLite persistence (`MIGRATION_1_2`), `PRAGMA quick_check` integrity verification, edit/delete CRUD, JSON import/export, and automatic rejection of API keys, passwords, and OTPs.
- **Prompt Injection Defense-in-Depth (`PromptSecurityGuard`):** Zero-width/bidi control char stripping, spoofed action-tag filtering, injection marker screening on imports, and `<untrusted_data>` XML delimiter wrapping for telemetry, memories, and tool results.
- **Vault Screen & Data Portability (`VaultScreen`, `JarvisDataPortability`, `JarvisUsageSummary`):** Real token/rate-limit telemetry dashboard, `QUOTA_EXHAUSTED` (`HTTP 402` / `insufficient_quota`) handling with zero auto-recharge, sanitized atomic JSON export, cache clear, key revocation, and full local data wipe.
- **Hardware Compatibility Matrix & Lifecycle Power Guards:** Runtime gyroscope/camera/flash/microphone/vibrator probe (`SensorsScreen`), 150ms sensor UI throttling, `ON_START`/`ON_STOP` lifecycle pausing, `SavedStateHandle` command draft persistence, and interrupted-AI crash recovery.
- **Documentation & Tests:** Added `ARCHITECTURE_REPORT.md`, `TEST_REPORT.md`, `RELEASE_CHECKLIST.md`, and 9 new unit/integration tests across 3 new test suites (`24/24` passing).

## [1.1.0] - 2026-11-22
### Added
- Multi-provider OpenAI-compatible client (`OpenAiCompatibleClient`) supporting OpenAI, DeepSeek, OpenRouter, NVIDIA NIM, AgentRouter, Local Ollama, and Custom endpoints.
- Robust `EndpointUrlNormalizer` preventing duplicated `/v1` or `/chat/completions` paths and enforcing HTTPS on remote hosts.
- Android Keystore AES-256-GCM API key vault (`SecureKeyStore`) with legacy plaintext key migration and secret redaction.
- Per-provider isolated configuration persistence (`JarvisPreferences`) so switching providers never overwrites custom URLs or models.
- Dedicated **Test Connection** diagnostic tool probing `/models` and `/chat/completions` with HTTP status, latency, and AgentRouter `401` client-policy detection.
- Model-aware capability handling (`max_completion_tokens` vs `max_tokens`, omitting `temperature` for reasoning models, token-budget truncation notices).
- `SseStreamParser` with UTF-8 split-boundary safety, multi-event buffer parsing, array content parts, and `reasoning_content`.
- Operator confirmation dialog for AI-proposed device/external actions, voice command deduplication, and workflow step cancellation.
- Automated JVM unit, local mock-HTTP-server, and manifest/security-config test suites plus `AUDIT_REPORT.md`, `SECURITY.md`, and `README.md`.

### Security
- Disabled global `android:usesCleartextTraffic` in `AndroidManifest.xml` in favor of scoped `network_security_config.xml`.
- Excluded key storage, preferences, and Sentry photos from `backup_rules.xml` and `data_extraction_rules.xml`.

