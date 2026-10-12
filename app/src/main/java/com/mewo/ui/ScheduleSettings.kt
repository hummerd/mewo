package com.mewo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mewo.domain.LearningSchedule
import com.mewo.domain.LearningWindow
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

internal data class WindowDraft(
    val dayMask: Int,
    val start: String,
    val end: String,
) {
    constructor(
        window: LearningWindow,
    ) : this(window.dayMask, formatTime(window.startMinute), formatTime(window.endMinute))

    fun toWindow(label: String): LearningWindow =
        LearningWindow(
            LearningWindow.daysFromMask(dayMask),
            parseTime(start, label),
            parseTime(end, label),
        )

    companion object {
        private val timePattern = Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]|24:00")

        private fun formatTime(minute: Int): String =
            String.format(
                Locale.ROOT,
                "%02d:%02d",
                minute / LearningWindow.MINUTES_PER_HOUR,
                minute % LearningWindow.MINUTES_PER_HOUR,
            )

        private fun parseTime(
            value: String,
            label: String,
        ): Int {
            require(timePattern.matches(value.trim())) { "$label: enter times as HH:mm, for example 08:00." }
            val parts = value.trim().split(':')
            return parts[0].toInt() * LearningWindow.MINUTES_PER_HOUR + parts[1].toInt()
        }
    }
}

internal data class ScheduleDraft(
    val working: WindowDraft,
    val weekend: WindowDraft,
) {
    constructor(schedule: LearningSchedule) : this(WindowDraft(schedule.workingDays), WindowDraft(schedule.weekend))

    fun toSchedule(): LearningSchedule = LearningSchedule(working.toWindow("Working days"), weekend.toWindow("Weekend"))

    companion object {
        val saver =
            listSaver<ScheduleDraft, Any>(
                save = {
                    listOf(
                        it.working.dayMask,
                        it.working.start,
                        it.working.end,
                        it.weekend.dayMask,
                        it.weekend.start,
                        it.weekend.end,
                    )
                },
                restore = {
                    ScheduleDraft(
                        WindowDraft(it[0] as Int, it[1] as String, it[2] as String),
                        WindowDraft(it[3] as Int, it[4] as String, it[5] as String),
                    )
                },
            )
    }
}

@Composable
internal fun ScheduleSettings(
    draft: ScheduleDraft,
    enabled: Boolean,
    onChange: (ScheduleDraft) -> Unit,
) {
    Text("Learning schedule", style = MaterialTheme.typography.titleLarge)
    Text(
        "Times follow your phone’s local time. Unselected days have no reminders. " +
            "For overnight learning, select the day the window starts. The end time is excluded.",
        style = MaterialTheme.typography.bodySmall,
    )
    WindowSettings("Working days", draft.working, enabled) { onChange(draft.copy(working = it)) }
    WindowSettings("Weekend", draft.weekend, enabled) { onChange(draft.copy(weekend = it)) }
}

@Composable
private fun WindowSettings(
    label: String,
    draft: WindowDraft,
    enabled: Boolean,
    onChange: (WindowDraft) -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayOfWeek.entries.forEach { day ->
                    val bit = 1 shl (day.value - 1)
                    FilterChip(
                        selected = draft.dayMask and bit != 0,
                        onClick = { onChange(draft.copy(dayMask = draft.dayMask xor bit)) },
                        enabled = enabled,
                        label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)) },
                        modifier =
                            Modifier.semantics {
                                contentDescription = "$label ${day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}"
                            },
                    )
                }
            }
            ScheduleTimeField("$label start", draft.start, enabled) { onChange(draft.copy(start = it)) }
            ScheduleTimeField("$label end", draft.end, enabled) { onChange(draft.copy(end = it)) }
            Text(
                if (draft.dayMask ==
                    0
                ) {
                    "This schedule is off. Select days to enable it."
                } else {
                    "All day: 00:00–24:00. Overnight example: 22:00–06:00."
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ScheduleTimeField(
    label: String,
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        supportingText = { Text("24-hour time (HH:mm)") },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}
