package app.wasabi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wasabi.data.Achievement
import app.wasabi.data.GameDetail
import java.util.Locale

@Composable
fun GameScreen(vm: AppViewModel, screen: Screen.Game) {
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f)) {
            // Landscape 16:9 / 4:3: game info in a side panel, achievements beside it.
            val sidePanel = maxWidth >= WideWidth && maxWidth > maxHeight
            when (val s = vm.game) {
                null, Load.Loading -> LoadingView()
                is Load.Err -> ErrorView(s.message, vm) { vm.refresh() }
                is Load.Ok -> if (sidePanel) {
                    Row(Modifier.fillMaxSize()) {
                        Column(
                            Modifier.width(260.dp).fillMaxHeight().verticalScroll(rememberScrollState())
                                .padding(start = 14.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
                        ) { GameHeader(vm, s.data, stacked = true) }
                        AchievementGrid(vm, s.data, withHeader = false, modifier = Modifier.weight(1f))
                    }
                } else {
                    AchievementGrid(vm, s.data, withHeader = true)
                }
            }
        }
        HintBar("B" to "Back", "Y" to "Filter", "X" to "Refresh")
    }
}

@Composable
private fun AchievementGrid(vm: AppViewModel, g: GameDetail, withHeader: Boolean, modifier: Modifier = Modifier) {
    val list = vm.filteredAchievements()
    CardGrid(vm.gameListState, modifier) {
        if (withHeader) fullWidthItem("header") { GameHeader(vm, g, stacked = false) }
        if (g.achievements.isEmpty()) {
            fullWidthItem("none") { EmptyView("This game has no achievements yet.") }
        } else if (list.isEmpty()) {
            fullWidthItem("none") {
                EmptyView(
                    when (vm.achFilter) {
                        AchFilter.Locked -> "All unlocked. Nice!"
                        AchFilter.Missable -> "No missable achievements in this game."
                        else -> "Nothing unlocked yet."
                    }
                )
            }
        }
        items(list, key = { it.id }) { a -> AchievementRow(vm, a, g.players) }
    }
}

@Composable
private fun GameHeader(vm: AppViewModel, g: GameDetail, stacked: Boolean) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (stacked) {
            Thumb(g.iconUrl, 72.dp)
            Column {
                Text(
                    g.title, color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
                Text(g.console, color = Palette.Dim, fontSize = 12.sp)
                if (g.players > 0) Text("${num(g.players)} players", color = Palette.Faint, fontSize = 11.sp)
            }
            AwardTag(g.award)
        } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Thumb(g.iconUrl, 64.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    g.title, color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(g.console, color = Palette.Dim, fontSize = 12.sp)
                if (g.players > 0) Text("${num(g.players)} players", color = Palette.Faint, fontSize = 11.sp)
            }
            AwardTag(g.award)
        }
        (vm.screen as? Screen.Game)?.user?.let { Tag("$it's progress", Palette.Soft) }
        GameMedia(g)
        StatChips(g)
        GameFacts(g)
        if (g.total > 0) {
            ProgressLine(g.fraction, g.isHardcore)
            Text(
                "${g.shown} of ${g.total} unlocked · ${num(g.pointsEarned)}/${num(g.points)} pts" +
                    if (g.earnedHc in 1 until g.earned) " · ${g.earnedHc} hardcore" else "",
                color = Palette.Dim, fontSize = 12.sp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("Back", onClick = { vm.back() }, icon = Icons.AutoMirrored.Rounded.ArrowBack)
            if (g.total > 0) Pill("Show: ${vm.achFilter.label}", onClick = { vm.option() }, icon = Icons.AutoMirrored.Rounded.List)
        }
        // Adds or removes the game through the website session, in the
        // background (signing in once first if needed). See RaWeb.
        val wanted = vm.isWanted(g.id)
        val busy = vm.wantBusy == g.id
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(
                when {
                    busy -> if (wanted == true) "Removing…" else "Adding…"
                    wanted == true -> "On Want to Play"
                    else -> "Want to Play"
                },
                icon = when {
                    busy -> Icons.Rounded.Refresh
                    wanted == true -> Icons.Rounded.Check
                    else -> Icons.Rounded.Add
                },
                selected = wanted == true && !busy,
                onClick = { vm.toggleWantToPlay(g.id) },
            )
            vm.wantError?.let { Text(it, color = Palette.Error, fontSize = 11.sp, modifier = Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun AchievementRow(vm: AppViewModel, a: Achievement, players: Int) {
    val textAlpha = if (a.unlocked) 1f else 0.75f
    FocusRow("a:${a.id}", vm) {
        Thumb(a.badgeUrl, 48.dp, alpha = if (a.unlocked) 1f else 0.85f)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    a.title,
                    color = Palette.Text.copy(alpha = textAlpha), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${a.points}",
                    color = when {
                        a.hardcore -> Palette.Accent
                        a.unlocked -> Palette.Soft
                        else -> Palette.Dim
                    },
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp),
                )
                // RetroPoints, like the website's "(24)".
                if (a.trueRatio > 0) Text(" (${num(a.trueRatio)})", color = Palette.Faint, fontSize = 11.sp)
            }
            Text(a.description, color = Palette.Dim.copy(alpha = textAlpha), fontSize = 12.sp)
            if (players > 0) {
                Spacer(Modifier.height(4.dp))
                UnlockRateBar(a.numAwarded.toFloat() / players, a.numAwardedHc.toFloat() / players)
                Text(
                    pct(a.numAwarded, players) + " unlocked · " + pct(a.numAwardedHc, players) + " hardcore",
                    color = Palette.Faint, fontSize = 10.sp,
                )
            }
            val details = buildList {
                if (a.unlocked) add("Unlocked ${ago(a.unlockedAt)}" + if (a.hardcore) "" else " (softcore)")
                when (a.type) {
                    "missable" -> add("Missable")
                    "progression" -> add("Progression")
                    "win_condition" -> add("Win condition")
                }
                a.author?.let { add("by $it") }
            }
            if (details.isNotEmpty()) {
                Text(
                    details.joinToString(" · "),
                    color = if (a.hardcore) Palette.Accent else Palette.Faint,
                    fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun pct(part: Int, whole: Int) =
    if (whole <= 0) "0%" else String.format(Locale.getDefault(), "%.1f%%", 100f * part / whole)

/** All unlocks (silver) with the hardcore share on top (accent), like the website's bar. */
@Composable
private fun UnlockRateBar(all: Float, hardcore: Float) {
    Box(
        Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Palette.Track),
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(all.coerceIn(0f, 1f)).background(Palette.Soft.copy(alpha = 0.55f)))
        Box(Modifier.fillMaxHeight().fillMaxWidth(hardcore.coerceIn(0f, 1f)).background(Palette.Accent))
    }
}

/** Title screen, in-game screenshot and box art, scrolling sideways. */
@Composable
private fun GameMedia(g: GameDetail) {
    val shots = listOfNotNull(g.titleImageUrl, g.ingameImageUrl)
    if (shots.isEmpty() && g.boxArtUrl == null) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        shots.forEach { MediaImage(it, width = 112.dp) }
        g.boxArtUrl?.let { MediaImage(it, width = 84.dp) }
    }
}

@Composable
private fun MediaImage(url: String, width: Dp) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.width(width).height(84.dp).clip(RoundedCornerShape(8.dp)).background(Palette.Surface),
    )
}

/** Totals at a glance: achievements, points, RetroPoints, missables. */
@Composable
private fun StatChips(g: GameDetail) {
    if (g.total == 0) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Tag("${num(g.total)} achievements", Palette.Text)
        Tag("${num(g.points)} pts", Palette.Accent)
        if (g.retroPoints > 0) Tag("${num(g.retroPoints)} RetroPoints", Palette.Soft)
        if (g.missableCount > 0) Tag("${g.missableCount} missable", Palette.Error)
    }
}

/** Developer, publisher, genre, release, players and your playtime. */
@Composable
private fun GameFacts(g: GameDetail) {
    val facts = buildList {
        g.developer?.let { add("Developer" to it) }
        g.publisher?.let { add("Publisher" to it) }
        g.genre?.let { add("Genre" to it) }
        releaseText(g.released, g.releasedPrecision)?.let { add("Released" to it) }
        if (g.players > 0) {
            add("Players" to "${num(g.players)}" + if (g.playersHardcore > 0) " · ${num(g.playersHardcore)} hardcore" else "")
        }
        if (g.playtimeSeconds > 0) add("Playtime" to playtime(g.playtimeSeconds))
        if (g.authors.isNotEmpty()) {
            val a = g.authors
            add("Made by" to if (a.size <= 3) a.joinToString(", ") else "${a.take(2).joinToString(", ")} and ${a.size - 2} others")
        }
    }
    if (facts.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Palette.Surface).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        facts.forEach { (label, value) ->
            Row {
                Text(label, color = Palette.Dim, fontSize = 11.sp, modifier = Modifier.width(72.dp))
                Text(value, color = Palette.Text, fontSize = 12.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun releaseText(date: String?, precision: String?): String? {
    val d = runCatching { LocalDate.parse(date?.take(10)) }.getOrNull() ?: return null
    return when (precision) {
        "year" -> "${d.year}"
        "month" -> d.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))
        else -> d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}

private fun playtime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m"
        else -> "under a minute"
    }
}
