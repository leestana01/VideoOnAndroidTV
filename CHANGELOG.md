# Changelog

All notable changes are documented here. Versions follow Semantic Versioning.

## 1.0.2 - 2026-09-20

### Fixed

- Keep the player surface detached from the home screen and return home cleanly with Back.
- Add a remote-focused hierarchical browser for granted folders and removable USB storage.
- Recognize common media extensions when storage providers return generic MIME types.
- Prompt to resume or restart previously watched media.
- Make D-pad left/right and hardware rewind/fast-forward seek by 10 seconds with on-screen feedback.
- Show quick actions whenever playback is paused, keep them visible while paused, and allow D-pad Up to restore them.


## 1.0.1 - 2026-09-20

### Fixed

- Serve phone synchronization state from a thread-safe playback snapshot instead of accessing Media3 from the HTTP worker thread.
- Improve phone receiver startup, resynchronization, status feedback, and polling latency.
- Automatically hide quick actions during playback and restore them with the remote's Menu or Settings key.
- Reattach and configure the loudness effect from the active Media3 audio session when adjusting levels above 100%, with visible applied-state feedback.
- Require a non-empty signing certificate fingerprint before publishing a release.

## 1.0.0 - 2026-09-20

### Added

- Remote-first Media3 video and audio playback for Android TV and API 21+ devices.
- Local file, removable USB folder, network URL, and Android open-intent support.
- D-pad and media-key transport controls with visible focus treatment.
- Persistent resume position per document.
- Audio booster from 0% to 400% with speaker-safety warning.
- Tokenized local-network phone audio receiver with QR onboarding and playback synchronization.
- Reproducible CI, minimum-API launch gate, signed releases, checksums, and certificate reporting.
