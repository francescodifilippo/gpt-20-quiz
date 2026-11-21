# GPT 20 Quiz — SIWC Android on-device acceptance plan

**Status:** beta implementation only; real SIWC authorization/inference must be tested by a human on an Android device. A successful build does not verify OAuth.

## Prerequisites

- Use the newest CI artifact **gpt-20-quiz-siwc-beta-debug**; do not reuse the older **gpt-20-quiz-demo-debug** APK.
- Android with an available web browser and connectivity. Install the beta APK from its GitHub Actions ZIP.
- A ChatGPT account that is eligible for SIWC plan sharing. Approval is controlled in OpenAI's OAuth browser UI; it cannot be preauthorized by this repository.
- This beta currently supports one connected account/registration. No developer API key is required.

## First-time sign-in

1. Launch the app. Verify the login screen shows **Continue with ChatGPT** and that **Start game** is disabled.
2. Tap Continue with ChatGPT. The app starts a local HTTP listener on 127.0.0.1 with an unpredictable available port and opens the system browser at the official OpenAI authorization endpoint.
3. Confirm the browser requests permission for **GPT 20 Quiz**. If consent is granted, the browser should show a local page saying authorization was received. **Return manually to the app**; automatic app switching is not yet implemented.
4. Confirm the app says the account is connected and the ChatGPT plan is authorized. If access was denied or the plan is ineligible, the app must not enable inference.
5. Tap **Load available models**; select a listed model. The app must not invent a model or silently substitute a developer API key.
6. Select a role and a game language (default is Automatic; Latin and Esperanto are first in the list). Start a match.
7. Observe network-backed questions/responses. In the responder role, the AI asks about the user's secret; in the guesser role, the AI picks a secret and truthfully answers user questions.
8. Continue a game up to 20 turns; verify that the last AI turn is a guess (when AI is guessing), wins and losses are counted by the app, and scores remain independent by role.

## Failure paths

- Cancel browser authorization; verify that no plan usage is enabled.
- Sign in without granting chatgpt.tokens.use.direct; verify that the app refuses inference and offers consent again.
- Revoke permission in ChatGPT settings; verify subsequent model requests do not return simulated answers.
- Disconnect inside the app; verify tokens are cleared and the app cannot make further AI requests.
- Disconnect the phone from the network during a streamed reply; verify the app displays a failure and does not award points.
- Test a usage-limit response and an ineligible account; verify no automatic billing substitution.
- Change phone language midgame; verify that the active match retains its selected effective language.
- Repeat tests in Latin and Esperanto; record language consistency, question quality, and whether the model obeys the final-turn rule.
- Background and foreground the app while the browser is signing in. Check callback reliability on Android 10, 13, 16 and any other available version.

## Security review before distribution

- Verify OAuth state, nonce, ID-token signature, issuer, audience and expiration; do not accept unverified ID tokens.
- Inspect app logs to ensure no access/refresh tokens, authorization codes or raw callback URLs are logged.
- Confirm encrypted local credentials use Android Keystore and Android backup is disabled.
- Verify refresh-token rotation, denied refresh, remote revocation and account identity consistency.
- Review current OpenAI SIWC terms, UX guidelines, eligibility, and Google Play requirements.
- Implement robust account switching, process-death recovery, accessibility testing, and an appropriate privacy disclosure before Play Store publication.

## Documentation

- Open-source SIWC registration: https://developers.openai.com/siwc/token-sharing-open-source/sign-in
- Model discovery and streamed inference: https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference
- Current preview limitations: https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations
- SIWC terms: https://openai.com/policies/sign-in-with-chatgpt-terms/

**Do not publish a claim of tested SIWC interoperability until the device evidence is recorded.**
