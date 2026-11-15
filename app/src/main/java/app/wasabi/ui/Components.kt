package app.wasabi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Accent colors offered in Settings. */
enum class AccentColor(val label: String, val color: Color) {
    Wasabi("Wasabi", Color(0xFF9DC93B)),
    Teal("Teal", Color(0xFF2EC4B6)),
    Sky("Sky", Color(0xFF5AB0FF)),
    Violet("Violet", Color(0xFFB48CFF)),
    Pink("Pink", Color(0xFFFF6FB5)),
    Coral("Coral", Color(0xFFFF7A5C)),
    Gold("Gold", Color(0xFFF5B700)),
}

object Palette {
    /** The chosen accent. Composables that read [Accent] redraw when it changes. */
    var accent by mutableStateOf(AccentColor.Wasabi)
    val Bg = Color(0xFF000000)          // true black for the AMOLED panel
    val Surface = Color(0xFF141418)
    val SurfaceHi = Color(0xFF26262E)
    val Track = Color(0xFF2E2E36)
    val Accent: Color get() = accent.color   // hardcore, focus, selection
    val Soft = Color(0xFFAFC0D8)        // softcore / silver
    val Text = Color(0xFFF2F2F5)
    val Dim = Color(0xFF9C9CA8)
    val Faint = Color(0xFF62626C)
    val Error = Color(0xFFFF7A7A)
}

@Composable
fun WasabiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Accent,
            onPrimary = Color.Black,
            background = Palette.Bg,
            onBackground = Palette.Text,
            surface = Palette.Surface,
            onSurface = Palette.Text,
            surfaceVariant = Palette.SurfaceHi,
            onSurfaceVariant = Palette.Dim,
            outline = Palette.Faint,
            error = Palette.Error,
        ),
        content = content,
    )
}

/**
 * A list row that works with the D-pad: it is always focusable (so the list
 * can scroll with the pad), shows a wasabi-green outline when focused, and grabs
 * focus when the view model asks for [focusKey].
 */
@Composable
fun FocusRow(
    focusKey: String,
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    if (vm.focusTarget == focusKey) {
        LaunchedEffect(focusKey) {
            runCatching { requester.requestFocus() }
            vm.consumeFocus(focusKey)
        }
    }
    val shape = RoundedCornerShape(12.dp)
    val base = modifier
        .fillMaxWidth()
        .focusRequester(requester)
        .onFocusChanged {
            if (it.isFocused != focused) {
                focused = it.isFocused
                if (it.isFocused) vm.rememberFocus(focusKey)
            }
        }
        .clip(shape)
        .background(if (focused) Palette.SurfaceHi else Palette.Surface)
        .border(2.dp, if (focused) Palette.Accent else Color.Transparent, shape)
    val interactive = if (onClick != null) base.clickable(onClick = onClick) else base.focusable()
    Row(
        interactive.padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun Pill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    focusKey: String? = null,
    vm: AppViewModel? = null,
    compact: Boolean = false,
    icon: ImageVector? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    if (focusKey != null && vm != null && vm.focusTarget == focusKey) {
        LaunchedEffect(focusKey) {
            runCatching { requester.requestFocus() }
            vm.consumeFocus(focusKey)
        }
    }
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused && focusKey != null) vm?.rememberFocus(focusKey)
            }
            .clip(shape)
            .background(
                when {
                    selected -> Palette.Accent
                    focused -> Palette.SurfaceHi
                    else -> Palette.Surface
                }
            )
            .border(2.dp, if (focused) (if (selected) Palette.Text else Palette.Accent) else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 5.dp else 6.dp),
    ) {
        val tint = if (selected) Color.Black else Palette.Text
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = text.ifEmpty { null }, tint = tint, modifier = Modifier.size(if (compact) 15.dp else 16.dp))
                if (text.isNotEmpty()) Spacer(Modifier.width(5.dp))
            }
            if (text.isNotEmpty()) {
                Text(
                    text,
                    color = tint,
                    fontSize = if (compact) 12.sp else 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Xbox face-button colors. */
private val xboxColors = mapOf(
    "A" to Color(0xFF5DC21E),
    "B" to Color(0xFFE5383B),
    "X" to Color(0xFF2E7DE6),
    "Y" to Color(0xFFF2B705),
)

/**
 * A controller button in the standard Xbox style: colored round A/B/X/Y,
 * LB/RB bumpers, and the round Menu (≡) button for Start. Keys can be given
 * by their Android names (L1, R1, Start) or their Xbox names.
 */
@Composable
fun ButtonBadge(key: String) {
    val face = xboxColors[key]
    when {
        face != null -> Box(
            Modifier.size(18.dp).clip(CircleShape).background(face),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                key,
                color = if (key == "Y") Color(0xFF1A1A1A) else Color.White,
                fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
            )
        }
        key == "Start" || key == "Menu" -> Box(
            Modifier.size(18.dp).clip(CircleShape).background(Color(0xFF3A3A44)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Menu, contentDescription = "Menu", tint = Color.White, modifier = Modifier.size(12.dp))
        }
        else -> {
            val label = when (key) {
                "L1" -> "LB"
                "R1" -> "RB"
                "L2" -> "LT"
                "R2" -> "RT"
                else -> key
            }
            // Bumper: rounded on top, nearly square at the bottom.
            val shape = RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp, bottomStart = 3.dp, bottomEnd = 3.dp)
            Box(
                Modifier.height(18.dp).clip(shape).background(Color(0xFF3A3A44)).padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            }
        }
    }
}

@Composable
fun Thumb(url: String?, size: Dp, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp), alpha: Float = 1f) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alpha = alpha,
        modifier = modifier.size(size).clip(shape).background(Palette.SurfaceHi),
    )
}

@Composable
fun ProgressLine(fraction: Float, hardcore: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Palette.Track),
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .background(if (hardcore) Palette.Accent else Palette.Soft),
            )
        }
    }
}

/** Small colored label for mastered / beaten games. */
@Composable
fun AwardTag(kind: String?) {
    val (label, color) = when (kind) {
        "mastered" -> "Mastered" to Palette.Accent
        "completed" -> "Completed" to Palette.Soft
        "beaten-hardcore" -> "Beaten" to Palette.Accent
        "beaten-softcore" -> "Beaten" to Palette.Soft
        else -> return
    }
    Tag(label, color)
}

/** Small tinted label, e.g. "Mastered" or "Follows you". */
@Composable
fun Tag(label: String, color: Color) {
    Text(
        label,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
fun LoadingView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Palette.Accent, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
    }
}

@Composable
fun ErrorView(message: String, vm: AppViewModel, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, color = Palette.Error, textAlign = TextAlign.Center, fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))
        Pill("Retry", onRetry, focusKey = "retry", vm = vm, icon = Icons.Rounded.Refresh)
    }
    LaunchedEffect(message) { vm.focusTarget = "retry" }
}

@Composable
fun EmptyView(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, color = Palette.Dim, textAlign = TextAlign.Center, fontSize = 14.sp)
    }
}

/** Button legend along the bottom edge, e.g. [A] Open  [B] Back. */
@Composable
fun HintBar(vararg hints: Pair<String, String>) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Bg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        hints.forEach { (key, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                // "L1/R1" shows as two badges.
                key.split('/').forEach { ButtonBadge(it) }
                Spacer(Modifier.width(2.dp))
                Text(label, color = Palette.Dim, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

// ---- formatting ----

private val apiDate = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val shortDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

/** RetroAchievements dates are UTC, as "2024-03-01 18:22:10" or ISO-8601. */
fun parseDate(s: String?): Instant? {
    if (s.isNullOrBlank()) return null
    return runCatching {
        LocalDateTime.parse(s.take(19).replace('T', ' '), apiDate).toInstant(ZoneOffset.UTC)
    }.getOrNull()
}

fun ago(s: String?): String {
    val t = parseDate(s) ?: return ""
    val d = Duration.between(t, Instant.now())
    return when {
        d.toMinutes() < 1 -> "just now"
        d.toMinutes() < 60 -> "${d.toMinutes()}m ago"
        d.toHours() < 24 -> "${d.toHours()}h ago"
        d.toDays() < 30 -> "${d.toDays()}d ago"
        else -> shortDate.format(t.atZone(ZoneId.systemDefault()))
    }
}

fun fullDate(s: String?): String =
    parseDate(s)?.let { shortDate.format(it.atZone(ZoneId.systemDefault())) }.orEmpty()

fun num(n: Int): String = NumberFormat.getIntegerInstance().format(n)
