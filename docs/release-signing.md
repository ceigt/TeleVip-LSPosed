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

## Verified cloud run

On 2026-10-22 (Asia/Taipei), [Build APK run 39807413565](https://github.com/ceigt/TeleVip-LSPosed/actions/runs/39807413565) completed both build and signing jobs successfully for commit `9ccef28da6ca30c40ec6926f3076816e0733877b`.

The signed artifact was downloaded and checked independently using SDK build tools 36.0.0. Its signature, original certificate, alignment, package `org.televip`, versionCode 355, versionName `3.6.13-telegram-13.1.0-api102` and API 102 declarations passed verification. Artifact contents were exactly the APK, SHA256SUMS.txt and SIGNING-INFO.json.

- Unsigned APK SHA256: `b1f812b7664c9b6ed667aae81ca248d8caec5187b47d28d7a057fcacfff53a08`.
- Cloud-signed APK SHA256: `9447187e8c6b713baec8f8f831f80b06b07af6a665987af86262306264c6b697`.
- The cloud-signed APK is byte-for-byte identical to the original-computer-signed 3.6.13 already installed and verified on the phone. No app source or version changed for this signing setup.

The local verifier also rejected an intentionally wrong expected version. Workflow structure checks confirmed pinned action commits, no signing secrets in the build job, and repository/event/ref guards on the signing job. PR/fork restrictions were inspected in the workflow and environment configuration; no additional test PR was created.

References: [GitHub environment secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets), [environment branch restrictions](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments), [Android apksigner](https://developer.android.com/tools/apksigner).

## Maintenance build 3.6.14

On 2026-10-26 (Asia/Singapore), [run 40234171334](https://github.com/ceigt/TeleVip-LSPosed/actions/runs/40234171334) completed build and sign for source `7c84654faca40f8f6136f825ccc50c7ec4ba2df0`. The signed artifact was downloaded and independently verified with the original certificate, package `org.televip`, versionCode 356, versionName `3.6.14-telegram-13.1.0-api102`, alignment and API 102 declarations. APK SHA256: `bc38eefc2faba135f419230a059a32c5d1306277269b95dbd00fb305099dad31`.

Both artifact archive digests and the signing metadata agree with the same workflow run. All unsigned APK ZIP entries match the signed APK's original contents. The phone is disconnected, so this is a verified signed test artifact with installation and UI checks pending. See [3.6.14 verification](releases/3.6.14.md).

## Play 13.1.1 build 3.6.15

On 2026-10-26 (Asia/Singapore), [run 40252159827](https://github.com/ceigt/TeleVip-LSPosed/actions/runs/40252159827) completed both jobs for `cdf433ad6a53375df131a5c93f89ee7e95752ef6`. The downloaded signed artifact passes the original-certificate, package, versionCode 357, versionName `3.6.15-telegram-13.1.1-api102`, alignment and API 102 checks. Signed APK SHA256: `15e54ca5022a2434f016b0161e56bde012d19f150972a89e670a560e56f4f71c`.

It is installed over 3.6.13 with data preserved. The pulled-back installed APK is identical to that signed artifact. Actual Play 13.1.1, the older Play control, isolated Android regressions and selected UI flows have been verified; preferences and temporary system settings are restored. See [3.6.15 verification and limits](releases/3.6.15.md).
