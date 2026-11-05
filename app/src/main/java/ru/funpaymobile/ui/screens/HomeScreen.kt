package ru.funpaymobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.funpaymobile.data.Snapshot
import ru.funpaymobile.data.rub
import ru.funpaymobile.ui.components.AppCard
import ru.funpaymobile.ui.components.DemoBanner
import ru.funpaymobile.ui.components.IconBadge
import ru.funpaymobile.ui.components.ScreenPadding
import ru.funpaymobile.ui.components.ScreenTitle
import ru.funpaymobile.ui.components.SectionLabel
import ru.funpaymobile.ui.theme.Accent
import ru.funpaymobile.ui.theme.AccentDeep
import ru.funpaymobile.ui.theme.Border
import ru.funpaymobile.ui.theme.Muted
import ru.funpaymobile.ui.theme.Surface2

@Composable
fun HomeScreen(
    data: Snapshot,
    hideBalance: Boolean,
    onOrders: () -> Unit,
    onChats: () -> Unit,
    onLots: () -> Unit,
    onHistory: () -> Unit
) {
    LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenTitle("Главная", "FunPay Mobile") }
        if (data.isDemo) item { DemoBanner() }

        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(AccentDeep, Accent.copy(alpha = 0.55f), Surface2)))
                    .padding(20.dp)
            ) {
                Text("Баланс (демо)", color = Muted.copy(alpha = 0.95f), fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (hideBalance) "••••• ₽" else data.balanceRub.rub(),
                    fontSize = 34.sp, fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Активные заказы", data.activeOrders.toString(), Modifier.weight(1f))
                StatCard("Продажи за 7 дней", data.salesLast7Total.rub(), Modifier.weight(1f))
            }
        }

        item {
            AppCard {
                Text("Продажи по дням", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                BarChart(data.salesLast7Days)
            }
        }

        item { SectionLabel("Быстрые действия") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction("Заказы", Icons.AutoMirrored.Filled.ReceiptLong, Modifier.weight(1f), onOrders)
                QuickAction("Чаты", Icons.AutoMirrored.Filled.Chat, Modifier.weight(1f), onChats)
                QuickAction("Товары", Icons.Filled.Inventory2, Modifier.weight(1f), onLots)
                QuickAction("Операции", Icons.Filled.History, Modifier.weight(1f), onHistory)
            }
        }

        item { SectionLabel("Последние события") }
        item {
            AppCard {
                data.events.forEachIndexed { i, e ->
                    if (i > 0) Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Filled.Notifications, size = 34)
                        Spacer(Modifier.width(12.dp))
                        Text(e.text, Modifier.weight(1f), fontSize = 14.sp)
                        Text(e.time, color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        AppCard {
            Text(label, color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier) {
        AppCard(onClick = onClick) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, null, tint = Accent)
                Spacer(Modifier.height(6.dp))
                Text(label, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun BarChart(values: List<Int>) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val days = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    Row(
        Modifier.fillMaxWidth().height(120.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        values.forEachIndexed { i, v ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((84f * v / max).coerceAtLeast(3f).dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (v == max) Accent else Accent.copy(alpha = 0.35f))
                        .border(0.dp, Border)
                )
                Spacer(Modifier.height(6.dp))
                Text(days.getOrElse(i) { "" }, color = Muted, fontSize = 11.sp)
            }
        }
    }
}
