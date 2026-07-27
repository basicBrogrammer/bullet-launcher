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

### Visual demos without a device (screenshot harness)

Because the emulator can't boot here, use the JVM screenshot harness to produce visual proof of UI changes for PRs. It renders the real app layouts with Robolectric native graphics (no device/emulator needed):

- Command: `./gradlew :app:renderDemoScreens` → writes PNGs to `app/build/demo-screenshots/`. (`build/` is git-ignored; copy the PNGs to `/opt/cursor/artifacts/` to attach them.)
- Harness code lives in `app/src/test/java/app/olauncher/demo/` (`ScreenshotDemoTest.kt` + `DemoHostActivity.kt`). It's `testImplementation`-only (Robolectric + androidx.test) and does **not** affect the app APK.
- To demo a specific change, add/adjust a render case for the affected screen (inflate its layout, seed sample data, capture) and re-run. Stitch PNGs into a short MP4 with `ffmpeg` (preinstalled) if a video is preferred.
- Caveat: renders static layouts with seeded data, not live device behavior (e.g. `TextClock` doesn't tick; installed-app lists are faked). It's for visual UI proof, not end-to-end runtime testing.
