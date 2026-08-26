# Play Console — Data safety draft (copy/paste)

Use this as a starting point in Play Console → App content → Data safety.
Adjust if you add Crashlytics, Sentry, ads, or accounts later.

## Overview answers

- **Does your app collect or share any of the required user data types?**  
  Yes — but mostly processed on-device / optional features. Be accurate in the console forms.
- **Is all user data encrypted in transit?**  
  Yes for any HTTPS network calls (weather / wallpaper). Local journal is on-device.
- **Do you provide a way for users to request data deletion?**  
  Yes — uninstalling the app removes on-device journal/prefs. Calendar events created in Google Calendar remain under the user’s calendar account.
- **Privacy policy URL:** host `docs/privacy-policy.md` (GitHub Pages, raw URL, or your site) and paste that URL.

## Data types to declare (typical for current app)

| Data type | Collected? | Shared? | Notes |
|-----------|------------|---------|-------|
| App activity / installed apps | Yes (on device) | No | Needed for launcher / drawer |
| Calendar events | Optional | Shared only with the user’s calendar provider when sync is used | User-initiated event sync |
| Approximate/precise location | Optional | May be sent to weather provider if weather is enabled | Not required for core use |
| Photos/videos | No | No | |
| Personal info (name, email) | No | No | No account |
| Financial info | No | No | |
| Health | No | No | |
| Messages | No | No | |
| Files / docs | No | No | Journal is app-private storage |
| Device IDs / advertising ID | No | No | Unless you add analytics later |
| Crash logs / analytics | No | No | Manual diagnosis for now |

## Security practices

- Data is encrypted in transit (HTTPS) when network features are used.
- Users can delete on-device data by clearing app storage or uninstalling.
- Optional Accessibility service (if enabled by the user) is only for double-tap lock and does not leave the device.

## After you add crash reporting

If you later add Crashlytics/Sentry, update Data safety to include crash logs / diagnostics and update the privacy policy.
