# Independent maintenance and feature shortlist

Reviewed 2026-11-10 (Asia/Singapore), against origin `4a02f4b47b0b454514d82e3b344fb6bef23391e5`, module 3.6.19 and the latest authenticated Play 13.2.1 target. This is an assessment, not implementation or a new device-test verdict.

## Maintenance direction

The user has asked this fork to maintain Telegram compatibility independently. Repairs do not wait for either upstream. Public feature descriptions may supply ideas; implementation uses this fork, inspectable official Telegram sources and authenticated target APKs, with local designs and tests. Closed-source APKs are not needed for this work. Retain attribution and licence notices for earlier GPL adaptations.

Keep existing preferences and privacy behavior by default. New optional behavior starts off. Never substitute a server read request for a local read-position fix without an explicit user choice. Retention, cleanup and import changes need a reviewable design before changing user data. The canceled automatic maintenance schedules stay disabled.

Continue the existing delivery gates: real Play APK signature and channel evidence; feature/protocol/selector checks; meaningful regression and device checks; trusted GitHub build/sign with the original certificate; preserve-data installation; and release only after the relevant checks support it. A fully compatible Telegram update does not require a new module version.

## Public feature reference

Reviewed the public [Re-TeleVIP changelog through 1.5.87](https://github.com/2B-4G10/Re-TeleVIP/blob/a60e5c6aa13cd8a8544a9d673e73f3f5ac3d2e56/CHANGELOG.md), including the post-1.5.4 descriptions. These are behavior leads, not verified implementation claims. No new upstream code or binary is imported.

## Ranked candidates

| Priority | Candidate | Current evidence and smallest useful scope |
| --- | --- | --- |
| P1, foundational | Persistent local reading position while hiding receipts | This fork has no independent persistent watermark; README already explains recurring old unreads. Store position per account/dialog/topic without advancing the server watermark. Separate restoring the scroll position from calculating local unread counts. Test restart, new incoming messages, topic separation, explicit mark-as-read, account switching and logout. This is a larger optional feature, not a switch-default change. |
| P1, foundational | Durable deleted-message archive, then export/import | `ShowDeletedMessages` and `MessageStorage` currently mark existing host rows rather than keep an independently restorable archive. Start with isolated local storage/recovery; add user-selected backup only after it is reliable. Account/dialog/message identity, duplicate suppression, storage bounds and explicit local deletion/logout semantics must be specified. Restoring records must not resurrect a user's deliberate deletion. Uncached/lost content cannot be reconstructed. No database migration is performed in this assessment. |
| P1, targeted | Poll/time annotation layout | `MessageTimeModifier` prepends ID/deleted text and enlarges time widths, with no dedicated poll-footer collision handling. Reproduce on disposable/public timed and ordinary polls, long author names and font scaling before a layout fix. Do not infer failure solely from a surviving method signature or the upstream changelog. |
| P1, small visual fix | Remove the profile status/ID grey rectangle while preserving copy and alignment | The 2026-11-09 public-profile screenshots visibly show a grey rectangle around the two-line status. Current rendering appends the ID and attaches a click listener to the host status view. Investigate the host background/pressed-state path; do not assume that removing a background globally is the repair. Limit changes to module-owned ID labels; verify both profile kinds, light/dark, scrolling and copy. |
| P1, investigation | Current protection-removal routes | `RemovesContentSaving` hooks chat no-forward, selected-message and forwarding predicates. It has no explicit additional per-message/private-Disable-Sharing route. Public descriptions suggest these routes changed in 13.2; failure has not been reproduced here. Use disposable protected content to check copy/save/menu/screenshot scopes and compiled/inlined paths. Local saving/copying is a separate scope from sending a new forwarded copy. |
| P2 | Settings search, clearer categories and explanations | `SettingsFallback` currently builds a flat scroll list. Add search and concise descriptions using the existing controls, keeping compact switches, accessibility and existing values. Category navigation can follow if needed. No glass rendering library or entire UI rewrite is justified for search. |
| P2 | User-visible compatibility status | Existing HookHealth and privacy degradation information mostly go to logs/the warning area. Show host/module versions, hook initialization health, last verified target and unknown/unverified states locally. Distinguish hooks loaded, APK static checks and end-to-end behavior. Store updates cannot be controlled by an in-app update interceptor; do not promise to block Play updates. No automatic diagnostic upload or new schedule is implied. |
| P2, verify first | Own-message edits and deleted pinned-message cleanup | SaveEditsHistory has no sender filter, so own edits are not established as unsupported; trace the own-edit write path and reproduce before adding a new hook. Server deletion interception currently skips native update handling, so local pin references deserve a fixture test. Any cleanup should affect local cached references, not send a channel-wide unpin request. |
| P3 | Seconds and outgoing read-time display | Seconds are a small optional convenience but must integrate with poll/footer sizing, locale formatting and deduplication. Read-time display additionally needs verified protocol availability, bounded per-account caching, rate limits, callback/lifecycle correctness and privacy checks. Do not enable it or fetch anything during this assessment. |

## Suggested work order

1. Small, independently testable changes: profile grey rectangle; poll annotation reproduction/fix; then settings search. Investigate protection-removal and own-edit paths with disposable fixtures alongside those changes, repairing only reproduced gaps.
2. Design and test persistent deleted-message storage and local read-position support as separate changes. Start with an opt-in implementation that preserves existing data and behavior. Add backup/import after storage and cleanup semantics are settled.
3. Add local compatibility reporting and convenience options. Read-time display comes after the storage/cache/privacy design, not as a direct port of an unavailable implementation.

## Existing behavior and lower-value work

- Shared chat/profile menu listener routing already keeps handlers per fragment class in MenuClicks. Do not reimplement the upstream account-age restart fix without a local reproduction.
- Telegram 13.2 profile/status alignment, separate self/other IDs, settings entry, update suppression, account-specific download policy and message parameter changes were already adapted in 3.6.19. Play 13.2.1 was verified without changing that APK.
- Keep the real-Premium download guard. Do not expand ordinary-account concurrency based only on upstream marketing; throughput and server-wait behavior need measurements.
- Liquid glass sliders, forced low-battery glass, broad new-client coverage, many extra languages and a full theme system have lower benefit for the current Play-focused maintenance task.
- Anonymous usage/hook reporting is not part of this plan. Compatibility evidence can remain local or explicitly user-shared. Restricted forwarding also remains separate: account, topic, album, scheduling, paid-send and async object ownership cannot be replaced by guessed defaults.

No app code, preferences, database, APK version or release changes in this assessment. No fresh device test is claimed. The most recent actual target evidence remains [the Play 13.2.1 verification](2026-11-09-play-13.2.1.md).
