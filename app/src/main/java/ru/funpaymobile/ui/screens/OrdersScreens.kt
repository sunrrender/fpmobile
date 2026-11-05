package ru.funpaymobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.funpaymobile.data.Order
import ru.funpaymobile.data.OrderKind
import ru.funpaymobile.data.OrderStatus
import ru.funpaymobile.data.Snapshot
import ru.funpaymobile.data.rub
import ru.funpaymobile.ui.components.AppCard
import ru.funpaymobile.ui.components.DemoBanner
import ru.funpaymobile.ui.components.DetailHeader
import ru.funpaymobile.ui.components.InfoRow
import ru.funpaymobile.ui.components.NotFoundView
import ru.funpaymobile.ui.components.ScreenPadding
import ru.funpaymobile.ui.components.ScreenTitle
import ru.funpaymobile.ui.components.StatusChip
import ru.funpaymobile.ui.theme.Muted

@Composable
fun OrdersScreen(data: Snapshot, onOpen: (String) -> Unit) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = data.orders.filter {
        when (filter) {
            null -> true
            "kind:Purchase" -> it.kind == OrderKind.Purchase
            "kind:Sale" -> it.kind == OrderKind.Sale
            else -> it.status.name == filter
        }
    }

    LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenTitle("Заказы", "Покупки и продажи (демо)") }
        if (data.isDemo) item { DemoBanner() }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                item { FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("Все") }) }
                item { FilterChip(selected = filter == "kind:Purchase", onClick = { filter = "kind:Purchase" }, label = { Text("Покупки") }) }
                item { FilterChip(selected = filter == "kind:Sale", onClick = { filter = "kind:Sale" }, label = { Text("Продажи") }) }
                items(OrderStatus.entries.toList()) { s ->
                    FilterChip(selected = filter == s.name, onClick = { filter = s.name }, label = { Text(s.label) })
                }
            }
        }
        if (shown.isEmpty()) {
            item { Text("Нет заказов по этому фильтру", color = Muted, modifier = Modifier.padding(8.dp)) }
        }
        items(shown, key = { it.id }) { o -> OrderCard(o) { onOpen(o.id) } }
    }
}

@Composable
private fun OrderCard(o: Order, onClick: () -> Unit) {
    val kindLabel = if (o.kind == OrderKind.Purchase) "Покупка" else "Продажа"
    AppCard(onClick = onClick) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("$kindLabel № ${o.id}", color = Muted, fontSize = 12.sp)
            StatusChip(o.status)
        }
        Spacer(Modifier.height(6.dp))
        Text(o.title, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(o.buyer, color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(o.date, color = Muted, fontSize = 12.sp)
            Text(o.amountRub.rub(), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun OrderDetailScreen(data: Snapshot, id: String?, onBack: () -> Unit, onMessage: (String) -> Unit) {
    val o = data.orders.firstOrNull { it.id == id }
    if (o == null) {
        NotFoundView("Заказ", onBack)
        return
    }
    val purchase = o.kind == OrderKind.Purchase
    Column(Modifier.fillMaxSize()) {
        DetailHeader("Заказ № ${o.id}", onBack)
        LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (data.isDemo) item { DemoBanner() }
            item {
                AppCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(o.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        StatusChip(o.status)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(o.description, color = Muted, fontSize = 13.sp)
                }
            }
            item {
                AppCard {
                    InfoRow("Тип", if (purchase) "Покупка" else "Продажа")
                    InfoRow(if (purchase) "Продавец" else "Заказчик", o.buyer)
                    InfoRow("Дата", o.date)
                    InfoRow("Сумма", o.amountRub.rub())
                    InfoRow("Статус", o.status.label)
                }
            }
            item {
                Button(onClick = { onMessage(o.buyer) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (purchase) "Написать продавцу" else "Написать заказчику")
                }
            }
            item {
                Text(
                    "Действия с заказом (выдача, оплата, возврат) недоступны, пока нет подключения к FunPay.",
                    color = Muted, fontSize = 12.sp
                )
            }
        }
    }
}
