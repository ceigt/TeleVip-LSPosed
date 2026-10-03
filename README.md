# TeleVip LSPosed

<p>
  <img src="https://img.shields.io/badge/Platform-Android-green">
  <img src="https://img.shields.io/badge/Framework-LSPosed-blue">
  <img src="https://img.shields.io/badge/License-GPL--3.0-orange">
</p>

A powerful LSPosed module that adds advanced customization features to Telegram clients.

## Telegram 12.10.6 / API 102 release

Version 3.6.7 uses module package `org.televip` and requires LSPosed API 102 support. Enable the module for `org.telegram.messenger`, restart Telegram, then open TeleVip from Telegram's settings page. The original release certificate is retained for in-place upgrades and existing settings are preserved.

This release adds runtime APK fingerprints and validated mapping caches, settings entry fallback routes, chat/profile menu handling for merged listener classes, and sponsored-message/search/proxy advertisement filtering with completed callbacks. A startup request gate holds requests until privacy hooks are ready, including service-only startup. Android 16 style switches now have compact tracks while keeping the full row touch target.

Reflection lookups use bounded caches, inherited members and deterministic overload selection; ambiguous overloads are rejected. Dialog button callbacks log failures without crashing the host UI. Source compatibility checks fail on missing required feature symbols or hook signatures and record inactive client branches separately.

Edit history is isolated by account and conversation. Older records without reliable identifiers are retained as an isolated backup and are not displayed. Account logout cleans up only its attributed history. The channel swipe setting blocks Telegram's own gesture in broadcast channels; the back button and Android system back gesture remain available.

Verification on Telegram 12.10.6: ten Android regression groups with 2542 assertions, 131 JVM reflection assertions and five compatibility-tool tests passed. The actual client APK audit covers 417 source sites, including 59 exact hook signatures. Startup logs show 53 resolved hooks and no missing hooks. Settings, compact switches, profile creation-date estimation and chat message navigation were checked on the connected phone. The user verified selected functional flows during test builds; this release does not claim exhaustive end-to-end coverage or validation of every other client below. See [device test coverage](tests/regression/README.md) and [compatibility audit limits](tests/compat/README.md).

Adaptations from [Re: TeleVIP](https://github.com/2B-4G10/Re-TeleVIP) are documented in [third-party notices](THIRD_PARTY_NOTICES.txt).

## ✨ Features

### Privacy
- Hide "Seen" status in:
    - Private chats
    - Channels and Groups
- Hide "Typing..." indicator
- Hide online status
- Hide phone number
- Hide story view status
- Show deleted messages
- Prevent deletion of secret media

### Media & Stories
- Save protected stories to gallery
- Save voice messages
- Enable secret media
- Save message edit history

### Telegram Modifications
- Remove content saving restrictions
- Disable stories
- Hide pinned messages
- Disable channel swipe
- Disable profile swipe
- Disable update notifications
- Disable number rounding

### Performance
- Boost Telegram download speed

### Premium
- Enable Local Premium


> More features are available but not listed here.


# 📱 Supported Clients

| Client | Version |
|---|---|
| Telegram (Google Play) | 12.10.6 (71122; selected features verified on device) |
| Telegram Beta | 12.9.0 (69579) |
| Telegram Web | 12.8.3 (69229) |
| TG Connect | 11.13.1 (11130109) |
| Plus Messenger | 12.8.1.0 (22350) |
| Nagram | 12.8.1 (1239) |
| NagramX | 12.8.1-2bcd1bd (1253) |
| Nagram XF | 12.7.3 (1245) |
| Nekogram | 12.8.1 (69160) |
| Cherrygram | 12.8.1 (69160) |
| Nicegram | 1.55.0 (2139) |
| iMe | 12.8.1 (12080102) |
| iMe Direct | 12.8.1 (12080109) |
| X Plus | 12.0.1 (61669) |
| ForkClient | 12.8.4.0 (691908) |
| ForkClient Beta | 12.8.4.0 (691909) |
| Skygram | 10.20.6 (40639) |
| Teegra | 10.3.2 (41469) |
| Telegraph | 12.8.1.1 (69172) |
| Telega | 2.4.3 (107) |
| Momogram | 12.6.4 |
| Forkgram Classic | 12.8.10.0 |
| Turrit | 1.8.9.9.5 |


# 📢 Updates

All TeleVip updates are published on Telegram:

➡️ https://t.me/t_l0_e


# ⚠️ Warning
> This module is intended for educational purposes only. Its use may result in issues with your Telegram account, including the risk of banning or suspension. Use it at your own risk.


# 📄 License

This project is licensed under the **GNU General Public License v3.0 (GPLv3)**.

See the [LICENSE](./LICENSE) file for more information.


# Credits

Partially based on:

- [Re-Telegram](https://github.com/Sakion-Team/Re-Telegram).


Developed by **@mustafa1dev**
