package ru.funpaymobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.funpaymobile.data.Review
import ru.funpaymobile.data.UserProfile
import ru.funpaymobile.ui.components.AppCard
import ru.funpaymobile.ui.components.Avatar
import ru.funpaymobile.ui.components.Chip
import ru.funpaymobile.ui.components.DetailHeader
import ru.funpaymobile.ui.components.ScreenPadding
import ru.funpaymobile.ui.theme.Accent
import ru.funpaymobile.ui.theme.Green
import ru.funpaymobile.ui.theme.Muted
import ru.funpaymobile.ui.theme.Orange
import ru.funpaymobile.ui.theme.Red

/**
 * Профиль продавца (или свой): аватар, рейтинг и отзывы. Для своего профиля можно ответить на отзыв.
 * Разметка отзывов на сайте не проверялась вживую, поэтому список может оказаться неполным.
 */
@Composable
fun UserProfileScreen(
    userId: Long,
    own: Boolean,
    loadProfile: suspend (Long) -> Result<UserProfile>,
    sendReply: suspend (String, String) -> Result<String>,
    onMessage: (() -> Unit)?,
    onBack: () -> Unit
) {
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var profile by remember(userId) { mutableStateOf<UserProfile?>(null) }
    var error by remember(userId) { mutableStateOf<String?>(null) }
    var reload by remember(userId) { mutableStateOf(0) }
    var replyTo by remember { mutableStateOf<Review?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(userId, reload) {
        error = null
        loadProfile(userId).fold({ profile = it }, { error = it.message ?: "Ошибка загрузки" })
    }

    Column(Modifier.fillMaxSize()) {
        DetailHeader(if (own) "Мой рейтинг и отзывы" else "Продавец", onBack)
        LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val p = profile
            when {
                error != null -> item {
                    Column {
                        Text(error!!, color = Red, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { reload++ }) { Text("Повторить") }
                    }
                }
                p == null -> item { Text("Загружаю профиль…", color = Muted) }
                else -> {
                    item {
                        AppCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(p.name, 56, p.avatarUrl)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Chip(if (p.online) "Онлайн" else "Не в сети", if (p.online) Green else Muted)
                                        p.ratingStars?.let { Chip("★ $it из 5", Orange) }
                                    }
                                }
                            }
                            if (p.ratingText.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(p.ratingText, color = Muted, fontSize = 13.sp)
                            }
                        }
                    }
                    if (onMessage != null) {
                        item { Button(onClick = onMessage, modifier = Modifier.fillMaxWidth()) { Text("Написать") } }
                    }
                    item {
                        OutlinedButton(onClick = { uri.openUri(p.pageUrl) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Открыть профиль на FunPay")
                        }
                    }
                    notice?.let { item { Text(it, color = Green, fontSize = 13.sp) } }
                    item { Text("Отзывы", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
                    if (p.reviews.isEmpty()) {
                        item {
                            Text(
                                "Отзывы не найдены или не удалось их разобрать. Полный список — на странице профиля на сайте.",
                                color = Muted, fontSize = 13.sp
                            )
                        }
                    } else {
                        items(p.reviews) { r ->
                            ReviewCard(r, canReply = own && r.reply.isNullOrBlank() && r.orderId != null) { replyTo = r }
                        }
                    }
                }
            }
        }
    }

    val target = replyTo
    if (target != null) {
        var text by remember(target) { mutableStateOf("") }
        var busy by remember(target) { mutableStateOf(false) }
        var err by remember(target) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { if (!busy) replyTo = null },
            title = { Text("Ответ на отзыв") },
            text = {
                Column {
                    Text(target.text, color = Muted, fontSize = 13.sp, maxLines = 4)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = text, onValueChange = { text = it.take(1000) },
                        placeholder = { Text("Ваш ответ покупателю") }, minLines = 3
                    )
                    err?.let { Text(it, color = Red, fontSize = 12.sp) }
                    Text("Ответ будет опубликован от вашего имени на FunPay.", color = Muted, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && text.isNotBlank(), onClick = {
                    busy = true
                    scope.launch {
                        sendReply(target.orderId.orEmpty(), text).fold(
                            { msg -> busy = false; replyTo = null; notice = msg; reload++ },
                            { e -> busy = false; err = e.message ?: "Ошибка отправки" }
                        )
                    }
                }) { Text(if (busy) "Отправляю…" else "Опубликовать", color = Accent) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { replyTo = null }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun ReviewCard(r: Review, canReply: Boolean, onReply: () -> Unit) {
    AppCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(r.author.ifEmpty { "Покупатель" }, fontWeight = FontWeight.Medium, fontSize = 14.sp, modifier = Modifier.weight(1f))
            r.rating?.let { Chip("★ $it", if (it >= 4) Green else if (it == 3) Orange else Red) }
        }
        if (r.date.isNotBlank()) Text(r.date, color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        Text(r.text, fontSize = 14.sp)
        r.reply?.let {
            Spacer(Modifier.height(8.dp))
            Text("Ответ продавца", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(it, color = Muted, fontSize = 13.sp)
        }
        if (canReply) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onReply) { Text("Ответить") }
        }
    }
}
