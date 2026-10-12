# MEWO

An offline Android app for learning Hebrew through word-reminder notifications.
The source of product requirements is [SPEC.md](SPEC.md).

## Run

Open this directory in Android Studio and install Android SDK Platform 37.
The Gradle wrapper uses the JDK 21 toolchain configured in
`gradle/gradle-daemon-jvm.properties`. Android Studio can configure the SDK;
command-line builds need `ANDROID_HOME` pointing to your SDK or an untracked
`local.properties` containing `sdk.dir=/absolute/path/to/sdk`.

The Gradle wrapper is included. The first build needs internet access to download
Gradle and the pinned Android/Kotlin dependencies.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
Install it on an Android 8.0+ device or emulator:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open MEWO and tap **Enable notifications**. On Android 13+, approve the permission
prompt. If notification permission or the reminder channel is blocked, the button
opens the appropriate system settings. Reminders default to hourly; the first
notification is scheduled after one interval and Android may delay it.

Settings control rotation size, interval, words per notification, and pausing.
Changes take effect after **Save settings**. Notification appearance counts do
not measure whether a word has been read or learned. Notifications replace the
previous reminder; expand one to see every included word.

## Structure

- `domain/ReminderEngine.kt`: pure word selection and increasing review intervals.
- `data/`: Room dictionary/progress, JSON import, and Preferences DataStore settings.
- `reminders/`: unique periodic WorkManager job, notification channel and publisher.
- `ui/`: Compose dictionary and validated settings screens, backed by ViewModel/Flow.
- `app/src/main/assets/hebrew.json`: 1,000 Hebrew learning records with English
  meanings and transliterations, in priority tiers 100, 300, and 1000 containing
  100, 300, and 600 records. These are approximate learning priorities rather
  than measured frequency ranks. See [dictionary maintenance](tools/dictionary/README.md).

New words enter the rotation in priority order, then dictionary order within
each tier. Due reviews remain ordered by due time. Masculine/feminine forms share
one learning record; cards and notifications show the feminine form,
pronunciation, and modification alongside the masculine form. Database upgrades
and dictionary imports preserve progress, including merged legacy gender forms.

Words leave the learning rotation after four appearances, allowing new words to
enter. Introduced review words continue at increasing intervals, capped at seven
days. Due words are selected in due-time order, with dictionary order as the tie
breaker. A delayed worker posts at most one notification and schedules from its
actual run time. Blocked or unsuccessful posts preserve progress.

Room progress persists across app/device restarts; WorkManager persists scheduling
across ordinary process death and reboot. As with other Android apps, a user
force-stop suspends background work until the app is opened again. No account,
server, or runtime network connection is needed.

## Verification

Run all static checks with GNU Make and the Gradle wrapper:

```sh
make lint
```

This runs Android Lint for the debug variant, detekt on all app source sets
(including tests), and ktlint on Kotlin sources and Gradle Kotlin scripts.
Gradle continues running the other checks when one reports violations, and the
command exits with a failure if any check fails. The first run downloads the
pinned lint tools. This command checks code without automatically changing it.

Android Lint reports are in `app/build/reports/`, detekt reports in
`build/reports/detekt/`, and ktlint reports in `build/reports/ktlint/` and
`app/build/reports/ktlint/`.

The JVM suite covers initial selection, doubling/capping intervals, replacement
words, review after dictionary exhaustion, overdue ordering, rotation changes,
paused reminders, frequency changes, and input validation.

Database integration tests require a running emulator/device:

```sh
./gradlew :app:connectedDebugAndroidTest
```

Before release, also check on a device:

1. Allow notifications, choose a 15-minute interval, and verify Hebrew/English
   text in a notification, including its expanded view and tap-to-open behavior.
2. Deny permission or disable the channel; counts must remain unchanged. Return
   from system settings and verify the permission banner updates.
3. Save invalid/valid settings, pause/resume, and confirm that rotation changes
   preserve counts. Rotate the device while editing settings.
4. Reopen the app and reboot the device; progress/settings should remain saved
   and enabled reminders should continue. Check light/dark mode and large fonts.

The 20 JVM tests, static checks, and debug APK build have been verified. All six
database integration tests passed on the Android 17 (API 37) Pixel emulator,
including the version-1 upgrade, gender-record progress merge, and repeated
imports. Notification delivery and the runtime checks above should also be
verified on a physical device.
