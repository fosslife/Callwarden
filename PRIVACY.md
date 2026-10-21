# Privacy Policy for Hush

Last updated: 21 October 2026

Hush is an offline call blocker for Android, published by FossLife. This policy describes what the
app does with your data. The short version: nothing leaves your phone, because the app has no way
to send it.

## No data is collected, transmitted or shared

Hush has **no internet access**. Its Android manifest explicitly removes the `INTERNET` and
`ACCESS_NETWORK_STATE` permissions, so the operating system will refuse any attempt by the app to
open a network connection. There is no server, no account, no analytics, no advertising SDK, no
crash reporting and no caller-ID or crowd-sourced spam lookup.

No personal data is collected by the developer. No data is sold or shared with third parties.

## What stays on your device

The app stores the following in its private app storage, readable only by the app itself:

- **Your blocking rules** — the phone numbers and patterns you type in.
- **Screened-call history** — the number, time and outcome of recent incoming calls that Android
  handed to the app for screening, kept so you can turn a caller into a rule. It is capped at the
  most recent 500 entries. You can clear it, or turn off logging entirely, in Settings.
- **Your settings** — your country calling code and the catch-all toggles.

This data is excluded from Android cloud backup (`allowBackup="false"`), so it is not copied to
Google's servers. Uninstalling the app deletes all of it. You can export your rules to a JSON file
yourself, to a location you choose; nothing is uploaded as part of that.

## Permissions and why they are needed

- **Call screening role** (granted by you when you set Hush as your call-screening app) — Android
  asks the app about each incoming call from a number that is not in your contacts, and the app
  answers using only your local rules. Call contents are never accessed; the app receives the
  number and returns an allow/block/silence decision.
- **Contacts (read and write)** — used only by the Contacts tab, and only after you grant it at
  runtime. Reading lists your saved contacts so you can pick one; writing sets Android's built-in
  "send to voicemail" flag on the contacts you choose. Your contacts are never copied out of the
  Contacts provider, stored in the app, or transmitted anywhere.

Hush does not request call log, SMS, microphone, location or camera permissions.

## Children

The app is not directed at children and collects no data from anyone.

## Changes

Any change to this policy will be published in this file in the app's public source repository, with
an updated date above.

## Contact

Questions about this policy: open an issue at https://github.com/fosslife/hush/issues

The complete source code is public and auditable, so every claim above can be verified:
https://github.com/fosslife/hush
