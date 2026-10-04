# Privacy Policy — Bullet Launcher

**Last updated:** 9 August 2026  
**Package:** `app.bulletlauncher`  
**Contact:** open an issue at the [Bullet Launcher GitHub repository](https://github.com/basicBrogrammer/bullet-launcher)

Bullet Launcher is a local-first Android home-screen launcher. This policy describes what the app accesses and what it does **not** do.

## Summary

- No ads.
- No analytics or crash SDK in the current release (unless you later enable one).
- No account, and no data sold to third parties.
- Journal entries are stored on your device.
- Optional features may use Calendar or Location only when you use them.

## Data stored on your device

Bullet Launcher stores on-device preferences and journal content, including:

- Bullet journal entries (tasks, events, notes), tags, and schedule keys
- Home-app dock assignments and launcher settings (theme, gestures, etc.)
- Optional links between event bullets and calendar event IDs

This data stays on your device unless you back it up through Android backup / your own export method.

## Permissions and why they exist

| Permission / access | Purpose |
|---------------------|---------|
| Home / launcher role | Act as your default home screen |
| Query installed apps | Show and launch apps in the dock and drawer |
| Calendar (read/write) | Optional two-way sync for ○ Event bullets |
| Coarse/fine location | Optional weather in the home header |
| Wallpaper | Optional daily wallpaper feature |
| Usage access | Optional screen-time display (only if you grant it) |
| Accessibility (optional) | Optional double-tap to lock; disabled unless you turn it on |
| Internet | Optional wallpaper download / weather lookup |

Calendar and location are not required to use the core journal and launcher.

## Data shared with others

By default, Bullet Launcher does not upload your journal to our servers (there is no Bullet Launcher account backend).

If you enable calendar sync, event details you choose to sync are written to the calendars on your device / Google Calendar account managed by Google, under Google’s policies.

If weather or wallpaper features contact a network service, only the minimum request needed for that feature is sent (for example a location-based weather query). Journal contents are not included.

## Children

Bullet Launcher is a general-purpose launcher and is not directed at children under 13.

## Changes

We may update this policy as the app changes. The “Last updated” date at the top will change when we do.

## Open source

Bullet Launcher is open source under the GNU GPLv3. You can inspect the code to verify how data is handled.
