# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```sh
./gradlew testDebugUnitTest assembleDebug      # the full check; APK at app/build/outputs/apk/debug/
./gradlew testDebugUnitTest --tests "com.fosslife.hush.core.RuleEngineTest.allowRuleBeatsBlockUnknown"
```

There is no lint step and no instrumented-test source set. JDK 17; the Android SDK lives at
`~/Android/Sdk`. CI (`.github/workflows/build.yml`) runs exactly the first command.

## Architecture

The whole app is ~1,400 lines across four packages, deliberately with no DI framework, no Room, no
networking library, and no serialization library. Keep it that way — see "Design constraints".

**`core/Rules.kt` is the heart and it is plain Kotlin with zero Android imports.** That is the single
most important structural rule in this repo: it exists so the matching logic runs under
`testDebugUnitTest` on the JVM with no Robolectric and no device. Anything decision-shaped belongs
here, not in the service.

Two entry points, both of which must agree:

- `RuleEngine.evaluate(number)` — rules only. Used by the Rules tab's "Test a number" box.
- `RuleEngine.screen(number, hidden, blockHidden, blockUnknown)` — rules **plus** the catch-all
  settings, returning the reason string shown in history. This is what `ScreeningService` calls.
  Precedence: a matching rule always wins over `blockUnknown`; within rules, ALLOW > BLOCK > SILENCE.

**Cross-format matching** is the subtle part. `PhoneNumbers.candidates()` expands an incoming number
into every form the user might have typed in a rule (`+918047181234`, `918047181234`, `8047181234`,
`08047181234`) using the configured home country code, and a rule matches if it matches *any* of
them. This is why a rule written `080 4718` catches a call from `+91 80 4718 …`. Changes here break
matching in ways that are invisible until a real call arrives — add a test.

**`service/ScreeningService.kt`** is intentionally dumb: resolve the number, call `screen()`, apply
the response, log. Android only hands it calls from numbers **not** in the user's contacts, which is
why saved contacts are blocked through `ContactsContract.SEND_TO_VOICEMAIL` (`data/DeviceContacts.kt`)
instead — a different mechanism for the same user-visible feature.

It logs **every** screened call, including ones that rang through (`LogEntry.action == null`). That
is not incidental: the Blocked tab is the only place the user can see a number worth turning into a
rule, and tapping **Block…** opens the shared `RuleDialog` prefilled with the number.

**`data/Store.kt`** is a process-wide singleton over `AtomicFile` + `SharedPreferences`, exposing
`StateFlow`s that the Compose screens collect. The service and the UI share the one instance.
`_log` is `by lazy` **on purpose** — `onScreenCall` has a deadline and must not read the 500-entry
history before deciding. Don't make it eager. Writes happen after `respondToCall`.

## Design constraints

These are product decisions, not preferences — don't "improve" past them:

- **Never auto-detect or suggest blocking patterns.** The user writes every pattern. Prefill a
  number, never infer a prefix or wildcard from call history. Explicitly requested by the owner.
- **No network, ever.** The manifest strips `INTERNET` and `ACCESS_NETWORK_STATE` with
  `tools:node="remove"` so no dependency can reintroduce them. Adding a library that needs network
  breaks the build, which is the intended behaviour.
- Distribution is Google Play **and** F-Droid, so a design has to clear Play policy. The app asks
  for no restricted permission (no `READ_CALL_LOG`, no SMS), which is what keeps it out of Play's
  declaration forms — adding one would mean a permissions review, so don't.
  `dependenciesInfo { includeInApk = false, includeInBundle = true }` keeps F-Droid reproducible
  while letting Play Console flag vulnerable SDKs.
- `PRIVACY.md` is the published privacy policy Play links to. If behaviour around data changes
  (new permission, anything stored, anything leaving the device), update it in the same commit.

## Release

```sh
./gradlew testDebugUnitTest bundleRelease     # AAB at app/build/outputs/bundle/release/app-release.aab
```

Upload keystore and its passwords live in `~/.gradle/gradle.properties` as `HUSH_UPLOAD_*`, never in
the repo. The file is `~/keys/callwarden-upload.jks` — the pre-rename name, deliberately left alone,
because Play ties the app to that key forever. Without those properties the release build comes out
unsigned instead of failing, which is what CI and F-Droid want. Bump `versionCode` for every upload.

## Testing against a real call

Unit tests cover matching. To exercise `ScreeningService` end-to-end, use an emulator — it can
simulate incoming calls:

```sh
emulator -avd <name> -no-window -no-snapshot-load -gpu swiftshader_indirect   # -no-window required: Qt/wayland plugin fails otherwise
adb shell cmd role add-role-holder android.app.role.CALL_SCREENING com.fosslife.hush
adb emu gsm call +918047181234     # then: adb emu gsm cancel +918047181234
adb shell dumpsys telecom | grep -E "callTerminationReason|state=RINGING"
adb shell run-as com.fosslife.hush cat files/log.json
```

`INCOMING_REJECTED` means blocked; `state=RINGING` means it got through. Seed state without touching
the UI via `run-as com.fosslife.hush` over `files/rules.json`, `files/log.json` and
`shared_prefs/settings.xml` (force-stop the app first so the prefs cache is reloaded).

**Xiaomi/HyperOS devices block `adb install` and `adb shell input`** (`INSTALL_FAILED_USER_RESTRICTED`,
`SecurityException: INJECT_EVENTS`) even with USB debugging on. `screencap` and `run-as` still work.
Push the APK to `/sdcard/Download/` for a manual install, and use the emulator for anything that
needs tapping.
