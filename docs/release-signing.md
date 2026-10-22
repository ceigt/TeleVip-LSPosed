# GitHub APK signing

The Build APK workflow produces an installable APK signed with the existing TeleVip release certificate on GitHub-hosted runners. Once the environment is configured, any authorized maintainer can trigger the workflow from another computer; the original signing computer does not need to be online.

## Download a signed build

1. Open GitHub Actions, choose Build APK and select Run workflow on branch `telegram-12.10.5`. Changes to app/build/signing inputs on that branch also trigger it automatically.
2. Wait for both `build` and `sign` to succeed.
3. Download the artifact named `TeleVip-<source commit>-signed`.
4. Extract `TeleVip-<version>.apk`, `SHA256SUMS.txt` and `SIGNING-INFO.json`. The latter records the exact source commit, workflow run, version, unsigned/signed APK hashes and certificate fingerprint.

The `-unsigned` artifact remains available for inspection. Pull requests, forks and other refs do not run the signing job. Publishing a GitHub release remains a separate operation after the required compatibility and device checks.

## Signing environment

Environment: `release-signing` in `ceigt/TeleVip-LSPosed`.

Deployment policy permits only the branch named `telegram-12.10.5` (type `branch`, not a similarly named tag). Signing is automatic on trusted branch pushes and manual runs; no required-reviewer gate is configured. Repository owners can add environment reviewers if they want approval before each signing job.

Environment secrets:

| Name | Value held by GitHub |
| --- | --- |
| `TELEVIP_KEYSTORE_BASE64` | Base64 representation of the original password-protected PKCS12 keystore |
| `TELEVIP_KEYSTORE_PASSWORD` | Keystore and private-key password used by the existing `televip` alias |

Base64 is an encoding, not encryption. Values are encrypted with the environment's public key before being submitted to the GitHub Secrets API. They are not source files or workflow artifacts. The Windows DPAPI password file is a local backup mechanism and is not used by Linux runners.

Expected release certificate SHA256:

`60f2274c49aac137bd0cc6a30b522f8930c95fe19c53ba7c2469a939665a2efc`

## Workflow boundaries

- The `build` job runs Gradle without the signing environment or signing secrets.
- The `sign` job runs on a fresh hosted runner after `build` succeeds. It checks the repository, event and exact branch, while the environment enforces its own branch policy.
- Signing downloads the unsigned artifact from the same workflow run and verifies its checksum. It does not run Gradle or execute the APK.
- Action dependencies are pinned to immutable commits and checkout does not retain Git credentials.
- Only the signing step receives the two secrets. It restores the keystore in a restricted temporary file and removes it on exit. Secrets are not command-line password arguments or printed output.
- SDK build tools 36.0.0 align and sign the APK. The PowerShell verifier supports both Linux and Windows, checks the original certificate and `org.televip`, requires the version declared in app/build.gradle, and validates alignment and the API 102 entry/declarations.
- The signed artifact uploads only the APK, checksum and public provenance metadata. Artifact retention is 30 days.

## Maintenance

Keep an independent encrypted backup of the existing signing material. To replace a stored copy, update the two environment secrets from that backup using GitHub's public-key encryption or the GitHub CLI; do not commit the keystore or print passwords. GitHub's secret APIs list names and metadata but do not return the stored plaintext.

When changing the development branch, update both the workflow guard and the environment branch policy together. Keep signing restricted to reviewed, trusted code. Required-symbol compatibility checks and real-device testing still determine whether a build is ready for release.

References: [GitHub environment secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets), [environment branch restrictions](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments), [Android apksigner](https://developer.android.com/tools/apksigner).
