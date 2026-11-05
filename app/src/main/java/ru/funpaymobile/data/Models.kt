package ru.funpaymobile.data

enum class OrderStatus(val label: String) {
    Created("Создан"),
    Paid("Оплачен"),
    InProgress("В работе"),
    Completed("Завершён"),
    Refunded("Возврат")
}

data class Order(
    val id: String,
    val title: String,
    val buyer: String,
    val date: String,
    val amountRub: Int,
    val status: OrderStatus,
    val description: String,
    val kind: OrderKind = OrderKind.Sale
)

enum class OrderKind { Sale, Purchase }

/** Предложение другого продавца на площадке. */
data class MarketOffer(
    val id: String,
    val title: String,
    val category: String,
    val priceRub: Int,
    val sellerName: String,
    val sellerInfo: String,
    val stock: Int,
    val description: String
)

data class ChatPreview(
    val id: String,
    val name: String,
    val lastMessage: String,
    val time: String,
    val unread: Int
)

data class Message(
    val id: Long,
    val text: String,
    val fromMe: Boolean,
    val time: String,
    /** true — сообщение существует только на устройстве и никуда не отправляется. */
    val localOnly: Boolean = false
)

data class Lot(
    val id: String,
    val title: String,
    val priceRub: Int,
    val category: String,
    val active: Boolean,
    val description: String
)

enum class OperationType { Income, Withdrawal, Fee }

data class Operation(
    val id: String,
    val title: String,
    val date: String,
    val amountRub: Int,
    val type: OperationType
)

data class AppEvent(val id: String, val text: String, val time: String)

data class Profile(
    val displayName: String,
    val connected: Boolean,
    val statusText: String
)

/** Полный набор данных, который приложение показывает на экранах. */
data class Snapshot(
    /** true — данные демонстрационные и не связаны с реальным аккаунтом FunPay. */
    val isDemo: Boolean,
    val balanceRub: Int,
    val salesLast7Days: List<Int>,
    val orders: List<Order>,
    val chats: List<ChatPreview>,
    val messages: Map<String, List<Message>>,
    val lots: List<Lot>,
    val market: List<MarketOffer>,
    val operations: List<Operation>,
    val events: List<AppEvent>,
    val profile: Profile
) {
    val activeOrders: Int
        get() = orders.count { it.status == OrderStatus.Paid || it.status == OrderStatus.InProgress }
    val salesLast7Total: Int get() = salesLast7Days.sum()
    val categories: List<String> get() = lots.map { it.category }.distinct()
    val marketCategories: List<String> get() = market.map { it.category }.distinct()
}

data class AppSettings(
    val hideBalance: Boolean = false,
    val animations: Boolean = true
)

fun Int.rub(): String {
    val sign = if (this < 0) "−" else ""
    val digits = kotlin.math.abs(this).toString()
    val grouped = digits.reversed().chunked(3).joinToString(" ").reversed()
    return "$sign$grouped ₽"
}
