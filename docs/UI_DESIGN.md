# UI design system — GPT 20 Quiz

## Direction
Native Android / Jetpack Compose, dark immersive game experience with high contrast and luminous violet/cyan accents. Large digits, subtle gradients, generous whitespace, clear large touch targets and accessible labels.

## Home
- Brand and experimental demo label at top.
- Large "20" hero card, one-line proposition and "Start game" CTA.
- Select one of two roles:
  - **Guesser** — You ask questions. Your own points and wins.
  - **Responder** — You keep the secret and answer. A separate points/wins bank.
- Pinned top-level scores visible on both role cards; distinct persisted SharedPreferences keys.
- Start/new game always restarts from 20, preserving career scores.

## In-game
- Remaining question count is always at the top, displayed at 80+ sp.
- Twenty-segment progress bar; count decreases on accepted turns, not on rendering.
- Question number and current role are unmistakable.
- Responder: five answer chips; scripted demo questions; final guess at turn 20.
- Guesser: text field for next question; demo yes/no reply; final attempt or give up.
- No fake claim of OpenAI connectivity: DEMO banner remains visible.

## Result
- Win or loss, earned points, role-specific accumulated points, restart or change role.
- Scoring policy: Guesser earns 100 + 10 x unused turns after its winning guess. Responder earns 100 + 10 x turns survived only when AI fails.
- Local demonstration scores are resettable by clearing app storage. A proper settings reset will be added before production.
- These prototype scores are **not competition-verified**, because no real AI game is connected.

## Accessibility
- Large minimum controls (~48dp), contrast on dark background, descriptive text for numeric state.
- Vertical scroll on small displays; no text-only conveyance of active role.
- Italian and English initial static UI; fallback English. All-language dynamic model text is future SIWC work.
- Font scaling and RTL QA required for production.

## Implementation stage
The project is a **native UI prototype**, not a SIWC-complete game. The local demo deliberately uses scripted/mock responses and lets the user self-confirm a guessing result. Neither offline mock responses nor scores are represented as an intelligent opponent.
