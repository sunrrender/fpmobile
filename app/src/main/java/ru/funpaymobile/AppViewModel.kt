package ru.funpaymobile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.funpaymobile.data.DemoRepository
import ru.funpaymobile.data.FunPayRepository
import ru.funpaymobile.data.Message
import ru.funpaymobile.data.SettingsStore
import ru.funpaymobile.data.Snapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface UiState {
    data object Loading : UiState
    data class Error(val message: String) : UiState
    data class Ready(val data: Snapshot) : UiState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    // Для подключения реальной интеграции замените реализацию здесь.
    private val repository: FunPayRepository = DemoRepository()
    private val store = SettingsStore(app)

    val settings = store.state

    private val _ui = MutableStateFlow<UiState>(UiState.Loading)
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _localMessages = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    val localMessages: StateFlow<Map<String, List<Message>>> = _localMessages.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _ui.value = UiState.Loading
            try {
                _ui.value = UiState.Ready(repository.load())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.value = UiState.Error(e.message ?: "Не удалось загрузить данные")
            }
        }
    }

    /** Сообщение сохраняется только в памяти приложения и НЕ отправляется в FunPay. */
    fun addLocalMessage(chatId: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val current = _localMessages.value[chatId].orEmpty()
        val msg = Message(System.nanoTime(), clean, fromMe = true, time = time, localOnly = true)
        _localMessages.value = _localMessages.value + (chatId to (current + msg))
    }

    fun setHideBalance(v: Boolean) = store.setHideBalance(v)
    fun setAnimations(v: Boolean) = store.setAnimations(v)
}
