package app.wasabi.ui

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.text.TextStyle
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

// Controller icons: one size, one neutral fill with a light edge so they
// read well on the black background, and bright colored face letters.
private val BadgeFill = Color(0xFF2A2A33)
private val BadgeEdge = Color(0xFF55555F)
private val BadgeSize = 20.dp

/** Face-button letters: A green, B red, X blue, Y yellow. */
private val faceLetter = mapOf(
    "A" to Color(0xFF4ADE80),
    "B" to Color(0xFFF87171),
    "X" to Color(0xFF60A5FA),
    "Y" to Color(0xFFFACC15),
)

/** Bold label text; line height equals the font size so it sits centered (no font padding by default). */
private val badgeText = TextStyle(fontWeight = FontWeight.ExtraBold)

/**
 * A controller button icon. Face buttons (A, B, X, Y) are circles with a
 * colored letter; shoulders (L1, R1, L2, R2) and any other named button are
 * rounded pills; Start is a menu icon, Select a view icon; "DPad" a D-pad
 * and "DPadLR" a D-pad with left/right arrows.
 */
@Composable
fun ButtonBadge(key: String) {
    val circle = Modifier.size(BadgeSize).clip(CircleShape).background(BadgeFill).border(1.dp, BadgeEdge, CircleShape)
    when (key) {
        "A", "B", "X", "Y" -> Box(circle, contentAlignment = Alignment.Center) {
            Text(key, color = faceLetter.getValue(key), fontSize = 12.sp, lineHeight = 12.sp, style = badgeText, maxLines = 1)
        }
        "Start", "Menu" -> Box(
            circle.drawBehind {
                val stroke = 1.4.dp.toPx()
                listOf(0.36f, 0.5f, 0.64f).forEach { y ->
                    drawLine(Color.White, Offset(size.width * 0.3f, size.height * y), Offset(size.width * 0.7f, size.height * y), stroke, StrokeCap.Round)
                }
            },
        )
        "Select", "View" -> Box(
            circle.drawBehind {
                val w = size.width
                val h = size.height
                val stroke = Stroke(1.2.dp.toPx())
                drawRect(Color.White, Offset(w * 0.26f, h * 0.3f), Size(w * 0.3f, h * 0.24f), style = stroke)
                drawRect(BadgeFill, Offset(w * 0.42f, h * 0.44f), Size(w * 0.34f, h * 0.26f))
                drawRect(Color.White, Offset(w * 0.42f, h * 0.44f), Size(w * 0.34f, h * 0.26f), style = stroke)
            },
        )
        "DPad", "DPadLR" -> Box(
            Modifier.size(BadgeSize).drawBehind {
                val w = size.width
                val arm = w * 0.36f
                val r = CornerRadius(2.dp.toPx())
                val edge = 1.dp.toPx()
                // Light edge first, then the fill, so the cross has a clean outline.
                drawRoundRect(BadgeEdge, Offset((w - arm) / 2 - edge, -0f), Size(arm + edge * 2, w), r)
                drawRoundRect(BadgeEdge, Offset(0f, (w - arm) / 2 - edge), Size(w, arm + edge * 2), r)
                drawRoundRect(BadgeFill, Offset((w - arm) / 2, edge), Size(arm, w - edge * 2), r)
                drawRoundRect(BadgeFill, Offset(edge, (w - arm) / 2), Size(w - edge * 2, arm), r)
                if (key == "DPadLR") {
                    val cy = w / 2
                    val t = w * 0.1f
                    drawPath(Path().apply { moveTo(w * 0.1f, cy); lineTo(w * 0.1f + t * 1.3f, cy - t); lineTo(w * 0.1f + t * 1.3f, cy + t); close() }, Color.White)
                    drawPath(Path().apply { moveTo(w * 0.9f, cy); lineTo(w * 0.9f - t * 1.3f, cy - t); lineTo(w * 0.9f - t * 1.3f, cy + t); close() }, Color.White)
                }
            },
        )
        else -> {
            val pill = RoundedCornerShape(6.dp)
            Box(
                Modifier.height(BadgeSize).clip(pill).background(BadgeFill).border(1.dp, BadgeEdge, pill).padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(key, color = Color.White, fontSize = 10.sp, lineHeight = 10.sp, style = badgeText, maxLines = 1)
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

/**
 * Asks before signing out. Cancel is focused first so a stray A press is
 * harmless; B (or tapping outside) also cancels.
 */
@Composable
fun SignOutDialog(vm: AppViewModel) {
    val kind = vm.confirmSignOut ?: return
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(kind) { runCatching { cancelFocus.requestFocus() } }
    AlertDialog(
        onDismissRequest = { vm.cancelSignOut() },
        containerColor = Palette.Surface,
        titleContentColor = Palette.Text,
        textContentColor = Palette.Dim,
        title = {
            Text(if (kind == SignOutKind.App) "Sign out of Wasabi?" else "Sign out of retroachievements.org?")
        },
        text = {
            Text(
                if (kind == SignOutKind.App) {
                    "You'll need your username and Web API key to sign in again. Your settings, favorites and hidden games stay on this device."
                } else {
                    "Wasabi will ask you to sign in to the website again the next time you add a game to Want to Play."
                }
            )
        },
        confirmButton = {
            Button(
                onClick = { vm.confirmSignOutNow() },
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Error, contentColor = Color.Black),
            ) { Text("Sign out", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            OutlinedButton(onClick = { vm.cancelSignOut() }, modifier = Modifier.focusRequester(cancelFocus)) {
                Text("Cancel", color = Palette.Text)
            }
        },
    )
}
