#!/usr/bin/env bash
# One-time local beta signing setup.
# Private keys and passwords must NEVER be checked into the public repository.
set -euo pipefail
umask 077

REPO="${GPT20_GITHUB_REPO:-francescodifilippo/gpt-20-quiz}"
KEY_DIR="${GPT20_SIGNING_BACKUP_DIR:-$HOME/.config/gpt-20-quiz/signing}"
KEYSTORE="$KEY_DIR/gpt20-beta.p12"
PASSWORD_FILE="$KEY_DIR/keystore-password.txt"
ALIAS="gpt20beta"

for tool in keytool openssl gh base64; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "Missing command: $tool. Install the JDK, openssl, and GitHub CLI (gh) first." >&2
    exit 1
  fi
done
gh auth status --hostname github.com >/dev/null

if [[ -e "$KEYSTORE" || -e "$PASSWORD_FILE" ]]; then
  if [[ ! -s "$KEYSTORE" || ! -s "$PASSWORD_FILE" ]]; then
    echo "Incomplete signing backup. Restore BOTH the keystore and password before proceeding." >&2
    exit 1
  fi
  echo "Reusing the existing beta signing identity (no key rotation)."
else
  mkdir -p "$KEY_DIR"
  chmod 700 "$KEY_DIR"
  PASSWORD="$(openssl rand -hex 24)"
  printf '%s' "$PASSWORD" > "$PASSWORD_FILE"
  chmod 600 "$PASSWORD_FILE"
  keytool -genkeypair \
    -alias "$ALIAS" \
    -keyalg RSA -keysize 3072 \
    -validity 3650 \
    -storetype PKCS12 \
    -dname "CN=GPT 20 Quiz beta,O=GPT 20 Quiz,C=IT" \
    -keystore "$KEYSTORE" \
    -storepass "$PASSWORD" \
    -keypass "$PASSWORD" \
    -noprompt >/dev/null
  chmod 600 "$KEYSTORE"
  echo "Created new persistent beta signing identity. Preserve it for all future updates."
fi

PASSWORD="$(cat "$PASSWORD_FILE")"
test -n "$PASSWORD"
keytool -list -keystore "$KEYSTORE" -alias "$ALIAS" -storepass "$PASSWORD" >/dev/null

# GitHub Secrets cannot be read back. Save the local backup securely.
# Standard input keeps private values out of gh command-line arguments.
base64 < "$KEYSTORE" | tr -d '\r\n' | gh secret set GPT20_BETA_KEYSTORE_BASE64 --repo "$REPO"
printf '%s' "$PASSWORD" | gh secret set GPT20_BETA_STORE_PASSWORD --repo "$REPO"
printf '%s' "$ALIAS" | gh secret set GPT20_BETA_KEY_ALIAS --repo "$REPO"
printf '%s' "$PASSWORD" | gh secret set GPT20_BETA_KEY_PASSWORD --repo "$REPO"

echo
echo "Uploaded four encrypted Actions secrets to $REPO."
echo "Certificate fingerprint (public; compare against future APK signatures):"
keytool -list -v -keystore "$KEYSTORE" -alias "$ALIAS" -storepass "$PASSWORD" \
  | grep -E 'SHA256:|SHA-256:' | head -1 || true
echo
echo "IMPORTANT: Back up this private keystore and password in a secure location:"
echo "  $KEYSTORE"
echo "  $PASSWORD_FILE"
echo "Do not send these files or their contents in chat or commit them to GitHub."
echo
echo "Now run or re-run Android beta build and tests in GitHub Actions."
echo "Only builds signed with this same identity can update each other in place."
