# TeleVip LSPosed

<p>
  <img src="https://img.shields.io/badge/Platform-Android-green">
  <img src="https://img.shields.io/badge/Framework-LSPosed-blue">
  <img src="https://img.shields.io/badge/License-GPL--3.0-orange">
</p>

A powerful LSPosed module that adds advanced customization features to Telegram clients.

## Telegram 12.10.5 / API 102 release

Version 3.6.5 uses module package `org.televip` and requires LSPosed API 102 support. Enable the module for `org.telegram.messenger`, restart Telegram, then open TeleVip from Telegram's settings page. It uses the same release signing certificate as 3.6.4 and the 3.6.5 test builds, so those versions can be upgraded in place.

Edit history is isolated by account and conversation. Older records without reliable account/conversation identifiers are retained in a separate backup table and are not displayed. New attributed history is cleaned up when its account logs out.

The channel swipe setting blocks Telegram's own swipe gesture only in broadcast channels; the top-left back button and Android system back gesture remain available.

Eight device regression groups with 2506 assertions passed. The user verified the settings entry, hidden read receipts and mark after sending, remote/local deletion, edit history, phone masking and channel swipe behavior on Telegram 12.10.5. These checks cover selected features, not every switch. Other client versions below have not been retested for this release. See [regression coverage and device checks](tests/regression/README.md).

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
| Telegram (Google Play) | 12.10.5 (71052; selected features verified on device) |
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
