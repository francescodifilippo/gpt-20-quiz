# GPT 20 Quiz

**An experimental, multilingual Android Twenty Questions game built with Kotlin and Sign in with ChatGPT (SIWC).**

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](LICENSE)

> **Status: native Android UI demo in source control.** Kotlin/Jetpack Compose screens, independent role scores and scripted offline gameplay are implemented. CI is configured to build an APK; CI results have not yet been verified. SIWC authentication and AI inference are not implemented. No real-AI gameplay claims are made.

## Concept

Think of a person, animal, object, place, or concept. An AI game host tries to guess it by asking up to 20 questions. Respond with **Yes / No / Probably / Probably not / Don't know**. Each question should reduce uncertainty using the full answer history. The app uses the device's preferred language, supports language selection, and delegates game text to the model for languages without a bundled translation.

## Android UI demo (November 2026)

The repository includes an interactive **Compose UI prototype** in [MainActivity.kt](app/src/main/java/com/francescodifilippo/gpt20quiz/MainActivity.kt):

- Dark neon theme with gradient hero and luminous accents.
- A very large **20 → 0** question countdown at the top of every game turn, plus a 20-segment progress indicator.
- Role choice **Guesser** (you ask) / **Responder** (you answer), with independent score totals and victory counters saved locally on device.
- **New Game** after each result, a clear earned-points result panel, and switch-role navigation.
- Italian/English static UI toggle (English fallback for other locales until translated). Model-generated arbitrary-language dialog is upcoming SIWC work.
- **Explicit demo mode**: scripted questions, deterministic mock replies, and self-reported guess success. The UI does not yet communicate with an AI.

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
