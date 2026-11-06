package ru.funpaymobile.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.funpaymobile.data.MarketCategory
import ru.funpaymobile.data.MarketGame
import ru.funpaymobile.data.MarketOffer
import ru.funpaymobile.data.Message
import ru.funpaymobile.data.Snapshot
import ru.funpaymobile.ui.components.AppCard
import ru.funpaymobile.ui.components.Chip
import ru.funpaymobile.ui.components.DetailHeader
import ru.funpaymobile.ui.components.InfoRow
import ru.funpaymobile.ui.components.NotFoundView
import ru.funpaymobile.ui.components.ScreenPadding
import ru.funpaymobile.ui.components.ScreenTitle
import ru.funpaymobile.ui.theme.Accent
import ru.funpaymobile.ui.theme.Green
import ru.funpaymobile.ui.theme.Muted
import ru.funpaymobile.ui.theme.Red

/** Реальный чат: сообщения подгружаются с FunPay каждые несколько секунд, пока экран открыт. */
@Composable
fun RealChatScreen(
    data: Snapshot,
    id: String?,
    title: String?,
    messages: Map<String, List<Message>>,
    refresh: suspend (String) -> String?,
    send: suspend (String, String) -> String?,
    onBack: () -> Unit
) {
    if (id == null) {
        NotFoundView("Диалог", onBack)
        return
    }
    val chatTitle = data.chats.firstOrNull { it.id == id }?.name ?: title ?: "Диалог"
    var error by remember(id) { mutableStateOf<String?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()

    LaunchedEffect(id) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                error = refresh(id)
                delay(8000)
            }
        }
    }

    ChatThread(
        title = chatTitle,
        showDemo = false,
        messages = messages[id].orEmpty(),
        threadKey = "real-$id",
        note = error ?: "Сообщения отправляются в ваш аккаунт FunPay.",
        onSend = { text -> scope.launch { error = send(id, text) } },
        onBack = onBack
    )
}

/** Реальная площадка: игры → разделы → предложения. Покупка выполняется на сайте. */
@Composable
fun RealMarketScreen(
    loadGames: suspend () -> Result<List<MarketGame>>,
    loadOffers: suspend (String, String) -> Result<List<MarketOffer>>,
    loadOfferInfo: suspend (String) -> Result<List<Pair<String, String>>>,
    onMessageSeller: (MarketOffer) -> Unit
) {
    val uri = LocalUriHandler.current
    var games by remember { mutableStateOf<List<MarketGame>?>(null) }
    var gamesError by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var game by remember { mutableStateOf<MarketGame?>(null) }
    var category by remember { mutableStateOf<MarketCategory?>(null) }
    var offers by remember { mutableStateOf<List<MarketOffer>?>(null) }
    var offersError by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var offersReload by remember { mutableStateOf(0) }
    var offer by remember { mutableStateOf<MarketOffer?>(null) }

    LaunchedEffect(reload) {
        gamesError = null
        loadGames().fold({ games = it }, { gamesError = it.message ?: "Ошибка загрузки" })
    }
    LaunchedEffect(category, offersReload) {
        val c = category ?: return@LaunchedEffect
        offers = null
        offersError = null
        loadOffers(c.url, c.title).fold({ offers = it }, { offersError = it.message ?: "Ошибка загрузки" })
    }
    BackHandler(enabled = game != null) {
        if (offer != null) { offer = null } else if (category != null) { category = null; query = "" } else { game = null; query = "" }
    }

    val g = game
    val c = category
    val sel = offer
    when {
        sel != null -> OfferInfoScreen(
            o = sel,
            loadInfo = loadOfferInfo,
            onMessage = if (sel.sellerId != null) ({ onMessageSeller(sel) }) else null,
            onBack = { offer = null }
        )
        g == null -> LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { ScreenTitle("Площадка", "Игры и разделы FunPay") }
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Поиск игры") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true, shape = RoundedCornerShape(16.dp)
                )
            }
            val list = games
            when {
                gamesError != null -> item { StatusText(gamesError!!, error = true) { reload++ } }
                list == null -> item { StatusText("Загружаю список игр…") }
                else -> items(list.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }, key = { it.name }) { item ->
                    AppCard(onClick = { game = item; query = "" }) {
                        Text(item.name, fontWeight = FontWeight.SemiBold)
                        Text("Разделов: ${item.categories.size}", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
        c == null -> Column(Modifier.fillMaxSize()) {
            DetailHeader(g.name) { game = null }
            LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(g.categories, key = { it.url }) { cat ->
                    AppCard(onClick = { category = cat }) { Text(cat.title, fontWeight = FontWeight.Medium) }
                }
            }
        }
        else -> Column(Modifier.fillMaxSize()) {
            DetailHeader("${g.name} · ${c.title}") { category = null; query = "" }
            LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Поиск по названию или продавцу") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true, shape = RoundedCornerShape(16.dp)
                    )
                }
                item {
                    Text("Покупка и оплата выполняются на сайте FunPay: нажмите на предложение, чтобы открыть его.",
                        color = Muted, fontSize = 12.sp)
                }
                val list = offers
                when {
                    offersError != null -> item { StatusText(offersError!!, error = true) { offersReload++ } }
                    list == null -> item { StatusText("Загружаю предложения…") }
                    list.isEmpty() -> item { StatusText("Предложений не найдено") }
                    else -> items(
                        list.filter { query.isBlank() || it.title.contains(query, true) || it.sellerName.contains(query, true) },
                        key = { it.id }
                    ) { o ->
                        AppCard(onClick = { offer = o }) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(o.sellerName.ifEmpty { "Продавец" }, color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                if (o.stock > 0) Chip("В наличии: ${o.stock}", Green)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(o.title, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            Text(o.priceText ?: "${o.priceRub} ₽", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Accent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusText(text: String, error: Boolean = false, onRetry: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(8.dp)) {
        Text(text, color = if (error) Red else Muted, fontSize = 13.sp)
        if (onRetry != null) {
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.OutlinedButton(onClick = onRetry) { Text("Повторить") }
        }
    }
}

@Composable
private fun OfferInfoScreen(
    o: MarketOffer,
    loadInfo: suspend (String) -> Result<List<Pair<String, String>>>,
    onMessage: (() -> Unit)?,
    onBack: () -> Unit
) {
    val uri = LocalUriHandler.current
    var info by remember(o.id) { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(o.id) {
        o.url?.let { url -> loadInfo(url).onSuccess { info = it } }
    }
    Column(Modifier.fillMaxSize()) {
        DetailHeader("Предложение") { onBack() }
        LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                AppCard {
                    Text(o.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(o.priceText ?: "${o.priceRub} ₽", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Accent)
                }
            }
            item {
                AppCard {
                    InfoRow("Продавец", o.sellerName.ifEmpty { "—" })
                    if (o.sellerInfo.isNotEmpty()) InfoRow("О продавце", o.sellerInfo)
                    if (o.stock > 0) InfoRow("В наличии", "${o.stock} шт.")
                    InfoRow("Раздел", o.category)
                }
            }
            if (info.isNotEmpty()) {
                item { AppCard { info.forEach { (k, v) -> InfoRow(k, v) } } }
            }
            if (onMessage != null) {
                item {
                    androidx.compose.material3.Button(onClick = onMessage, modifier = Modifier.fillMaxWidth()) {
                        Text("Написать продавцу")
                    }
                }
            }
            o.url?.let { url ->
                item {
                    androidx.compose.material3.OutlinedButton(onClick = { uri.openUri(url) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Открыть на FunPay для покупки")
                    }
                }
            }
            item {
                Text("Оплата и оформление покупки выполняются на сайте FunPay.", color = Muted, fontSize = 12.sp)
            }
        }
    }
}
