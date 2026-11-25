# GPT 20 Quiz

**An experimental, multilingual Android Twenty Questions game built with Kotlin and Sign in with ChatGPT (SIWC).**

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](LICENSE)

> **Status: SIWC-enabled Android beta (0.2.10).** Android browser sign-in, account-scoped model selection and real gameplay have been observed on a physical device. Kotlin compilation and JVM tests run in GitHub Actions. Game quality, model compatibility and broader Android coverage remain experimental; this is not a production release.


## Concept

Think of a person, animal, object, place, or concept. An AI game host tries to guess it by asking up to 20 questions. Respond with **Yes / No / Probably / Probably not / Don't know**. Each question should reduce uncertainty using the full answer history. The app uses the device's preferred language, supports language selection, and delegates game text to the model for languages without a bundled translation.

## Installations and in-place updates

**The APK's filename does not control Android upgrades.** Android verifies the package ID, signing certificate, and an increasing versionCode. Earlier CI runs generated a different debug signing key on each temporary runner, which made consecutive builds incompatible.

The repository now supports **persistent, private beta signing** with GitHub Actions Secrets, and a fixed published filename **gpt-20-quiz.apk**. New APK artifacts are published **only when the stable signing key has been configured**. CI continues to run tests without secrets but will not publish a misleading non-upgradeable APK.

To configure signing once from Linux/macOS/WSL, with JDK, openssl and authenticated GitHub CLI:

~~~bash
bash scripts/configure-beta-signing.sh
~~~

The script creates and securely stores a local signing key/password and uploads **four protected repository secrets** using your GitHub credentials. Back up the private signing files outside the repository. See the full guide: [Android stable signing and upgrades](docs/ANDROID_UPDATES_SIGNING.md).

**One final uninstall may be required** when replacing APKs 0.2.0–0.2.2, which were signed with temporary CI certificates. Once the persistently signed beta is installed, future versions with the same signing key can update in place. CI versionCode is derived from the monotonically increasing GitHub Actions run number.

## SIWC live beta — entry point

The real application launches [RealQuizApp.kt](app/src/main/java/com/francescodifilippo/gpt20quiz/RealQuizApp.kt), not the older offline mock. It will not enable **Start game** until the user grants ChatGPT plan usage and selects a model from the account's own `/v1/models` catalog.

- [SiwcAuth.kt](app/src/main/java/com/francescodifilippo/gpt20quiz/SiwcAuth.kt) starts official OAuth Authorization Code + PKCE via the system browser and a device-local 127.0.0.1 callback, verifies OpenAI's RS256 OIDC signature and claims, stores tokens encrypted through Android Keystore, and refreshes/disconnects the session.
- [LiveGameService.kt](app/src/main/java/com/francescodifilippo/gpt20quiz/LiveGameService.kt) uses the user's authorized bearer token for `POST https://api.openai.com/v1/responses`, with `store:false`, `stream:true`, explicit game context and terminal event checks. Two distinct role prompts drive the game; no mock answers appear in the actual app entry point.
- The game keeps the **large 20-question countdown**, separate local scores by role and its language selector (Latin, Esperanto, Automatic/default, then 24 EU official languages).
- Build variant **0.2.3-stable-signing** is a debug app; after the signing secrets are configured, obtain `gpt-20-quiz.apk` from the `gpt-20-quiz-signed-beta` artifact in [GitHub Actions](../../actions).

**Callback improvement in 0.2.1:** Chrome Custom Tabs opens OAuth; a temporary Android foreground service keeps the one-use 127.0.0.1 listener alive. If Chrome reports `ERR_CONNECTION_TIMED_OUT`, the player can copy the complete callback URL, return to GPT 20 Quiz and paste it **only in the app's masked recovery field**. Kotlin validates matching host, port, path, state and one-use code. Never share a callback URL in a chat, issue or screenshot. See [device testing instructions](docs/SIWC_ANDROID_TEST.md).

**Not yet verified:** Android browser loopback callback/return, real SIWC consent and use of ChatGPT plan, actual inference on the selected account, refresh/revocation, and behavior across process death. The beta currently supports one active ChatGPT account. These require physical-device integration testing before any Google Play release. See [Android SIWC test plan](docs/SIWC_ANDROID_TEST.md).

**Important:** The former [v1 prompt](prompts/twenty_questions_v1.md) is design documentation. Role-specific live prompts are currently implemented in `LiveGameService.kt`; versioning and further validation remain open tasks.

## Beta 0.2.10 — incorrect guesses continue the round

The Android beta previously ended the match on **every** AI final-guess
attempt: if the human pressed "No, AI was wrong" after an early guess,
the game incorrectly stopped and awarded the responder the win, even
when questions remained. The same premature termination happened when
a human guesser made an incorrect guess.

**New house rule:** A proposed identity may be guessed at any time and
always consumes **one of 20 actions**. A correct guess ends the round.
An incorrect guess before the twentieth action is recorded as a
rejected guess (kind="guess", answer="no") and the game continues
with the updated history and remaining turn counter. An incorrect
twentieth guess ends the round in favor of the answerer.

The UI asks simply **Sì / No** on AI identity guesses, with an explicit
explanation that "No" spends one turn and continues when possible.
The same rule applies when the player is the guesser. The countdown
and independent role scores remain enforced by the Android engine.

- [GameRules.kt](app/src/main/java/com/francescodifilippo/gpt20quiz/GameRules.kt)
- [LiveGameService.kt](app/src/main/java/com/francescodifilippo/gpt20quiz/LiveGameService.kt)
- [Prompt v3 + game rule amendment](prompts/twenty_questions_v3.md)

Regression tests cover early wrong and correct guesses, the final
incorrect guess, the full 20-turn maximum and the history instruction.
The gameplay change must still be validated by a real match on Android.

## Beta 0.2.9 — recover from unsupported reasoning effort

A real device test selected **gpt-6.2-sol** with `reasoning.effort=minimal`
and received **HTTP 400 `unsupported_value`** before the first question.
OpenAI's GPT-6.2 migration guide recommends **low** rather than
`none`/`minimal`. This is a model request-parameter incompatibility,
not an OAuth login error and not a failure of the Twenty Questions prompt.

The beta now:
- Parses only short safe `error.code` and `error.param` fields from the
  HTTP error body. Raw provider messages, tokens and request payloads remain
  excluded from the UI.
- Distinguishes a confirmed `reasoning.effort` rejection from a probable
  effort rejection when the provider omits the rejected parameter.
- On an effort-related HTTP 400 **inside a match**, displays the same
  independent effort selector beside the model and permits the player to
  change their effort **explicitly**, then retry the current action
  without consuming a question.
- Preserves the selected value across model changes and never silently
  downgrades it. For GPT-6.2, suggests **low** or **auto** where appropriate.
- Includes JVM tests for named parameters, missing parameters, unrelated
  failures and safe redaction.

The model picker and effort selector remain **independent**, as requested;
individual model capability restrictions are explained by the API response,
rather than hiding effort options.

Sources:
- [GPT-6.2 migration guide](https://developers.openai.com/api/docs/guides/latest-model)
- [Reasoning guide](https://developers.openai.com/api/docs/guides/reasoning)

## Beta 0.2.8 — shorter neutral prompt and one-sentence language rule

The v2 live prompt was unnecessarily verbose and mentioned a specific
**Stokke chair**. That example was useful for reviewing a failed game,
but it could bias subsequent questions toward chairs. The new
[compact v3 prompt](prompts/twenty_questions_v3.md) removes target-specific
examples and gives the model only: maximize question information gain,
avoid repetition, guess early when a common object identity is likely,
and make the final guess by turn 20. Role-specific JSON contracts remain
explicit.

The game-language instruction is now **one sentence**, for example:
"Use Italian (it-IT) for all player-facing game text; keep JSON field
names and answer codes unchanged." Automatic language selection is
resolved on Android before the sentence is generated. Role, remaining
turns and answer history remain structured game state rather than
redundant language prompt paragraphs.

This is an implementation change and a hypothesis about better gameplay,
not a claim of measured higher win rates. Follow-up should compare
successful guesses and turns consumed across representative games.

## Beta 0.2.7 — model-independent effort and early object-category guessing

A real gameplay test with the chosen SIWC model, GPT-5.6 Luna, showed
that the AI attempted to distinguish construction details of a chair
instead of making the more useful early guess "Is it a chair?".
The user's actual target was a **Stokke chair**.

The game now follows a versioned **category-first** Twenty Questions
strategy. A normal everyday object identity is an acceptable final
guess, even when the secret is a branded version of it. "Chair" is
an acceptable answer for a Stokke chair; "furniture" is too generic.
The AI must identify the category as soon as the history reasonably
supports it, and use a FINAL GUESS action rather than asking about
pedestals, product models or other secondary details.
The 20th turn remains a mandatory final guess, and guessing earlier
is encouraged.

The player may select **reasoning effort independently from the model**:
Automatic, None, Minimal, Low, Medium, High, Extra high, Maximum.
The preference is persisted and kept when the model selection changes.
The value is frozen for the current match and sent in every Responses
request as the actual reasoning.effort parameter, except for Automatic
which omits the API field. Models may have different supported values;
unsupported settings produce an explicit server error without the app
silently downgrading effort.

- Gameplay rules: [TwentyQuestionsStrategy.kt](app/src/main/java/com/francescodifilippo/gpt20quiz/TwentyQuestionsStrategy.kt)
- Versioned strategy: [prompts/twenty_questions_v2.md](prompts/twenty_questions_v2.md)
- Effort policy: [ReasoningEffort.kt](app/src/main/java/com/francescodifilippo/gpt20quiz/ReasoningEffort.kt)
- UI selector: [ReasoningEffortSelector.kt](app/src/main/java/com/francescodifilippo/gpt20quiz/ReasoningEffortSelector.kt)

The strategy is tested for consistency in JVM unit tests; **the actual
deduction quality of a model can only be established by playing new games**.

## Beta 0.2.6 — streaming model catalog (512K response truncation fix)

Two real Android screenshots showed an HTTP 200 response identified as JSON,
but model discovery failed. An older UI version also rendered an extremely
long response body containing unrelated agent metadata. A code review found a
specific client defect: **GET /v1/models was read into a 512K-character buffer
and discarded its remaining bytes**, making a valid larger JSON document
unparseable. The diagnostic line on the phone also omitted response length
because the recorded count exceeded that earlier reporting threshold.

The 0.2.6 client instead uses a bounded, **incremental JSON reader** (Gson
`JsonReader`) against the HTTP stream. It only retains `models[]` entries
with `visibility == "list"`, `slug` and `display_name`; unknown fields
(including potentially long metadata) are skipped without displaying,
logging, persisting or processing them as instructions. HTTP and token
handling remain unchanged.

- A synthetic **>512K** response with lengthy unrelated JSON fields and a
  valid `models` array now passes its JVM regression test.
- Truncated JSON and responses without a `models` array are still rejected.
- A hard safety bound of 16 million decoded characters remains, with a
  distinct `MODELS_TOO_LARGE` diagnostic and no raw response content.
- No model names or available models are invented. Only actual
  account-visible models returned by SIWC may enable Start Game.

The prior screens are evidence of failure at catalog parsing, **not proof that
SIWC model discovery is now working on the device**. Re-test version 0.2.6:
press **Aggiorna modelli** and verify the account-visible list appears, then
test a real inference request. Never upload raw model catalog bodies or
OAuth tokens as diagnostics.

## Beta 0.2.5 — model-response format inspection

The reported device error is **MODELS_BAD_RESPONSE** after SIWC sign-in.
This means the app saw an HTTP 2xx response but could not parse a JSON
object from its body. Official SIWC documentation and OpenAI's reference
client both expect an object with a `models` array, so the shape was
not replaced with speculative alternatives.

Beta 0.2.5 removes a possible leading UTF-8 BOM, requests
`Accept-Encoding: identity` to simplify Android HTTP body handling,
and limits the size of downloaded response text. If the body is still
invalid, the app displays only safe metadata:

- HTTP status (e.g. 200)
- Allowlisted Content-Type category (json, html, text, event-stream, ...)
- Safe body format category (empty, html, json-array, json-object, ...)
- Response length and sanitized request ID when available.

No raw body, OAuth token, authorization code or prompt is collected or
displayed. A successful CI build does not identify the actual
response format sent to this specific phone: the next device test must
read the new safe diagnostic line.

## Beta 0.2.4 — traceable SIWC model-catalog failures

On the Android device, SIWC sign-in completed, but the account model picker
reported only a generic failure. The local screenshot cannot identify whether
this is a token, network, HTTP, or response-format error.

The Android app now classifies every **GET /v1/models** failure by stage:

- **MODELS_TOKEN:** OAuth access token unavailable or refresh failed.
- **MODELS_NETWORK:** connection, timeout, DNS, or TLS failure to api.openai.com.
- **MODELS_HTTP_NNN:** concrete HTTP status (401, 403, 429, 5xx, etc.) with
  an optional, strictly validated short provider error code.
- **MODELS_BAD_RESPONSE:** HTTP success with invalid JSON.
- **MODELS_BAD_CATALOG:** valid JSON but no SIWC `models` array.
- **MODELS_UNEXPECTED:** unexpected error, with no raw exception text shown.

The stage is shown in the **Modelli disponibili** panel, next to **Aggiorna modelli**.
No access token, raw provider body, authorization code, or complete prompt
is shown, logged, or persisted. The pure diagnostics layer is covered by
dedicated tests. The provider's live result on the device remains to be
measured; compilation alone cannot identify the cause.

Official contract: [SIWC models and inference](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference).
The app continues to require a real account-scoped visible model, rather
than pretending that an inferred/hard-coded model is available.

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
