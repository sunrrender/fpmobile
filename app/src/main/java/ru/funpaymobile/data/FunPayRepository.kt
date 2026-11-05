package ru.funpaymobile.data

/**
 * Единственная точка получения данных для UI.
 *
 * Сейчас есть только [DemoRepository]. Если FunPay предоставит официальный и
 * разрешённый способ доступа (API / OAuth), достаточно написать вторую реализацию
 * этого интерфейса и подставить её в [ru.funpaymobile.AppViewModel].
 * Экраны менять не придётся.
 *
 * Секреты (пароли, cookies, токены) не должны попадать в исходный код, логи или чат;
 * для их хранения предполагается Android Keystore / EncryptedSharedPreferences.
 */
interface FunPayRepository {
    suspend fun load(): Snapshot
}
