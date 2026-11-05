package ru.funpaymobile.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Хранит настройки в SharedPreferences, поэтому они сохраняются между запусками. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        AppSettings(
            hideBalance = prefs.getBoolean(KEY_HIDE_BALANCE, false),
            animations = prefs.getBoolean(KEY_ANIMATIONS, true)
        )
    )
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    fun setHideBalance(value: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_BALANCE, value).apply()
        _state.value = _state.value.copy(hideBalance = value)
    }

    fun setAnimations(value: Boolean) {
        prefs.edit().putBoolean(KEY_ANIMATIONS, value).apply()
        _state.value = _state.value.copy(animations = value)
    }

    private companion object {
        const val KEY_HIDE_BALANCE = "hide_balance"
        const val KEY_ANIMATIONS = "animations"
    }
}
