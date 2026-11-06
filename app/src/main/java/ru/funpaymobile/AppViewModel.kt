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
import ru.funpaymobile.data.FunPayClient
import ru.funpaymobile.data.FunPayException
import ru.funpaymobile.data.FunPayRepository
import ru.funpaymobile.data.LocalStore
import ru.funpaymobile.data.MarketGame
import ru.funpaymobile.data.MarketOffer
import ru.funpaymobile.data.Message
import ru.funpaymobile.data.Order
import ru.funpaymobile.data.OrderKind
import ru.funpaymobile.data.OrderStatus
import ru.funpaymobile.data.RealRepository
import ru.funpaymobile.data.SecureKeyStore
import ru.funpaymobile.data.SettingsStore
import ru.funpaymobile.data.Snapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface UiState {
    data object Loading : UiState
    data class Error(val message: String, val hasSavedKey: Boolean) : UiState
    data class Ready(val data: Snapshot) : UiState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val demoRepository: FunPayRepository = DemoRepository()
    private var repository: FunPayRepository = demoRepository
    private var client: FunPayClient? = null
    private var forceDemo = false

    private val store = SettingsStore(app)
    private val local = LocalStore(app)
    private val secure = SecureKeyStore(app)
    private val saved = local.load()

    val settings = store.state
    val secureStorageAvailable: Boolean get() = secure.available

    private val _ui = MutableStateFlow<UiState>(UiState.Loading)
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    // Локальные данные демо-режима
    private val _localMessages = MutableStateFlow<Map<String, List<Message>>>(saved.messages)
    val localMessages: StateFlow<Map<String, List<Message>>> = _localMessages.asStateFlow()
    private val _localOrders = MutableStateFlow<List<Order>>(saved.orders)
    val localOrders: StateFlow<List<Order>> = _localOrders.asStateFlow()
    private val _localChats = MutableStateFlow<List<ChatPreview>>(saved.chats)
    val localChats: StateFlow<List<ChatPreview>> = _localChats.asStateFlow()

    // Сообщения реальных чатов
    private val _chatMessages = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    val chatMessages: StateFlow<Map<String, List<Message>>> = _chatMessages.asStateFlow()

    private var marketGamesCache: List<MarketGame>? = null

    init {
        load()
    }

    fun load() {
        val key = if (client == null && !forceDemo) secure.read() else null
        if (key != null) autoConnect(key) else loadCurrent()
    }

    private fun loadCurrent() {
        viewModelScope.launch {
            _ui.value = UiState.Loading
            try {
                _ui.value = UiState.Ready(repository.load())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.value = UiState.Error(errorText(e), secure.read() != null)
            }
        }
    }

    private fun autoConnect(key: String) {
        viewModelScope.launch {
            _ui.value = UiState.Loading
            try {
                val c = FunPayClient(key)
                c.login()
                val repo = RealRepository(c)
                val snap = repo.load()
                client = c
                repository = repo
                _ui.value = UiState.Ready(snap)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.value = UiState.Error(errorText(e), true)
            }
        }
    }

    /** Вход по golden_key. Колбэк получает null при успехе или текст ошибки. */
    fun connect(rawKey: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val c = FunPayClient(rawKey)
                c.login()
                val repo = RealRepository(c)
                val snap = repo.load()
                client = c
                repository = repo
                forceDemo = false
                _chatMessages.value = emptyMap()
                marketGamesCache = null
                secure.save(rawKey.trim())
                _ui.value = UiState.Ready(snap)
                onDone(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onDone(errorText(e))
            }
        }
    }

    fun disconnect() {
        secure.clear()
        client = null
        repository = demoRepository
        forceDemo = false
        _chatMessages.value = emptyMap()
        marketGamesCache = null
        loadCurrent()
    }

    fun useDemo() {
        forceDemo = true
        client = null
        repository = demoRepository
        loadCurrent()
    }

    /** Фоновое обновление без экрана загрузки; при ошибке остаются прежние данные. */
    fun refreshSilently() {
        if (client == null) return
        viewModelScope.launch {
            try {
                _ui.value = UiState.Ready(repository.load())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // оставляем прежние данные
            }
        }
    }

    private fun errorText(e: Exception): String =
        if (e is FunPayException) (e.message ?: "Ошибка FunPay")
        else "Не удалось загрузить данные: ${e.message ?: e.javaClass.simpleName}"

    // ---------------------------------------------------------------- реальные чаты и площадка

    suspend fun refreshChat(id: String): String? {
        val c = client ?: return "Нет подключения к аккаунту"
        return try {
            _chatMessages.value = _chatMessages.value + (id to c.chatMessages(id))
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorText(e)
        }
    }

    suspend fun sendChat(id: String, text: String): String? {
        val c = client ?: return "Нет подключения к аккаунту"
        return try {
            c.sendMessage(id, text.trim())
            refreshChat(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorText(e)
        }
    }

    suspend fun marketGames(): Result<List<MarketGame>> {
        marketGamesCache?.let { return Result.success(it) }
        val c = client ?: return Result.failure(IllegalStateException("Нет подключения к аккаунту"))
        return try {
            Result.success(c.marketGames().also { marketGamesCache = it })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(errorText(e)))
        }
    }

    suspend fun marketOffers(url: String, title: String): Result<List<MarketOffer>> {
        val c = client ?: return Result.failure(IllegalStateException("Нет подключения к аккаунту"))
        return try {
            Result.success(c.marketOffers(url, title))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(errorText(e)))
        }
    }

    // ---------------------------------------------------------------- демо-режим

    /** Сообщение сохраняется только на устройстве и НЕ отправляется в FunPay (демо-режим). */
    fun addLocalMessage(chatId: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val current = _localMessages.value[chatId].orEmpty()
        val msg = Message(System.nanoTime(), clean, fromMe = true, time = time, localOnly = true)
        _localMessages.value = _localMessages.value + (chatId to (current + msg))
        persist()
    }

    /** Демо-заказ: существует только на устройстве, оплата не выполняется. */
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

    /** Демо-режим: id диалога с собеседником; при необходимости создаёт локальный диалог. */
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
