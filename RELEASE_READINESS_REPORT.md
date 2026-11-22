# J.A.R.V.I.S. (PrivateAgent) — Release Readiness & Publishing Report

**Repository Target:** `https://github.com/AbuZar-Ansarii/Jarvis`  
**Local Workspace:** `/app/applet` (Native Android Kotlin + Jetpack Compose)  
**Target Release Version:** `1.2.0` (`versionCode = 2`, tag `v1.2.0`)  
**Application ID:** `com.aistudio.jarvisagent.vpxkrm`  

---

## 1. Phase 1–14 Pre-Release Audit Matrix

| Audit Item | Status | Verified Facts & Details |
| --- | :---: | --- |
| Kotlin/Compose compilation (`compile_applet`) | **PASS** | Compiles cleanly with KSP, Compose compiler, Material 3, Firebase AI Logic, Room v2, Moshi, and OkHttp. |
| Automated unit, mock-HTTP, migration & security tests (`gradle :app:testDebugUnitTest`) | **PASS** | **24 / 24 tests passing across 10 suites** (`0` failures, `0` skipped). |
| Android Lint static pass (`gradle :app:lintDebug`) | **PASS** | `BUILD SUCCESSFUL` (`app/build/reports/lint-results-debug.html`). |
| Debug APK assembly (`gradle :app:assembleDebug`) | **PASS** | Debug APK builds cleanly (`27,677,651` bytes in `.build-outputs/app-debug.apk`). |
| Application ID & semantic versioning | **PASS** | Preserved `com.aistudio.jarvisagent.vpxkrm`; aligned `versionCode = 2`, `versionName = "1.2.0"` with `CHANGELOG.md` `1.2.0` (avoids upstream tags `v1.0.0`, `v1.0.0.1`, `v1.0.2`, `v1.1.0`). |
| Android SDK, JDK, AGP & Gradle alignment | **PASS** | `compileSdk = 36.1`, `minSdk = 24`, `targetSdk = 36`, AGP `9.1.1`, Gradle `9.3.1`, Temurin JDK `21.0.13` (`JavaVersion.VERSION_11` bytecode target). |
| Native library & ABI compatibility | **PASS** | Verified `.so` libraries in APK for `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`; opt-in `-PenableAbiSplits=true` added for CI release splits (`arm64-v8a`, `armeabi-v7a`, `universal`). |
| Manifest, transport & backup security | **PASS** | `usesCleartextTraffic="false"`, `network_security_config.xml` HTTPS enforcement (loopback-only HTTP), backup exclusions for keys/prefs/captures, zero broad storage permissions. |
| Secret & `.gitignore` audit | **PASS** | Zero hardcoded keys in `app/src/main/`; `.gitignore` updated to ignore `.build-outputs/`, `*.apk`, `*.aab`, `*.jks`, `*.keystore`, `*.p12`, `.env`, `.env.*` (`!.env.example`). |
| GitHub Actions release workflow (`.github/workflows/android-release.yml`) | **PASS** | Created automated workflow: tests, lint, secret validation, temporary keystore decode + guaranteed cleanup, split/universal release build, `aapt2` metadata check, `apksigner` verification, `SHA256SUMS.txt`, `BUILD_INFO.txt`, non-overwrite release creation. |
| Bengali phone installation guide (`INSTALL_GUIDE_BN.md`) | **PASS** | Created complete step-by-step phone-only download, install, setup, and troubleshooting guide without recommending disabling Play Protect. |
| **Git working tree, branch creation & remote push** | **BLOCKED** | `/app/applet` has **no `.git` repository**, **no `gh` CLI**, and **no GitHub authentication/PAT/SSH keys**. Upstream `AbuZar-Ansarii/Jarvis` already holds a Flutter codebase on `main`/`master` (`c1f5fb5`). Requires user-authorized push onto a dedicated branch (e.g. `release/android-native-v1.2.0`). |
| **Flutter SDK / Dart checks (`pubspec.yaml`, `lib/`)** | **BLOCKED** | Upstream repo is Flutter (`com.privateagent.private_agent`, `1.1.0+2`), whereas `/app/applet` is a native Kotlin/Compose codebase (`com.aistudio.jarvisagent.vpxkrm`). |
| **Signed Release APK build & `apksigner` verification** | **BLOCKED** | Release keystore (`my-upload-key.jks` / `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`) is not present in container. Never silently generated to preserve upgrade compatibility. |
| **GitHub Release live publication & URL verification** | **BLOCKED** | Blocked pending authenticated Git push + GitHub Actions secrets configuration by repository owner. |
| **Physical Android device / Streaming Emulator install & smoke test** | **NOT TESTED** | No ADB/emulator access in container (`BUILD VERIFIED` for debug only; `SIGNATURE VERIFIED`, `INSTALLATION VERIFIED`, `DEVICE FUNCTIONALITY VERIFIED` remain pending). |

---

## 2. Safe Keystore Setup & Backup Instructions (Phone/Web + Secure Workstation)

Because `app/build.gradle.kts` preserves AI Studio's `signingConfigs.release` (`keyAlias = "upload"`), **if you do not already have an upload keystore for `com.aistudio.jarvisagent.vpxkrm`**, generate one securely **once** and guard it carefully:

```bash
keytool -genkeypair -v \
  -keystore my-upload-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias upload
```

Encode it to Base64 without committing the `.jks` file:
```bash
base64 -w 0 my-upload-key.jks > keystore.b64.txt
```

1. Open **`https://github.com/AbuZar-Ansarii/Jarvis/settings/secrets/actions`** on your phone or browser.
2. Add these exact **Repository secrets**:
   - `ANDROID_KEYSTORE_BASE64`: contents of `keystore.b64.txt` (then delete `keystore.b64.txt`!)
   - `ANDROID_KEYSTORE_PASSWORD`: keystore store password
   - `ANDROID_KEY_ALIAS`: `upload`
   - `ANDROID_KEY_PASSWORD`: key password
3. **Backup Procedure:** Store `my-upload-key.jks` and its passwords in at least two offline encrypted locations (e.g., encrypted password manager + offline USB/vault). Losing this keystore permanently prevents seamless in-place APK upgrades on user phones.

---

## 3. Repeatable Phone-Only Push & Future Update Workflow

### Initial Safe Push (Preserving Existing Upstream History)
Because `AbuZar-Ansarii/Jarvis` already has Flutter history on `main`/`master`:
1. Use AI Studio's **Push to GitHub** menu (or an authenticated Git client) to push this native Android workspace to a **dedicated branch** (for example `release/android-native-v1.2.0`), rather than force-overwriting `main`/`master`.
2. Ensure `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` (`upload`), and `ANDROID_KEY_PASSWORD` are saved under **GitHub -> Settings -> Secrets and variables -> Actions**.
3. Open **GitHub -> Actions -> Android Release Build & Publish -> Run workflow**, set `publish_release = true` and `release_tag = v1.2.0` (or push tag `v1.2.0` from GitHub Web UI / Git client).

### Every Future Update (From Phone via GitHub Web UI)
1. In `app/build.gradle.kts`, edit only `defaultConfig`:
   - Increment `versionCode` by `+1` (e.g. `2` -> `3`).
   - Bump `versionName` semantically (e.g. `"1.2.0"` -> `"1.2.1"` or `"1.3.0"`).
2. Update `CHANGELOG.md` and commit directly to your release branch via GitHub's web editor (keeping `applicationId = "com.aistudio.jarvisagent.vpxkrm"` and the same signing secrets untouched so user Hermes memories and Room v2 data survive upgrades!).
3. Open **Actions -> Android Release Build & Publish -> Run workflow** (with `publish_release = true` and `release_tag = v1.2.1`), or create tag `v1.2.1` under **Releases -> Draft a new release**.
4. Wait for all checks (`testDebugUnitTest`, `lintDebug`, `assembleRelease`, `aapt2`, `apksigner`) to turn green. Users can then download `JARVIS-v1.2.1-arm64-v8a.apk` on their phones and install directly over `1.2.0` without uninstalling.
