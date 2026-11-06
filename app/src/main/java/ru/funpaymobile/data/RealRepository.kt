package ru.funpaymobile.data

/** Реальные данные аккаунта FunPay через [FunPayClient]. */
class RealRepository(private val client: FunPayClient) : FunPayRepository {

    override suspend fun load(): Snapshot {
        client.login()
        val warnings = mutableListOf<String>()

        val sales = client.orders(sales = true)
        val purchases = softly("покупки", warnings) { client.orders(sales = false) }
        val chats = softly("чаты", warnings) { client.chats() }
        val lots = softly("мои предложения", warnings) { client.myLots() }

        val orders = (sales + purchases).distinctBy { it.id }
        return Snapshot(
            isDemo = false,
            balanceRub = client.balanceRub,
            salesLast7Days = emptyList(),
            orders = orders,
            chats = chats,
            messages = emptyMap(),
            rooms = emptyMap(),
            lots = lots,
            market = emptyList(),
            operations = emptyList(),
            events = orders.take(3).map { AppEvent("ev-${it.id}", "Заказ ${it.id}: ${it.status.label}", it.date) },
            profile = Profile(client.username, true, "Подключён · ID ${client.userId}", client.userId),
            balanceText = client.balanceText.ifEmpty { null },
            notice = if (warnings.isEmpty()) null else "Не загрузилось: ${warnings.joinToString("; ")}"
        )
    }

    private suspend fun <T> softly(what: String, warnings: MutableList<String>, block: suspend () -> List<T>): List<T> =
        try {
            block()
        } catch (e: FunPayException) {
            if (e.kind == FunPayException.Kind.InvalidKey) throw e
            warnings += "$what (${e.message})"
            emptyList()
        }
}
