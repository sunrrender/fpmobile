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
import ru.funpaymobile.data.Lot
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
import ru.funpaymobile.ui.theme.Green
import ru.funpaymobile.ui.theme.Muted

@Composable
fun LotsScreen(data: Snapshot, onBack: () -> Unit, onNew: (() -> Unit)? = null, onOpen: (String) -> Unit) {
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = data.lots.filter { category == null || it.category == category }

    Column(Modifier.fillMaxSize()) {
    DetailHeader("Мои предложения", onBack)
    LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (data.isDemo) item { DemoBanner() }
        if (onNew != null) item {
            androidx.compose.material3.Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text("Создать новый лот") }
        }
        if (!data.isDemo && data.lots.isEmpty()) item {
            Text("Предложений не найдено (или они не загрузились). Нажмите «Создать новый лот» либо обновите экран.", color = Muted, fontSize = 13.sp)
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                item { FilterChip(selected = category == null, onClick = { category = null }, label = { Text("Все") }) }
                items(data.categories) { c ->
                    FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                }
            }
        }
        items(shown, key = { it.id }) { lot -> LotCard(lot) { onOpen(lot.id) } }
    }
    }
}

@Composable
private fun LotCard(lot: Lot, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(lot.category, color = Muted, fontSize = 12.sp)
            Chip(if (lot.active) "Активно" else "Скрыто", if (lot.active) Green else Muted)
        }
        Spacer(Modifier.height(6.dp))
        Text(lot.title, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text((lot.priceText ?: lot.priceRub.rub()), fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
fun LotDetailScreen(data: Snapshot, id: String?, onBack: () -> Unit) {
    val lot = data.lots.firstOrNull { it.id == id }
    if (lot == null) {
        NotFoundView("Товар", onBack)
        return
    }
    Column(Modifier.fillMaxSize()) {
        DetailHeader(lot.title, onBack)
        LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (data.isDemo) item { DemoBanner() }
            item {
                AppCard {
                    Text((lot.priceText ?: lot.priceRub.rub()), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(lot.description, color = Muted, fontSize = 13.sp)
                }
            }
            item {
                AppCard {
                    InfoRow("Категория", lot.category)
                    InfoRow("Статус", if (lot.active) "Активно" else "Скрыто")
                    InfoRow("Цена", (lot.priceText ?: lot.priceRub.rub()))
                }
            }
            item {
                Text(
                    if (data.isDemo) "Редактирование и публикация предложений недоступны в демо-режиме."
                    else "Редактирование и публикация предложений выполняются на сайте FunPay.",
                    color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}
