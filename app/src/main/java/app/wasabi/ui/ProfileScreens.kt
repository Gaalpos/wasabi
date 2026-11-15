package app.wasabi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wasabi.data.AwardItem
import app.wasabi.data.Unlock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

// ---------------------------------------------------------------- Awards

/** Your awards wall: counts at the top, then every visible badge, newest first. */
@Composable
fun AwardsScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Awards", "Mastered, completed and beaten games, events and site awards")
        Box(Modifier.weight(1f)) {
            when (val s = vm.awards) {
                null, Load.Loading -> LoadingView()
                is Load.Err -> ErrorView(s.message, vm) { vm.refresh() }
                is Load.Ok -> {
                    val a = s.data
                    val shown = vm.filteredAwards()
                    CardGrid(vm.awardsListState) {
                        fullWidthItem("counts") {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CountTile("Mastered", a.mastered, Palette.Accent, Modifier.weight(1f))
                                CountTile("Completed", a.completed, Palette.Soft, Modifier.weight(1f))
                                CountTile("Beaten", a.beatenHardcore + a.beatenSoftcore, Palette.Text, Modifier.weight(1f))
                                CountTile("Events", a.events + a.site, Palette.Dim, Modifier.weight(1f))
                            }
                        }
                        fullWidthItem("afilter") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Pill("Show: ${vm.awardFilter.label}", onClick = { vm.option() }, compact = true, icon = Icons.AutoMirrored.Rounded.List, focusKey = "afilter", vm = vm)
                                Spacer(Modifier.weight(1f))
                                Text("${num(shown.size)} awards", color = Palette.Dim, fontSize = 12.sp)
                            }
                        }
                        if (shown.isEmpty()) fullWidthItem("none") { EmptyView("No awards here yet.") }
                        items(shown.size, key = { "aw:$it:${shown[it].title}" }) { i -> AwardRow(vm, "aw:$i", shown[i]) }
                    }
                }
            }
        }
        HintBar("A" to "Open game", "Y" to "Filter", "B" to "Back", REFRESH to "Refresh")
    }
}

@Composable
private fun CountTile(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(Palette.Surface).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(num(value), color = color, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Palette.Dim, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun AwardRow(vm: AppViewModel, key: String, a: AwardItem) {
    val (label, color) = when (a.kind) {
        "mastered" -> "Mastered" to Palette.Accent
        "completed" -> "Completed" to Palette.Soft
        "beaten-hardcore" -> "Beaten" to Palette.Accent
        "beaten-softcore" -> "Beaten (softcore)" to Palette.Soft
        "event" -> "Event" to Palette.Dim
        else -> "Site award" to Palette.Dim
    }
    FocusRow(key, vm, onClick = a.gameId?.let { id -> { vm.openGame(id, a.title) } }) {
        Thumb(a.iconUrl, 44.dp)
        Column(Modifier.weight(1f)) {
            Text(a.title, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (a.console.isNotEmpty()) Text(a.console, color = Palette.Dim, fontSize = 11.sp, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(label, color)
                if (a.kind == "mastered" || a.kind == "beaten-hardcore") HardcoreBadge()
                a.date?.let { Text(fullDate(it), color = Palette.Faint, fontSize = 11.sp) }
            }
        }
    }
}

// ---------------------------------------------------------------- Stats

/** Points and unlocks over the last 90 days, streaks and an unlock calendar. */
@Composable
fun StatsScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Stats", "Your last 90 days")
        Box(Modifier.weight(1f)) {
            when (val s = vm.stats) {
                null, Load.Loading -> LoadingView()
                is Load.Err -> ErrorView(s.message, vm) { vm.refresh() }
                is Load.Ok -> StatsContent(vm, s.data)
            }
        }
        HintBar("B" to "Back", REFRESH to "Refresh")
    }
}

/** Unlocks grouped by local day. */
private fun byDay(unlocks: List<Unlock>): Map<LocalDate, List<Unlock>> {
    val zone = ZoneId.systemDefault()
    return unlocks.mapNotNull { u -> parseDate(u.date)?.let { it.atZone(zone).toLocalDate() to u } }
        .groupBy({ it.first }, { it.second })
}

@Composable
private fun StatsContent(vm: AppViewModel, unlocks: List<Unlock>) {
    val today = LocalDate.now()
    val days = byDay(unlocks)
    fun pointsSince(d: Int) = days.filterKeys { !it.isBefore(today.minusDays(d.toLong() - 1)) }.values.flatten().sumOf { it.points }
    // Streaks: consecutive days with at least one unlock.
    var best = 0
    var run = 0
    var d = today.minusDays(90)
    while (!d.isAfter(today)) {
        run = if (days.containsKey(d)) run + 1 else 0
        if (run > best) best = run
        d = d.plusDays(1)
    }
    var current = 0
    var c = if (days.containsKey(today)) today else today.minusDays(1)
    while (days.containsKey(c)) { current++; c = c.minusDays(1) }
    val hardcore = unlocks.count { it.hardcore }

    CardGrid(vm.statsListState) {
        fullWidthItem("st:0") {
            FocusRow("st:0", vm) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatTile("7 days", "${num(pointsSince(7))} pts", Modifier.weight(1f))
                        StatTile("30 days", "${num(pointsSince(30))} pts", Modifier.weight(1f))
                        StatTile("90 days", "${num(pointsSince(91))} pts", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatTile("Unlocks", num(unlocks.size), Modifier.weight(1f))
                        StatTile("Streak", "$current days", Modifier.weight(1f))
                        StatTile("Best streak", "$best days", Modifier.weight(1f))
                    }
                    if (unlocks.isNotEmpty()) {
                        Text(
                            "${(100 * hardcore / unlocks.size)}% of these unlocks were in hardcore",
                            color = Palette.Dim, fontSize = 12.sp,
                        )
                    }
                }
            }
        }
        fullWidthItem("weeks") {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Surface).padding(10.dp)) {
                Text("Points per week", color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                WeeklyBars(days, today)
            }
        }
        fullWidthItem("calendar") {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Surface).padding(10.dp)) {
                Text("Unlock calendar", color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Each square is a day; brighter means more unlocks", color = Palette.Dim, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Calendar(days, today)
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(8.dp)).background(Palette.SurfaceHi).padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = Palette.Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = Palette.Dim, fontSize = 10.sp, maxLines = 1)
    }
}

/** 13 weekly bars (oldest left), points per week. */
@Composable
private fun WeeklyBars(days: Map<LocalDate, List<Unlock>>, today: LocalDate) {
    val weeks = (12 downTo 0).map { w ->
        val end = today.minusDays(7L * w)
        val start = end.minusDays(6)
        days.filterKeys { !it.isBefore(start) && !it.isAfter(end) }.values.flatten().sumOf { it.points }
    }
    val max = (weeks.maxOrNull() ?: 0).coerceAtLeast(1)
    val accent = Palette.Accent
    val track = Palette.Track
    Canvas(Modifier.fillMaxWidth().height(90.dp)) {
        val gap = 4.dp.toPx()
        val w = (size.width - gap * (weeks.size - 1)) / weeks.size
        weeks.forEachIndexed { i, v ->
            val x = i * (w + gap)
            drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(3.dp.toPx()))
            val h = size.height * v / max
            if (v > 0) drawRoundRect(accent, Offset(x, size.height - h), Size(w, h), CornerRadius(3.dp.toPx()))
        }
    }
    Row(Modifier.fillMaxWidth()) {
        Text("12 weeks ago", color = Palette.Faint, fontSize = 10.sp, modifier = Modifier.weight(1f))
        Text("This week · best ${num(weeks.maxOrNull() ?: 0)} pts", color = Palette.Faint, fontSize = 10.sp)
    }
}

/** 13 weeks × 7 days; brighter squares mean more unlocks that day. */
@Composable
private fun Calendar(days: Map<LocalDate, List<Unlock>>, today: LocalDate) {
    val start = today.minusDays(7L * 13 - 1)
    val max = (days.values.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
    val accent = Palette.Accent
    val empty = Palette.Track
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        val cols = 13
        val gap = 3.dp.toPx()
        val cell = minOf((size.width - gap * (cols - 1)) / cols, (size.height - gap * 6) / 7)
        for (i in 0 until cols * 7) {
            val day = start.plusDays(i.toLong())
            if (day.isAfter(today)) break
            val n = days[day]?.size ?: 0
            val col = i / 7
            val row = i % 7
            val color = if (n == 0) empty else accent.copy(alpha = 0.3f + 0.7f * n / max)
            drawRoundRect(color, Offset(col * (cell + gap), row * (cell + gap)), Size(cell, cell), CornerRadius(2.dp.toPx()))
        }
    }
    val month = { d: LocalDate -> d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    Row(Modifier.fillMaxWidth()) {
        Text(month(start), color = Palette.Faint, fontSize = 10.sp, modifier = Modifier.weight(1f))
        Text("Today", color = Palette.Faint, fontSize = 10.sp)
    }
}

/** Title strip for full-screen pages without a tab bar. */
@Composable
fun ScreenTitle(title: String, sub: String? = null) {
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 4.dp)) {
        Text(title, color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        if (sub != null) Text(sub, color = Palette.Dim, fontSize = 11.sp)
    }
}
