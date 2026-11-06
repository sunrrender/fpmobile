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
import androidx.compose.foundation.layout.width
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
import ru.funpaymobile.data.BuyOutcome
import ru.funpaymobile.data.FieldType
import ru.funpaymobile.data.FormField
import ru.funpaymobile.data.MarketCategory
import ru.funpaymobile.data.MarketGame
import ru.funpaymobile.data.MarketOffer
import ru.funpaymobile.data.Message
import ru.funpaymobile.data.Snapshot
import ru.funpaymobile.data.UserProfile
import ru.funpaymobile.ui.components.AppCard
import ru.funpaymobile.ui.components.Avatar
import ru.funpaymobile.ui.components.Chip
import ru.funpaymobile.ui.components.DetailHeader
import ru.funpaymobile.ui.components.InfoRow
import ru.funpaymobile.ui.components.NotFoundView
import ru.funpaymobile.ui.components.ScreenPadding
import ru.funpaymobile.ui.components.ScreenTitle
import ru.funpaymobile.ui.theme.Accent
import ru.funpaymobile.ui.theme.Green
import ru.funpaymobile.ui.theme.Muted
import ru.funpaymobile.ui.theme.Orange
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
    loadForms: suspend (String) -> Result<String>,
    buy: suspend (String, Map<String, String>) -> Result<BuyOutcome>,
    loadBuyFields: suspend (String) -> Result<List<FormField>>,
    loadProfile: suspend (Long) -> Result<UserProfile>,
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
    var sellerOf by remember { mutableStateOf<MarketOffer?>(null) }

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
        if (sellerOf != null) { sellerOf = null } else if (offer != null) { offer = null } else if (category != null) { category = null; query = "" } else { game = null; query = "" }
    }

    val g = game
    val c = category
    val sel = offer
    val seller = sellerOf
    when {
        seller != null && seller.sellerId != null -> UserProfileScreen(
            userId = seller.sellerId,
            own = false,
            loadProfile = loadProfile,
            sendReply = { _, _ -> Result.failure(IllegalStateException("Отвечать можно только на отзывы в своём профиле")) },
            onMessage = { onMessageSeller(seller) },
            onBack = { sellerOf = null }
        )
        sel != null -> OfferInfoScreen(
            o = sel,
            loadInfo = loadOfferInfo,
            loadForms = loadForms,
            buy = buy,
            loadBuyFields = loadBuyFields,
            onMessage = if (sel.sellerId != null) ({ onMessageSeller(sel) }) else null,
            onProfile = if (sel.sellerId != null) ({ sellerOf = sel }) else null,
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
                                Avatar(o.sellerName, 32, o.sellerAvatar)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(o.sellerName.ifEmpty { "Продавец" }, color = Muted, fontSize = 12.sp, maxLines = 1)
                                    if (o.sellerInfo.isNotEmpty()) Text(o.sellerInfo, color = Orange, fontSize = 11.sp)
                                }
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
    loadForms: suspend (String) -> Result<String>,
    buy: suspend (String, Map<String, String>) -> Result<BuyOutcome>,
    loadBuyFields: suspend (String) -> Result<List<FormField>>,
    onMessage: (() -> Unit)?,
    onProfile: (() -> Unit)?,
    onBack: () -> Unit
) {
    val uri = LocalUriHandler.current
    var info by remember(o.id) { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var forms by remember(o.id) { mutableStateOf<String?>(null) }
    var buyBusy by remember(o.id) { mutableStateOf(false) }
    var buyResult by remember(o.id) { mutableStateOf<BuyOutcome?>(null) }
    var buyError by remember(o.id) { mutableStateOf<String?>(null) }
    var confirmBuy by remember(o.id) { mutableStateOf(false) }
    val buyScope = rememberCoroutineScope()
    var buyFields by remember(o.id) { mutableStateOf<List<FormField>?>(null) }
    var buyFieldsError by remember(o.id) { mutableStateOf<String?>(null) }
    val buyValues = remember(o.id) { androidx.compose.runtime.mutableStateMapOf<String, String>() }
    LaunchedEffect(confirmBuy) {
        val url = o.url
        if (confirmBuy && url != null && buyFields == null) {
            loadBuyFields(url).fold({ buyFields = it }, { buyFieldsError = it.message ?: "Не удалось загрузить форму покупки" })
        }
    }
    LaunchedEffect(o.id) {
        o.url?.let { url -> loadInfo(url).onSuccess { info = it } }
    }
    if (confirmBuy && o.url != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmBuy = false },
            title = { Text("Подтвердите покупку") },
            text = {
                Column {
                    InfoRow("Товар", o.title)
                    InfoRow("Продавец", o.sellerName.ifEmpty { "—" })
                    InfoRow("Сумма", o.priceText ?: "${o.priceRub} ₽")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Дальше оплата проходит на FunPay: с вашего баланса, если средств хватает, иначе FunPay сам посчитает недостающую сумму и предложит СБП или другой способ. Ссылка откроется только после нажатия кнопки.",
                        color = Muted, fontSize = 12.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    val shown = buyFields.orEmpty().filter { it.type != FieldType.Hidden }
                    shown.forEach { fld ->
                        Spacer(Modifier.height(6.dp))
                        FieldEditor(fld, buyValues[fld.name] ?: fld.value) { buyValues[fld.name] = it }
                    }
                    if (buyFields == null && buyFieldsError == null) Text("Загружаю способы оплаты…", color = Muted, fontSize = 12.sp)
                    buyFieldsError?.let { Text(it, color = Red, fontSize = 12.sp) }
                    val needChoice = shown.any { it.type == FieldType.Select && (buyValues[it.name] ?: it.value).isBlank() }
                    if (needChoice) Text("Выберите способ оплаты выше.", color = Muted, fontSize = 11.sp)
                    val canBuy = o.priceRub in 1..100 && buyFields != null && !needChoice
                    androidx.compose.material3.Button(
                        enabled = canBuy && !buyBusy && buyResult == null,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            buyBusy = true; buyError = null
                            buyScope.launch {
                                buy(o.url, buyValues.toMap()).fold({ buyResult = it }, { e -> buyError = e.message ?: "Не удалось оформить покупку" })
                                buyBusy = false
                            }
                        }
                    ) { Text(if (buyBusy) "Оформляю…" else "Оплатить в приложении") }
                    if (!canBuy) Text("Покупка в приложении пока ограничена тестовым лимитом 100 ₽.", color = Muted, fontSize = 11.sp)
                    buyError?.let { Text(it, color = Red, fontSize = 12.sp) }
                    buyResult?.let { r ->
                        Spacer(Modifier.height(6.dp))
                        Text(r.message, color = if (r.kind == BuyOutcome.Kind.Order) Green else Orange, fontSize = 13.sp)
                        r.url?.let { link ->
                            androidx.compose.material3.OutlinedButton(onClick = { uri.openUri(link) }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (r.kind == BuyOutcome.Kind.Order) "Открыть заказ" else "Перейти к оплате / пополнению")
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.material3.OutlinedButton(onClick = { uri.openUri(o.url) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Перейти к оплате на FunPay")
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmBuy = false; buyResult = null; buyError = null }) { Text("Закрыть", color = Accent) }
            }
        )
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(o.sellerName, 40, o.sellerAvatar)
                        Spacer(Modifier.width(10.dp))
                        Text(o.sellerName.ifEmpty { "Продавец" }, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(6.dp))
                    if (o.sellerInfo.isNotEmpty()) InfoRow("О продавце", o.sellerInfo)
                    if (o.stock > 0) InfoRow("В наличии", "${o.stock} шт.")
                    InfoRow("Раздел", o.category)
                }
            }
            if (info.isNotEmpty()) {
                item { AppCard { info.forEach { (k, v) -> InfoRow(k, v) } } }
            }
            if (onProfile != null) {
                item {
                    androidx.compose.material3.OutlinedButton(onClick = onProfile, modifier = Modifier.fillMaxWidth()) {
                        Text("Рейтинг и отзывы продавца")
                    }
                }
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
                    androidx.compose.material3.OutlinedButton(onClick = { confirmBuy = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Купить")
                    }
                }
            }
            item {
                Text("Оплата и оформление покупки выполняются на сайте FunPay.", color = Muted, fontSize = 12.sp)
            }
            o.url?.let { url ->
                item {
                    val sc = rememberCoroutineScope()
                    androidx.compose.material3.TextButton(onClick = { sc.launch { forms = loadForms(url).fold({ it }, { e -> "Ошибка: ${e.message}" }) } }) {
                        Text("Диагностика: форма покупки", color = Muted)
                    }
                    forms?.let { d ->
                        androidx.compose.foundation.text.selection.SelectionContainer { Text(d, color = Muted, fontSize = 10.sp, lineHeight = 13.sp) }
                    }
                }
            }
        }
    }
}
