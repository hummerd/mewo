package com.mewo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.mewo.R
import com.mewo.data.WordEntity
import com.mewo.domain.LearningStatus
import com.mewo.domain.ReminderEngine
import com.mewo.domain.ReminderSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MewoScreen(
    state: MewoState,
    notificationsAllowed: Boolean,
    onEnableNotifications: () -> Unit,
    onNotificationSound: () -> Unit,
    onSave: (ReminderSettings, (String?) -> Unit) -> Unit,
    onRetry: () -> Unit,
    onAbout: () -> Unit,
    onUpdateWord: (String, Boolean, (String?) -> Unit) -> Unit,
) {
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(settingsOpen) { settingsOpen = false }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (settingsOpen) "Settings" else "MEWO · Hebrew") },
                navigationIcon = {
                    if (settingsOpen) TextButton(onClick = { settingsOpen = false }) { Text("Back") }
                },
                actions = {
                    TextButton(onClick = onAbout) { Text(stringResource(R.string.about_app)) }
                    if (!settingsOpen && !state.loading && state.error == null) {
                        TextButton(onClick = { settingsOpen = true }) { Text("Settings") }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            state.error != null ->
                Column(Modifier.padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(state.error, color = MaterialTheme.colorScheme.error)
                    Button(onClick = onRetry) { Text("Retry") }
                }
            settingsOpen ->
                SettingsScreen(state, Modifier.padding(padding), onSave, onNotificationSound) {
                    settingsOpen =
                        false
                }
            else ->
                WordList(
                    state,
                    notificationsAllowed,
                    onEnableNotifications,
                    Modifier.padding(padding),
                    onUpdateWord,
                )
        }
    }
}

@Composable
private fun WordList(
    state: MewoState,
    notificationsAllowed: Boolean,
    onEnableNotifications: () -> Unit,
    modifier: Modifier,
    onUpdateWord: (String, Boolean, (String?) -> Unit) -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    state.words.find { it.id == selectedId }?.let { word ->
        WordControlsDialog(word, onUpdateWord) { selectedId = null }
    }
    val progress = state.words.map { it.progress() }
    val activeIds = ReminderEngine.activeIds(progress, state.settings.rotationSize)
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("A little Hebrew, throughout your day.", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text("${state.words.size} words and phrases · ${state.words.sumOf { it.shownCount.toLong() }} appearances")
            Text(
                if (state.settings.enabled) {
                    "Reminders about every ${state.settings.intervalMinutes} minutes during your learning schedule"
                } else {
                    "Reminders paused"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!notificationsAllowed) {
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Allow word reminders", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Notifications are blocked. Allow them to receive Hebrew reminders. " +
                                "Your progress stays saved.",
                        )
                        Button(onClick = onEnableNotifications) { Text("Enable notifications") }
                    }
                }
            }
        }
        item {
            Text("Your dictionary", style = MaterialTheme.typography.titleMedium)
            Text("Tap a word to reset its counter or mark it as known.", style = MaterialTheme.typography.bodySmall)
        }
        items(
            state.words,
            key = { it.id },
        ) { word ->
            WordCard(word, ReminderEngine.status(word.progress(), activeIds)) { selectedId = word.id }
        }
        item {
            Text(
                "Appearances count notifications posted, even if you have not read them.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun WordCard(
    word: WordEntity,
    status: LearningStatus,
    onClick: () -> Unit,
) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                word.hebrew,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineSmall.copy(textDirection = TextDirection.Rtl),
                textAlign = TextAlign.End,
            )
            Text(word.english, style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr))
            Text(
                word.transliteration,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr),
            )
            FeminineWord(word)
            Text("Priority ${word.priority}", style = MaterialTheme.typography.labelSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    when (status) {
                        LearningStatus.NEW -> "New"
                        LearningStatus.LEARNING -> "Learning"
                        LearningStatus.PAUSED -> "Paused"
                        LearningStatus.REVIEW -> "Not to forget"
                        LearningStatus.KNOWN -> "Known"
                    },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text("Shown ${word.shownCount} times", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun FeminineWord(word: WordEntity) {
    word.feminineDescription()?.let { description ->
        Text(description, style = MaterialTheme.typography.bodyMedium)
        Text(
            word.feminineTransliteration.orEmpty(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr),
        )
    }
}

@Composable
private fun SettingsScreen(
    state: MewoState,
    modifier: Modifier,
    onSave: (ReminderSettings, (String?) -> Unit) -> Unit,
    onNotificationSound: () -> Unit,
    onSaved: () -> Unit,
) {
    var rotation by rememberSaveable { mutableStateOf(state.settings.rotationSize.toString()) }
    var interval by rememberSaveable { mutableStateOf(state.settings.intervalMinutes.toString()) }
    var batch by rememberSaveable { mutableStateOf(state.settings.wordsPerNotification.toString()) }
    var enabled by rememberSaveable { mutableStateOf(state.settings.enabled) }
    var schedule by rememberSaveable(stateSaver = ScheduleDraft.saver) {
        mutableStateOf(ScheduleDraft(state.settings.schedule))
    }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RemindersSwitch(enabled, !saving) { enabled = it }
        NumberSetting("Starting words in rotation", rotation, "1–${state.words.size}", !saving) {
            rotation = it
            error = null
        }
        NumberSetting("Notification interval (minutes)", interval, "15–1440 minutes", !saving) {
            interval = it
            error = null
        }
        NumberSetting("Words per notification", batch, "1–rotation size", !saving) {
            batch = it
            error = null
        }
        ScheduleSettings(schedule, !saving) {
            schedule = it
            error = null
        }
        SettingsHelp(onNotificationSound, !saving)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        SettingsSaveButton(
            saving = saving,
            buildSettings = {
                ReminderSettings(
                    rotation.settingNumber(),
                    interval.settingNumber(),
                    batch.settingNumber(),
                    enabled,
                    schedule.toSchedule(),
                ).also { it.validate(state.words.size) }
            },
            onSaving = { saving = true },
            onSave = onSave,
            onResult = {
                saving = false
                error = it
                if (it == null) onSaved()
            },
        )
    }
}

private fun String.settingNumber(): Int = requireNotNull(toIntOrNull()) { "Enter a whole number in each number field." }

@Composable
private fun RemindersSwitch(
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text("Reminders enabled", style = MaterialTheme.typography.titleMedium)
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun SettingsSaveButton(
    saving: Boolean,
    buildSettings: () -> ReminderSettings,
    onSaving: () -> Unit,
    onSave: (ReminderSettings, (String?) -> Unit) -> Unit,
    onResult: (String?) -> Unit,
) {
    Button(enabled = !saving, modifier = Modifier.fillMaxWidth(), onClick = {
        val validated = runCatching(buildSettings)
        if (validated.isFailure) {
            onResult(validated.exceptionOrNull()?.message)
        } else {
            onSaving()
            onSave(validated.getOrThrow(), onResult)
        }
    }) { Text(if (saving) "Saving…" else "Save settings") }
}

@Composable
private fun NumberSetting(
    label: String,
    value: String,
    range: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        supportingText = { Text(range) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SettingsHelp(
    onNotificationSound: () -> Unit,
    enabled: Boolean,
) {
    OutlinedButton(onClick = onNotificationSound, enabled = enabled) { Text("Notification sound") }
    Text(
        "Choose a reminder sound or silence reminders in Android settings. Sound changes apply immediately.",
        style = MaterialTheme.typography.bodySmall,
    )
    Text(
        "Delivery times are approximate and Android may delay reminders. " +
            "Learning frequency halves each day. New words fill the freed capacity; after five days, " +
            "words move to occasional review. Reminders continue at your chosen interval.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        "Changing settings preserves your progress and existing review times.",
        style = MaterialTheme.typography.bodySmall,
    )
}
