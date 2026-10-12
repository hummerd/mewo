package com.mewo.reminders

import android.content.Context
import android.text.Spanned
import android.widget.RemoteViews
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.text.italic
import com.mewo.R
import com.mewo.data.WordEntity

/** Custom RemoteViews keep inline styles out of Android's standard text-template normalization. */
internal class ReminderNotificationLayout(
    private val context: Context,
) {
    fun compact(words: List<WordEntity>): RemoteViews =
        RemoteViews(context.packageName, R.layout.notification_compact).apply {
            setTextViewText(R.id.reminder_text, line(words.first()))
        }

    fun expanded(words: List<WordEntity>): RemoteViews =
        RemoteViews(context.packageName, R.layout.notification_expanded).apply {
            setTextViewText(
                R.id.reminder_text,
                buildSpannedString {
                    words.forEachIndexed { index, word ->
                        if (index > 0) append("\n\n")
                        append(line(word))
                        word.feminineHebrew?.let { hebrew ->
                            append("\nFeminine: ")
                            append(line(hebrew, word.feminineTransliteration.orEmpty(), word.english))
                            append(" (${word.feminineModification})")
                        }
                    }
                },
            )
        }

    fun line(word: WordEntity): Spanned = line(word.hebrew, word.transliteration, word.english)

    private fun line(
        hebrew: String,
        transcription: String,
        translation: String,
    ): Spanned =
        buildSpannedString {
            // Isolate each direction so brackets and spaces cannot reorder the three fields.
            append("\u2067")
            bold { append(hebrew) }
            append("\u2069 \u2066")
            italic { append("[$transcription]") }
            append("\u2069 \u2066$translation\u2069")
        }
}
