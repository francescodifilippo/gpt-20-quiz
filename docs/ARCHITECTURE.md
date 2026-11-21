# Architecture (draft)

## Target

A Kotlin Android application with a Jetpack Compose UI and a fully local gameplay state machine. Model inference uses OpenAI only with the logged-in player's explicit SIWC permission and under the terms of their eligible ChatGPT plan.

```text
Compose UI (language, play, result, settings)
    |
ViewModel / immutable GameState
    |             |
GameEngine     AiGameHost interface
(turn budget,     |-- FakeGameHost (unit/instrumented tests)
valid answers,    `-- ResponsesGameHost (SIWC OAuth token)
finish)                 |
                         |-- SiwcSessionManager (sign-in, refresh, sign-out)
                         |-- ModelCatalog
                         `-- POST https://api.openai.com/v1/responses
```

## Contract

**GameState:** `languageTag`, `status`, `nextTurn`, `maxTurns=20`, `history`, `pendingQuestion`, `lastGuess`, and `promptVersion`.

**Answer:** one of `YES`, `NO`, `PROBABLY`, `PROBABLY_NOT`, `UNKNOWN`. Labels are localized in the UI. State always stores canonical codes.

**AiGameHost.next(state):** returns `Question(text)` or `Guess(text)`. The adapter creates a per-request context from the immutable state; no assumption of server conversation persistence.

**GameEngine:**
- Accepts only one answer to the current, pending question.
- Rejects extra answers, duplicate submissions, missing active questions and out-of-order responses.
- The twentieth turn must end with a guess. A model response violating this rule is rejected before display and handled by bounded recovery.
- Accepts player confirmation after a guess, records win/loss and terminates.
- Retains versioned state in local storage; an in-progress game can be resumed.

## Prompting

Versioned prompt: [twenty_questions_v1.md](../prompts/twenty_questions_v1.md). The model handles strategy, linguistics and phrasing; **Kotlin** handles protocol and invariants. The prompt is not a secret or a security boundary.

## Language

Use the Android locale as initial BCP 47 language tag. Player may override it. Bundle reliable UI translations for selected locales; use English fallback for unsupported static UI strings until translations are available. AI-generated question text follows the player's language. Avoid claiming every possible language is guaranteed by every model.

### Game language selection (implemented in UI demo)

The independent **game language** picker lists Latin and Esperanto first,
then Automatic (Android device language), then the 24 official EU languages.
`auto` is the initial preference even though it is displayed third.
The catalog is in `GameLanguage.kt` and the searchable Compose screen is
in `LanguagePicker.kt`.

- Store `game_language_code` in local `SharedPreferences`, default `auto`.
- On `startGame`, resolve `auto` to the Android device's **full** BCP 47
  locale, or use the chosen language code.
- Freeze `activeGameLanguageCode` and `activeGameLanguageTag` for the
  current match. The player can change the preference on the Home screen
  before the next game, but the active session remains unchanged.
- `GameSessionState` stores `GameLanguageSelection`, role and turn
  counters. `GameLanguagePrompt.initialInstructions(state)` generates
  per-turn model instructions with the actual language, role and counters.
- UI chrome stays Italian/English for now; the AI will generate game text
  in the chosen language once SIWC integration is enabled.
- Language names and translation quality require validation on all
  supported locales; Latin and Esperanto are experimental too.

## Security

- Android app-scoped secure storage of OAuth token set; no shared server-side token store.
- OAuth Authorization Code + PKCE + OIDC validation (including state and nonce).
- SIWC dynamic client registration and Android local-loopback callback require an early physical-device proof of concept.
- Network calls directly from the user's device; no external inference gateway, shared API key or pooling of subscription access.
- State history and user answers are transmitted only when the user plays and grants permission.
- No personal-data analytics SDK in MVP.

## Tests

Pure Kotlin unit tests for 20-turn cap, illegal answers, duplicate taps, answer normalization, final guess, locale, save/resume and model-output validation. Android integration tests for OAuth callback, token storage, token refresh, stream interruption and cancellation. UI tests for accessibility and long/RTL language strings.

## Outstanding decisions

- Android SIWC OAuth redirect viability must be verified; do not assume ordinary deep-link callbacks are supported by the documented OSS flow.
- Confirm exact UI wording and branding against SIWC guidelines.
- Choose the minimum Android SDK and model strategy only after a compatibility spike.
- Confirm whether the game can support an alternative inference method when SIWC plan usage is unavailable (without weakening the no-charge SIWC path).
