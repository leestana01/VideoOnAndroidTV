# Vela Player

Vela is a remote-first, open-source video player for Android TV, Google TV, projectors, tablets, and phones. It plays local files, USB storage, network URLs, and Android share/open intents without collecting playback history outside the device.

## Highlights

- **Broad playback support** — Media3/ExoPlayer with device decoder fallback for MP4, MKV, WebM, MPEG-TS, MP3, AAC, FLAC, Opus, and other formats supported by the device.
- **Remote-first controls** — D-pad center toggles play/pause, left/right seek, media keys work directly, and all actions have visible focus states.
- **0–400% audio** — system loudness enhancement above 100%, with explicit hearing and speaker-safety guidance.
- **Phone audio** — scan an on-screen QR code and use a phone browser as a synchronized speaker over the local network.
- **USB and storage access** — Android Storage Access Framework support, including removable USB drives exposed by the device picker.
- **Old-device support** — Android 5.0 / API 21 and newer, with no Google Play Services dependency.
- **Private by design** — no analytics, advertising, accounts, cloud backend, or internet permission use beyond user-opened URLs and the local phone-audio server.

## Install

Download the APK and matching SHA-256 file from [GitHub Releases](../../releases). On the TV, allow installation from the file manager used to open the APK. Verify the checksum before installation:

```sh
sha256sum -c Vela-Player-1.0.2.apk.sha256
```

GitHub Releases is the only official binary distribution channel. Every release note includes the APK signing certificate fingerprint.

## Remote controls

- **Center / Play-Pause:** play or pause
- **Left / Rewind:** seek backward 10 seconds
- **Right / Fast-forward:** seek forward 10 seconds
- **Up / Menu / Settings:** focus quick actions
- **Back:** return from playback to the home screen

Quick actions hide after five seconds while a video is playing. They appear immediately and remain visible while playback is paused.

## USB and folder browser

Choose **Browse USB / folder** and grant a storage root once through Android's required permission screen. All navigation after that happens in Vela's own full-screen, D-pad-first file browser. Directories are listed before playable media, common media extensions are recognized even when a USB provider reports a generic MIME type, and the selected root is remembered. Previously watched media offers **Resume** and **Start from beginning** before playback.

## Phone audio

1. Put the TV and phone on the same trusted Wi-Fi network.
2. Start a video, choose **Phone audio**, and scan the QR code.
3. Tap **Start synchronized audio** in the phone browser.
4. Optionally choose **Mute TV**.

The phone receives the selected media directly from the TV and synchronizes against the TV position twice per second. The randomized link expires when Vela exits. Playback is intentionally LAN-only and is not uploaded anywhere.

Android phones generally do not expose the Bluetooth A2DP Sink profile to third-party apps, so reliable direct TV-to-phone Bluetooth speaker mode is not possible across standard Android devices. Vela uses local Wi-Fi instead. A phone may still route browser audio to its own paired Bluetooth speaker or headphones.

## Format compatibility

Actual codec limits depend on the device decoder. H.264/AVC and AAC in MP4 offer the widest compatibility. HEVC, AV1, VP9, Dolby formats, high bit depth, and 4K/8K profiles require matching device support. Vela enables Media3 decoder fallback but does not claim support that the installed hardware or OS cannot provide.

| Source | Support |
|---|---|
| Local / Downloads | Storage Access Framework |
| USB drive | System storage grant plus Vela folder browser |
| HTTP(S) URL | Android open intent / Media3 |
| Other apps | `VIEW` intent for video and audio |
| SMB / NFS | Use a file manager that exposes a content URI |

## Build

Requirements: JDK 17 and Android SDK 35.

```sh
./gradlew lintDebug testDebugUnitTest assembleDebug assembleRelease
```

Release builds are signed only when a local ignored `keystore.properties` file is present. Signing keys and passwords must never be committed. CI runs lint, unit tests, debug and minified release assembly, then installs the release APK on the minimum-supported API emulator and proves the launcher process survives.

## Architecture

- `MainActivity` owns the TV player surface and D-pad behavior.
- `AudioGain` isolates safe gain mapping and is unit-tested.
- `AudioRelayServer` exposes a tokenized, ephemeral HTTP range endpoint and synchronized receiver page on the LAN.
- `MediaBrowserDialog` provides remote-first directory traversal for granted local and USB storage.
- `PlaybackStore` retains per-document resume positions locally.

See [SECURITY.md](SECURITY.md) for the security model and reporting process and [CONTRIBUTING.md](CONTRIBUTING.md) for development standards.

## License

Apache License 2.0. See [LICENSE](LICENSE).
