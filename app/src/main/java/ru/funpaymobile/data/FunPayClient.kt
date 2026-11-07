package ru.funpaymobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class FunPayException(message: String, val kind: Kind = Kind.Other, val status: Int? = null) : Exception(message) {
    enum class Kind { InvalidKey, Network, Blocked, Parse, Other }
}

/**
 * Неофициальный клиент сайта funpay.com, работающий с cookie golden_key ВАШЕГО аккаунта.
 * Официального API у FunPay нет: клиент разбирает HTML-страницы, поэтому при изменении
 * вёрстки сайта отдельные функции могут перестать работать.
 *
 * Безопасность: ключ уходит только на https://funpay.com (любой другой хост блокируется,
 * редиректы обрабатываются вручную), в логи не пишется.
 */
class FunPayClient(goldenKey: String) {

    private val key = goldenKey.trim()

    init {
        if (!KEY_REGEX.matches(key)) {
            throw FunPayException(
                "Ключ выглядит некорректно: нужна одна строка без пробелов, кавычек и запятых.",
                FunPayException.Kind.InvalidKey
            )
        }
    }

    private val http = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    var userId: Long = 0
        private set
    var username: String = ""
        private set
    var balanceText: String = ""
        private set
    var balanceRub: Int = 0
        private set
    var activeSales: Int = 0
        private set

    var ownAvatarUrl: String? = null
        private set

    private var csrf = ""
    private val extraCookies = linkedMapOf<String, String>()
    @Volatile private var refreshSession = false
    private var session: String? = null
    private var lastLogin = 0L

    // ---------------------------------------------------------------- сеть

    private fun safeUrl(url: String?): String? {
        val u = url?.toHttpUrlOrNull() ?: return null
        return if (u.scheme == "https" && u.host == HOST) u.toString() else null
    }

    private fun urlEncode(v: String): String = java.net.URLEncoder.encode(v, "UTF-8")

    /** Форма в том же виде, как её отправляет браузер (с charset), в отличие от FormBody OkHttp. */
    private fun formBody(pairs: List<Pair<String, String>>): RequestBody =
        pairs.joinToString("&") { "${urlEncode(it.first)}=${urlEncode(it.second)}" }
            .toRequestBody("application/x-www-form-urlencoded; charset=UTF-8".toMediaType())

    private fun storeCookies(headers: List<String>, pageLoad: Boolean = false) {
        for (h in headers) {
            val nv = h.substringBefore(";")
            val name = nv.substringBefore("=").trim()
            val value = nv.substringAfter("=", "").trim()
            when {
                name.isEmpty() -> Unit
                name == "PHPSESSID" -> if (value.isNotEmpty() && value != "deleted" && (pageLoad || session == null || refreshSession)) {
                    session = value
                    refreshSession = false
                }
                name in SKIP_COOKIES -> Unit
                value.isEmpty() || value == "deleted" -> extraCookies.remove(name)
                COOKIE_TOKEN.matches(name) && COOKIE_VALUE.matches(value) -> extraCookies[name] = value
            }
        }
    }

    private fun errorSnippet(body: String): String {
        if (body.isBlank()) return ""
        val msg = try { JSONObject(body).optString("msg") } catch (e: Exception) { "" }
        val text = msg.ifBlank { Jsoup.parse(body).text() }
        return text.replace(Regex("\\s+"), " ").trim().take(200)
    }

    private class RedirectStop(val url: String) : Exception()

    private fun call(url: String, form: RequestBody? = null, ajax: Boolean = false, referer: String? = null, hops: Int = 0, follow: Boolean = true): String {
        val target = safeUrl(url)
            ?: throw FunPayException("Запрос к стороннему адресу заблокирован для защиты ключа.", FunPayException.Kind.Other)
        val cookie = buildString {
            append("golden_key=").append(key).append("; cookie_prefs=1; locale=ru")
            extraCookies.forEach { (k, v) -> append("; ").append(k).append("=").append(v) }
            session?.let { append("; PHPSESSID=").append(it) }
        }
        val rb = Request.Builder()
            .url(target)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "ru")
            .header("Cookie", cookie)
        if (ajax) rb.header("Accept", "*/*").header("X-Requested-With", "XMLHttpRequest")
        if (form != null) {
            rb.post(form)
            rb.header("Origin", BASE)
        }
        safeUrl(referer)?.let { rb.header("Referer", it) }

        return try {
            http.newCall(rb.build()).execute().use { r ->
                storeCookies(r.headers("Set-Cookie"), pageLoad = !ajax)
                when {
                    r.isRedirect -> {
                        if (hops >= 3) throw FunPayException("Слишком много перенаправлений.", FunPayException.Kind.Other)
                        val loc = r.header("Location")
                            ?: throw FunPayException("Некорректный ответ сервера.", FunPayException.Kind.Other)
                        val next = r.request.url.resolve(loc)?.toString()
                            ?: throw FunPayException("Некорректный ответ сервера.", FunPayException.Kind.Other)
                        if (next.endsWith("account/login")) {
                            throw FunPayException("Сессия FunPay недействительна: войдите заново.", FunPayException.Kind.InvalidKey, r.code)
                        }
                        if (!follow) throw RedirectStop(next)
                        call(next, null, ajax, referer, hops + 1)
                    }
                    r.code == 403 || r.code == 429 -> throw FunPayException(
                        "FunPay отклонил запрос (код ${r.code}). Возможно, включена защита от ботов или слишком частые запросы. " +
                            "Откройте funpay.com в браузере, пройдите проверку и повторите позже.",
                        FunPayException.Kind.Blocked, r.code
                    )
                    r.code in 500..599 -> throw FunPayException("Сервер FunPay временно недоступен (код ${r.code}).", FunPayException.Kind.Network, r.code)
                    !r.isSuccessful -> {
                        val snippet = errorSnippet(r.peekBody(4096).string())
                        throw FunPayException(
                            "FunPay вернул код ${r.code}" + if (snippet.isNotEmpty()) ": $snippet" else ".",
                            FunPayException.Kind.Other, r.code
                        )
                    }
                    else -> r.body?.string().orEmpty()
                }
            }
        } catch (e: IOException) {
            throw FunPayException("Нет соединения с funpay.com. Проверьте интернет (и VPN).", FunPayException.Kind.Network)
        }
    }

    private fun runner(objects: JSONArray, request: JSONObject?, referer: String = "$BASE/chat/"): JSONObject {
        if (System.currentTimeMillis() - lastLogin > SESSION_TTL_MS) loginSync()
        val body = formBody(
            listOf(
                "objects" to objects.toString(),
                "request" to (request?.toString() ?: "False"),
                "csrf_token" to csrf
            )
        )
        val text = call("$BASE/runner/", body, ajax = true, referer = referer)
        return try {
            JSONObject(text)
        } catch (e: Exception) {
            throw FunPayException("FunPay вернул неожиданный ответ (возможно, сессия истекла или нужна проверка в браузере).", FunPayException.Kind.Parse)
        }
    }

    // ---------------------------------------------------------------- вход

    suspend fun login() = withContext(Dispatchers.IO) { loginSync() }

    private fun loginSync() {
        refreshSession = true // PHPSESSID берём из ответа главной страницы, как делает браузер
        val html = try { call("$BASE/") } finally { refreshSession = false }
        val doc = Jsoup.parse(html, "$BASE/")
        val name = doc.selectFirst("div.user-link-name")?.text()?.trim()
        if (name.isNullOrEmpty()) {
            throw FunPayException(
                "FunPay не считает вас авторизованным: ключ неверный или устарел. Проверьте golden_key.",
                FunPayException.Kind.InvalidKey
            )
        }
        try {
            val app = JSONObject(doc.body().attr("data-app-data"))
            userId = app.getLong("userId")
            csrf = app.getString("csrf-token")
        } catch (e: Exception) {
            throw FunPayException("Не удалось прочитать данные сессии на главной странице (сайт мог измениться).", FunPayException.Kind.Parse)
        }
        username = name
        val bal = doc.selectFirst("span.badge-balance")?.text()?.let(::cleanText).orEmpty()
        balanceText = bal
        balanceRub = Math.round(parseAmount(bal)).toInt()
        activeSales = doc.selectFirst("span.badge-trade")?.text()?.trim()?.toIntOrNull() ?: 0
        lastLogin = System.currentTimeMillis()
    }

    // ---------------------------------------------------------------- заказы

    suspend fun orders(sales: Boolean): List<Order> = withContext(Dispatchers.IO) {
        val doc = Jsoup.parse(call(if (sales) "$BASE/orders/trade" else "$BASE/orders/"), "$BASE/")
        requireAuthorized(doc)
        val kind = if (sales) OrderKind.Sale else OrderKind.Purchase
        doc.select("a.tc-item").mapNotNull { el ->
            val id = el.selectFirst("div.tc-order")?.text()?.trim().orEmpty()
            if (id.isEmpty()) return@mapNotNull null
            val status = when {
                el.hasClass("warning") -> OrderStatus.Refunded
                el.hasClass("info") -> OrderStatus.Paid
                else -> OrderStatus.Completed
            }
            val title = el.selectFirst("div.order-desc div")?.text()?.trim()
                ?: el.selectFirst("div.order-desc")?.text()?.trim().orEmpty()
            val priceStr = el.selectFirst("div.tc-price")?.text()?.let(::cleanText)
            Order(
                id = id,
                title = title.ifEmpty { "Заказ $id" },
                buyer = el.selectFirst("div.media-user-name")?.text()?.trim().orEmpty(),
                date = (el.selectFirst("div.tc-date-time") ?: el.selectFirst("div.tc-date-left"))?.text()?.trim().orEmpty(),
                amountRub = Math.round(parseAmount(priceStr)).toInt(),
                status = status,
                description = el.selectFirst("div.order-desc div.text-muted")?.text()?.trim().orEmpty(),
                kind = kind,
                priceText = priceStr,
                webUrl = safeUrl(el.absUrl("href")),
                counterpartId = userIdFrom(el.selectFirst("div.media-user-name span")?.attr("data-href"))
            )
        }.distinctBy { it.id }
    }

    // ---------------------------------------------------------------- чаты

    suspend fun chats(): List<ChatPreview> = withContext(Dispatchers.IO) {
        var runnerError: FunPayException? = null
        val fromRunner = try {
            chatsFromRunner()
        } catch (e: FunPayException) {
            if (e.kind == FunPayException.Kind.InvalidKey) throw e
            runnerError = e
            emptyList()
        }
        if (fromRunner.isNotEmpty()) return@withContext fromRunner
        // Запасной путь: список диалогов со страницы чатов.
        val doc = Jsoup.parse(call("$BASE/chat/"), "$BASE/")
        requireAuthorized(doc)
        val list = parseContacts(doc)
        if (list.isEmpty() && runnerError != null) throw runnerError
        list
    }

    private fun chatsFromRunner(): List<ChatPreview> {
        val objects = JSONArray().put(
            JSONObject().put("type", "chat_bookmarks").put("id", userId).put("tag", "00000000").put("data", false)
        )
        val json = runner(objects, null)
        val arr = json.optJSONArray("objects") ?: throw FunPayException("Пустой ответ при загрузке чатов.", FunPayException.Kind.Parse)
        var html: String? = null
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("type") == "chat_bookmarks") {
                html = o.optJSONObject("data")?.optString("html")
            }
        }
        if (html.isNullOrEmpty()) return emptyList()
        return parseContacts(Jsoup.parse(html))
    }

    private fun parseContacts(doc: Document): List<ChatPreview> =
        doc.select("a.contact-item").mapNotNull { a ->
            val id = a.attr("data-id").trim()
            if (id.isEmpty()) return@mapNotNull null
            ChatPreview(
                id = id,
                name = a.selectFirst("div.media-user-name")?.text()?.trim().orEmpty().ifEmpty { "Диалог $id" },
                lastMessage = a.selectFirst("div.contact-item-message")?.text()?.trim().orEmpty(),
                time = a.selectFirst("div.contact-item-time")?.text()?.trim().orEmpty(),
                unread = if (a.hasClass("unread")) 1 else 0,
                avatarUrl = avatarFrom(a.selectFirst("div.avatar-photo"))
            )
        }.distinctBy { it.id }

    suspend fun chatMessages(nodeId: String): List<Message> = withContext(Dispatchers.IO) {
        val doc = Jsoup.parse(call("$BASE/chat/?node=${java.net.URLEncoder.encode(nodeId, "UTF-8")}"), "$BASE/")
        requireAuthorized(doc)
        parseMessages(doc)
    }

    private fun parseMessages(doc: Document): List<Message> {
        var lastAuthor = ""
        var index = 0L
        return doc.select("div.chat-msg-item").mapNotNull { item ->
            index++
            item.selectFirst(".chat-msg-author-link")?.text()?.trim()?.takeIf { it.isNotEmpty() }?.let { lastAuthor = it }
            var text = item.selectFirst(".chat-msg-text")?.wholeText()?.trim().orEmpty()
            if (text.isEmpty() && item.selectFirst(".chat-img, .chat-msg-image, img") != null) text = "[вложение]"
            if (text.isEmpty()) return@mapNotNull null
            val dateEl = item.selectFirst(".chat-msg-date")
            val time = dateEl?.attr("title")?.takeIf { it.isNotBlank() } ?: dateEl?.text()?.trim().orEmpty()
            val mine = lastAuthor.isNotEmpty() && lastAuthor == username
            Message(
                id = item.id().filter { it.isDigit() }.toLongOrNull() ?: index,
                text = text,
                fromMe = mine,
                time = time,
                author = if (mine) null else lastAuthor.ifEmpty { null }
            )
        }
    }

    suspend fun sendMessage(nodeId: String, text: String) = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext
        val node: Any = nodeId.toLongOrNull() ?: nodeId
        // Как в актуальной библиотеке: вместе с запросом отправляется объект chat_node.
        val objects = JSONArray().put(
            JSONObject().put("type", "chat_node").put("id", node).put("tag", "00000000")
                .put("data", JSONObject().put("node", node).put("last_message", -1).put("content", ""))
        )
        val request = JSONObject().put("action", "chat_message").put(
            "data", JSONObject().put("node", node).put("last_message", -1).put("content", text)
        )
        val referer = "$BASE/chat/?node=${urlEncode(nodeId)}"
        val resp = try {
            runner(objects, request, referer)
        } catch (e: FunPayException) {
            if (e.status == 400 || e.status == 428) {
                loginSync() // обновляем csrf-токен и сессию, пробуем ещё раз
                runner(objects, request, referer)
            } else throw e
        }
        val r = resp.opt("response")
        if (r == null || r == JSONObject.NULL || r == false || (r is JSONObject && r.length() == 0)) {
            throw FunPayException("FunPay не подтвердил отправку сообщения.", FunPayException.Kind.Other)
        }
        val err = (r as? JSONObject)?.opt("error")
        if (err != null && err != JSONObject.NULL && err != false) {
            throw FunPayException("FunPay не принял сообщение: $err", FunPayException.Kind.Other)
        }
    }

    // ---------------------------------------------------------------- мои предложения

    suspend fun myLots(): List<Lot> = withContext(Dispatchers.IO) {
        val doc = Jsoup.parse(call("$BASE/users/$userId/"), "$BASE/")
        ownAvatarUrl = avatarFrom(doc.selectFirst("div.avatar-photo")) ?: ownAvatarUrl
        val result = mutableListOf<Lot>()
        for (container in doc.select("div.offer-list-title-container")) {
            val category = container.selectFirst("div.offer-list-title a")?.text()?.trim().orEmpty()
            val scope = container.parent() ?: continue
            for (el in scope.select("a.tc-item")) {
                val href = el.absUrl("href")
                val id = href.substringAfter("id=", "").ifEmpty { href.hashCode().toString() }
                val priceStr = el.selectFirst("div.tc-price")?.let { it.attr("data-s").ifBlank { it.text() } }?.let(::cleanText)
                result += Lot(
                    id = id,
                    title = el.selectFirst("div.tc-desc-text")?.text()?.trim().orEmpty().ifEmpty { "Предложение $id" },
                    priceRub = Math.round(parseAmount(priceStr)).toInt(),
                    category = category,
                    active = true,
                    description = el.selectFirst("div.tc-server")?.text()?.trim().orEmpty(),
                    priceText = priceStr?.let { if (it.any { c -> !c.isDigit() && c != '.' }) it else "$it ₽" },
                    webUrl = safeUrl(href)
                )
            }
        }
        result.distinctBy { it.id }
    }

    // ---------------------------------------------------------------- площадка

    suspend fun marketGames(): List<MarketGame> = withContext(Dispatchers.IO) {
        val doc = Jsoup.parse(call("$BASE/"), "$BASE/")
        val games = doc.select("div.promo-game-item").mapNotNull { g ->
            val titleEl = g.selectFirst("div.game-title")
            val name = titleEl?.text()?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            var cats = g.select("ul a[href]").mapNotNull { a ->
                val url = safeUrl(a.absUrl("href"))
                val title = a.text().trim()
                if (url != null && title.isNotEmpty()) MarketCategory(title, url) else null
            }
            if (cats.isEmpty()) {
                cats = listOfNotNull(titleEl?.selectFirst("a")?.let { a -> safeUrl(a.absUrl("href"))?.let { MarketCategory(name, it) } })
            }
            MarketGame(name, cats)
        }.filter { it.categories.isNotEmpty() }
        if (games.isEmpty()) {
            throw FunPayException("Не удалось разобрать список игр на главной странице (структура сайта могла измениться).", FunPayException.Kind.Parse)
        }
        games
    }

    suspend fun marketOffers(categoryUrl: String, categoryTitle: String): List<MarketOffer> = withContext(Dispatchers.IO) {
        val doc = Jsoup.parse(call(categoryUrl), "$BASE/")
        doc.select("a.tc-item").take(80).mapNotNull { el ->
            val href = el.absUrl("href")
            val url = safeUrl(href) ?: return@mapNotNull null
            val priceStr = el.selectFirst("div.tc-price")?.let { it.text() }?.let(::cleanText)
            MarketOffer(
                id = href.substringAfter("id=", href),
                title = el.selectFirst("div.tc-desc-text")?.text()?.trim().orEmpty().ifEmpty { "Предложение" },
                category = categoryTitle,
                priceRub = Math.round(parseAmount(priceStr)).toInt(),
                sellerName = (el.selectFirst("div.tc-seller div.media-user-name") ?: el.selectFirst("div.media-user-name"))
                    ?.text()?.trim().orEmpty(),
                sellerInfo = sellerInfoOf(el),
                stock = el.selectFirst("div.tc-amount")?.text()?.filter { it.isDigit() }?.toIntOrNull() ?: 0,
                description = "",
                priceText = priceStr,
                url = url,
                sellerId = userIdFrom((el.selectFirst("div.tc-seller div.media-user-name span") ?: el.selectFirst("div.media-user-name span"))?.attr("data-href")),
                sellerAvatar = avatarFrom(el.selectFirst("div.avatar-photo"))
            )
        }
    }

    // ---------------------------------------------------------------- заказ, предложение, новый лот

    /** Поля заказа со страницы заказа (название параметра → значение). */
    suspend fun orderInfo(orderId: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val id = orderId.removePrefix("#").filter { it.isLetterOrDigit() }
        if (id.isEmpty()) return@withContext emptyList()
        val doc = Jsoup.parse(call("$BASE/orders/$id/"), "$BASE/")
        requireAuthorized(doc)
        val review = doc.selectFirst(".review-item-text, .review-compiled-review")?.text()?.trim().orEmpty()
        paramItems(doc) + (if (review.isNotEmpty()) listOf("Отзыв" to review) else emptyList())
    }

    suspend fun offerInfo(offerUrl: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        paramItems(Jsoup.parse(call(offerUrl), "$BASE/"))
    }

    private fun paramItems(doc: Document): List<Pair<String, String>> =
        doc.select("div.param-item").mapNotNull { p ->
            val k = p.selectFirst("h5")?.text()?.trim().orEmpty()
            val v = (p.selectFirst("div") ?: p).text().trim().removePrefix(k).trim()
            if (k.isEmpty() || v.isEmpty()) null else k to v
        }.take(20)

    /** Что видит приложение вместо формы: заголовок, текст страницы, формы и ссылки на создание лота. */
    private fun noFormMessage(doc: Document, node: String): String {
        val title = doc.title().take(80)
        val text = (doc.selectFirst("div.alert, div.content-with-cd, div.content, body"))?.text()?.take(220).orEmpty()
        val forms = doc.select("form").take(4).joinToString("; ") { f -> (f.attr("action").ifEmpty { "(без action)" }) + " поля:" + f.select("input[name],select[name],textarea[name]").take(8).joinToString(",") { it.attr("name") } }
        val links = doc.select("a[href*=offerEdit]").take(3).joinToString(", ") { it.attr("href") }
        return "Не удалось найти форму создания лота в разделе $node. Заголовок: «$title». Текст страницы: «$text». Формы: ${forms.ifEmpty { "нет" }}. Ссылки offerEdit: ${links.ifEmpty { "нет" }}."
    }

    private fun fetchDoc(url: String): Document {
        var body = call(url)
        if (body.trimStart().startsWith("{")) {
            body = try { JSONObject(body).optString("html") } catch (e: Exception) { "" }
        }
        val doc = Jsoup.parse(body, "$BASE/")
        requireAuthorizedOrFragment(doc)
        return doc
    }

    private fun findLotForm(doc: Document): Element? =
        doc.selectFirst("form.form-offer-editor")
            ?: doc.select("form").firstOrNull { it.selectFirst("input[name=offer_id]") != null }
            ?: doc.select("form").firstOrNull { f -> f.select("[name^=fields]").isNotEmpty() }

    private fun findFilterForm(doc: Document): Element? =
        doc.select("form").firstOrNull { it.attr("action").contains("Filter", true) || it.selectFirst("input[name=query]") != null }

    private fun choiceLinks(doc: Document, pageUrl: String): List<MarketCategory> =
        doc.select("a[href]").mapNotNull { a ->
            val href = a.absUrl("href")
            val ok = (href.contains("offerEdit") && !href.contains("setlocale") && href != pageUrl) ||
                Regex("^https://funpay\\.com/lots/\\d+/?$").containsMatchIn(href)
            val t = a.text().trim()
            if (ok && t.isNotEmpty()) MarketCategory(t, href) else null
        }.distinctBy { it.url }.take(60)

    /**
     * Открывает страницу создания лота. Если на ней форма — возвращает её; если вместо формы выбор игры/раздела
     * (как в «Промокодах») — возвращает варианты и признак поиска, а форма загружается после выбора.
     */
    suspend fun lotStep(categoryUrl: String, categoryTitle: String): LotStep = withContext(Dispatchers.IO) {
        val pageUrl = if (categoryUrl.contains("offerEdit")) categoryUrl else {
            val node = Regex("/lots/(\\d+)/?").find(categoryUrl)?.groupValues?.get(1)
                ?: throw FunPayException("Для этого раздела (например, игровая валюта) создайте лот на сайте FunPay.", FunPayException.Kind.Other)
            "$BASE/lots/offerEdit?node=$node"
        }
        val doc = fetchDoc(pageUrl)
        val form = findLotForm(doc)
        if (form != null) {
            val fields = parseForm(form)
            if (fields.none { it.type != FieldType.Hidden }) {
                throw FunPayException("Форма лота пуста или не распознана.", FunPayException.Kind.Parse)
            }
            return@withContext LotStep.Form(LotForm(categoryTitle, fields, dumpForm(form)))
        }
        val links = choiceLinks(doc, pageUrl)
        val filter = findFilterForm(doc)
        if (links.isNotEmpty() || filter != null) return@withContext LotStep.Choices(pageUrl, links, filter != null)
        throw FunPayException(noFormMessage(doc, pageUrl), FunPayException.Kind.Parse)
    }

    /** Поиск на странице выбора (форма фильтра отправляется так же, как её отправляет сайт). */
    suspend fun lotSearch(pageUrl: String, query: String): List<MarketCategory> = withContext(Dispatchers.IO) {
        val doc = fetchDoc(pageUrl)
        val f = findFilterForm(doc) ?: throw FunPayException("Поиск на странице не найден.", FunPayException.Kind.Parse)
        val action = safeUrl(f.absUrl("action").ifBlank { null })
            ?: throw FunPayException("Адрес поиска недопустим.", FunPayException.Kind.Other)
        val params = f.select("input[name]").filter { it.attr("type") !in setOf("submit", "button") }
            .map { it.attr("name") to (if (it.attr("name") == "query") query else it.attr("value")) }
            .ifEmpty { listOf("query" to query) }
        val body = if (f.attr("method").equals("post", true)) {
            call(action, formBody(params), ajax = true, referer = pageUrl)
        } else {
            val qs = params.joinToString("&") { "${urlEncode(it.first)}=${urlEncode(it.second)}" }
            call(action + (if (action.contains("?")) "&" else "?") + qs, ajax = true, referer = pageUrl)
        }
        val html = if (body.trimStart().startsWith("{")) {
            try { JSONObject(body).optString("html").ifEmpty { body } } catch (e: Exception) { body }
        } else body
        val items = choiceLinks(Jsoup.parse(html, "$BASE/"), pageUrl)
        if (items.isEmpty()) {
            throw FunPayException("Ничего не найдено. Ответ сайта: «${errorSnippet(body).take(150)}»", FunPayException.Kind.Other)
        }
        items
    }

    /** Структура формы для диагностики: имена, типы, скрытость, варианты. Значения токенов не включаются. */
    private fun dumpForm(form: Element): String = buildString {
        append("action=").append(form.attr("action")).append(" method=").append(form.attr("method")).append('\n')
        for (el in form.select("input, textarea, select, button")) {
            val name = el.attr("name")
            val hiddenBy = el.parents().firstOrNull { it.hasClass("hidden") || it.attr("style").replace(" ", "").contains("display:none") }
            append(el.tagName()).append(' ').append(name.ifEmpty { "(без имени)" })
            if (el.tagName() == "input") append(" type=").append(el.attr("type").ifEmpty { "text" })
            if (hiddenBy != null) append(" СКРЫТО(").append(hiddenBy.className().take(30)).append(')')
            if (el.hasAttr("disabled")) append(" disabled")
            if (el.tagName() == "select") {
                append(" опции=[")
                append(el.select("option").take(8).joinToString("|") { (if (it.hasAttr("selected")) "*" else "") + it.attr("value") + "=" + it.text().take(15) })
                append(']')
            } else if (el.tagName() != "button") {
                val v = if (name.contains("csrf", true) || name.contains("token", true)) "…" else el.attr("value").take(15)
                append(" value=").append(v)
            }
            append('\n')
        }
    }

    /**
     * Оформляет покупку так же, как браузер: отправляет форму со страницы предложения.
     * Редиректы не выполняются: заказ → открываем в приложении, оплата/пополнение → отдаём ссылку пользователю.
     */
    private fun findBuyForm(offerUrl: String): Element {
        val page = Jsoup.parse(call(offerUrl), "$BASE/")
        requireAuthorized(page)
        return page.select("form").filter { it.selectFirst("input[name=id], input[name=offer_id]") != null }
            .let { c -> c.firstOrNull { it.attr("action").contains("order") } ?: c.firstOrNull() }
            ?: throw FunPayException("Форма покупки на странице не найдена. Нажмите «Перейти к оплате на FunPay».", FunPayException.Kind.Parse)
    }

    /** Поля формы покупки (способ оплаты, количество и т. п.), которые должен заполнить пользователь. */
    suspend fun buyFields(offerUrl: String): List<FormField> = withContext(Dispatchers.IO) {
        parseForm(findBuyForm(offerUrl))
    }

    suspend fun buy(offerUrl: String, chosen: Map<String, String>): BuyOutcome = withContext(Dispatchers.IO) {
        try {
            buyOnce(offerUrl, chosen)
        } catch (e: FunPayException) {
            // «Обновите страницу и повторите попытку» (400/428): берём свежую сессию и форму и пробуем один раз ещё.
            if (e.status == 400 || e.status == 428) {
                loginSync()
                buyOnce(offerUrl, chosen)
            } else throw e
        }
    }

    private fun buyOnce(offerUrl: String, chosen: Map<String, String>): BuyOutcome {
        val form = findBuyForm(offerUrl)
        val payload = parseForm(form).mapNotNull { f ->
            val v = chosen[f.name] ?: f.value
            when {
                f.name.isBlank() -> null
                f.type == FieldType.Check -> if (v == "on") f.name to "on" else null
                f.name == "amount" && v.isBlank() -> f.name to "1"
                else -> f.name to v
            }
        }
        val action = safeUrl(form.absUrl("action").ifBlank { null }) ?: "$BASE/orders/new"
        return submitBuy(action, payload, offerUrl)
    }

    /** Второй шаг: подтверждение заказа на странице, которую показал FunPay. */
    suspend fun buyConfirm(step: ConfirmStep): BuyOutcome = withContext(Dispatchers.IO) {
        val r = submitBuy(step.action, step.fields, step.referer)
        if (r.kind == BuyOutcome.Kind.Confirm) {
            throw FunPayException("FunPay снова показал страницу подтверждения: заказ не создан.", FunPayException.Kind.Other)
        }
        r
    }

    /**
     * Отправляет форму как браузер, редиректы не выполняет. Успехом считается только переход на страницу заказа;
     * переход на оплату отдаётся ссылкой; страница подтверждения возвращается как шаг; всё остальное — ошибка.
     */
    private fun submitBuy(action: String, payload: List<Pair<String, String>>, referer: String): BuyOutcome {
        val outcome = try {
            val body = call(action, formBody(payload), ajax = false, referer = referer, follow = false)
            val j = try { JSONObject(body) } catch (e: Exception) { null }
            if (j != null) {
                val u = j.optString("url")
                val msg = j.optString("msg")
                if (u.isNotBlank()) classifyBuy(u.let { if (it.startsWith("/")) BASE + it else it }, msg)
                else throw FunPayException(msg.ifBlank { "FunPay не оформил заказ." }, FunPayException.Kind.Other)
            } else {
                val d = Jsoup.parse(body, "$BASE/")
                confirmStep(d, action)?.let { BuyOutcome(BuyOutcome.Kind.Confirm, null, "FunPay показал страницу подтверждения заказа.", it) }
                    ?: run {
                        val text = (d.selectFirst("div.alert, .alert-danger, p.lead, .error") ?: d.selectFirst("h1") ?: d.selectFirst("title"))?.text()?.take(250).orEmpty()
                        throw FunPayException("Заказ не создан: FunPay вернул страницу вместо подтверждения. ${if (text.isNotBlank()) "Ответ: «$text»." else ""}", FunPayException.Kind.Other)
                    }
            }
        } catch (r: RedirectStop) {
            classifyBuy(r.url, "")
        }
        if (outcome.kind == BuyOutcome.Kind.Order && outcome.url != null) {
            val confirmed = try { call(outcome.url); true } catch (e: FunPayException) { false }
            return BuyOutcome(
                BuyOutcome.Kind.Order, outcome.url,
                outcome.message + if (confirmed) " Подтверждено FunPay." else " Страницу заказа открыть не удалось, проверьте раздел «Заказы»."
            )
        }
        return outcome
    }

    private fun confirmStep(doc: Document, referer: String): ConfirmStep? {
        val form = doc.select("form")
            .filter { it.selectFirst("input[name=query]") == null && !it.attr("action").contains("setlocale") && it.select("input[name],select[name]").isNotEmpty() }
            .let { c -> c.firstOrNull { it.attr("action").contains("order") } ?: c.firstOrNull() } ?: return null
        val action = safeUrl(form.absUrl("action").ifBlank { null }) ?: return null
        val fields = parseForm(form).mapNotNull { f ->
            when {
                f.name.isBlank() -> null
                f.type == FieldType.Check -> if (f.value == "on") f.name to "on" else null
                else -> f.name to f.value
            }
        }.toMutableList()
        form.selectFirst("button[name], input[type=submit][name]")?.let { b ->
            if (fields.none { it.first == b.attr("name") }) fields += b.attr("name") to b.attr("value")
        }
        val rows = paramItems(doc) + doc.select("table tr").mapNotNull { tr ->
            val k = tr.selectFirst("th")?.text()?.trim().orEmpty()
            val v = tr.selectFirst("td")?.text()?.trim().orEmpty()
            if (k.isEmpty() || v.isEmpty()) null else k to v
        }
        val scope = form.parents().firstOrNull { it.hasClass("content") || it.hasClass("content-with-cd") } ?: form.parent() ?: doc.body()
        return ConfirmStep(action, fields, referer, rows.take(12), scope.text().take(500), dumpForm(form))
    }

    private fun classifyBuy(url: String, msg: String): BuyOutcome {
        val order = Regex("^https://funpay\\.com/orders/([A-Za-z0-9]+)/?$").find(url)
        return when {
            order != null -> BuyOutcome(BuyOutcome.Kind.Order, url, msg.ifBlank { "Заказ #${order.groupValues[1]} создан." })
            url.startsWith("https://") -> BuyOutcome(BuyOutcome.Kind.Payment, url, msg.ifBlank { "FunPay просит оплатить или пополнить баланс." })
            else -> throw FunPayException("FunPay вернул неожиданную ссылку.", FunPayException.Kind.Other)
        }
    }

    /** Формы на странице предложения (для диагностики покупки). */
    suspend fun offerForms(offerUrl: String): String = withContext(Dispatchers.IO) {
        val doc = Jsoup.parse(call(offerUrl), "$BASE/")
        val forms = doc.select("form")
        if (forms.isEmpty()) "На странице нет форм" else forms.joinToString("\n---\n") { dumpForm(it) }
    }

    private fun requireAuthorizedOrFragment(doc: Document) {
        // Ответ может быть фрагментом без шапки сайта; полностью отсутствующая форма обрабатывается выше.
        if (doc.selectFirst("form") == null && doc.selectFirst("div.user-link-name") == null) {
            throw FunPayException("Сессия FunPay недействительна: войдите заново.", FunPayException.Kind.InvalidKey)
        }
    }

    private fun parseForm(form: Element): List<FormField> {
        val out = mutableListOf<FormField>()
        val radios = linkedMapOf<String, MutableList<Element>>()
        fun labelFor(el: Element, name: String): String {
            val byFor = el.id().takeIf { it.isNotEmpty() }?.let { form.selectFirst("label[for=$it]")?.text() }
            return (byFor ?: el.closest(".form-group")?.selectFirst("label")?.text() ?: el.attr("placeholder"))
                .trim().ifEmpty { name }
        }
        for (el in form.select("input, textarea, select")) {
            val name = el.attr("name")
            if (name.isBlank()) continue
            when (el.tagName()) {
                "textarea" -> out += FormField(name, labelFor(el, name), FieldType.Area, el.text(), required = el.hasAttr("required"))
                "select" -> {
                    // Как в актуальной библиотеке: скрытые группы формы не отправляются.
                    val opts = el.select("option").map { FormOption(it.attr("value"), it.text().trim()) }
                    val sel = el.selectFirst("option[selected]")?.attr("value") ?: opts.firstOrNull()?.value.orEmpty()
                    if (el.closest(".form-group")?.hasClass("hidden") == true) {
                        // Браузер отправляет и скрытые поля. Если значение не задано, а выбор есть, даём выбрать его самому.
                        val real = opts.filter { it.value.isNotBlank() }
                        when {
                            sel.isNotBlank() -> out += FormField(name, name, FieldType.Hidden, sel)
                            real.size == 1 -> out += FormField(name, name, FieldType.Hidden, real[0].value)
                            real.size > 1 -> out += FormField(name, labelFor(el, name).let { if (it == name) "Тип (поле формы)" else it }, FieldType.Select, "", opts, true)
                            else -> out += FormField(name, name, FieldType.Hidden, sel)
                        }
                        continue
                    }
                    out += FormField(name, labelFor(el, name), FieldType.Select, sel, opts, el.hasAttr("required"))
                }
                else -> when (el.attr("type").lowercase()) {
                    "hidden" -> out += FormField(name, name, FieldType.Hidden, el.attr("value"))
                    "checkbox" -> out += FormField(name, labelFor(el, name), FieldType.Check, if (el.hasAttr("checked")) "on" else "")
                    "radio" -> radios.getOrPut(name) { mutableListOf() }.add(el)
                    "submit", "button", "file", "image", "reset" -> Unit
                    "number" -> out += FormField(name, labelFor(el, name), FieldType.Number, el.attr("value"), required = el.hasAttr("required"))
                    else -> out += FormField(name, labelFor(el, name), FieldType.Text, el.attr("value"), required = el.hasAttr("required"))
                }
            }
        }
        for ((name, els) in radios) {
            val opts = els.map { r -> FormOption(r.attr("value"), (r.parent()?.text()?.trim()).orEmpty().ifEmpty { r.attr("value") }) }
            val sel = els.firstOrNull { it.hasAttr("checked") }?.attr("value") ?: els.first().attr("value")
            out += FormField(name, labelFor(els.first(), name), FieldType.Select, sel, opts)
        }
        return out
    }

    /** Публикует лот: отправляет заполненную форму на FunPay. Возвращает сообщение об успехе. */
    suspend fun publishLot(values: List<Pair<String, String>>, nodeId: String? = null): String = withContext(Dispatchers.IO) {
        if (System.currentTimeMillis() - lastLogin > SESSION_TTL_MS) loginSync()
        val fields = values.toMutableList()
        if (fields.none { it.first == "csrf_token" }) fields += "csrf_token" to csrf
        if (fields.none { it.first == "location" }) fields += "location" to "trade"
        val ref = "$BASE/lots/offerEdit?node=${nodeId ?: fields.firstOrNull { it.first == "node_id" }?.second.orEmpty()}"
        val body = try {
            call("$BASE/lots/offerSave", formBody(fields), ajax = true, referer = ref)
        } catch (e: FunPayException) {
            if (e.status != 400 && e.status != 428) throw e
            // Обновляем сессию и csrf-токен и пробуем один раз ещё.
            loginSync()
            val retry = fields.filter { it.first != "csrf_token" } + ("csrf_token" to csrf)
            call("$BASE/lots/offerSave", formBody(retry), ajax = true, referer = ref)
        }
        val resp = try {
            JSONObject(body)
        } catch (e: Exception) {
            throw FunPayException("FunPay вернул неожиданный ответ при сохранении лота.", FunPayException.Kind.Parse)
        }
        val err = resp.opt("error")
        val failed = err != null && err != JSONObject.NULL && err != false && err != 0
        val errs = resp.optJSONArray("errors")
        if (failed || (errs != null && errs.length() > 0)) {
            val msg = resp.optString("msg").ifEmpty { errs?.toString().orEmpty() }.ifEmpty { "FunPay отклонил лот. Проверьте заполнение полей." }
            throw FunPayException(msg, FunPayException.Kind.Other)
        }
        "Лот отправлен на FunPay"
    }

    // ---------------------------------------------------------------- профиль, отзывы

    suspend fun userProfile(id: Long): UserProfile = withContext(Dispatchers.IO) {
        val doc = Jsoup.parse(call("$BASE/users/$id/"), "$BASE/")
        requireAuthorized(doc)
        val name = doc.selectFirst("span.mr4")?.text()?.trim().orEmpty().ifEmpty { "Пользователь $id" }
        val avatar = avatarFrom(doc.selectFirst("div.avatar-photo"))
        if (id == userId && avatar != null) ownAvatarUrl = avatar
        val stars = doc.selectFirst("div.rating-stars")?.select("i.fas")?.size
        val ratingText = (doc.selectFirst("div.rating-full") ?: doc.selectFirst("div.rating-block"))
            ?.text()?.let(::cleanText).orEmpty()
        val online = doc.selectFirst("span.media-user-status")?.text()?.let { it.contains("Онлайн") || it.contains("Online") } ?: false
        UserProfile(id, name, avatar, online, stars, ratingText, parseReviews(doc), "$BASE/users/$id/")
    }

    // Разметка отзывов не проверялась на живом сайте: парсер намеренно терпимый к вариантам классов.
    private fun parseReviews(doc: Document): List<Review> {
        val items = doc.select("div.review-item").ifEmpty { doc.select("div.review-container") }
        return items.mapNotNull { el ->
            val text = el.selectFirst(".review-item-text, .review-text, .review-compiled-review")?.text()?.trim().orEmpty()
            if (text.isEmpty()) return@mapNotNull null
            val ratingClass = el.select("[class*=rating]").map { it.className() }
                .firstNotNullOfOrNull { Regex("rating(\\d)").find(it)?.groupValues?.get(1)?.toIntOrNull() }
            Review(
                author = el.selectFirst(".review-item-user, .media-user-name")?.text()?.trim().orEmpty(),
                text = text,
                rating = ratingClass,
                reply = el.selectFirst(".review-item-answer, .review-answer, .review-item-reply")?.text()?.trim()?.ifEmpty { null },
                date = el.selectFirst(".review-item-date, .text-muted")?.text()?.trim().orEmpty(),
                orderId = el.selectFirst("a[href*=/orders/]")?.attr("href")?.let { Regex("/orders/([A-Za-z0-9]+)").find(it)?.groupValues?.get(1) }
            )
        }.take(40)
    }

    /** Ответ на отзыв по заказу (тот же запрос, что использует сайт: orders/review). */
    suspend fun replyReview(orderId: String, text: String): String = withContext(Dispatchers.IO) {
        val id = orderId.removePrefix("#").filter { it.isLetterOrDigit() }
        if (id.isEmpty() || text.isBlank()) throw FunPayException("Пустой ответ или номер заказа.", FunPayException.Kind.Other)
        if (System.currentTimeMillis() - lastLogin > SESSION_TTL_MS) loginSync()
        val body = formBody(
            listOf(
                "authorId" to userId.toString(), "text" to text.trim(), "rating" to "5",
                "csrf_token" to csrf, "orderId" to id
            )
        )
        call("$BASE/orders/review", body, ajax = true, referer = "$BASE/orders/$id/")
        "Ответ отправлен"
    }

    // ---------------------------------------------------------------- вспомогательное

    private fun requireAuthorized(doc: Document) {
        if (doc.selectFirst("div.user-link-name") == null) {
            throw FunPayException("Сессия FunPay недействительна: войдите заново.", FunPayException.Kind.InvalidKey)
        }
    }

    private fun randomTag(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..10).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    private companion object {
        const val HOST = "funpay.com"
        const val BASE = "https://funpay.com"
        const val USER_AGENT = "FunPayMobile/0.7.0 (Android; unofficial client)"
        const val SESSION_TTL_MS = 20 * 60 * 1000L
        // Печатные ASCII-символы, кроме пробела, кавычки, запятой, точки с запятой и обратной косой черты.
        val KEY_REGEX = Regex("[\\x21\\x23-\\x2B\\x2D-\\x3A\\x3C-\\x5B\\x5D-\\x7E]{8,128}")

        val SKIP_COOKIES = setOf("golden_key", "cookie_prefs", "locale", "fav_games")
        val COOKIE_TOKEN = Regex("[A-Za-z0-9_\\-.]{1,64}")
        val COOKIE_VALUE = Regex("[\\x21\\x23-\\x2B\\x2D-\\x3A\\x3C-\\x5B\\x5D-\\x7E]{1,512}")

        fun avatarFrom(el: Element?): String? {
            val style = el?.attr("style").orEmpty()
            val raw = Regex("url\\(['\"]?([^)'\"]+)").find(style)?.groupValues?.get(1)?.trim() ?: return null
            val url = when {
                raw.startsWith("https://") -> raw
                raw.startsWith("//") -> "https:$raw"
                raw.startsWith("/") -> "https://funpay.com$raw"
                else -> return null
            }
            return url
        }

        fun sellerInfoOf(el: Element): String {
            val stars = el.selectFirst("div.rating-stars")?.select("i.fas")?.size
            val reviews = el.selectFirst("div.media-user-reviews")?.text()?.filter { it.isDigit() }?.toIntOrNull()
            return listOfNotNull(stars?.let { "★ $it" }, reviews?.let { "$it отзывов" }).joinToString(" · ")
        }

        fun userIdFrom(href: String?): Long? = href?.let { Regex("/users/(\\d+)").find(it)?.groupValues?.get(1)?.toLongOrNull() }

        fun cleanText(s: String): String = s.replace(' ', ' ').trim()

        fun parseAmount(text: String?): Double {
            if (text == null) return 0.0
            val t = text.replace(' ', ' ').replace(" ", "").replace(',', '.')
            return Regex("-?\\d+(?:\\.\\d+)?").find(t)?.value?.toDoubleOrNull() ?: 0.0
        }
    }
}
