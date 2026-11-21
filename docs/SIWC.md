# Sign in with ChatGPT (SIWC) — Android technical notes

**Status:** design only, not yet integrated or authenticated successfully on Android.

## Documentation

- https://developers.openai.com/siwc/quickstart
- https://developers.openai.com/siwc/token-sharing-open-source
- https://developers.openai.com/siwc/token-sharing-open-source/sign-in
- https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference
- https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations
- https://openai.com/policies/sign-in-with-chatgpt-terms/

These documents and availability can change; revalidate immediately before release.

## OSS sign-in flow

1. Generate and persist a unique host identifier for each app installation.
2. Start a local callback listener on `127.0.0.1`, and create new high-entropy `state`, `nonce` and PKCE S256 verifier/challenge.
3. Open the *system browser* authorization page. First authorization uses `client_id=dynamic_agent_client` and `agent_name_hint=GPT 20 Quiz`. Request identity scopes and optional permission scopes for plan usage.
4. Receive callback and validate `state`. On first authorization save the *issued* client ID returned in callback, not the dynamic placeholder.
5. Exchange code using PKCE and validate ID token signature (JWKS), issuer, audience, expiry, nonce and expected account.
6. Store tokens locally, scoped to the user account/issued client ID, with Android platform protection. Refresh and rotate tokens correctly; support disconnect.
7. Only enable inference if `chatgpt.tokens.use.direct` was granted and the account is eligible.

**Important mobile spike:** The documented OSS redirect is an HTTP loopback callback on `127.0.0.1` with a fixed path (e.g. `/auth/callback`). A native Android browser/foreground-background lifecycle may have additional constraints. Do not silently substitute a custom URI scheme; validate a functioning flow on Android.

## Inference

Use the user's OAuth access token, not a hard-coded developer key:

- `POST https://api.openai.com/v1/responses`
- `Authorization: Bearer <user's access token>`
- `store: false`
- `stream: true`
- `instructions`: versioned game rules
- `input`: array with all game state needed for this turn
- Select a model supported by that authenticated account; do not hardcode an unverified model.
- Read and validate streamed output; handle error events, limits, token expiry and cancellation.

For preview compatibility, **do not use** unsupported top-level `prompt`, `previous_response_id` (HTTP), `conversation`, `temperature` or `max_output_tokens`; see the preview limitations.

## Terms and limits

- One player's authorization is used solely for that player's own game sessions.
- No shared account, token resale, subscription pooling or remote inference proxy.
- Users can use ChatGPT plan features through SIWC without paying the app developer.
- Provide eligibility/limit explanations, privacy notice, account removal and app disconnect.
- Permission to sign in is separate from permission to consume plan usage.
- Neither capability grants access to the user's private ChatGPT chat history.

## Acceptance criteria

On a real Android device: initial registration, browser return, valid ID token, plan usage granted/declined, successful streamed response, token renewal, logout/revoke, offline state, and handling of an ineligible plan. Document platform version, device, network, and result for each test.
