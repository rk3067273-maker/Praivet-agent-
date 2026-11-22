# J.A.R.V.I.S. (PrivateAgent) — Android HUD Companion

A native Kotlin & Jetpack Compose Liquid-Glass AI HUD featuring **Firebase AI Logic (Gemini 3.8 Flash)**, **OpenAI-compatible providers**, **Local Ollama**, **Zero-Touch Air Gestures**, **Voice Intelligence**, **Autonomous Workflows**, and **Tactical Sentry Mode**.

## Supported AI Providers & Configuration

Configure providers in the **Agent** tab. Each provider's Base URL, Model ID, Token Budget, Temperature, and Android Keystore-encrypted API key are stored independently.

| Provider | Base URL | Auth Header | Example Models | Notes |
| --- | --- | --- | --- | --- |
| **Gemini (Firebase AI)** | Managed by Firebase | App Check (No API key) | `gemini-3.8-flash` | Default zero-key cloud core |
| **OpenAI** | `https://api.openai.com/v1` | `Bearer <key>` | `gpt-4o-mini`, `o3-mini` | `o1/o3/o4/gpt-5` automatically use `max_completion_tokens` & omit `temperature` |
| **DeepSeek** | `https://api.deepseek.com/v1` | `Bearer <key>` | `deepseek-chat`, `deepseek-reasoner` | `deepseek-reasoner` omits unsupported `temperature` |
| **OpenRouter** | `https://openrouter.ai/api/v1` | `Bearer <key>` | `openai/gpt-4o-mini` | Unified OpenAI-compatible router |
| **NVIDIA NIM** | `https://integrate.api.nvidia.com/v1` | `Bearer <key>` | `meta/llama-3.1-70b-instruct` | Hosted NIM inference |
| **AgentRouter** | `https://agentrouter.org/v1` | `Bearer <key>` | `deepseek-v3` | See AgentRouter note below |
| **Local Ollama** | `http://127.0.0.1:11434` | None | `llama3.2`, `qwen2.5` | Local loopback / LAN only |
| **Custom** | `https://<your-host>/v1` | `Bearer <key>` | Custom model ID | Must use HTTPS for remote hosts |

### URL Normalization Rules
- Trailing slashes (`.../v1/`), bare hostnames (`https://api.deepseek.com`), and already-present `/chat/completions` suffixes are normalized cleanly without duplicating `/chat/completions`.
- Remote providers strictly require `https://`. `http://` is permitted only for local dev hosts (`127.0.0.1`, `localhost`, `10.0.2.2`, RFC1918 LAN).

### AgentRouter Compatibility & `HTTP 401: unauthorized client detected`
AgentRouter's official gateway enforces **client allowlisting** and rejects generic mobile/API clients directly with `HTTP 401` (`unauthorized client detected`), even when your API key is valid.
- This app **does not** spoof third-party client headers (`User-Agent` / `Originator`).
- To use AgentRouter legitimately, route traffic through your own authorized local/private gateway, or select **OpenRouter**, **DeepSeek**, **OpenAI**, **Gemini**, or **Local Ollama**.

### Troubleshooting
- **Test Connection:** Tap **Test Connection** in the **Agent** tab to validate URL normalization, check `/models` (when supported), and send a minimal `/chat/completions` verification request.
- **Token Truncation:** If an answer ends with `[Truncated: Max completion token budget reached]`, raise **Max Completion Tokens** in **Agent** settings.

## Permissions & Privacy
- **Microphone (`RECORD_AUDIO`):** Voice commands and *"Hey Jarvis"* wake-word standby.
- **Camera (`CAMERA`):** Optional silent front-camera snapshot in Sentry Mode.
- **Photo Picker:** Zero-permission Android Photo Picker for multimodal vision queries.
- All API keys are encrypted via **Android Keystore (AES-256-GCM)** and excluded from Android backups.

## Phone Installation & Automated Releases
- **Bengali Phone-Only Installation Guide:** [`INSTALL_GUIDE_BN.md`](INSTALL_GUIDE_BN.md)
- **Automated Build & GitHub Release Workflow:** [`.github/workflows/android-release.yml`](.github/workflows/android-release.yml)
- **Release Readiness & Keystore Guide:** [`RELEASE_READINESS_REPORT.md`](RELEASE_READINESS_REPORT.md)

## Release Checklist
- [x] Run `gradle :app:testDebugUnitTest` (24/24 tests across 10 suites passing).
- [x] Run `gradle :app:lintDebug` and `compile_applet` / `gradle :app:assembleDebug` cleanly.
- [x] Verify `usesCleartextTraffic="false"`, `network_security_config.xml`, and `backup_rules.xml` exclusions.
- [ ] Configure `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` (`upload`), and `ANDROID_KEY_PASSWORD` in GitHub Repository Secrets, then trigger `.github/workflows/android-release.yml` for `v1.2.0`.

