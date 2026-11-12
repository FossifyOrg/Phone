# Fossify Phone: audit and fix report

**Date:** 2026-11-12
**Base:** FossifyOrg/Phone `main` @ `25804aa` (2026-11-03), stable release 1.11.1 (versionCode 22)
**Fixes:** branch [`correcoes-chamadas`](https://github.com/ruipereira1/Phone/tree/correcoes-chamadas) on ruipereira1/Phone (18 commits, one per change)
**Test device:** Google Pixel 10a, Android 17 (API 37); AirPods; Toyota head unit with Android Auto

## Summary

The audit started from one user-facing problem: calls through Android Auto connected but had no audio in the car. The root cause was the app overriding the audio mode that Telecom owns. While investigating it, I went through the open bug reports and the call-handling code. The result is 13 fixes, 1 performance fix, 23 unit tests and dependency updates. Every commit builds and passes the project's CI checks (unit tests, detekt, Android lint).

The original Android Auto problem is fixed and was confirmed in the car.

## Method

1. Read the call-handling code: `CallService`, `CallManager`, `CallActivity`, `CallNotificationManager`, `CallContactHelper`, `RecentsHelper`, `ContactsFragment`.
2. Went through the 233 public issues (open and closed) and matched reports to code paths.
3. Classified each finding:
   - **confirmed in code:** the defect is visible in the source;
   - **probable:** the cause fits the reports but needs a device test;
   - **needs reproduction:** no cause found in the code.
4. Fixed only what had an identified cause, with one commit per fix.
5. Verified on a real device over `adb`, reading `logcat` and `dumpsys telecom | audio | power` during live calls.

## Fixes

| Commit | Issue(s) | Problem | Cause | Change |
|---|---|---|---|---|
| `d5c74dc` | Android Auto, related to #814 | Calls via car or Bluetooth connect without audio | `CallActivity` set `AudioManager.mode = MODE_IN_CALL` / `MODE_NORMAL`, fighting Telecom for the audio route | Stop touching `AudioManager.mode`. Once per call, move audio to Bluetooth if it is still on the earpiece; never override a route the user chose |
| `439e95a` | #914, #56, #302 | Mute turns itself off, or the button shows the wrong state | Mute applied via both `AudioManager.isMicrophoneMute` and `InCallService.setMuted()`; the button re-read `AudioManager` | Mute only through Telecom; the button reflects `CallAudioState.isMuted` |
| `af6b80d` | #903, #338, #826, #889 | Call lost after swiping the notification away; incoming call hidden by Do Not Disturb / Bedtime | Android 14+ lets users dismiss `setOngoing` notifications; no `Person` attached, so DND can't match the caller | Re-post from a `deleteIntent` during a call; `CallStyle.forIncomingCall` on Android 12+ when the full-screen intent is allowed (with fallback); attach the caller as a `Person` |
| `9d072ec` | #903, #105 | No way back to an ongoing call from the app | No in-app entry point | "Ongoing call" bar on the main screen |
| `f292765` | #676, #907 | No incoming call screen on Xiaomi / Redmi / POCO | MIUI/HyperOS also need "Show on lock screen" and "Pop-up windows while in background" | One-time explanation with a shortcut to the MIUI permission editor |
| `761fa7d` | #896, #374 | Proximity sensor stops after 60 min; tied to the call screen | Wake lock owned by `CallActivity` with a 60-minute timeout | `ProximitySensorHelper` driven by `CallService`; no timeout; kept on during call waiting |
| `1651579` | #906 | Speaker button reacts late | UI waited for Telecom's callback | Immediate UI feedback, re-synced from the real state after 2 s |
| `436fea0` | #921 | Contact search freezes with large address books | Filtering and normalization on the UI thread at every keystroke | Pre-normalized index, 150 ms debounce, background filtering; accent-insensitive both ways |
| `bde4d2a` | #180 | Tapping next to the hang-up button ends the call | Hidden accept button lets the decline button stretch over the whole row | Fixed-size buttons inside weighted holders |
| `a841623` | #761 | App stays in front after a call ends | `CallActivity` shared the task with `MainActivity`; `finish()` surfaced the main screen | Own `taskAffinity`; always `finishAndRemoveTask()` |
| `3175b15` | #873 | Network caller name (CNAP) not shown | `Call.Details.callerDisplayName` never used | Use it when presentation is allowed |
| `dc35402` | #74 | History shows a bare number for contacts with several numbers | Only `phoneNumbers.first()` compared | Compare all numbers (`phoneNumbersMatch()`, unit tested) |
| `a981996` | #865 | History ignores "Start name with surname" | System `CACHED_NAME` used as is | Prefer `contact.getNameToDisplay()` |
| `54c7a32` | #259, #679 | Call screen or notification slow or frozen with many contacts | `getCallContact()` loaded the entire address book on every call state change | One `PhoneLookup` query plus loading that single contact; also adds the missing callback for `sip:` / `voicemail:` handles |

**Supporting commits:**
- `f21558b`: unit tests. JUnit is a test-only dependency, so it needs maintainer approval.
- `c74d8dd`, `e8c7f52`: dependency bumps (AGP 9.5.1, Kotlin 2.4.21, libphonenumber 9.0.43, geocoder 3.43, kotlinx-serialization 1.13.0).
- `4c03d42`: `compileSdk 37`.

The supporting commits are not intended for upstream PRs without prior approval. The project's rules require it for build and dependency changes.

## Device test results (Pixel 10a, Android 17)

| Scenario | Result |
|---|---|
| Set as default dialer (after "Allow restricted settings") | OK |
| Outgoing and incoming calls | OK. The incoming call screen appeared on Android 17; see #854 |
| Speaker enabled while dialing stays on after answer (#575) | OK, not reproduced |
| Speaker toggle, 8 times | OK, Telecom applies each change immediately |
| Proximity: on at the ear, off on speaker, released at call end | OK |
| Mute held for about 2 minutes (#914) | OK. Every mute change in the Telecom log came from a user tap |
| AirPods ↔ speaker ↔ earpiece | OK, one tap each, under 1 s |
| #272 (Bluetooth switching needs 2–3 taps) | Not reproduced on this device. No change made |
| Tap beside the hang-up button (#180); return to previous app (#761) | OK, confirmed by the user |
| Android Auto in the car | **OK**, audio in the car, confirmed by the user |
| Crashes, ANRs, leaked wake locks or notifications | None |

**Before and after, from Telecom logs in the same car.** With 1.11.1, a single call moved from `BLUETOOTH_SCO` to `SPEAKER`, back to `BLUETOOTH_SCO`, then to `EARPIECE` (18 s with no audio in the car) and back again. The next call stayed on the phone speaker until hang-up. With the fixed build, audio stayed on the requested route.

## Compatibility

- **Supported range:** minSdk 26 (Android 8) to Android 17. `targetSdk` stays at 36 on purpose. Targeting 37 removes `ACCOUNT_NAME` / `ACCOUNT_TYPE` from the ContactsContract data view, and Fossify Commons 7.1.3 still reads them for contact sources.
- **16 KB page size:** the native libraries are aligned to 16 KB.
- **Lint:** no new errors. Deprecations reported against API 37 (`callAudioState`, `setAudioRoute`, `onCallAudioStateChanged`) predate this work. Migrating to the `CallEndpoint` API is left for a coordinated upstream change.

## Still open

| Item | Status |
|---|---|
| #746 / #319: block patterns | Probable cause in **Fossify Commons**: the pattern is converted to a regex and matched against the raw incoming number with no normalization, so `+33…` does not match `0…` |
| #772: ringtone plays twice with headphones | The app does not play the ringtone; Telecom / Bluetooth in-band ringing does |
| #456: calls auto-rejected | Intermittent; reports point to network or SIM factors. Not reproduced |
| #792: black screen (Motorola) | No cause found |
| #385: slow history on launch | Possible improvement through caching; already discussed upstream |
| #695: hidden-number calls missing from history | To test with a `#31#` call |
| #199 / #216 / #685: hold, swap, merge | To test with two real calls |

## Upstream status

Following the project's contribution rules (an issue must be labelled `help wanted` before a PR), comments with cause and fix were posted on:
- [#814](https://github.com/FossifyOrg/Phone/issues/814#issuecomment-6834548865);
- [#180](https://github.com/FossifyOrg/Phone/issues/180#issuecomment-6834549374);
- [#761](https://github.com/FossifyOrg/Phone/issues/761#issuecomment-6834549901).

Comments for the other issues are drafted. PRs will be opened per fix once maintainers approve.
