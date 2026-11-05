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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import ru.funpaymobile.data.MarketOffer
import ru.funpaymobile.data.Snapshot
import ru.funpaymobile.data.rub
import ru.funpaymobile.ui.components.AppCard
import ru.funpaymobile.ui.components.Chip
import ru.funpaymobile.ui.components.DemoBanner
import ru.funpaymobile.ui.components.DetailHeader
import ru.funpaymobile.ui.components.InfoRow
import ru.funpaymobile.ui.components.NotFoundView
import ru.funpaymobile.ui.components.ScreenPadding
import ru.funpaymobile.ui.components.ScreenTitle
import ru.funpaymobile.ui.theme.Accent
import ru.funpaymobile.ui.theme.Green
import ru.funpaymobile.ui.theme.Muted

@Composable
fun MarketScreen(data: Snapshot, onOpen: (String) -> Unit, onOpenRoom: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var cheapFirst by rememberSaveable { mutableStateOf(false) }

    val filtered = data.market.filter {
        (category == null || it.category == category) &&
            (query.isBlank() || it.title.contains(query, ignoreCase = true) || it.sellerName.contains(query, ignoreCase = true))
    }
    val shown = if (cheapFirst) filtered.sortedBy { it.priceRub } else filtered

    LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenTitle("Площадка", "Предложения других продавцов") }
        if (data.isDemo) item { DemoBanner() }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Поиск по названию или продавцу") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                item { FilterChip(selected = category == null, onClick = { category = null }, label = { Text("Все") }) }
                items(data.marketCategories) { c ->
                    FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                }
                item { FilterChip(selected = cheapFirst, onClick = { cheapFirst = !cheapFirst }, label = { Text("Сначала дешёвые") }) }
            }
        }
        category?.let { c ->
            item {
                AppCard(onClick = { onOpenRoom(c) }) {
                    Text("Чат раздела «$c»", fontWeight = FontWeight.SemiBold)
                    Text("Общее обсуждение участников, не только про продажи", color = Muted, fontSize = 12.sp)
                }
            }
        }
        if (shown.isEmpty()) {
            item { Text("Ничего не найдено", color = Muted, modifier = Modifier.padding(8.dp)) }
        }
        items(shown, key = { it.id }) { offer -> OfferCard(offer) { onOpen(offer.id) } }
    }
}

@Composable
private fun OfferCard(o: MarketOffer, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(o.category, color = Muted, fontSize = 12.sp)
            Chip("В наличии: ${o.stock}", Green)
        }
        Spacer(Modifier.height(6.dp))
        Text(o.title, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("${o.sellerName} · ${o.sellerInfo}", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Text(o.priceRub.rub(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Accent)
    }
}

@Composable
fun OfferDetailScreen(
    data: Snapshot,
    id: String?,
    onBack: () -> Unit,
    onCreateOrder: (MarketOffer, Int) -> Unit,
    onMessage: (String) -> Unit
) {
    val offer = data.market.firstOrNull { it.id == id }
    if (offer == null) {
        NotFoundView("Предложение", onBack)
        return
    }
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var qty by rememberSaveable { mutableStateOf(1) }

    Column(Modifier.fillMaxSize()) {
        DetailHeader("Предложение", onBack)
        LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (data.isDemo) item { DemoBanner() }
            item {
                AppCard {
                    Text(offer.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(offer.priceRub.rub(), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Accent)
                    Spacer(Modifier.height(6.dp))
                    Text(offer.description, color = Muted, fontSize = 13.sp)
                }
            }
            item {
                AppCard {
                    InfoRow("Продавец", offer.sellerName)
                    InfoRow("О продавце", offer.sellerInfo)
                    InfoRow("Категория", offer.category)
                    InfoRow("В наличии", "${offer.stock} шт.")
                }
            }
            item {
                Button(
                    onClick = { qty = 1; showDialog = true },
                    enabled = offer.stock > 0,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Создать заказ") }
            }
            item {
                OutlinedButton(onClick = { onMessage(offer.sellerName) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Написать продавцу")
                }
            }
            item {
                Text(
                    "Демонстрационный режим: оплата не выполняется, заказ и сообщения остаются только на этом устройстве.",
                    color = Muted, fontSize = 12.sp
                )
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Создать заказ") },
            text = {
                Column {
                    Text(offer.title, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (qty > 1) qty-- }) {
                            Icon(Icons.Filled.Remove, contentDescription = "Меньше")
                        }
                        Text("$qty", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
                        IconButton(onClick = { if (qty < offer.stock) qty++ }) {
                            Icon(Icons.Filled.Add, contentDescription = "Больше")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Итого: ${(offer.priceRub * qty).rub()}", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("Демо: оплата не выполняется, продавцу ничего не отправляется.", color = Muted, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false; onCreateOrder(offer, qty) }) { Text("Создать") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Отмена") } }
        )
    }
}
