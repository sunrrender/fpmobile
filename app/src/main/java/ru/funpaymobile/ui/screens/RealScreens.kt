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
import androidx.compose.foundation.verticalScroll
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
import ru.funpaymobile.data.ConfirmStep
import ru.funpaymobile.data.Quote
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
    confirmOrder: suspend (ConfirmStep) -> Result<BuyOutcome>,
    loadQuotes: suspend (String, Map<String, String>) -> Result<List<Quote>>,
    balance: Double?,
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
            confirmOrder = confirmOrder,
            loadQuotes = loadQuotes,
            balance = balance,
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
    confirmOrder: suspend (ConfirmStep) -> Result<BuyOutcome>,
    loadQuotes: suspend (String, Map<String, String>) -> Result<List<Quote>>,
    balance: Double?,
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
    var cur by remember(o.id) { mutableStateOf("RUB") }
    var rates by remember(o.id) { mutableStateOf<Map<String, Double>?>(null) }
    var ratesError by remember(o.id) { mutableStateOf<String?>(null) }
    var sumInput by remember(o.id) { mutableStateOf("") }
    var quotes by remember(o.id) { mutableStateOf<List<Quote>?>(null) }
    var quotesBusy by remember(o.id) { mutableStateOf(false) }
    var quotesError by remember(o.id) { mutableStateOf<String?>(null) }
    val dialogScroll = androidx.compose.foundation.rememberScrollState()
    LaunchedEffect(confirmBuy) {
        if (confirmBuy && rates == null) ru.funpaymobile.data.Rates.load().fold({ rates = it }, { ratesError = it.message })
    }
    LaunchedEffect(buyResult, buyError) { if (buyResult != null || buyError != null) dialogScroll.animateScrollTo(0) }

    if (confirmBuy && o.url != null) {
        fun fmt(v: Double): String = String.format(java.util.Locale.US, "%.2f", v)
        val allShown = buyFields.orEmpty().filter { it.type != FieldType.Hidden }
        val amountField = allShown.firstOrNull { it.name == "amount" || it.label.contains("Получу", true) }
        val payField = allShown.firstOrNull { it.label.contains("Заплачу", true) }
        val shown = allShown.filter { it != amountField && it != payField }
            .map { if (it.name == "method") it.copy(label = "Способ оплаты") else it }
        val unit = o.priceText?.replace(',', '.')?.let { Regex("[0-9]+(\\.[0-9]+)?").find(it)?.value?.toDoubleOrNull() } ?: o.priceRub.toDouble()
        val rate = rates?.get(cur) ?: 1.0
        val qty = amountField?.let { buyValues[it.name] ?: it.value }?.trim()?.toIntOrNull() ?: 1
        val totalRub = unit * qty
        val sym = mapOf("RUB" to "₽", "USD" to "$", "EUR" to "€")

        // Своё поле «Заплачу» у FunPay хранится в рублях; значение считаем по цене и количеству.
        LaunchedEffect(qty, payField?.name, unit) { if (payField != null) buyValues[payField.name] = fmt(totalRub) }
        // Начальная сумма и пересчёт при смене валюты/загрузке курсов и формы.
        LaunchedEffect(buyFields, cur, rates) { if (unit > 0) sumInput = fmt(totalRub / rate) }
        // Способ оплаты по умолчанию — первый реальный вариант (виден в поле, можно сменить).
        LaunchedEffect(buyFields) {
            buyFields?.forEach { f ->
                if (f.type == FieldType.Select && (buyValues[f.name] ?: f.value).isBlank()) {
                    // Если среди способов есть оплата с баланса FunPay, выбираем её, иначе первый вариант.
                    val real = f.options.filter { it.value.isNotBlank() }
                    (real.firstOrNull { it.label.contains("баланс", true) || it.label.contains("balance", true) } ?: real.firstOrNull())
                        ?.let { buyValues[f.name] = it.value }
                }
            }
        }
        fun onQty(t: String) {
            val name = amountField?.name ?: return
            buyValues[name] = t.filter { it.isDigit() }.take(6)
            val q = buyValues[name]?.toIntOrNull() ?: 0
            sumInput = fmt(unit * q / rate)
        }
        fun onSum(t: String) {
            sumInput = t.filter { it.isDigit() || it == '.' || it == ',' }.take(12)
            val v = sumInput.replace(',', '.').toDoubleOrNull() ?: 0.0
            val q = if (unit > 0) Math.floor(v * rate / unit + 1e-9).toInt().coerceIn(0, 999999) else 0
            amountField?.let { buyValues[it.name] = if (q > 0) q.toString() else "" }
        }

        val needChoice = shown.any { it.type == FieldType.Select && (buyValues[it.name] ?: it.value).isBlank() }
        val qtyOk = qty >= 1 && (o.stock <= 0 || qty <= o.stock)
        val canBuy = buyFields != null && !needChoice && qtyOk && unit > 0 && totalRub <= 100.0
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!buyBusy) confirmBuy = false },
            title = { Text("Подтвердите покупку") },
            text = {
                Column(Modifier.verticalScroll(dialogScroll)) {
                    buyError?.let {
                        Text("Заказ не создан", color = Red, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        androidx.compose.foundation.text.selection.SelectionContainer { Text(it, color = Red, fontSize = 12.sp) }
                        Spacer(Modifier.height(8.dp))
                    }
                    buyResult?.confirm?.let { cs ->
                        Text("Проверьте заказ: так его показывает FunPay", color = Orange, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        if (cs.summary.isNotEmpty()) cs.summary.forEach { (k, v) -> InfoRow(k, v) }
                        else Text(cs.text, color = Muted, fontSize = 12.sp)
                        val exact = cs.summary.firstOrNull { it.first.contains("Сумма", true) }?.second
                            ?.filter { it.isDigit() || it == '.' || it == ',' }?.replace(',', '.')?.toDoubleOrNull()
                        BalancePanel(balance, exact, "по расчёту FunPay")
                        Text("Заказ ещё не создан. Он будет создан после нажатия «Подтвердить заказ».", color = Muted, fontSize = 11.sp)
                        buyResult?.trace?.takeIf { it.isNotBlank() }?.let { tr ->
                            androidx.compose.foundation.text.selection.SelectionContainer { Text("[диагностика ответа]\n$tr", color = Muted, fontSize = 9.sp, lineHeight = 12.sp) }
                        }
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text("[диагностика формы подтверждения]\n" + cs.dump, color = Muted, fontSize = 9.sp, lineHeight = 12.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    buyResult?.takeIf { it.confirm == null }?.let { r ->
                        Text(
                            when (r.kind) {
                                BuyOutcome.Kind.Order -> "Заказ создан"
                                BuyOutcome.Kind.Unverified -> "Заказ, возможно, создан: проверьте"
                                else -> "FunPay просит оплатить"
                            },
                            color = if (r.kind == BuyOutcome.Kind.Order) Green else Orange, fontWeight = FontWeight.Bold, fontSize = 14.sp
                        )
                        Text(r.message, color = Muted, fontSize = 12.sp)
                        if (r.trace.isNotBlank()) androidx.compose.foundation.text.selection.SelectionContainer {
                            Text("[диагностика ответа]\n" + r.trace, color = Muted, fontSize = 9.sp, lineHeight = 12.sp)
                        }
                        r.url?.let { link ->
                            androidx.compose.material3.OutlinedButton(onClick = { uri.openUri(link) }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (r.kind == BuyOutcome.Kind.Order || r.kind == BuyOutcome.Kind.Unverified) "Открыть заказ на FunPay" else "Перейти к оплате / пополнению")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    InfoRow("Товар", o.title)
                    InfoRow("Продавец", o.sellerName.ifEmpty { "—" })
                    InfoRow("Цена за 1 шт.", o.priceText ?: "${o.priceRub} ₽")
                    shown.forEach { fld ->
                        Spacer(Modifier.height(6.dp))
                        FieldEditor(fld, buyValues[fld.name] ?: fld.value) { buyValues[fld.name] = it }
                    }
                    if (buyFields == null && buyFieldsError == null) Text("Загружаю форму покупки…", color = Muted, fontSize = 12.sp)
                    buyFieldsError?.let { Text(it, color = Red, fontSize = 12.sp) }
                    Spacer(Modifier.height(10.dp))
                    if (amountField != null) {
                        androidx.compose.material3.OutlinedTextField(
                            value = buyValues[amountField.name] ?: amountField.value, onValueChange = ::onQty,
                            label = { Text("Количество" + if (o.stock > 0) " (в наличии ${o.stock})" else "") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    androidx.compose.material3.OutlinedTextField(
                        value = sumInput, onValueChange = ::onSum,
                        label = { Text("Сумма, ${sym[cur]}") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("RUB", "USD", "EUR").forEach { c ->
                            androidx.compose.material3.FilterChip(
                                selected = cur == c, enabled = c == "RUB" || rates != null,
                                onClick = { cur = c }, label = { Text(c) }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("К оплате: ${fmt(totalRub)} ₽ ($qty × ${fmt(unit)} ₽)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    val methodLabel = shown.firstOrNull { it.name == "method" }?.let { f ->
                        f.options.firstOrNull { it.value == (buyValues[f.name] ?: f.value) }?.label
                    }
                    val selectedQuote = quotes?.firstOrNull { it.label == methodLabel }?.total
                    BalancePanel(balance, selectedQuote ?: totalRub, if (selectedQuote != null) "по расчёту FunPay" else "примерно, без комиссии способа оплаты")
                    androidx.compose.material3.OutlinedButton(
                        enabled = !quotesBusy && o.url != null,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            quotesBusy = true; quotesError = null
                            buyScope.launch {
                                loadQuotes(o.url!!, buyValues.toMap()).fold({ quotes = it }, { quotesError = it.message ?: "Не удалось получить расчёт" })
                                quotesBusy = false
                            }
                        }
                    ) { Text(if (quotesBusy) "Считаю у FunPay…" else "Узнать точную цену по способам оплаты") }
                    quotesError?.let { Text(it, color = Red, fontSize = 12.sp) }
                    quotes?.forEach { q ->
                        val diff = q.total?.let { t -> if (totalRub > 0) (t / totalRub - 1) * 100 else null }
                        val tag = when {
                            q.total == null -> q.note
                            diff == null -> ""
                            diff < -0.05 -> " (скидка ${fmt(-diff)}% к цене)"
                            diff > 0.05 -> " (комиссия +${fmt(diff)}%)"
                            else -> " (без наценки)"
                        }
                        Text("• ${q.label}: ${q.total?.let { fmt(it) + " ₽" } ?: "—"}$tag", color = if (diff != null && diff < -0.05) Green else Muted, fontSize = 12.sp)
                    }
                    if (cur != "RUB" && rates != null) {
                        Text("≈ ${fmt(totalRub / rate)} ${sym[cur]} по курсу ЦБ: 1 ${sym[cur]} = ${fmt(rate)} ₽", color = Muted, fontSize = 11.sp)
                    }
                    ratesError?.let { Text("$it Доступны только рубли.", color = Orange, fontSize = 11.sp) }
                    Text(
                        "Сумма в рублях — это цена × количество без комиссии способа оплаты и без курса FunPay: точную сумму к оплате посчитает и покажет сам FunPay. Курс в $/€ справочный. Если на балансе не хватает денег, FunPay предложит пополнить недостающую сумму; ссылка откроется только после нажатия кнопки.",
                        color = Muted, fontSize = 11.sp
                    )
                    if (needChoice) Text("Выберите способ оплаты.", color = Orange, fontSize = 12.sp)
                    if (!qtyOk) Text("Укажите количество от 1" + if (o.stock > 0) " до ${o.stock}." else ".", color = Orange, fontSize = 12.sp)
                    if (totalRub > 100.0) Text("В приложении пока тестовый лимит покупки 100 ₽.", color = Orange, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.material3.OutlinedButton(onClick = { uri.openUri(o.url) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Перейти к оплате на FunPay")
                    }
                }
            },
            confirmButton = {
                val step = buyResult?.confirm
                if (step != null) androidx.compose.material3.Button(
                    enabled = !buyBusy,
                    onClick = {
                        if (buyBusy) return@Button
                        buyBusy = true; buyError = null
                        buyScope.launch {
                            confirmOrder(step).fold({ buyResult = it }, { e -> buyError = e.message ?: "Не удалось подтвердить заказ" })
                            buyBusy = false
                        }
                    }
                ) { Text(if (buyBusy) "Оформляю…" else "Подтвердить заказ") }
                else androidx.compose.material3.Button(
                    enabled = canBuy && !buyBusy && buyResult == null,
                    onClick = {
                        if (buyBusy) return@Button
                        buyBusy = true; buyError = null
                        buyScope.launch {
                            buy(o.url, buyValues.toMap()).fold({ buyResult = it }, { e -> buyError = e.message ?: "Не удалось оформить покупку" })
                            buyBusy = false
                        }
                    }
                ) { Text(if (buyBusy) "Оформляю…" else "Оплатить ${fmt(totalRub)} ₽") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(enabled = !buyBusy, onClick = { confirmBuy = false; buyResult = null; buyError = null }) { Text("Закрыть", color = Accent) }
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

@Composable
private fun BalancePanel(balance: Double?, total: Double?, source: String) {
    if (total == null) return
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    fun f(v: Double) = String.format(java.util.Locale.US, "%.2f", v)
    Spacer(Modifier.height(4.dp))
    when {
        balance == null -> Text("Баланс FunPay неизвестен: сумма ${f(total)} ₽ ($source).", color = Muted, fontSize = 12.sp)
        balance + 1e-9 >= total -> Text(
            "Баланс ${f(balance)} ₽ покрывает ${f(total)} ₽ ($source). После покупки с баланса останется ${f(balance - total)} ₽.",
            color = Green, fontSize = 12.sp
        )
        else -> {
            Text(
                "Не хватает ${f(total - balance)} ₽: баланс ${f(balance)} ₽, к оплате ${f(total)} ₽ ($source).",
                color = Orange, fontSize = 12.sp
            )
            androidx.compose.material3.OutlinedButton(onClick = { uri.openUri("https://funpay.com/account/balance") }, modifier = Modifier.fillMaxWidth()) {
                Text("Пополнить баланс на FunPay")
            }
        }
    }
}
