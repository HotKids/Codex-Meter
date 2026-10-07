# Codex Meter

[![Android CI](https://img.shields.io/github/actions/workflow/status/HotKids/Codex-Meter/build-apk.yml?branch=main&label=Android%20CI&logo=github)](https://github.com/HotKids/Codex-Meter/actions/workflows/build-apk.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Android 12+](https://img.shields.io/badge/Android-12%2B-3DDC84.svg)](https://developer.android.com/about/versions/12)

Codex Meter 是一款用于查看与监控 Codex 用量的开源 Android 应用，基于 [BenItBuhner/Codex-Meter](https://github.com/BenItBuhner/Codex-Meter) 项目，使用 Codex 和 Claude Code 进行二次开发。

应用集中展示 ChatGPT 账户的会话限额、每周限额、剩余额度及重置时间，并提供桌面小组件、用量提醒和实时通知，便于及时了解用量状态。

[下载最新版](https://github.com/HotKids/Codex-Meter/releases/latest) · [反馈问题](https://github.com/HotKids/Codex-Meter/issues) · [更新记录](CHANGELOG.md)

## 下载与安装

系统要求：**Android 12 及以上**。

请前往 [发布页面](https://github.com/HotKids/Codex-Meter/releases/latest) 下载 APK 安装包。如系统提示安装来源受限，请为当前浏览器或文件管理器开启安装权限。

安装后，可通过「设置 → 应用更新」检查新版本，或从发布页面下载新版安装包进行更新。

## 使用指南

1. 打开应用，按照引导登录 ChatGPT；也可稍后在设置页登录。
2. 在浏览器中完成 OpenAI 登录与授权，无需填写 API Key 或手动复制令牌。授权完成后会自动返回应用；部分系统的 localhost 跳转可能耗时较长，此时可尝试手动返回应用。
3. 在首页查看账户用量。点击首页编辑按钮，可选择显示项目并拖动调整顺序。
4. 根据需要添加桌面小组件，或在设置中配置用量提醒与实时通知。

登录与用量刷新均需使用能够访问 OpenAI / ChatGPT 的网络环境。

## 功能概览

- **限额与重置时间**：查看会话限额、每周限额及对应的重置时间。采用月周期的账户可查看每月限额。
- **剩余额度**：查看已购买的 Codex 额度余额。
- **重置额度**：查看可用重置额度及到期时间。账户支持此功能时，可在应用内使用重置额度。
- **用量历史**：查看本机记录的用量变化、消耗速度及预计耗尽时间。历史记录自成功刷新用量后开始积累，预计耗尽时间根据已有记录估算。
- **用量提醒**：配置剩余用量预警、限额重置提醒，以及重置额度新增和到期提醒。
- **外观设置**：支持简体中文与英文、明暗主题及系统动态取色。
- **备份迁移**：支持导入与导出设置，并可选择是否包含登录信息。

限额周期、剩余额度和重置额度以账户实际返回的数据为准。没有可用余额或重置额度时，首页会自动隐藏对应项目。

## 桌面小组件

长按桌面并打开小组件列表，选择 **Codex Meter**，即可添加小组件。首次添加时会打开配置页面，可选择显示内容并调整顺序。

| 小组件 | 显示方式 |
|---|---|
| **Codex 仪表盘 · 2×1** | 以圆弧展示会话用量、每周用量或重置倒计时，宽度固定 |
| **Codex 用量 · 2×2 及以上** | 以卡片展示用量、重置时间和剩余额度，支持调整尺寸及内容顺序 |

小组件中的用量百分比均表示**剩余用量**。用量卡片可在重置项目中显示可用重置额度；剩余额度以数值展示。

Pro 套餐新添加的小组件默认关闭 **5 小时会话限额**，显示每周用量与重置时间。如需显示会话限额，可在配置页面开启；已有小组件保留已保存的设置。

小组件支持调整背景、不透明度及配色。配色提供**自动、原生、经典**三种选项：自动模式在 Pixel 设备上使用原生配色，在其他设备上使用经典配色；也可自行指定配色。配色选择不影响圆角与桌面适配。用量卡片提供刷新按钮，点击会话、每周或余额区域可打开首页并定位到相应卡片；若该卡片已隐藏或暂无数据，则仅打开首页。兼容的 Samsung One UI 设备还支持锁屏与息屏显示小组件。

针对 ColorOS 原生桌面，已对小组件背景与圆角进行基础适配。受系统白名单限制，该桌面暂不提供 **2×1 仪表盘小组件**，可使用 2×2 及以上的用量卡片。

## 实时通知与提醒

在「设置 → 实时通知」中开启实时监控，可通过通知查看用量与重置信息，也可配置达到指定阈值时自动启动。实时通知的详细内容与顺序遵循**最近一次保存的小组件设置**；在首页关闭会话限额后，实时通知也会隐藏该项。

实时通知支持原生 [Android Live Updates](https://developer.android.com/develop/ui/views/notifications/live-update)，并兼容 Samsung Now Bar。支持 Live Updates 的 ColorOS 设备也可通过该功能显示实时通知。具体显示形式取决于系统版本、固件及通知权限；不支持上述显示方式时，可使用普通持续通知。用量提醒可在「设置 → 用量提醒」中单独配置，另提供额度耗尽和登录失效提醒。

## 隐私与数据

- 登录与授权通过 OpenAI 的浏览器页面完成，应用不会获取账户密码。
- 登录凭证经加密后保存在本机，加密密钥由 Android Keystore 保护。用量历史与设置同样保存在本机。
- 登录与用量查询直接访问 OpenAI / ChatGPT，不经过本项目的中转服务器；应用更新通过 GitHub 获取。
- 导出备份时可选择包含登录信息。此类备份包含账户令牌，请妥善保管，避免分享或上传至不可信位置。

有关数据存储与网络访问的详细说明，请参阅应用内的「隐私说明」。

## 开发计划

- [ ] **多账号管理**：支持添加与切换多个账户，分别查看各账户的用量信息。
- [ ] **更多服务支持**：支持 Claude、Grok 等服务的用量查询与监控。

## 反馈与致谢

如遇到问题或有功能建议，请提交 [GitHub Issue](https://github.com/HotKids/Codex-Meter/issues)。报告问题时，请提供应用版本、设备型号、系统版本及复现步骤；涉及界面显示的问题，可附上截图。

本项目由 [HotKids](https://github.com/HotKids) 维护，基于 [BenItBuhner/Codex-Meter](https://github.com/BenItBuhner/Codex-Meter) 开发，采用 [MIT 许可证](LICENSE)。感谢上游项目及相关贡献者。Codex Meter 是社区项目，并非 OpenAI 或 Samsung 的官方应用。

参与开发前，请阅读 [贡献指南](CONTRIBUTING.md)。
