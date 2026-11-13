<div align="center">

# TeleVip LSPosed

为 Telegram 添加隐私控制、消息记录和界面增强的 Xposed 模块。
本分支独立维护 Google Play 版 Telegram 的兼容性。

[![Release](https://img.shields.io/github/v/release/ceigt/TeleVip-LSPosed?label=Release)](https://github.com/ceigt/TeleVip-LSPosed/releases/latest)
[![Build](https://github.com/ceigt/TeleVip-LSPosed/actions/workflows/build-apk.yml/badge.svg?branch=main)](https://github.com/ceigt/TeleVip-LSPosed/actions/workflows/build-apk.yml)
[![Xposed API](https://img.shields.io/badge/libxposed-API%20102-blue)](#安装)
[![License](https://img.shields.io/badge/License-GPL--3.0-orange)](LICENSE)
[![Telegram](https://img.shields.io/badge/Telegram-TeleVip%20Next-2AABEE?logo=telegram)](https://t.me/televip_next)

**[下载最新版 APK](https://github.com/ceigt/TeleVip-LSPosed/releases/latest) · [TeleVip Next 频道](https://t.me/televip_next) · [更新记录](https://github.com/ceigt/TeleVip-LSPosed/releases) · [反馈问题](https://github.com/ceigt/TeleVip-LSPosed/issues)**

</div>

## 功能

| 隐私与广告 | 消息与记录 | 媒体与界面 |
| --- | --- | --- |
| 私聊、频道与群组分别控制已读上报 | 保留已缓存的删除消息 | 故事、语音及受限媒体的本地保存选项 |
| 隐藏输入状态与故事浏览记录 | 保存消息编辑历史 | 扩展本地保存、复制和查看范围 |
| 本机手机号显示为圆点 | 独立删除副本、搜索与导入导出 | 隐藏故事栏和固定消息栏 |
| 在线状态上报控制 | 按账号、会话、话题记住阅读位置 | 本人和其他资料页分别显示 ID |
| 过滤赞助消息、搜索及代理推广 | 显示消息 ID、时间秒数 | 始终显示下载入口、关闭数字缩写 |
| 本机兼容状态与异常提示 | 显式查询符合条件的私聊已读时间 | 日历、本地 Premium 与下载参数优化 |

另提供新加入频道／群组与首次机器人会话的默认静音、频道和资料页滑动返回控制，以及更新提示控制等选项。

设置入口集成在 **Telegram → Settings（设置）→ TeleVip**，位于 **Account** 上方。设置页提供搜索、分类、功能说明和紧凑的 Android 16 风格开关；语言跟随 Telegram，缺失译文回退为英文。

## 安装

### 需要准备

- 已正常运行、支持 **libxposed API 102** 的 Xposed 框架。
- 下表中经过验证的 Google Play 版 Telegram。
- 模块最低安装版本为 Android 8.1（API 27）；框架自身的系统要求另行适用。本分支当前实机验证环境为 Android 16。

### 操作步骤

1. 从 [GitHub Releases](https://github.com/ceigt/TeleVip-LSPosed/releases/latest) 下载并安装 APK。
2. 在模块管理器中启用 **TeleVip**，勾选 Telegram 的作用域 `org.telegram.messenger`。
3. **强制停止 Telegram，再重新打开**。
4. 进入 Telegram 设置页，打开 **TeleVip**，按需启用功能。
5. 标有“修改后重启”的选项，需要再次重启 Telegram 才能完整生效。

本项目沿用 `org.televip` 包名和原发布证书，可直接覆盖升级。新增可选功能默认关闭，已有开关选择保留。

## 兼容情况

当前发行的核验目标：

| 项目 | 已验证范围 |
| --- | --- |
| Telegram 来源 | Google Play 官方版 |
| Telegram 包名 | `org.telegram.messenger` |
| Telegram 版本 | **13.2.2（73031）** |
| 模块接口 | **libxposed API 102** |
| 实机系统 | Android 16 |

历史客户端适配代码仍保留。网站版 `org.telegram.messenger.web`、Beta 及其他第三方客户端未在本版完成认证；上游的支持列表不代表本分支的验证结果。Telegram 升级后，也需要重新核对对应版本。

本次兼容审查覆盖 **539 个必需调用点**；Android 隔离回归通过 **22 组／3140 项断言**。实机检查覆盖设置入口、归档搜索、进程重建后的导入、加密暂存与导出等指定流程。真实账号退出和破坏性日期删除由隔离测试覆盖，不代表所有服务端行为均已实测。

[详细验证记录](docs/releases/26.11.13.md) · [历史适配记录](docs/releases) · [兼容检查说明](tests/compat/README.md)

## 数据与备份

编辑历史、独立删除副本和阅读位置按账号隔离，保存在 Telegram 的应用私有空间。

- **显示删除消息**依赖 Telegram 已缓存的内容，不能找回此前已经丢失、从未缓存的消息。
- **独立删除副本**保存启用后的有效捕获记录，支持搜索、单条移除和系统文件选择器导入导出；秘密或限时消息不进入这个独立归档。
- 导出的 **JSON 备份是明文**，应妥善保存。模块内部中间文件使用加密暂存，操作结束或账号变化时清理。
- 主动删除副本后，导入旧备份不会恢复已明确移除的记录。卸载 Telegram 或清除其数据前，请先导出需要保留的副本。

## 常见问题

<details>
<summary><b>开启隐藏已读后，为什么旧消息又变成未读？</b></summary>

隐藏已读会阻止服务器阅读进度正常推进。服务器刷新会话状态时，旧未读可能再次出现；本地阅读位置只能帮助恢复浏览位置，不能重建服务器的全局未读计数。

若希望频道和群组正常同步、仅隐藏私聊已读，请关闭 **隐藏已读 → 频道和群组**，保留 **私聊** 开启。已有未读需要正常阅读或手动标记已读。

</details>

<details>
<summary><b>“禁用频道滑动返回”会阻止系统返回手势吗？</b></summary>

它控制 Telegram 聊天内容区域的页面滑动。Android 屏幕边缘返回手势和顶部返回按钮仍可使用；资料页的滑动控制另有独立开关。

</details>

<details>
<summary><b>本地 Premium、下载优化和已读时间有哪些限制？</b></summary>

本地 Premium 改变客户端的部分本地表现，不会授予服务端订阅权益。下载参数优化只在下载所属账号具备真实 Premium 时应用，也不保证突破服务端或网络限制。

已读时间需要手动查询，受消息类型、时间范围、对方隐私及账号规则约束。服务器不提供时显示不可用，不推算或绕过权限。

隐藏在线选项会减少状态上报；发送消息、互动等场景下仍可能显示在线。

</details>

<details>
<summary><b>Telegram 更新后找不到 TeleVip，或某个功能失效怎么办？</b></summary>

先检查模块是否启用、作用域是否正确，并完整重启 Telegram。能打开设置时，可在 **本机兼容状态** 查看宿主版本和模块状态。

反馈时请提供 Telegram 来源与版本、模块版本、Android 和框架版本、具体开关及复现步骤。截图和日志请先遮盖手机号、账号信息与私人消息。

</details>

## 更新与频道

项目更新频道：**[TeleVip Next · @televip_next](https://t.me/televip_next)**。

正式版本在 GitHub Releases 发布后，同步推送签名 APK 和简短更新说明到频道。普通代码提交和未正式发布的构建不会重复推送。下载文件与 GitHub Release 保持一致，发布页同时提供校验和及签名来源信息。

本项目使用年月日版本号 `YY.MM.DD`；同日修订追加序号，Android versionCode 持续递增。

## 开发与构建

维护分支为 **`main`**，原 `telegram-12.10.5` 分支保留历史记录。本分支独立维护当前适配，历史借鉴代码的出处见第三方说明。

<details>
<summary><b>本地编译与正式签名</b></summary>

需要 JDK 17、Android SDK 36 和 Build Tools 36.0.0：

```sh
git clone --branch main https://github.com/ceigt/TeleVip-LSPosed.git
cd TeleVip-LSPosed
./gradlew :app:assembleRelease
```

Windows 使用 `gradlew.bat`。本地默认产出未签名 APK；正式版由 GitHub Actions 在受限环境中使用原证书签名，PR 和其他分支无法读取发布证书。

[云端签名与发布维护](docs/release-signing.md) · [设备隔离回归](tests/regression/README.md)

</details>

## 致谢与许可证

- [Mustafa／TeleVip-LSPosed](https://github.com/mustafa1dev/TeleVip-LSPosed)：原项目。
- [Re: TeleVIP](https://github.com/2B-4G10/Re-TeleVIP)：历史 GPL 版本的适配代码与设计参考。
- [Re-Telegram](https://github.com/Sakion-Team/Re-Telegram)：原项目的部分基础。

具体来源、版本及本地改动见 [THIRD_PARTY_NOTICES.txt](THIRD_PARTY_NOTICES.txt)。本分支按 [GNU GPL v3.0](LICENSE) 发布。
