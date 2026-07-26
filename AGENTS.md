# Olauncher

Olauncher is a minimal Android home-screen launcher app (single Gradle module `:app`, Kotlin, AGP 8.9.1, Gradle wrapper 8.11.1). Build with the Gradle wrapper (`./gradlew`).

## Cursor Cloud specific instructions

- The Android SDK is preinstalled in the VM snapshot at `~/android-sdk` (cmdline-tools, `platform-tools`, `platforms;android-35`, `build-tools;35.0.0`). `local.properties` (git-ignored) is recreated by the update script and points `sdk.dir` there.
- JDK 21 is the system JDK; the module targets Java 17 source/target and builds fine on 21.
- Common commands (run from repo root):
  - Build (dev): `./gradlew assembleDebug` → outputs `app/build/outputs/apk/debug/app-debug.apk` (package `app.olauncher.debug`).
  - Unit tests: `./gradlew testDebugUnitTest` — note the repo currently has **no** unit/instrumented test sources, so this task reports `NO-SOURCE` and passes trivially.
  - Lint: `./gradlew lintDebug` — this currently **fails** on ~18 pre-existing lint errors (e.g. `NewApi` in `HomeFragment.kt`). These are pre-existing code issues, not an environment problem; the lint tooling itself works.
- Running the app: this is an Android launcher, so a running "app" requires an emulator or physical device. The emulator IS preinstalled in the snapshot (`emulator` package + `system-images;android-35;google_apis;x86_64` + an AVD named `test35`), but it **cannot boot** in the Cloud VM: this is a Firecracker microVM with no `/dev/kvm` and no loadable kernel modules (no `/lib/modules`, no `modprobe`), so KVM/hardware acceleration is unavailable. The x86_64 Android Emulator strictly requires KVM, and the modern emulator no longer supports ARM images on x86 hosts, so there is no software-only fallback. Verify changes via `./gradlew assembleDebug` plus APK inspection (`aapt dump badging`/`apkanalyzer`), or run the emulator / install the APK on a machine that has KVM or a real device.
