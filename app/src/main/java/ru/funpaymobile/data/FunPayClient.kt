package ru.funpaymobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
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

class FunPayException(message: String, val kind: Kind = Kind.Other) : Exception(message) {
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

    private var csrf = ""
    private var session: String? = null
    private var lastLogin = 0L

    // ---------------------------------------------------------------- сеть

    private fun safeUrl(url: String?): String? {
        val u = url?.toHttpUrlOrNull() ?: return null
        return if (u.scheme == "https" && u.host == HOST) u.toString() else null
    }

    private fun call(url: String, form: FormBody? = null, ajax: Boolean = false, hops: Int = 0): String {
        val target = safeUrl(url)
            ?: throw FunPayException("Запрос к стороннему адресу заблокирован для защиты ключа.", FunPayException.Kind.Other)
        val cookie = buildString {
            append("golden_key=").append(key).append("; locale=ru")
            session?.let { append("; PHPSESSID=").append(it) }
        }
        val rb = Request.Builder()
            .url(target)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "ru")
            .header("Cookie", cookie)
        if (ajax) rb.header("X-Requested-With", "XMLHttpRequest")
        if (form != null) rb.post(form)

        try {
            http.newCall(rb.build()).execute().use { r ->
                r.headers("Set-Cookie").firstOrNull { it.startsWith("PHPSESSID=") }?.let {
                    session = it.substringAfter("PHPSESSID=").substringBefore(";")
                }
                when {
                    r.isRedirect -> {
                        if (hops >= 3) throw FunPayException("Слишком много перенаправлений.", FunPayException.Kind.Other)
                        val loc = r.header("Location")
                            ?: throw FunPayException("Некорректный ответ сервера.", FunPayException.Kind.Other)
                        val next = r.request.url.resolve(loc)?.toString()
                            ?: throw FunPayException("Некорректный ответ сервера.", FunPayException.Kind.Other)
                        return call(next, null, ajax, hops + 1)
                    }
                    r.code == 403 || r.code == 429 -> throw FunPayException(
                        "FunPay отклонил запрос (код ${r.code}). Возможно, включена защита от ботов или слишком частые запросы. " +
                            "Откройте funpay.com в браузере, пройдите проверку и повторите позже.",
                        FunPayException.Kind.Blocked
                    )
                    r.code in 500..599 -> throw FunPayException("Сервер FunPay временно недоступен (код ${r.code}).", FunPayException.Kind.Network)
                    !r.isSuccessful -> throw FunPayException("FunPay вернул код ${r.code}.", FunPayException.Kind.Other)
                    else -> return r.body?.string().orEmpty()
                }
            }
        } catch (e: IOException) {
            throw FunPayException("Нет соединения с funpay.com. Проверьте интернет (и VPN).", FunPayException.Kind.Network)
        }
    }

    private fun runner(objects: JSONArray?, request: JSONObject?): JSONObject {
        if (System.currentTimeMillis() - lastLogin > SESSION_TTL_MS) loginSync()
        val form = FormBody.Builder()
            .add("objects", objects?.toString() ?: "")
            .add("request", request?.toString() ?: "false")
            .add("csrf_token", csrf)
            .build()
        val body = call("$BASE/runner/", form, ajax = true)
        return try {
            JSONObject(body)
        } catch (e: Exception) {
            throw FunPayException("FunPay вернул неожиданный ответ (возможно, сессия истекла или нужна проверка в браузере).", FunPayException.Kind.Parse)
        }
    }

    // ---------------------------------------------------------------- вход

    suspend fun login() = withContext(Dispatchers.IO) { loginSync() }

    private fun loginSync() {
        val doc = Jsoup.parse(call("$BASE/"), "$BASE/")
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
                webUrl = safeUrl(el.absUrl("href"))
            )
        }.distinctBy { it.id }
    }

    // ---------------------------------------------------------------- чаты

    suspend fun chats(): List<ChatPreview> = withContext(Dispatchers.IO) {
        val objects = JSONArray().put(
            JSONObject().put("type", "chat_bookmarks").put("id", userId).put("tag", randomTag()).put("data", false)
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
        if (html.isNullOrEmpty()) return@withContext emptyList()
        Jsoup.parse(html).select("a.contact-item").mapNotNull { a ->
            val id = a.attr("data-id").trim()
            if (id.isEmpty()) return@mapNotNull null
            ChatPreview(
                id = id,
                name = a.selectFirst("div.media-user-name")?.text()?.trim().orEmpty().ifEmpty { "Диалог $id" },
                lastMessage = a.selectFirst("div.contact-item-message")?.text()?.trim().orEmpty(),
                time = a.selectFirst("div.contact-item-time")?.text()?.trim().orEmpty(),
                unread = if (a.hasClass("unread")) 1 else 0
            )
        }.distinctBy { it.id }
    }

    suspend fun chatMessages(nodeId: String): List<Message> = withContext(Dispatchers.IO) {
        val doc = Jsoup.parse(call("$BASE/chat/?node=${java.net.URLEncoder.encode(nodeId, "UTF-8")}"), "$BASE/")
        requireAuthorized(doc)
        var lastAuthor = ""
        var index = 0L
        doc.select("div.chat-msg-item").mapNotNull { item ->
            index++
            item.selectFirst(".chat-msg-author-link")?.text()?.trim()?.takeIf { it.isNotEmpty() }?.let { lastAuthor = it }
            val textEl = item.selectFirst(".chat-msg-text")
            var text = textEl?.wholeText()?.trim().orEmpty()
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
        val request = JSONObject().put("action", "chat_message").put(
            "data",
            JSONObject().put("node", nodeId.toLongOrNull() ?: nodeId).put("last_message", -1).put("content", text)
        )
        val resp = runner(null, request)
        val err = resp.optJSONObject("response")?.opt("error")
        if (err != null && err != JSONObject.NULL && err != false) {
            throw FunPayException("FunPay не принял сообщение: $err", FunPayException.Kind.Other)
        }
    }

    // ---------------------------------------------------------------- мои предложения

    suspend fun myLots(): List<Lot> = withContext(Dispatchers.IO) {
        val doc = Jsoup.parse(call("$BASE/users/$userId/"), "$BASE/")
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
                sellerInfo = el.selectFirst("span.rating-mini-count")?.text()?.trim()?.let { "отзывов: $it" }.orEmpty(),
                stock = el.selectFirst("div.tc-amount")?.text()?.filter { it.isDigit() }?.toIntOrNull() ?: 0,
                description = "",
                priceText = priceStr,
                url = url
            )
        }
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
        const val USER_AGENT = "FunPayMobile/0.5.1 (Android; unofficial client)"
        const val SESSION_TTL_MS = 20 * 60 * 1000L
        // Печатные ASCII-символы, кроме пробела, кавычки, запятой, точки с запятой и обратной косой черты.
        val KEY_REGEX = Regex("[\\x21\\x23-\\x2B\\x2D-\\x3A\\x3C-\\x5B\\x5D-\\x7E]{8,128}")

        fun cleanText(s: String): String = s.replace(' ', ' ').trim()

        fun parseAmount(text: String?): Double {
            if (text == null) return 0.0
            val t = text.replace(' ', ' ').replace(" ", "").replace(',', '.')
            return Regex("-?\\d+(?:\\.\\d+)?").find(t)?.value?.toDoubleOrNull() ?: 0.0
        }
    }
}
