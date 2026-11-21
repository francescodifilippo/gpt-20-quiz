# GPT 20 Quiz

**An experimental, multilingual Android Twenty Questions game built with Kotlin and Sign in with ChatGPT (SIWC).**

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](LICENSE)

> **Status: SIWC-enabled Android beta (0.2.2).** Browser-based SIWC authorization and live Responses API calls are implemented in Kotlin. CI compiles the app and runs unit tests, but SIWC sign-in has been reported working on a physical Android device, but model discovery and real inference still need on-device verification. Do not treat this as a production-ready release.

## Concept

Think of a person, animal, object, place, or concept. An AI game host tries to guess it by asking up to 20 questions. Respond with **Yes / No / Probably / Probably not / Don't know**. Each question should reduce uncertainty using the full answer history. The app uses the device's preferred language, supports language selection, and delegates game text to the model for languages without a bundled translation.

## SIWC live beta — entry point

The real application launches [RealQuizApp.kt](app/src/main/java/com/francescodifilippo/gpt20quiz/RealQuizApp.kt), not the older offline mock. It will not enable **Start game** until the user grants ChatGPT plan usage and selects a model from the account's own `/v1/models` catalog.

- [SiwcAuth.kt](app/src/main/java/com/francescodifilippo/gpt20quiz/SiwcAuth.kt) starts official OAuth Authorization Code + PKCE via the system browser and a device-local 127.0.0.1 callback, verifies OpenAI's RS256 OIDC signature and claims, stores tokens encrypted through Android Keystore, and refreshes/disconnects the session.
- [LiveGameService.kt](app/src/main/java/com/francescodifilippo/gpt20quiz/LiveGameService.kt) uses the user's authorized bearer token for `POST https://api.openai.com/v1/responses`, with `store:false`, `stream:true`, explicit game context and terminal event checks. Two distinct role prompts drive the game; no mock answers appear in the actual app entry point.
- The game keeps the **large 20-question countdown**, separate local scores by role and its language selector (Latin, Esperanto, Automatic/default, then 24 EU official languages).
- Build variant **0.2.0-siwc-beta** is a debug app; obtain its APK from the `gpt-20-quiz-siwc-beta-debug` artifact in the latest successful [GitHub Actions](../../actions) run.

**Callback improvement in 0.2.1:** Chrome Custom Tabs opens OAuth; a temporary Android foreground service keeps the one-use 127.0.0.1 listener alive. If Chrome reports `ERR_CONNECTION_TIMED_OUT`, the player can copy the complete callback URL, return to GPT 20 Quiz and paste it **only in the app's masked recovery field**. Kotlin validates matching host, port, path, state and one-use code. Never share a callback URL in a chat, issue or screenshot. See [device testing instructions](docs/SIWC_ANDROID_TEST.md).

**Not yet verified:** Android browser loopback callback/return, real SIWC consent and use of ChatGPT plan, actual inference on the selected account, refresh/revocation, and behavior across process death. The beta currently supports one active ChatGPT account. These require physical-device integration testing before any Google Play release. See [Android SIWC test plan](docs/SIWC_ANDROID_TEST.md).

**Important:** The former [v1 prompt](prompts/twenty_questions_v1.md) is design documentation. Role-specific live prompts are currently implemented in `LiveGameService.kt`; versioning and further validation remain open tasks.

## Beta 0.2.2 — model selector, language navigation, bounded messages

Following a successful Android SIWC sign-in test, beta 0.2.2 addresses user-reported UX issues:

- On sign-in or app restart, request the signed-in user's model catalog automatically.
- Show request progress and counts directly next to the model selector; display concise errors and a Retry/Refresh button on failure or empty results, without falling back to simulated answers.
- Keep the large countdown and per-role scoreboards from the original UI.
- The language screen has a back button at its top, Android system Back support, and automatically returns to setup when a language is selected.
- Do not render raw service response bodies or full prompt text as a bottom-of-screen message. Limit model display names to concise single-line labels.
- Explain why **Start new game** is disabled: sign-in, missing plan consent, loading, or model not chosen.

**Remaining:** verify the actual model catalog and start a real game on the Android phone. If the account exposes no eligible models, the app displays that status instead of inventing a model.

## Legacy UI demo (not launched)
The repository retains an earlier, no-longer-launched **Compose UI prototype** in [MainActivity.kt](app/src/main/java/com/francescodifilippo/gpt20quiz/MainActivity.kt):

- Dark neon theme with gradient hero and luminous accents.
- A very large **20 → 0** question countdown at the top of every game turn, plus a 20-segment progress indicator.
- Role choice **Guesser** (you ask) / **Responder** (you answer), with independent score totals and victory counters saved locally on device.
- **New Game** after each result, a clear earned-points result panel, and switch-role navigation.
- Italian/English static UI toggle (English fallback for other locales until translated). Model-generated arbitrary-language dialog is upcoming SIWC work.
- **Explicit demo mode**: scripted questions, deterministic mock replies, and self-reported guess success. The UI does not yet communicate with an AI.

**Game-language picker (implemented):** The Home screen includes a
searchable language selection screen with **Latin, Esperanto, Automatic**
as the first three entries, then the 24 official EU languages. **Automatic
(`auto`) is the default**, irrespective of its position in the list.
The selection persists in Android preferences and is resolved and frozen
when each match starts. The UI language (Italian/English) is independent.
The future SIWC prompt adapter receives the chosen effective BCP 47 tag,
language name, role and turn budget from `GameSessionState`. See
[language catalog](app/src/main/java/com/francescodifilippo/gpt20quiz/GameLanguage.kt),
[language selector UI](app/src/main/java/com/francescodifilippo/gpt20quiz/LanguagePicker.kt)
and [language rules tests](app/src/test/java/com/francescodifilippo/gpt20quiz/GameLanguageTest.kt).
**The old demo does not generate any text with AI**. The new SIWC beta will request the chosen language from the authenticated model.

**Demo scoring:** Guesser wins earn `100 + (unused turns × 10)`; Responder wins earn `100 + (survived turns × 10)`. Losses earn 0. Totals and wins are stored separately for each role using local `SharedPreferences`. Because demo mode has no real opponent, demo scores are not competitive or verified.

**Build:** Open the project in Android Studio with JDK 17, Gradle 9.6 / AGP 9.4 and Android SDK 37 installed, or run `./gradlew :app:testDebugUnitTest :app:assembleDebug`. A GitHub Actions workflow performs this build on commits to main; successful builds upload a debug APK artifact. This is an unsigned-for-distribution **debug** build, not a Play Store release.

See the [UI design specification](docs/UI_DESIGN.md) and [Android CI workflow](.github/workflows/android-ci.yml).

## Technology

- Android, **Kotlin**, **Jetpack Compose**, MVVM / unidirectional state.
- Deterministic Kotlin rules engine: turn counting, answer validation, game completion and restart.
- A versioned AI gameplay prompt in [prompts/twenty_questions_v1.md](prompts/twenty_questions_v1.md).
- [Sign in with ChatGPT](https://developers.openai.com/siwc/token-sharing-open-source) (SIWC), with user-granted plan usage where eligible.
- Direct requests from the signed-in device to the [Responses API](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference). No developer-owned inference proxy or shared subscription.

## Proposed gameplay

1. Player connects an eligible ChatGPT account and grants the necessary plan usage permission.
2. Player chooses a language (default: device language) and thinks of a secret.
3. AI asks one question at a time. The player taps one of five fixed answers.
4. The app records each question-answer pair and counts turns; the AI receives the exact state on every call.
5. The AI makes its final guess by the twentieth turn. The player confirms whether it was right.
6. Show the result and offer a new game.

The final guess is part of the 20-turn limit. This choice can be revisited before the first gameplay implementation.

## Security and privacy

- No API keys, refresh tokens, access tokens or user secrets in source control.
- Use OAuth Authorization Code with PKCE and OIDC; validate state, nonce, issuer, audience, expiry and signature of ID token.
- Store tokens only on the user's device using platform-protected storage, never in a remote managed service.
- Users may disconnect. Identity-only authorization does **not** imply permission to invoke a model.
- When permitted, send inference to the public Responses endpoint with `store: false`, `stream: true` and the necessary game history.
- A mobile compatibility proof of concept must validate the documented loopback OAuth redirect on Android.

See [architecture](docs/ARCHITECTURE.md), [SIWC integration notes](docs/SIWC.md), and [Play Store release preparation](docs/PLAY_RELEASE.md).

## Status and roadmap

1. Verify SIWC OAuth loopback callback on a physical Android device.
2. Build Kotlin/Compose app scaffold, gameplay state machine and unit tests.
3. Add SIWC adapter, secure local token store, account connection UI and model discovery.
4. Implement Responses streaming, strict output parsing and game recovery.
5. Add localization, accessibility, consent/privacy controls, UI tests and Android App Bundle.
6. Test on internal Play track; release only after review.

## Licensing and independence

Copyright © 2026 contributors. Licensed under [Apache License, Version 2.0](LICENSE).

This is an independent experimental open-source project. It is not affiliated with, endorsed by or operated by OpenAI. ChatGPT and related marks belong to their respective owners. ChatGPT plan usage is subject to OpenAI terms, eligibility and account limits.

## Current documentation

- [Original prompt](prompts/twenty_questions_v1.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Sign in with ChatGPT](docs/SIWC.md)
- [Google Play release checklist](docs/PLAY_RELEASE.md)

Contributions and discussions are welcome.
