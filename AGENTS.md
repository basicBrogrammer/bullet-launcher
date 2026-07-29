# Bullet Launcher

Bullet Launcher is a minimal Android home-screen launcher (Kotlin, single Gradle module `:app`, AGP 8.9.1, Gradle wrapper 8.11.1). It started from [Olauncher](https://github.com/tanujnotes/Olauncher) and is being reshaped around a **bullet-journal home screen** plus a **home-apps bottom sheet**.

Build with the Gradle wrapper (`./gradlew`).

## Product shape (current)

- **Display name / package:** `Bullet Launcher` / `app.bulletlauncher` (debug: `app.bulletlauncher.debug`). Kotlin package paths remain `app.olauncher.*`.
- **Home:** a ViewPager2 bullet journal (Monthly | Daily | Future) with rapid-logging bullets (task / event / note). Tap a task to toggle complete; **long-press opens Edit entry** (Save / Delete), not immediate delete. FAB `+` adds a bullet. Event bullets can **two-way sync** with the device / Google Calendar (`CalendarSyncHelper`).
- **Home apps:** a bottom sheet dock — 5 columns × up to 3 rows (collapsed = 1 row). Slot 13 is the **app drawer button**.
- **Gestures (important):**
  - **Swipe up** → expand the home apps sheet only (never opens the full drawer).
  - **Full app drawer** → open only by tapping the drawer button in the dock (overlay over home so dock stays droppable).
  - **Swipe down** → closes the open drawer, or collapses the expanded sheet; notification/search swipe-down only when both are closed.
  - **Back** closes the drawer; **Home** closes the drawer and returns to the Daily log.
- **Drawer chrome:** opaque surface — paper (`drawerBackgroundLight` / `#F3EEE6`) in light mode, system grey (`drawerBackgroundDark` / `#2C2C2E`) in dark mode via `?attr/drawerBackgroundColor`.
- Sideload debug APK (when present on a branch): `artifacts/bullet-launcher-debug.apk`.

## Cursor Cloud specific instructions

- The Android SDK is preinstalled in the VM snapshot at `~/android-sdk` (cmdline-tools, `platform-tools`, `platforms;android-35`, `build-tools;35.0.0`). `local.properties` (git-ignored) is recreated by the update script and points `sdk.dir` there. If missing, create it with `sdk.dir=$HOME/android-sdk`.
- JDK 21 is the system JDK; the module targets Java 17 source/target and builds fine on 21.
- Common commands (run from repo root):
  - Build (dev): `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk` (package `app.bulletlauncher.debug`, label **Bullet Launcher**).
  - Unit tests: `./gradlew testDebugUnitTest` — functional unit tests are sparse; `app/src/test/java/app/olauncher/demo/` holds the Robolectric screenshot harness (also run via `renderDemoScreens`).
  - Lint: `./gradlew lintDebug` — currently **fails** on pre-existing lint errors (e.g. `NewApi` in `HomeFragment.kt`). Lint tooling works; treat failures as known code debt unless the task is to fix them.
- Running the app: this is an Android launcher, so a running "app" requires an emulator or physical device. The emulator IS preinstalled in the snapshot (`emulator` package + `system-images;android-35;google_apis;x86_64` + an AVD named `test35`), but it **cannot boot** in the Cloud VM: this is a Firecracker microVM with no `/dev/kvm` and no loadable kernel modules (no `/lib/modules`, no `modprobe`), so KVM/hardware acceleration is unavailable. The x86_64 Android Emulator strictly requires KVM, and the modern emulator no longer supports ARM images on x86 hosts, so there is no software-only fallback. Verify changes via `./gradlew assembleDebug` plus APK inspection (`aapt dump badging`/`apkanalyzer`), or run the emulator / install the APK on a machine that has KVM or a real device.
- For downloadable sideload builds on a PR branch, copy the APK to `artifacts/bullet-launcher-debug.apk`, commit it, and link the GitHub raw URL in the PR (Cursor `/opt/cursor/artifacts/` links are not reliably downloadable outside the agent UI).

### Visual demos without a device (screenshot harness)

Because the emulator can't boot here, use the JVM screenshot harness to produce visual proof of UI changes for PRs. It renders the real app layouts with Robolectric native graphics (no device/emulator needed):

- Command: `./gradlew :app:renderDemoScreens` → writes PNGs to `app/build/demo-screenshots/`. (`build/` is git-ignored; copy the PNGs to `/opt/cursor/artifacts/` to attach them in the PR body.)
- Harness code lives in `app/src/test/java/app/olauncher/demo/` (`ScreenshotDemoTest.kt` + `DemoHostActivity.kt`). It's `testImplementation`-only (Robolectric + androidx.test) and does **not** affect the app APK.
- To demo a specific change, add/adjust a render case for the affected screen (inflate its layout, seed sample data, capture) and re-run. Stitch PNGs into a short MP4 with `ffmpeg` (preinstalled) if a video is preferred.
- Caveat: renders static layouts with seeded data, not live device behavior (e.g. `TextClock` doesn't tick; installed-app lists are faked). It's for visual UI proof, not end-to-end runtime testing.

### Key code map

| Area | Primary files |
|------|----------------|
| Home / gestures / drawer overlay | `ui/HomeFragment.kt`, `res/layout/fragment_home.xml` |
| Journal pager / bullets | `ui/JournalPagerAdapter.kt`, `ui/JournalBulletAdapter.kt`, `data/JournalStore.kt`, `data/JournalModels.kt` |
| Add / edit bullet dialog | `ui/AddBulletDialog.kt`, `res/layout/dialog_add_bullet.xml` |
| App drawer | `ui/AppDrawerFragment.kt`, `res/layout/fragment_app_drawer.xml` |
| Calendar sync | `helper/CalendarSyncHelper.kt` |
| Theme / drawer colors | `res/values/styles.xml`, `values-night/styles.xml`, `colors.xml`, `attrs.xml` (`drawerBackgroundColor`) |
