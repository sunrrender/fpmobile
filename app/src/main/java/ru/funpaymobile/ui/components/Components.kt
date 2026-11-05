package ru.funpaymobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.funpaymobile.data.OrderStatus
import ru.funpaymobile.ui.theme.Accent
import ru.funpaymobile.ui.theme.Border
import ru.funpaymobile.ui.theme.Green
import ru.funpaymobile.ui.theme.Muted
import ru.funpaymobile.ui.theme.Orange
import ru.funpaymobile.ui.theme.Red
import ru.funpaymobile.ui.theme.Surface1
import ru.funpaymobile.ui.theme.Surface2

val ScreenPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    var m = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(Surface1)
        .border(1.dp, Border, shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(modifier = m.padding(16.dp), content = content)
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 12.dp, bottom = 4.dp)) {
        if (subtitle != null) Text(subtitle, color = Muted, fontSize = 12.sp)
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DetailHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
        }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
    )
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, fontSize = 14.sp)
        Spacer(Modifier.width(12.dp))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
    }
}

@Composable
fun StatusChip(status: OrderStatus) {
    val color = when (status) {
        OrderStatus.Paid -> Orange
        OrderStatus.InProgress -> Accent
        OrderStatus.Completed -> Green
        OrderStatus.Refunded -> Red
    }
    Chip(status.label, color)
}

@Composable
fun Chip(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun DemoBanner(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Orange.copy(alpha = 0.12f))
            .border(1.dp, Orange.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Info, null, tint = Orange, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            "Демонстрационный режим: все данные вымышлены. Приложение не подключено к FunPay.",
            color = Orange, fontSize = 12.sp, lineHeight = 16.sp
        )
    }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color = Accent, size: Int = 40) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size((size * 0.5f).dp))
    }
}

@Composable
fun MenuRow(title: String, icon: ImageVector, subtitle: String? = null, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium)
                if (subtitle != null) Text(subtitle, color = Muted, fontSize = 12.sp)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Muted)
        }
    }
}

@Composable
fun LoadingView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Accent)
    }
}

@Composable
fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Что-то пошло не так", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(message, color = Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry) { Text("Повторить") }
    }
}

@Composable
fun NotFoundView(what: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        DetailHeader("Не найдено", onBack)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("$what не найден(а)", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun Avatar(name: String, size: Int = 44) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(Surface2).border(1.dp, Border, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(name.lastOrNull { !it.isWhitespace() }?.uppercase() ?: "?", color = Accent, fontWeight = FontWeight.Bold)
    }
}
