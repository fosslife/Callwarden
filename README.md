# CallWarden

A small, open-source, offline call blocker for Android. You can block numbers by
**pattern**, so a company that rotates through `+91 80 4718 xxxx` can't reach you.

- **No internet permission.** The manifest strips `INTERNET`, so the app cannot send data anywhere.
- No accounts, analytics, ads, crowd-sourced spam lists or caller-ID lookups.
- Rules and history are stored in the app's private storage and excluded from Google backup.
  You can export rules to a JSON file yourself.

## Rule types

| Type | Example | Matches |
|---|---|---|
| Exact number | `+91 98765 43210` | That number, written either way (`098765 43210` too) |
| Starts with | `+91 80 4718` | Every number beginning with those digits |
| Pattern | `+91 80 47## 12##` | `#` or `?` = one digit, `*` = any run of digits |
| Regex | `\+91(80\|22)4718\d{4}` | A Java regex that must match the whole number |

Each rule does one of three things:
- **Block**: reject the call.
- **Silence**: let it ring with no sound, so it still shows up as a missed call.
- **Always allow**: an exception that beats every block rule. For example, block `+91 80 4718`
  but allow `+91 80 4718 0000` because it's your bank.

Set **your country calling code** in Settings. With it set, a rule written as `080 4718` also matches
a call from `+91 80 4718 …`, and the reverse.

The Rules tab has a **Test a number** box that shows which rule, if any, would catch a number.

## Writing a rule from a call you got

The **Blocked** tab lists every call CallWarden screened, including the ones that rang through.
Tap **Block…** on one and the rule editor opens with that number filled in — then you decide the
pattern: trim the last digits for a *Starts with* rule, or replace them with `#` for a *Pattern*
rule. CallWarden never guesses a pattern for you.

## How it works

- `ScreeningService` is an Android
  [`CallScreeningService`](https://developer.android.com/reference/android/telecom/CallScreeningService).
  After you make CallWarden the phone's call-screening app (the app asks on first launch), Android
  asks it about every incoming call. The decision comes only from your local rules.
- `core/Rules.kt` holds the matching engine. It's plain Kotlin with no Android dependencies, and
  it's covered by `app/src/test/.../RuleEngineTest.kt`.
- The **Contacts** tab turns on Android's built-in "send to voicemail" flag for saved contacts.
  This is needed because Android doesn't pass calls from saved contacts to screening apps.
- Settings has **Block everyone not in my contacts** for when you want the opposite default.
  "Always allow" rules still get through it.

## Limitations

- Needs Android 10 (API 29) or newer.
- Only one app can hold the call-screening role at a time. If you use Truecaller or a similar app,
  this replaces it.
- SMS blocking isn't included. Android only lets the default SMS app filter messages.
- Pattern rules don't apply to saved contacts (see the Contacts tab above).
- The call history only contains calls Android handed to the screening service, so calls from
  saved contacts never appear there.

## Build

You need JDK 17 and the Android SDK (Android Studio is the easiest way to get it).

```sh
./gradlew testDebugUnitTest assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/debug/app-debug.apk
```

The GitHub Actions workflow in `.github/workflows/build.yml` runs the tests and uploads a debug APK
on every push.

## License

CallWarden is free software: you can redistribute it and/or modify it under the terms of the
[GNU General Public License v3.0](LICENSE) as published by the Free Software Foundation.
