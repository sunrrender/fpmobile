package ru.funpaymobile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.funpaymobile.data.ChatPreview
import ru.funpaymobile.data.DemoRepository
import ru.funpaymobile.data.FunPayRepository
import ru.funpaymobile.data.LocalStore
import ru.funpaymobile.data.MarketOffer
import ru.funpaymobile.data.Message
import ru.funpaymobile.data.Order
import ru.funpaymobile.data.OrderKind
import ru.funpaymobile.data.OrderStatus
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
    private val local = LocalStore(app)
    private val saved = local.load()

    val settings = store.state

    private val _ui = MutableStateFlow<UiState>(UiState.Loading)
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _localMessages = MutableStateFlow<Map<String, List<Message>>>(saved.messages)
    val localMessages: StateFlow<Map<String, List<Message>>> = _localMessages.asStateFlow()

    private val _localOrders = MutableStateFlow<List<Order>>(saved.orders)
    val localOrders: StateFlow<List<Order>> = _localOrders.asStateFlow()

    private val _localChats = MutableStateFlow<List<ChatPreview>>(saved.chats)
    val localChats: StateFlow<List<ChatPreview>> = _localChats.asStateFlow()

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
        persist()
    }

    /**
     * Создаёт демонстрационный заказ-покупку. Заказ существует только в памяти
     * приложения: оплата не выполняется, продавцу ничего не отправляется.
     */
    fun createOrder(offer: MarketOffer, qty: Int): String {
        val id = "M-${1001 + _localOrders.value.size}"
        val date = SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.getDefault()).format(Date())
        val order = Order(
            id = id,
            title = offer.title,
            buyer = offer.sellerName,
            date = date,
            amountRub = offer.priceRub * qty,
            status = OrderStatus.Created,
            description = "Демонстрационный заказ ($qty шт.), создан только на этом устройстве. " +
                "Оплата не выполнялась, продавцу ничего не отправлено.",
            kind = OrderKind.Purchase
        )
        _localOrders.value = listOf(order) + _localOrders.value
        persist()
        return id
    }

    /** Возвращает id диалога с собеседником; при необходимости создаёт локальный диалог. */
    fun ensureChat(name: String): String {
        val demo = (_ui.value as? UiState.Ready)?.data?.chats.orEmpty()
        demo.firstOrNull { it.name == name }?.let { return it.id }
        _localChats.value.firstOrNull { it.name == name }?.let { return it.id }
        val id = "lc${_localChats.value.size + 1}"
        val chat = ChatPreview(id, name, "Диалог создан на устройстве (демо)", "сейчас", 0)
        _localChats.value = listOf(chat) + _localChats.value
        persist()
        return id
    }

    private fun persist() = local.save(_localOrders.value, _localChats.value, _localMessages.value)

    fun setHideBalance(v: Boolean) = store.setHideBalance(v)
    fun setAnimations(v: Boolean) = store.setAnimations(v)
}
