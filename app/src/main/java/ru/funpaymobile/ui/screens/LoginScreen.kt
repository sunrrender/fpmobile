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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.funpaymobile.ui.components.AppCard
import ru.funpaymobile.ui.components.DetailHeader
import ru.funpaymobile.ui.components.ScreenPadding
import ru.funpaymobile.ui.theme.Muted
import ru.funpaymobile.ui.theme.Orange
import ru.funpaymobile.ui.theme.Red

@Composable
fun LoginScreen(
    secureStorageAvailable: Boolean,
    onConnect: (String, (String?) -> Unit) -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    // Ключ намеренно хранится в remember, а не в rememberSaveable: он не должен попадать в сохранённое состояние.
    var key by remember { mutableStateOf("") }
    var agreed by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        DetailHeader("Вход в FunPay", onBack)
        LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                AppCard {
                    Text("Неофициальный вход по golden_key", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "У FunPay нет официального API для сторонних приложений. Вход работает через cookie golden_key вашего аккаунта, " +
                            "как у открытых проектов вроде FunPayCardinal. Приложение читает те же страницы, что вы видите на сайте. " +
                            "Если FunPay изменит сайт, часть функций может перестать работать.",
                        color = Muted, fontSize = 13.sp, lineHeight = 18.sp
                    )
                }
            }
            item {
                AppCard {
                    Text("Как получить ключ", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "1. Войдите на funpay.com в браузере на компьютере.\n" +
                            "2. Нажмите F12 → вкладка Application (Приложение) → Cookies → funpay.com.\n" +
                            "3. Найдите строку golden_key и скопируйте значение.\n" +
                            "4. Передайте его себе на телефон и вставьте ниже.",
                        color = Muted, fontSize = 13.sp, lineHeight = 18.sp
                    )
                }
            }
            item {
                AppCard {
                    Text("Важно", fontWeight = FontWeight.SemiBold, color = Orange)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "• golden_key даёт полный доступ к вашему аккаунту. Никому его не отправляйте, в том числе в чат с ассистентом.\n" +
                            "• Ключ хранится только на этом устройстве в зашифрованном виде и отправляется только на funpay.com.\n" +
                            "• Правила FunPay могут не разрешать такие клиенты: риск ограничений аккаунта лежит на вас.\n" +
                            "• Оплата и покупки в приложении не выполняются: предложения открываются на сайте.",
                        color = Muted, fontSize = 13.sp, lineHeight = 18.sp
                    )
                    if (!secureStorageAvailable) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Защищённое хранилище недоступно на этом устройстве: ключ не будет сохранён, входить придётся при каждом запуске.",
                            color = Red, fontSize = 12.sp
                        )
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.filter { c -> !c.isWhitespace() }; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("golden_key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !busy
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = agreed, onCheckedChange = { agreed = it }, enabled = !busy)
                    Spacer(Modifier.width(4.dp))
                    Text("Я понимаю риски неофициального входа", fontSize = 13.sp)
                }
            }
            error?.let { msg ->
                item { Text(msg, color = Red, fontSize = 13.sp) }
            }
            item {
                Button(
                    onClick = {
                        busy = true
                        error = null
                        onConnect(key) { result ->
                            busy = false
                            if (result == null) {
                                key = ""
                                onDone()
                            } else {
                                error = result
                            }
                        }
                    },
                    enabled = agreed && key.length >= 8 && !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "Проверяю…" else "Войти") }
            }
        }
    }
}
