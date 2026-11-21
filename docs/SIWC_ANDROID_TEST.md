# GPT 20 Quiz — SIWC Android on-device acceptance plan

**Status:** beta implementation only; real SIWC authorization/inference must be tested by a human on an Android device. A successful build does not verify OAuth.

## Prerequisites

- Use the newest CI artifact **gpt-20-quiz-siwc-beta-debug**; do not reuse the older **gpt-20-quiz-demo-debug** APK.
- Android with an available web browser and connectivity. Install the beta APK from its GitHub Actions ZIP.
- A ChatGPT account that is eligible for SIWC plan sharing. Approval is controlled in OpenAI's OAuth browser UI; it cannot be preauthorized by this repository.
- This beta currently supports one connected account/registration. No developer API key is required.

## Loopback timeout on Android — recovery in 0.2.1

Some Android browsers cannot reach the local callback after ChatGPT consent (ERR_CONNECTION_TIMED_OUT at `127.0.0.1:<port>/auth/callback`). The app now starts OAuth in **Chrome Custom Tabs**, keeps its local listener alive with a time-limited **foreground short service**, and provides a manual recovery UI.

1. Tap **Continue with ChatGPT**. You should see the browser login tab and an Android foreground-service notification.
2. Complete ChatGPT authorization. If the callback page succeeds, return to GPT 20 Quiz to continue.
3. If Chrome instead reports a timeout for **127.0.0.1**, copy the **complete URL** from Chrome's address bar. On your own phone, return to the GPT 20 Quiz app.
4. Inside the login recovery panel labeled **CHROME NON RAGGIUNGE 127.0.0.1?**, paste the URL into the masked field, then tap **COMPLETA ACCESSO CON URL**.
5. The app validates exact loopback host, port, callback path and one-time OAuth `state`, then forwards the request to its own listener. The code is consumed locally. **Never paste a callback URL or authorization code into ChatGPT, GitHub issues, email, screenshots, analytics, or logs.**
6. This fallback is available only while sign-in is active and ends after the local OAuth listener times out; start a fresh login if expired.

Important: Custom Tabs alone does not alter loopback routing. The Android foreground service mitigates background-process termination, and the manual option handles failures due to browser/proxy routing. We still need an actual on-device successful sign-in to confirm compatibility.

## First-time sign-in

1. Launch the app. Verify the login screen shows **Continue with ChatGPT** and that **Start game** is disabled.
2. Tap Continue with ChatGPT. The app starts a local HTTP listener on 127.0.0.1 with an unpredictable available port, holds it alive via a temporary foreground service, and opens the official OpenAI authorization endpoint in Chrome Custom Tabs.
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
