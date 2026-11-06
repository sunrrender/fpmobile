package ru.funpaymobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.funpaymobile.data.AppSettings
import ru.funpaymobile.data.Operation
import ru.funpaymobile.data.OperationType
import ru.funpaymobile.data.Snapshot
import ru.funpaymobile.data.rub
import ru.funpaymobile.ui.components.AppCard
import ru.funpaymobile.ui.components.Avatar
import ru.funpaymobile.ui.components.Chip
import ru.funpaymobile.ui.components.DemoBanner
import ru.funpaymobile.ui.components.DetailHeader
import ru.funpaymobile.ui.components.InfoRow
import ru.funpaymobile.ui.components.MenuRow
import ru.funpaymobile.ui.components.ScreenPadding
import ru.funpaymobile.ui.components.ScreenTitle
import ru.funpaymobile.ui.theme.Green
import ru.funpaymobile.ui.theme.Muted
import ru.funpaymobile.ui.theme.Orange
import ru.funpaymobile.ui.theme.Red

const val APP_VERSION = "0.5.0"

@Composable
fun MoreScreen(
    data: Snapshot,
    onProfile: () -> Unit,
    onLogin: () -> Unit,
    onLots: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit
) {
    LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenTitle("Ещё", "Дополнительно") }
        if (data.isDemo) item { DemoBanner() }
        if (data.isDemo) item { MenuRow("Подключить аккаунт FunPay", Icons.Filled.Person, "Вход по golden_key (неофициально)", onLogin) }
        item { MenuRow("Профиль", Icons.Filled.Person, data.profile.statusText, onProfile) }
        item { MenuRow("Мои предложения", Icons.Filled.Inventory2, if (data.isDemo) "Демонстрационные товары" else "Ваши лоты на FunPay", onLots) }
        item { MenuRow("История операций", Icons.Filled.History, if (data.isDemo) "Демонстрационные записи" else "Пока только в демо", onHistory) }
        item { MenuRow("Настройки", Icons.Filled.Settings, null, onSettings) }
        item { MenuRow("О приложении", Icons.Filled.Info, "Версия $APP_VERSION", onAbout) }
    }
}

@Composable
fun ProfileScreen(data: Snapshot, onBack: () -> Unit, onLogin: () -> Unit, onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        DetailHeader("Профиль", onBack)
        LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(data.profile.displayName, 56)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(data.profile.displayName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(Modifier.height(4.dp))
                            Chip(if (data.profile.connected) "Подключён" else "Не подключён", if (data.profile.connected) Green else Orange)
                        }
                    }
                }
            }
            item {
                AppCard {
                    InfoRow("Режим", if (data.isDemo) "Демонстрационный" else "Рабочий")
                    InfoRow("Аккаунт FunPay", data.profile.statusText)
                }
            }
            item {
                if (data.isDemo) {
                    androidx.compose.material3.Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text("Войти по golden_key") }
                } else {
                    androidx.compose.material3.OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("Выйти и забыть ключ") }
                }
            }
            item {
                Text(
                    "Никогда не отправляйте пароль, cookies или golden_key в чат и сторонним лицам.",
                    color = Muted, fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun HistoryScreen(data: Snapshot, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        DetailHeader("История операций", onBack)
        LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (data.isDemo) item { DemoBanner() }
            items(data.operations, key = { it.id }) { op -> OperationRow(op) }
        }
    }
}

@Composable
private fun OperationRow(op: Operation) {
    val color = when (op.type) {
        OperationType.Income -> Green
        OperationType.Withdrawal -> Orange
        OperationType.Fee -> Red
    }
    AppCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(op.title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(op.date, color = Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.width(10.dp))
            Text((if (op.amountRub > 0) "+" else "") + op.amountRub.rub(), color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onHideBalance: (Boolean) -> Unit,
    onAnimations: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        DetailHeader("Настройки", onBack)
        LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                SwitchCard("Скрывать баланс", "Показывать «•••••» вместо суммы на главной", settings.hideBalance, onHideBalance)
            }
            item {
                SwitchCard("Анимации переходов", "Плавные переходы между экранами", settings.animations, onAnimations)
            }
            item {
                Text("Настройки сохраняются на устройстве между запусками.", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SwitchCard(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium)
                Text(subtitle, color = Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
fun AboutScreen(isDemo: Boolean, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        DetailHeader("О приложении", onBack)
        LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (isDemo) item { DemoBanner() }
            item {
                AppCard {
                    InfoRow("Приложение", "FunPay Mobile")
                    InfoRow("Версия", APP_VERSION)
                    InfoRow("Платформа", "Kotlin · Jetpack Compose · Material 3")
                }
            }
            item {
                Text(
                    (if (isDemo) "Это неофициальное приложение, не связанное с FunPay. Сейчас показаны вымышленные демо-данные; подключить аккаунт можно в разделе «Ещё»."
                        else "Это неофициальное приложение, не связанное с FunPay. Оно работает через вход по golden_key вашего аккаунта (неофициальный способ: возможны сбои при изменении сайта и риск ограничений от FunPay). Ключ хранится на устройстве в зашифрованном виде и отправляется только на funpay.com.") +
                        " Не передавайте golden_key никому.",
                    color = Muted, fontSize = 13.sp, lineHeight = 18.sp
                )
            }
        }
    }
}
