package ru.funpaymobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Актуальные курсы валют (официальные курсы ЦБ РФ через открытое зеркало). Запрос идёт без ключа FunPay
 * и cookies. Курс справочный: FunPay считает по своему курсу и комиссии способа оплаты.
 */
object Rates {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()

    /** Сколько рублей стоит единица валюты. */
    suspend fun load(): Result<Map<String, Double>> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("https://www.cbr-xml-daily.ru/daily_json.js")
                .header("User-Agent", "FunPayMobile (Android; unofficial client)").build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw IllegalStateException("курсы недоступны (код ${r.code})")
                val v = JSONObject(r.body?.string().orEmpty()).getJSONObject("Valute")
                fun rate(code: String): Double =
                    v.getJSONObject(code).let { it.getDouble("Value") / it.getDouble("Nominal") }
                Result.success(mapOf("RUB" to 1.0, "USD" to rate("USD"), "EUR" to rate("EUR")))
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Не удалось загрузить курсы валют: ${e.message}"))
        }
    }
}
