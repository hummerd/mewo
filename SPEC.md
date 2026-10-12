# MEWO project

## Spec rules

Spec should not contain all details of implementation. It is not a change log.
But is should contain architectural decisions, descriptions of app behaviour and
algorithms, design concepts.

## Reminder algorithm

We select N (N should be configurable, ~10 or so) words from our dictionary and start
to send notifications for them. First words should appear frequently and then less and
less frequent, and as frequency falls we introduce new words.

Reminder timing is approximate. The minimum configurable notification interval is
15 minutes. Android background scheduling may delay notifications beyond the
configured interval; these delays are acceptable. Exact delivery times are not
required.

### Selection and progression

- The bundled dictionary contains Hebrew, English meaning, and transliteration.
- Defaults: 10 rotating words, a 60-minute notification interval, and one word per
  notification. Rotation size is 1–dictionary size; words per notification is
  1–rotation size. The notification interval is 15–1440 minutes.
- Words start in dictionary order. Up to N words are in the learning rotation.
- Select introduced words whose next review is due, earliest due first, breaking
  ties by dictionary order. Each notification contains up to the configured count
  of distinct words. Do not send an empty notification or catch-up bursts.
- After a successful notification post, increment each included word's shown count
  and schedule its next review from the actual posting time. Its interval is the
  base notification interval multiplied by 2^(shown count - 1), capped at seven
  days. This measures notification appearances, not whether the user read them.
- After four appearances a word moves to review, freeing a learning slot for the
  next unseen word. Review words remain eligible alongside learning words. When
  the dictionary is exhausted, continue reviewing introduced words.
- Reducing rotation size pauses excess learning words in dictionary order without
  resetting their progress; increasing it resumes paused words before new words.
- Setting changes preserve progress and existing due times. New review intervals
  use the latest frequency. Changing frequency updates the background schedule.
- Reminders start enabled. Users can pause/resume reminders. Permission denial,
  disabled notifications, and paused reminders do not increment shown counts.
- Persist dictionary progress and settings locally across process death/reboots.

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
