# FriendLink

FriendLink is an experimental Fabric client mod that backports Minecraft's official Friends / P2P multiplayer flow to Minecraft 26.1.2.

It uses Mojang/Microsoft services for friends presence, official signaling, TURN auth, and WebRTC transport.

## Status

Experimental. The current build is focused on validating the official P2P path on Minecraft 26.1.2.

## Requirements

- Minecraft 26.1.2
- Fabric Loader
- Fabric API
- Microsoft accounts with Minecraft friends / online presence enabled

## Notes

- This is not a custom server relay.
- The official flow depends on Microsoft services and may be unstable on restricted or high-latency networks.
- Both clients should use the same mod build.

## Build

```bat
gradlew.bat build
```

The jar is produced under `build/libs/`.

## License

FriendLink is licensed under GPL-3.0-only. See `LICENSE`.
