# Android updates: persistent signing for GPT 20 Quiz

## Root cause

The APK filename is **not** Android's update identity.

Android accepts an in-place upgrade when:

1. The package identifier (**applicationId**) is unchanged: **com.francescodifilippo.gpt20quiz**.
2. The new APK is signed with the **same signing certificate and private key** as the installed version (or a supported signing lineage).
3. The new **versionCode** is higher than the installed version.

The earlier workflow relied on a default debug keystore generated on each
temporary GitHub Actions runner. New runners generally generate different
signing certificates, so consecutive CI APKs cannot update each other.
Renaming the APK would not solve INSTALL_FAILED_UPDATE_INCOMPATIBLE.

## New beta signing configuration

The Android Gradle build can now read a persistent beta keystore from four
environment variables. GitHub Actions reads these from **repository secrets**,
not public source files. Every future signed APK reuses the same key.

Once signing is configured, every published workflow artifact is named
**gpt-20-quiz-signed-beta** and contains **gpt-20-quiz.apk**.

**Security rule:** CI will compile and test the app even without signing
secrets, but will NOT publish an installable APK without stable signing.
This avoids accidentally distributing another non-upgradeable build.

## One-time setup (Linux, macOS, or WSL)

Requirements: Java JDK (keytool), openssl, base64, GitHub CLI (gh),
and a GitHub account with permission to manage repository Actions secrets.

Open a local terminal:

~~~bash
gh auth login
git clone https://github.com/francescodifilippo/gpt-20-quiz.git
cd gpt-20-quiz
bash scripts/configure-beta-signing.sh
~~~

If you already have a local clone, use it and update the main branch
before running the script.

The setup script creates a private, reusable signing identity under
**~/.config/gpt-20-quiz/signing/**:

- **gpt20-beta.p12** — secret signing keystore.
- **keystore-password.txt** — secret password.

If these files already exist, the script reuses them, so signing keys
are not silently rotated.

It then configures four protected GitHub repository secrets:

- **GPT20_BETA_KEYSTORE_BASE64** — encoded keystore bytes.
- **GPT20_BETA_STORE_PASSWORD** — keystore password.
- **GPT20_BETA_KEY_ALIAS** — gpt20beta.
- **GPT20_BETA_KEY_PASSWORD** — key password.

Verify their NAMES (not values) at:
GitHub repository → Settings → Secrets and variables → Actions.

**Back up the keystore and password securely.** GitHub Secrets are
write-only; you cannot retrieve their values. Losing this key makes
future in-place updates impossible for installed beta versions.

Do not post the key, password, or their base64 encoding in chat, issues,
commits, emails, screenshots, or logs.

## Build and install

After configuring secrets:

1. Open **GitHub → Actions → Android beta build and tests**.
2. Select **Run workflow**, branch main.
3. Download **gpt-20-quiz-signed-beta** from the successful run's Artifacts.
4. Extract **gpt-20-quiz.apk** and install it on the Android phone.

**One final uninstall may be necessary.** Previously installed beta APKs
(0.2.0–0.2.2) used temporary CI debug-signing keys. Their private key
is no longer available. Uninstalling removes local scores, preferences,
and login credentials; sign in again after installing the persistently
signed version.

After that one-time migration, subsequent updates install over the app
without clearing its data, provided the package ID, signing key, and
alias stay unchanged. On GitHub Actions, versionCode is automatically
set to 10000 + GITHUB_RUN_NUMBER (monotonically increasing per workflow).
Local developer builds retain the base versionCode 5 and should not be
used for updates over newer CI builds.

GitHub Actions verifies the APK signature and prints the **public signing
certificate SHA-256 fingerprint**. Record it once and compare against
future builds. It must remain the same.

## Play Store boundary

The beta uses a development signing identity and a debug build. It is
not an approved Google Play release certificate. A later production
signing strategy may require a separate migration. Never publish the
beta private key publicly to simplify distribution.
