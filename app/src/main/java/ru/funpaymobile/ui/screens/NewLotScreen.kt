package ru.funpaymobile.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.funpaymobile.data.FieldType
import ru.funpaymobile.data.FormField
import ru.funpaymobile.data.LotForm
import ru.funpaymobile.data.MarketCategory
import ru.funpaymobile.data.MarketGame
import ru.funpaymobile.ui.components.AppCard
import ru.funpaymobile.ui.components.DetailHeader
import ru.funpaymobile.ui.components.ScreenPadding
import ru.funpaymobile.ui.theme.Muted
import ru.funpaymobile.ui.theme.Orange
import ru.funpaymobile.ui.theme.Red

/**
 * Создание настоящего лота на FunPay: выбор игры и раздела, затем форма, которую отдаёт сам сайт
 * (поля зависят от раздела). Перед отправкой — подтверждение.
 */
@Composable
fun NewLotScreen(
    loadGames: suspend () -> Result<List<MarketGame>>,
    loadForm: suspend (MarketCategory) -> Result<LotForm>,
    publish: suspend (List<Pair<String, String>>) -> Result<String>,
    onBack: () -> Unit,
    onPublished: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var games by remember { mutableStateOf<List<MarketGame>?>(null) }
    var gamesError by remember { mutableStateOf<String?>(null) }
    var game by remember { mutableStateOf<MarketGame?>(null) }
    var category by remember { mutableStateOf<MarketCategory?>(null) }
    var form by remember { mutableStateOf<LotForm?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    val values = remember { mutableStateMapOf<String, String>() }
    var query by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loadGames().fold({ games = it }, { gamesError = it.message ?: "Ошибка загрузки" })
    }
    LaunchedEffect(category) {
        val c = category ?: return@LaunchedEffect
        form = null
        formError = null
        message = null
        loadForm(c).fold(
            { f ->
                values.clear()
                f.fields.forEach { values[it.name] = it.value }
                form = f
            },
            { formError = it.message ?: "Не удалось загрузить форму" }
        )
    }
    BackHandler {
        when {
            category != null -> category = null
            game != null -> game = null
            else -> onBack()
        }
    }

    val g = game
    val c = category
    val f = form
    Column(Modifier.fillMaxSize()) {
        DetailHeader(
            when {
                c != null -> "Новый лот · ${c.title}"
                g != null -> g.name
                else -> "Новый лот"
            }
        ) {
            when {
                category != null -> category = null
                game != null -> game = null
                else -> onBack()
            }
        }
        LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                g == null -> {
                    item {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Поиск игры") }, singleLine = true, shape = RoundedCornerShape(16.dp)
                        )
                    }
                    val list = games
                    when {
                        gamesError != null -> item { Text(gamesError!!, color = Red, fontSize = 13.sp) }
                        list == null -> item { Text("Загружаю список игр…", color = Muted) }
                        else -> items(list.filter { query.isBlank() || it.name.contains(query, true) }, key = { it.name }) { item ->
                            AppCard(onClick = { game = item; query = "" }) { Text(item.name, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
                c == null -> items(g.categories, key = { it.url }) { cat ->
                    AppCard(onClick = { category = cat }) { Text(cat.title, fontWeight = FontWeight.Medium) }
                }
                formError != null -> item { Text(formError!!, color = Red, fontSize = 13.sp) }
                f == null -> item { Text("Загружаю форму раздела…", color = Muted) }
                else -> {
                    item {
                        Text(
                            "Поля формы получены с FunPay для раздела «${f.categoryTitle}». Заполните их так же, как на сайте.",
                            color = Muted, fontSize = 12.sp
                        )
                    }
                    if (f.dump.isNotBlank()) item {
                        var showDump by remember { mutableStateOf(false) }
                        Column {
                            TextButton(onClick = { showDump = !showDump }) { Text("Диагностика: структура формы", color = Muted) }
                            if (showDump) androidx.compose.foundation.text.selection.SelectionContainer {
                                Text(f.dump, color = Muted, fontSize = 10.sp, lineHeight = 13.sp)
                            }
                        }
                    }
                    items(f.fields.filter { it.type != FieldType.Hidden }) { field ->
                        FieldEditor(field, values[field.name] ?: field.value) { values[field.name] = it }
                    }
                    message?.let { m -> item { Text(m, color = Red, fontSize = 13.sp) } }
                    item {
                        Button(
                            onClick = {
                                val missing = f.fields.filter {
                                    (it.required || it.type == FieldType.Select) && it.type != FieldType.Check && it.type != FieldType.Hidden &&
                                        (values[it.name] ?: it.value).isBlank()
                                }
                                if (missing.isNotEmpty()) {
                                    message = "Заполните обязательные поля: " + missing.joinToString { it.label }
                                } else {
                                    message = null
                                    confirm = true
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Опубликовать лот") }
                    }
                }
            }
        }
    }

    if (confirm && f != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirm = false },
            title = { Text("Создать лот на FunPay?") },
            text = {
                Text(
                    "Лот будет создан в вашем настоящем аккаунте и станет виден покупателям. Проверьте поля формы.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            val payload = f.fields.mapNotNull { fld ->
                                val v = values[fld.name] ?: fld.value
                                if (fld.type == FieldType.Check) (if (v == "on") fld.name to "on" else null) else fld.name to v
                            }
                            publish(payload).fold(
                                { text ->
                                    busy = false
                                    confirm = false
                                    android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_LONG).show()
                                    onPublished()
                                },
                                { e ->
                                    busy = false
                                    confirm = false
                                    var m = e.message ?: "Не удалось создать лот"
                                    val sent = f.fields.filter { fld -> m.contains(fld.name) }.joinToString("; ") { fld ->
                                        val vals = payload.filter { it.first == fld.name }.map { it.second }
                                        "${fld.label}: отправлено ${if (vals.isEmpty()) "ничего" else vals.joinToString("|") { v -> "«$v»" }}, вариантов в форме: ${fld.options.size}"
                                    }
                                    f.fields.forEach { fld -> m = m.replace(fld.name, fld.label) }
                                    if (sent.isNotEmpty()) m += "\n[диагностика] $sent"
                                    m += "\n[поля формы] " + payload.filter { it.first != "csrf_token" }
                                        .joinToString("; ") { "${it.first}=${it.second.take(12).replace("\n", " ")}" }
                                    m += "\n[в форме нет] " + (if (f.fields.any { it.name == "fields[type]" }) "—" else "поля fields[type]")
                                    message = m.replace("[", "").replace("]", "").replace("\"", "")
                                }
                            )
                        }
                    }
                ) { Text(if (busy) "Отправляю…" else "Создать") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { confirm = false }) { Text("Отмена") } }
        )
    }
}

@Composable
internal fun FieldEditor(field: FormField, value: String, onChange: (String) -> Unit) {
    val label = field.label + if (field.required) " *" else ""
    when (field.type) {
        FieldType.Text, FieldType.Number -> OutlinedTextField(
            value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
            label = { Text(label) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = if (field.type == FieldType.Number) KeyboardType.Decimal else KeyboardType.Text),
            shape = RoundedCornerShape(16.dp)
        )
        FieldType.Area -> OutlinedTextField(
            value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
            label = { Text(label) }, minLines = 3, shape = RoundedCornerShape(16.dp)
        )
        FieldType.Check -> Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = value == "on", onCheckedChange = { onChange(if (it) "on" else "") })
            Text(label, fontSize = 14.sp)
        }
        FieldType.Select -> {
            var open by remember { mutableStateOf(false) }
            Column {
                Text(label, color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Box {
                    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(field.options.firstOrNull { it.value == value }?.label ?: "Выберите")
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        field.options.forEach { opt ->
                            DropdownMenuItem(text = { Text(opt.label) }, onClick = { onChange(opt.value); open = false })
                        }
                    }
                }
            }
        }
        FieldType.Hidden -> Unit
    }
}
