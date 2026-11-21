# GPT 20 Quiz — compact gameplay prompt v3

**Status:** active Android SIWC prompt, implemented in
[`TwentyQuestionsStrategy.kt`](../app/src/main/java/com/francescodifilippo/gpt20quiz/TwentyQuestionsStrategy.kt).
Version `3.0-information-gain`.

## Design

The normal Twenty Questions model plays an information-gathering game, not
a long interview about fine details.

The *AI as guesser* instructions now establish only:

1. Ask **one discriminating yes/no question** which divides the remaining
   plausible possibilities as well as possible (information gain).
2. Reassess the most plausible identity after every answer. **Guess early**
   when an ordinary object identity is sufficiently supported.
3. Treat uncertain answers cautiously; avoid redundant or compound questions.
4. For ordinary products, the recognizable type is enough; brands and
   construction variants are not required. Names of people/places/characters
   require the specific recognizable entity.
5. Use at most 20 turns and **make a final guess on the 20th**.
6. Return a small structured JSON object, with `kind` = `question` or
   `guess`, and a short `text` field.

Other roles (AI holds a secret, answers human questions and verifies the
human's final answer) use distinct, shorter prompts in the same Kotlin file.

No specific examples of possible target objects are inserted into the live
prompt, to avoid steering the model toward them. The Stokke-chair incident
is recorded as a regression scenario in the older v2 documentation and
tests, **not** as a hint in new games.

## Early guesses and incorrect guesses (revision 0.2.10)

A proposed answer is a **guess**, even if written as a yes/no question.
The responder confirms it with **Sì/No**. The guess counts as one
of the 20 allowed actions:

- **Sì / Yes:** identity confirmed, the match ends immediately.
- **No before turn 20:** record the rejected identity as
  `{"kind":"guess","question":"...","answer":"no"}` in the next
  game-history input; decrement the counter by **one** and continue.
  The AI must not repeat that excluded identity.
- **No on turn 20:** the turn limit has been exhausted, the guesser loses.
- The exact same rules apply when the human is the guesser.

This is the selected classical house rule for GPT 20 Quiz, not a
universal requirement for every Twenty Questions variant. Some
implementations allow one definitive guess only; the default here
supports repeated early guesses within the shared 20-turn budget.

**Implementation:** `GameRules.resolveGuess`, `RealQuizApp` and
`LiveGameService.GameExchange(kind="guess")`.
Regression tests cover correct early guesses, wrong early guesses,
wrong 20th guesses, and the full 20-turn budget.

## Language as a single directive

The app resolves the language preference once per match. For `auto`,
it uses the device's full BCP-47 language tag. Then it appends **one
sentence** to every role-specific instruction:

> Use Italian (it-IT) for all player-facing game text; keep JSON field names and answer codes unchanged.

Replace `Italian (it-IT)` with the selected resolved language, e.g.
`Latin (la)` or `Esperanto (eo)`. The role, turn count and history stay
in the structured game input and engine rather than being repeated in
a long language-policy block.

## Separation of responsibilities

- **Kotlin:** SIWC auth, selected model, reasoning.effort, effective
  language, immutable session settings, scoring, turn budget and
  response validation.
- **Model prompt:** only game decision policy and concise output contract.
- **Tests:** language resolution and single-sentence policy, short neutral
  prompt, final guess semantics and type-level correctness.
- **Future:** empirical A/B tests of v2 versus v3 using real game transcripts
  and win rate/median turns; a short prompt is not automatically a better one.

## Non-proprietary references

- [A Mirror to Human Question Asking: Analyzing the Akinator Online Question Game](https://www.mdpi.com/2504-2289/7/1/26)
- [Twenty Questions — a Bayesian reasoner](https://thoregraepel.github.io/twenty-questions/)
- [ChatGPT's Information Seeking Strategy: Insights from the 20-Questions Game](https://aclanthology.org/2023.inlg-main.11/)

These sources provide information-gain and early-identification concepts,
not a proprietary Akinator prompt or an exact reconstruction of its engine.
