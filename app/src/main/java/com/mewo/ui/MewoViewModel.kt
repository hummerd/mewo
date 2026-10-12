package com.mewo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mewo.data.SettingsStore
import com.mewo.data.WordEntity
import com.mewo.data.WordRepository
import com.mewo.domain.ReminderSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MewoState(
    val words: List<WordEntity> = emptyList(),
    val settings: ReminderSettings = ReminderSettings(),
    val loading: Boolean = true,
    val error: String? = null,
)

class MewoViewModel(
    private val repository: WordRepository,
    private val store: SettingsStore,
) : ViewModel() {
    private val loadError = MutableStateFlow<String?>(null)
    private val ready = MutableStateFlow(false)
    val state =
        combine(repository.words, store.settings, ready, loadError) { words, settings, ready, error ->
            MewoState(words, settings, !ready && error == null, error)
        }.catch { emit(MewoState(loading = false, error = "Could not read local data. Restart MEWO to retry.")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MewoState())

    init {
        load()
    }

    fun load() {
        loadError.value = null
        viewModelScope.launch {
            try {
                repository.initialize()
                ready.value = true
            } catch (
                error: CancellationException,
            ) {
                throw error
            } catch (_: Exception) {
                loadError.value = "Could not load the dictionary. Please retry."
            }
        }
    }

    fun save(
        settings: ReminderSettings,
        onResult: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                store.save(settings, state.value.words.size)
                onResult(null)
            } catch (
                error: CancellationException,
            ) {
                throw error
            } catch (
                error: IllegalArgumentException,
            ) {
                onResult(error.message)
            } catch (_: Exception) {
                onResult("Could not save settings. Please retry.")
            }
        }
    }
}
