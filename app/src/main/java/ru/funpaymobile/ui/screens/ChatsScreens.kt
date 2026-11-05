package ru.funpaymobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.funpaymobile.data.ChatPreview
import ru.funpaymobile.data.Message
import ru.funpaymobile.data.Snapshot
import ru.funpaymobile.ui.components.AppCard
import ru.funpaymobile.ui.components.Avatar
import ru.funpaymobile.ui.components.DemoBanner
import ru.funpaymobile.ui.components.DetailHeader
import ru.funpaymobile.ui.components.NotFoundView
import ru.funpaymobile.ui.components.ScreenPadding
import ru.funpaymobile.ui.components.ScreenTitle
import ru.funpaymobile.ui.components.SectionLabel
import ru.funpaymobile.ui.theme.Accent
import ru.funpaymobile.ui.theme.AccentDeep
import ru.funpaymobile.ui.theme.Muted
import ru.funpaymobile.ui.theme.Surface2

@Composable
fun ChatsScreen(data: Snapshot, onOpen: (String) -> Unit, onOpenRoom: (String) -> Unit) {
    LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenTitle("Чаты", "Диалоги и чаты разделов") }
        if (data.isDemo) item { DemoBanner() }
        item { SectionLabel("Чаты разделов") }
        items(data.marketCategories, key = { "room-$it" }) { c ->
            val last = data.rooms[c]?.lastOrNull()
            AppCard(onClick = { onOpenRoom(c) }) {
                Text("Чат раздела «$c»", fontWeight = FontWeight.SemiBold)
                Text(
                    last?.let { "${it.author}: ${it.text}" } ?: "Пока нет сообщений",
                    color = Muted, fontSize = 13.sp, maxLines = 1
                )
            }
        }
        item { SectionLabel("Личные диалоги") }
        items(data.chats, key = { it.id }) { c -> ChatRow(c) { onOpen(c.id) } }
    }
}

@Composable
private fun ChatRow(c: ChatPreview, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(c.name)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(c.lastMessage, color = Muted, fontSize = 13.sp, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(c.time, color = Muted, fontSize = 11.sp)
                if (c.unread > 0) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(Accent).padding(horizontal = 7.dp, vertical = 2.dp)
                    ) { Text(c.unread.toString(), color = Surface2, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
fun ChatDetailScreen(
    data: Snapshot,
    localMessages: Map<String, List<Message>>,
    id: String?,
    onSend: (String, String) -> Unit,
    onBack: () -> Unit
) {
    val chat = data.chats.firstOrNull { it.id == id }
    if (chat == null || id == null) {
        NotFoundView("Диалог", onBack)
        return
    }
    ChatThread(
        title = chat.name,
        showDemo = data.isDemo,
        messages = data.messages[id].orEmpty() + localMessages[id].orEmpty(),
        threadKey = id,
        note = "Сообщения сохраняются только на этом устройстве и не отправляются в FunPay.",
        onSend = { onSend(id, it) },
        onBack = onBack
    )
}

@Composable
fun RoomChatScreen(
    data: Snapshot,
    localMessages: Map<String, List<Message>>,
    category: String?,
    onSend: (String, String) -> Unit,
    onBack: () -> Unit
) {
    if (category == null || category !in data.marketCategories) {
        NotFoundView("Чат раздела", onBack)
        return
    }
    val key = "room:$category"
    ChatThread(
        title = "Чат: $category",
        showDemo = data.isDemo,
        messages = data.rooms[category].orEmpty() + localMessages[key].orEmpty(),
        threadKey = key,
        note = "Демо-чат раздела: сообщения других участников вымышлены, ваши остаются только на этом устройстве.",
        onSend = { onSend(key, it) },
        onBack = onBack
    )
}

@Composable
private fun ChatThread(
    title: String,
    showDemo: Boolean,
    messages: List<Message>,
    threadKey: String,
    note: String,
    onSend: (String) -> Unit,
    onBack: () -> Unit
) {
    var draft by rememberSaveable(threadKey) { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        DetailHeader(title, onBack)
        if (showDemo) {
            Box(Modifier.padding(horizontal = 16.dp)) { DemoBanner() }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = ScreenPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { m -> Bubble(m) }
        }
        Text(note, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp))
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Сообщение…") },
                shape = RoundedCornerShape(22.dp),
                maxLines = 4
            )
            IconButton(
                onClick = { onSend(draft); draft = "" },
                enabled = draft.isNotBlank()
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить", tint = if (draft.isNotBlank()) Accent else Muted)
            }
        }
    }
}

@Composable
private fun Bubble(m: Message) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromMe) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (m.fromMe) AccentDeep else Surface2)
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            if (!m.fromMe && m.author != null) {
                Text(m.author, color = Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(m.text, fontSize = 15.sp)
            Text(
                if (m.localOnly) "${m.time} · локально" else m.time,
                color = Muted, fontSize = 10.sp, modifier = Modifier.align(Alignment.End)
            )
        }
    }
}
