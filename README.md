# FriendLink

基于 [ZzaiQWQ/FriendLink](https://github.com/ZzaiQWQ/FriendLink) 移植修改。

FriendLink 是一个实验性的 NeoForge 客户端 Mod，将 Minecraft 官方的 Friends / P2P 多人联机流程移植到 Minecraft 1.21.1。

它使用 Mojang/Microsoft 的官方好友服务、信令服务、TURN 认证和 WebRTC 传输来实现 P2P 联机。

FriendLink 是非官方的 Minecraft Mod，与 Mojang Studios 或 Microsoft 无关。

## 状态

实验性。当前版本专注于在 Minecraft 1.21.1 (NeoForge) 上验证官方 P2P 联机流程。

## 环境要求

- Minecraft 1.21.1
- NeoForge
- 需要使用 Microsoft 账号登录，且已开启好友/在线状态功能

## 与原项目的主要区别

- 将原 Fabric 项目移植到 NeoForge 1.21.1
- 重新设计了 UI：在标题画面添加好友图标按钮，在暂停菜单添加"对好友开放"和"好友列表"按钮
- 支持显示好友详细在线状态（游玩中 / 对好友开放中）
- 支持主动加入好友世界
- 被邀请时显示接受/拒绝按钮

## 注意事项

- **首次启动需要联网**：根据 Minecraft EULA，Mod 不能直接分发游戏资源文件（如纹理）。为遵守许可协议，FriendLink 不包含任何 Minecraft 官方资源。首次启动时，Mod 会自动从 Mojang 官方服务器下载26.2版本的客户端 Jar，缓存到 `config/friendlink/cached_client.jar`，之后从缓存中动态读取 UI 纹理。后续启动无需再联网。
- FriendLink 不包含、不分发、不替代 Minecraft 本体
- 这不是自定义服务器转发
- 官方联机流程依赖 Microsoft 服务，在受限或高延迟网络下可能不稳定
- 联机双方建议使用相同的 Mod 版本

## 构建

```bat
gradlew.bat build
```

生成的 jar 文件位于 `build/libs/`。

## 许可证

FriendLink 基于原项目，遵循 GPL-3.0-only。详见 `LICENSE`。
