# GPT 20 Quiz — Twenty Questions strategy v2

Status: implemented in the live Android gameplay policy, source file
app/src/main/java/com/francescodifilippo/gpt20quiz/TwentyQuestionsStrategy.kt.
Version: **2.0-category-first**.

This is a gameplay strategy document, not an OAuth configuration file.
The Android implementation combines the strategy with the frozen game
language and turn budget in the SIWC Responses request.

## Objective

Identify the ordinary identity of the hidden thing as soon as reasonably
possible. Twenty turns are a **maximum**. An early, correct final guess is
better than exploring details until the maximum turn count.

## Default winning specificity

The correct answer is an **everyday object identity**, not necessarily the
brand, manufacturer, version, construction method, color or product model.
A player may think of something more specific, but should be able to
confirm its ordinary category as correct.

Example: the player thinks of a **Stokke chair**.

- "È una sedia?" is a legitimate early **final guess**. It may win, even
  without naming Stokke or determining what type of chair it is.
- "È un mobile?" is too generic to establish the ordinary identity.
- "È una sedia con base centrale o a piedistallo?" explores a construction
  detail after the ordinary identity may already have been inferred.
  Unless it differentiates two plausible everyday objects, this should
  not consume another turn.

A future optional "exact brand and model" mode may set stricter rules,
but that is **not** the default game.

## Progressive questions

1. Separate broad possibilities using one discriminative yes/no question.
2. Narrow to recognizable everyday identities, not product submodels.
3. Make an early final guess as soon as the evidence supports one.
4. If uncertain, ask one question distinguishing plausible categories.
5. Never ask redundant or double-barreled questions.
6. Treat probably / probably_not / unknown as uncertain evidence.
7. Question number 20 is always a final guess; no exploratory question
   may consume that turn.

For the model output, an identity proposal such as "È una sedia?" must
have action kind **guess**, not **question**, even though its grammar is
interrogative. It ends the game when the human confirms whether it is right.

## Human as guesser

When the AI holds the secret, it answers questions consistently. The
guess-verification step accepts an ordinary category such as "chair"
for "Stokke chair", but rejects too-general descriptions like
"furniture" for a chair.

## Reasoning effort is independent of model

The UI offers one persisted preference applicable to **every** model:
auto, none, minimal, low, medium, high, xhigh, max.

Automatic omits the API reasoning.effort parameter. All other choices
are passed to the API unchanged. Because individual models can reject
particular levels, a rejection produces a visible error; the app never
silently downgrades or changes the requested effort. The selected effort
is frozen for each match and is separate from the model selector.

Effort is an **API request parameter**, not a behavioral hint embedded in
the game strategy prompt.

## Safety and validation

The local engine owns turn count, scoring, player role and the final-turn
constraint. The response from the model must match the expected JSON
action schema. The full history is supplied as explicit game data with
each request. Do not display tokens, private reasoning or unrelated
agent metadata.

Unit tests cover the Stokke/chair scenario, early-guess policy and
model-independent effort choices. The gameplay quality still needs
testing on the user's actual signed-in SIWC model.
