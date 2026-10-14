# MEWO project

## Spec rules

Spec should not contain all details of implementation. It is not a change log.
But is should contain architectural decisions, descriptions of app behaviour and
algorithms, design concepts.

## Reminder algorithm

Notification should always appear based on schedule.

We select N (N should be configurable, ~10 or so) words from our dictionary and
start to send notifications for them. Selected words should appear frequently
and with every day less and less frequent (with exponential fade out), and after
5 days of showing word it should fall into "not ot forget category".

As frequency of current word set falls we introduce new words.

Also we must show words from "not ot forget category" from time to time.

Reminder timing is approximate. The minimum configurable notification interval is
15 minutes. Android background scheduling may delay notifications beyond the
configured interval; these delays are acceptable. Exact delivery times are not
required.

## Implementation

Native Android app using Kotlin, Jetpack Compose/Material 3, ViewModel and Flow,
Room for progress, Preferences DataStore for settings, and WorkManager for
approximate periodic notifications. All features work offline; no backend.
Support Android 8.0 (API 26) and newer. Request notification permission on Android
13 and newer; explain blocked notifications and offer system notification settings.
Use a single notification channel and one replaceable reminder notification, with
an expanded view showing all included words. Tapping it opens the word list.
Reminder text uses bold Hebrew, regular English meaning, and italic transliteration.
In the expanded view, each word's transliteration appears on the next line, with
a blank line between words. Preserve Hebrew right-to-left text direction.

We have 3 priorities for words - 100, 300 and 1000. These are 100 most common words,
next 300 most common words and then 600  most common words and phrases. Algorithm
should show first words with lowest priority moving to higher priorities.

Male and female forms for words should be treated as one record, showing male form
and modification to make it female form.

Notification should be shown only during learning schedule. User should choose
weekdays and hours during which notifications should appear. We should have 2
schedules for working days and weekend.

## UI

### Main activity

UI Must show list of words and how many times it was shown.
Show Hebrew with right-to-left text, English meaning, transliteration, and learning
status (new, learning, paused, review). Include notification permission status and
navigation to settings. Support light/dark themes and scrollable screens.

### Settings

Settings must have:

- How many words we rotating
- How frequently we show words in notifications
- How many words we show in notification
- Enable/pause reminders
- Notification sound (opens Android's reminder channel settings)

Validate all settings before saving and explain that delivery may be delayed.
