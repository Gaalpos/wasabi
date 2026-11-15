package app.wasabi.ui

import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Add
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
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

/**
 * Buttons that can refresh (chosen in Settings). The Retroid Pocket Classic
 * has L1/R1 (tabs) and L2/R2 on the shoulders and no sticks, so no L3/R3.
 */
enum class RefreshButton(val label: String, val keyCode: Int) {
    R2("R2", android.view.KeyEvent.KEYCODE_BUTTON_R2),
    L2("L2", android.view.KeyEvent.KEYCODE_BUTTON_L2),
    Select("Select", android.view.KeyEvent.KEYCODE_BUTTON_SELECT),
}

/** Controller settings that hint bars and key handling read. */
object Controls {
    var refresh by mutableStateOf(RefreshButton.R2)
}

/** Use as a HintBar key to show whichever button refreshes. */
const val REFRESH = "\u0000refresh"

private val BadgeSlate = Color(0xFF403D52)
private val BadgeEdge = Color(0xFF34324A)

/** Face-button letter colors: Y yellow, X blue, A green, B red. */
private val faceLetter = mapOf(
    "Y" to Color(0xFFFFD31A),
    "X" to Color(0xFF2EA8E5),
    "A" to Color(0xFF43EB4A),
    "B" to Color(0xFFFF5252),
)

/**
 * A controller button icon, drawn after the provided button set: slate
 * circles with colored letters for A/B/X/Y, slanted bumpers for L1/R1,
 * triggers for L2/R2, ringed sticks for L3/R3, View (Select) and Menu
 * (Start) circles, and a D-pad ("DPad", or "DPadLR" for left/right).
 */
@Composable
fun ButtonBadge(key: String) {
    when (key) {
        "A", "B", "X", "Y" -> Box(
            Modifier.size(18.dp).clip(CircleShape).background(BadgeSlate).border(1.dp, BadgeEdge, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(key, color = faceLetter.getValue(key), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
        "L1", "R1" -> ShoulderBadge(key, width = 27.dp, height = 17.dp, mirrored = key == "R1") { w, h ->
            // Bumper: tall on the outer side, sloping down toward the middle.
            moveTo(0f, h * 0.22f)
            quadraticTo(0f, 0f, w * 0.14f, 0f)
            lineTo(w * 0.88f, h * 0.26f)
            quadraticTo(w, h * 0.3f, w, h * 0.48f)
            lineTo(w, h * 0.82f)
            quadraticTo(w, h, w * 0.88f, h)
            lineTo(w * 0.14f, h)
            quadraticTo(0f, h, 0f, h * 0.8f)
            close()
        }
        "L2", "R2" -> ShoulderBadge(key, width = 19.dp, height = 20.dp, mirrored = key == "R2") { w, h ->
            // Trigger: a rounded column that flares out at the bottom.
            moveTo(w * 0.3f, h * 0.1f)
            quadraticTo(w * 0.3f, 0f, w * 0.42f, 0f)
            lineTo(w * 0.9f, 0f)
            quadraticTo(w, 0f, w, h * 0.08f)
            lineTo(w, h * 0.86f)
            quadraticTo(w, h, w * 0.86f, h)
            lineTo(w * 0.12f, h)
            quadraticTo(0f, h, w * 0.04f, h * 0.88f)
            quadraticTo(w * 0.3f, h * 0.55f, w * 0.3f, h * 0.1f)
            close()
        }
        "L3", "R3" -> Box(
            Modifier.size(18.dp).drawBehind {
                val r = size.minDimension / 2
                drawCircle(Color(0xFF2A2939), r)
                drawCircle(Color(0xFF3A384D), r * 0.84f)
                drawCircle(Color(0xFF55536B), r * 0.8f, style = Stroke(0.6.dp.toPx()))
            },
            contentAlignment = Alignment.Center,
        ) {
            Text(key, color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
        "Start", "Menu" -> Box(
            Modifier.size(18.dp).clip(CircleShape).background(BadgeSlate).drawBehind {
                val w = size.width
                val stroke = 1.3.dp.toPx()
                listOf(0.33f, 0.5f, 0.67f).forEach { y ->
                    drawLine(Color.White, Offset(w * 0.28f, size.height * y), Offset(w * 0.72f, size.height * y), stroke, StrokeCap.Round)
                }
            },
        )
        "Select", "View" -> Box(
            Modifier.size(18.dp).clip(CircleShape).background(BadgeSlate).drawBehind {
                val w = size.width
                val h = size.height
                val stroke = Stroke(1.dp.toPx())
                drawRect(Color.White, Offset(w * 0.24f, h * 0.28f), Size(w * 0.34f, h * 0.26f), style = stroke)
                drawRect(BadgeSlate, Offset(w * 0.4f, h * 0.42f), Size(w * 0.38f, h * 0.3f))
                drawRect(Color.White, Offset(w * 0.4f, h * 0.42f), Size(w * 0.38f, h * 0.3f), style = stroke)
            },
        )
        "DPad", "DPadLR" -> Box(
            Modifier.size(19.dp).drawBehind {
                val w = size.width
                val h = size.height
                val arm = w * 0.34f
                val corner = CornerRadius(1.5.dp.toPx())
                drawRoundRect(Color(0xFF393748), Offset((w - arm) / 2, 0f), Size(arm, h), corner)
                drawRoundRect(Color(0xFF393748), Offset(0f, (h - arm) / 2), Size(w, arm), corner)
                drawRect(Color(0xFF2B2A39), Offset((w - arm) / 2, (h - arm) / 2), Size(arm, arm))
                if (key == "DPadLR") {
                    val cy = h / 2
                    val t = w * 0.09f
                    val left = Path().apply { moveTo(w * 0.07f, cy); lineTo(w * 0.07f + t * 1.4f, cy - t); lineTo(w * 0.07f + t * 1.4f, cy + t); close() }
                    val right = Path().apply { moveTo(w * 0.93f, cy); lineTo(w * 0.93f - t * 1.4f, cy - t); lineTo(w * 0.93f - t * 1.4f, cy + t); close() }
                    drawPath(left, Color.White)
                    drawPath(right, Color.White)
                }
            },
        )
        else -> Box(
            Modifier.height(18.dp).clip(RoundedCornerShape(50)).background(BadgeSlate).padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(key, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

/** A bumper or trigger shape with its name; [mirrored] for the right-hand side. */
@Composable
private fun ShoulderBadge(
    label: String,
    width: Dp,
    height: Dp,
    mirrored: Boolean,
    outline: Path.(w: Float, h: Float) -> Unit,
) {
    Box(Modifier.size(width, height), contentAlignment = if (mirrored) Alignment.BottomStart else Alignment.BottomEnd) {
        Canvas(Modifier.matchParentSize().graphicsLayer { if (mirrored) scaleX = -1f }) {
            val p = Path().apply { outline(size.width, size.height) }
            drawPath(p, BadgeSlate)
            drawPath(p, BadgeEdge, style = Stroke(0.8.dp.toPx()))
        }
        Text(
            label, color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Black, maxLines = 1,
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp),
        )
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
            .horizontalScroll(rememberScrollState())
            .background(Palette.Bg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        hints.forEach { (rawKey, label) ->
            val key = if (rawKey == REFRESH) Controls.refresh.label else rawKey
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

/**
 * Want to Play button for a game row: + to add, ✓ when it's on the list
 * (tap to remove). X does the same for the selected row.
 */
@Composable
fun WantToggle(vm: AppViewModel, gameId: Int) {
    val wanted = vm.isWanted(gameId) == true
    val busy = vm.wantBusy == gameId
    Icon(
        when {
            busy -> Icons.Rounded.Refresh
            wanted -> Icons.Rounded.Check
            else -> Icons.Rounded.Add
        },
        contentDescription = if (wanted) "On Want to Play" else "Add to Want to Play",
        tint = if (wanted) Palette.Accent else Palette.Dim,
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .clickable { vm.toggleWantToPlay(gameId) }
            .background(if (wanted) Palette.Accent.copy(alpha = 0.15f) else Palette.SurfaceHi)
            .padding(4.dp),
    )
}
