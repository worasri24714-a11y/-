package app.copyruler.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.copyruler.data.AppSettings
import app.copyruler.data.SettingsRepository
import app.copyruler.data.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    /** null until DataStore has been read. */
    val settings: StateFlow<AppSettings?> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setVolumeKeys(enabled: Boolean) {
        viewModelScope.launch { repository.setVolumeKeys(enabled) }
    }

    fun setHaptics(enabled: Boolean) {
        viewModelScope.launch { repository.setHaptics(enabled) }
    }

    fun setFontSize(sizeSp: Int) {
        viewModelScope.launch { repository.setFontSize(sizeSp) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }
}
