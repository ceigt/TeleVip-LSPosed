# Priority fix regression tests

This suite compiles selected production classes into a separate Android instrumentation APK. It requires JDK 17, Android SDK platform 36 and build tools 36.0.0. No third-party test libraries are required.

Run from the repository root in PowerShell:

```powershell
./tests/regression/run.ps1 -Sdk '<Android SDK path>' -JavaHome '<JDK path>' -Serial '<adb device serial>' -Adb '<adb executable path>'
```

Omit `-Serial` to build only. `-Adb` defaults to `adb` on PATH. Each run creates an isolated output directory under `app/build/regression`. Device runs install the test package `org.televip.regression`, execute the suite, save `result.txt` and uninstall the test package. The suite uses its own application data; it does not read Telegram messages or log out Telegram accounts.

## Coverage

Thirteen groups cover:

1. Message ID parsing, including empty input, signs and overflow.
2. One-shot read permits: request identity, connection identity, expiration, concurrency and bounded capacity.
3. Child switch persistence and initialization when only one child is enabled.
4. Gregorian dates and yesterday labels across month boundaries.
5. Background worker survival after an injected exception.
6. Real Android SQLite: version 1 migration into an isolated legacy table, account and dialog separation, repeated edit versions, logout revocation, late writes, persisted cleanup after reopening and contained SQL failure.
7. Telegram storage boundary resource disposal on success, empty results and injected failure; disabled-switch behavior.
8. Hook initialization with missing targets, partial failure and retry, concurrent duplicate prevention, independent menu state reset, phone masking limited to the owning display scope, private/group/channel swipe behavior and Calendar time formatting.
9. Telegram settings row and personal info adapter renaming, rejection of ambiguous or incompatible signatures; Android 16 style switch click callbacks, accessibility state, touch targets and light/dark LTR/RTL drawing.

10. Platform dialog callback protection: successful callbacks run once, failures are logged, and positive/negative/neutral button dispatch does not crash the UI.

11. Media scheduler selection across instance/staticized layouts, rejection of wrong returns/argument order/ambiguous targets, strict ad-response reader types, disabled/null/already-empty response behavior, and media path identity isolation. Empty ad responses are also tested without a usable no-argument constructor: inherited lists are initialized, each allocation owns its lists, static/unrelated fields remain unchanged, normal constructor behavior is preserved and abstract/unsupported collection types are rejected. These selectors also run on the JVM and Android.

Database tests use real Android SQLite. Telegram storage wrappers are controlled fakes for error injection and resource checks. Hook installation, real network callbacks and Telegram UI interactions require separate device verification.

The initialization and swipe tests use the production reflection helpers and feature callback with a fake interceptor installer and synthetic chat objects. They verify callback behavior and installation bookkeeping; the actual API 102 interceptor and Telegram field mappings are checked on the installed module separately.

12. Startup failure recovery using production MainHook/BootstrapRequests against a controlled host: three bounded retries, ordinary request release and selective fallback; known read/typing/story/status classification, normal/timestamp response completion, peer/field/response faults, visible degradation, one-shot permits, switch off/on behavior, nested/exceptional TTL restoration, save-button gating and account checks that do not wait for a held database write lock.

The new failure tests deliberately leave the main privacy feature hook unavailable to exercise the permanent bootstrap fallback. Protocol classes, native dispatcher and feature initialization are controlled fakes; tests do not send network traffic from Telegram or alter its messages. Temporary media restoration tests exercise the production scope helper; real one-time media remains a separate end-to-end check.

13. Upstream 1.5 adaptations: the production edit-history hook and real module SQLite run against a controlled host queue. Cases cover offline/group and channel edits, account/dialog separation, live edits without an edit flag, unedited/no-op/missing-cache loads, disabled recording, old-text capture before the host overwrite, and cursor/buffer disposal. Typed story-list filtering covers existing items, indexed/bulk insertion, final instance fields, idempotence, unrelated upload lists and rejection before partial replacement. Host message decoding remains a controlled boundary; real remote edits still require disposable Telegram content.

## Device verification

For the priority fix build, confirm the TeleVip entry opens from Telegram settings and check startup logs for installed history, deletion and ghost mode hooks.

Use disposable messages between your own accounts for functional checks:

- With hidden read receipts enabled, opening an incoming message should not mark it read for the sender. If mark after sending is enabled, sending a reply should mark the intended conversation read.
- A message deleted remotely should remain visible when anti-delete is enabled. Your own local delete should still work. With anti-delete disabled, remote deletion should follow Telegram's normal behavior.
- Edit a text message from A to B to A. Its history should retain both previous versions. Messages in other chats or accounts must not share history, even if their message IDs match.
- If testing logout cleanup, use a disposable account. Logging out must remove only that account's new history. The unassigned legacy backup remains isolated and is never displayed.
- Invalid message ID input must show a validation message without crashing.

Passing the suite confirms these isolated regression cases; it does not certify every TeleVip feature or replace the functional checks above.
