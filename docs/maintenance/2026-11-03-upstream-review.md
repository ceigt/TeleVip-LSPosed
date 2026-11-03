# Upstream review, 2026-11-03

Manual check in Asia/Singapore. Automatic update schedules remain disabled. No runtime code, preferences, databases or installed packages were changed.

## Current GitHub and device baseline

- Origin `telegram-12.10.5` was checked online and fetched at `27c5170cd3584d2bc820e63ef4980115257709ea`, recording module `3.6.18-telegram-13.1.1-api102` / versionCode 360. Its application and build inputs are unchanged from signed source `46998ddf7c4af7a3b7f0f826252e670387799170`.
- The connected phone reports module 3.6.18 (360) and Telegram `org.telegram.messenger` 13.1.1 (72371). Installed module SHA256 is `3ac9eec166abdde098aadc0ab82e31d00e5f287084b2289c8fa4fa8b8e556434`; Telegram base SHA256 remains `f4b44311c7f7451836084b4bcdd8379857e3f2ca1b64b7e063e8568f6002128f`, matching the previously authenticated Play target.
- Both jobs of [Build APK 41215755477](https://github.com/ceigt/TeleVip-LSPosed/actions/runs/41215755477) are confirmed successful. The installed APK was pulled back again and `tests/release/verify.ps1` passes original certificate, package, exact version, alignment and API 102 declarations. Its hash matches the previously verified cloud artifact. No reinstallation is needed.
- The latest published stable GitHub Release is still [3.6.15](https://github.com/ceigt/TeleVip-LSPosed/releases/tag/v3.6.15-telegram-13.1.1-api102). The installed 3.6.18 is a newer signed Actions build; these are separate delivery states.
- Existing uncommitted 3.6.16-era edits in the older local checkout were preserved. This review uses an isolated checkout of the fetched origin, rather than publishing those stale edits over newer work.

## Upstream evidence

Original [mustafa1dev/TeleVip-LSPosed](https://github.com/mustafa1dev/TeleVip-LSPosed/commit/a879b70288794f09ebdf24c200e3c0ca8c3974a1) remains at `a879b70288794f09ebdf24c200e3c0ca8c3974a1`: no new source since the previous review.

[Re-TeleVIP](https://github.com/2B-4G10/Re-TeleVIP/commit/d89b0c790eea3986035129dfeda948b82a0b6a15) advanced from reviewed `c4e67570724d4fe8755bea3cb7ec9f4e35426b4f` (1.5.2) to `d89b0c790eea3986035129dfeda948b82a0b6a15` (1.5.6). [1.5.6](https://github.com/2B-4G10/Re-TeleVIP/releases/tag/v1.5.6) was published at 2026-11-03T00:14:06Z. The current release collection contains 1.5.4, 1.5.5 and 1.5.6, each with only an APK asset.

The current and 1.5.3 repository trees contain distribution documentation and artwork, not application source. The 1.5.3 release API and former `Re-TeleVIP-1.5.3-source.zip` download both return HTTP 404. The upstream [LICENSE](https://github.com/2B-4G10/Re-TeleVIP/blob/d89b0c790eea3986035129dfeda948b82a0b6a15/LICENSE) and [changelog](https://github.com/2B-4G10/Re-TeleVIP/blob/d89b0c790eea3986035129dfeda948b82a0b6a15/CHANGELOG.md) state that 1.5.4 onward is no longer released as GPL source; earlier copies retain their GPL licence. Previously imported GPL adaptations remain attributed. No new APK was downloaded, installed or reverse engineered for this review.

Release descriptions are feature leads, not inspected implementation or proof that a defect occurs on the current phone.

## Applicability and decisions

| Upstream change | Current implementation and decision |
| --- | --- |
| Keep deleted messages through busy-group reload, auto-delete and host cache rebuild; export/import | Highest-value candidate. Our `ShowDeletedMessages` intercepts server deletion updates and marks existing host rows; it has no independent archive from which to recover removed rows. This does not establish a reproduced loss on the phone. A separate archive would need account/dialog isolation, restore deduplication, explicit user-delete/logout cleanup, schema and export validation. No upstream source is available to port; no data migration or retention change is made. |
| Poll footer and long-author layout with time annotations | Valuable compatibility candidate because our `MessageTimeModifier` also extends the time string for IDs/deleted markers. The current code updates time widths but has no poll-specific footer/layout guard. Need a reproducible public/disposable poll fixture, actual host layout inspection and regression coverage before implementing a local fix. No passing or failing device verdict is invented. |
| Stories return after restart | Our 3.6.16 already filters cached and subsequently inserted PeerStories in the two mapped presentation lists, with constructor execution observed on warm-cache cold starts. This is more than a hasStories-only hook. Upstream's later exact failure mechanism cannot be compared without source; live-story fixtures remain unverified. Do not replace all controller lists or claim the newer fix is already identical. |
| Downloads idle icon animation | Potential small polish for the optional shortcut, currently off by default. Our hook forces visibility and leaves drawable behavior to the host. No reproduced idle-animation failure or upstream implementation is available; leave it unchanged. |
| Group switches toggle all children | The current Play settings route uses group headings and independent child switches, not an actionable parent switch. The legacy expandable row is an expansion control. Do not change private/channel receipt choices while fixing a different upstream UI. |
| Download acceleration without real Premium | Our real-account guard deliberately avoids applying Premium connection settings to ordinary accounts. The new algorithm cannot be reviewed or measured from release prose. Retain the existing guard and owning-account checks. |
| Separate history database to avoid another module's schema collision | Our account-isolated schema already differs from upstream. A collision is possible if another module opens the same host directory/file, but this scenario was not reproduced here. No database rename, migration or downgrade waiver is introduced. |
| Clear saved data also removes retained deleted messages | Our current settings have no equivalent clear-all action or independent deleted-message archive. Do not introduce deletion of host rows or history as an incidental port. |
| Seconds, language prompt suppression, Portuguese/Turkish | Optional new features/localization, not fixes required for the unchanged Play target. No new defaults or translations copied without source. |
| Turrit, Forkgram Classic and Nekogram fixes | Different clients; they do not establish a failing Play 13.1.1 route. No broad compatibility exemptions or unrelated hooks added. |
| Restricted-content forwarding attribution and anonymous usage reporting | Forwarding remains deferred for account/argument/async ownership concerns recorded in 3.6.18. No telemetry or new network reporting is introduced. |
| AGP 9.5.0 / upstream refactoring | No demonstrated benefit to the current verified build. No dependency or workflow churn. |

## Result and validation limits

No additional source is available to merge after the GPL 1.5.2 adaptations already in 3.6.18. This check therefore creates only a review record, with no new version, build or installation. The two useful follow-up investigations are independent deleted-message persistence and poll annotation layout; neither is represented as completed.

This run rechecked versions, hashes, signature/package/release metadata, source availability and relevant current source paths. It did not repeat feature UI, compatibility/regression suites, send messages, delete content, clear databases, change privacy settings or log out. Existing 3.6.18 test evidence and its end-to-end limits remain in [the verification record](../releases/3.6.18.md).
