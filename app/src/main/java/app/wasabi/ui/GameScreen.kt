package app.wasabi.ui

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import app.wasabi.data.GameExtras
import app.wasabi.data.RelatedGame
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
    val st = vm.gameState(screen)
    Column(Modifier.fillMaxSize()) {
        when (val s = st.detail) {
            Load.Loading -> Box(Modifier.weight(1f)) { LoadingView() }
            is Load.Err -> Box(Modifier.weight(1f)) { ErrorView(s.message, vm) { vm.refresh() } }
            is Load.Ok -> {
                GameHeader(vm, screen, s.data)
                GameTabStrip(vm, st)
                AnimatedContent(
                    targetState = st.tab,
                    transitionSpec = { tabTransition(vm.tabDirection) },
                    label = "game tabs",
                    modifier = Modifier.weight(1f).padding(top = 2.dp).swipeToSwitchTabs { vm.cycleTab(it) },
                ) { t ->
                    when (t) {
                        GameTab.Achievements -> AchievementsTab(vm, st, s.data)
                        GameTab.Info -> InfoTab(vm, st, s.data)
                        GameTab.Series -> SeriesTab(vm, st, screen)
                        GameTab.Similar -> SimilarTab(vm, st)
                        GameTab.Compare -> CompareTab(vm, st)
                        GameTab.Leaderboards -> LeaderboardsTab(vm, st)
                    }
                }
            }
        }
        when (st.tab) {
            GameTab.Achievements -> HintBar("X" to "Want to Play", "Y" to "Filter", "L1/R1" to "Tabs", "B" to "Back", REFRESH to "Refresh")
            GameTab.Info -> HintBar("A" to "View image", "X" to "Want to Play", "L1/R1" to "Tabs", "B" to "Back")
            GameTab.Leaderboards -> HintBar("X" to "Want to Play", "L1/R1" to "Tabs", "B" to "Back", REFRESH to "Refresh")
            GameTab.Compare -> HintBar("A" to "Profile", "X" to "Want to Play", "L1/R1" to "Tabs", "B" to "Back", REFRESH to "Refresh")
            else -> HintBar("A" to "Open", "X" to "Want to Play", "L1/R1" to "Tabs", "B" to "Back", REFRESH to "Refresh")
        }
    }
}

/** Compact top: icon, title, Want to Play (focused on open) and your progress. */
@Composable
private fun GameHeader(vm: AppViewModel, screen: Screen.Game, g: GameDetail) {
    Column(
        Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Thumb(g.iconUrl, 48.dp)
            Column(Modifier.weight(1f)) {
                Text(g.title, color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(g.console, color = Palette.Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    AwardTag(g.award)
                    screen.user?.let { Tag("$it's progress", Palette.Soft) }
                }
            }
        }
        // Want to Play adds or removes the game through the website session in
        // the background, signing in once first if needed (RaWeb).
        val wanted = vm.isWanted(g.id)
        val busy = vm.wantBusy == g.id
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                focusKey = "want", vm = vm,
            )
        }
        vm.wantError?.let { Text(it, color = Palette.Error, fontSize = 11.sp) }
        if (g.total > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressLine(g.fraction, g.isHardcore, Modifier.weight(1f))
                Text(
                    "${g.shown}/${g.total} · ${num(g.pointsEarned)}/${num(g.points)} pts",
                    color = Palette.Dim, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/** Achievements · Info · Series · Similar · Compare, switched with LB/RB or a tap. */
@Composable
private fun GameTabStrip(vm: AppViewModel, st: GameState) {
    val scroll = rememberScrollState()
    val bounds = remember { mutableStateMapOf<GameTab, Pair<Int, Int>>() }
    var viewport by remember { mutableIntStateOf(0) }
    val margin = with(LocalDensity.current) { 24.dp.roundToPx() }
    LaunchedEffect(st.tab, viewport, bounds.size) {
        val all = GameTab.entries
        val i = st.tab.ordinal
        val sel = bounds[st.tab] ?: return@LaunchedEffect
        if (viewport <= 0) return@LaunchedEffect
        // Keep the selected tab and its neighbors in view when they fit.
        val left = (all.getOrNull(i - 1)?.let { bounds[it]?.first } ?: sel.first) - margin
        val right = (all.getOrNull(i + 1)?.let { bounds[it]?.second } ?: sel.second) + margin
        val (from, to) = if (right - left <= viewport) left to right else (sel.first - margin) to (sel.second + margin)
        var target = scroll.value
        if (from < target) target = from
        if (to > target + viewport) target = to - viewport
        target = target.coerceIn(0, scroll.maxValue)
        if (target != scroll.value) scroll.animateScrollTo(target)
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        ButtonBadge("L1")
        Row(
            Modifier.weight(1f).padding(horizontal = 6.dp).onSizeChanged { viewport = it.width }.horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            GameTab.entries.forEach { t ->
                Pill(
                    t.label,
                    onClick = { vm.selectGameTab(t) },
                    selected = st.tab == t,
                    compact = true,
                    modifier = Modifier.onPlaced { c ->
                        val x = c.positionInParent().x.toInt()
                        bounds[t] = x to x + c.size.width
                    },
                )
            }
        }
        ButtonBadge("R1")
    }
}

@Composable
private fun AchievementsTab(vm: AppViewModel, st: GameState, g: GameDetail) {
    val list = vm.filteredAchievements(st)
    CardGrid(st.listStates.getValue(GameTab.Achievements)) {
        if (g.achievements.isEmpty()) {
            fullWidthItem("none") { EmptyView("This game has no achievements yet.") }
            return@CardGrid
        }
        fullWidthItem("filter") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("Show: ${st.filter.label}", onClick = { vm.option() }, compact = true, icon = Icons.AutoMirrored.Rounded.List, focusKey = "filter", vm = vm)
                Spacer(Modifier.weight(1f))
                Text("${num(list.size)} shown", color = Palette.Dim, fontSize = 12.sp)
            }
        }
        if (list.isEmpty()) {
            fullWidthItem("empty") {
                EmptyView(
                    when (st.filter) {
                        AchFilter.Locked -> "All unlocked. Nice!"
                        AchFilter.Missable -> "No missable achievements in this game."
                        else -> "Nothing unlocked yet."
                    }
                )
            }
        }
        items(list, key = { it.id }) { a -> AchievementRow(vm, a, g.players, g.title) }
    }
}

/** Images, totals and details; every row is selectable so the D-pad scrolls through it all. */
@Composable
private fun InfoTab(vm: AppViewModel, st: GameState, g: GameDetail) {
    val facts = gameFacts(g, (st.extras as? Load.Ok)?.data)
    CardGrid(st.listStates.getValue(GameTab.Info)) {
        if (g.images.isNotEmpty()) fullWidthItem("media") { GameMedia(vm, g) }
        if (g.total > 0) fullWidthItem("chips") { StatChips(g) }
        facts.forEachIndexed { i, (label, value) ->
            item(key = "fact:$i") {
                FocusRow("fact:$i", vm) {
                    Text(label, color = Palette.Dim, fontSize = 12.sp, modifier = Modifier.width(78.dp))
                    Text(value, color = Palette.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                }
            }
        }
        if (facts.isEmpty() && g.images.isEmpty()) fullWidthItem("none") { EmptyView("No extra details for this game.") }
    }
}

/** The game's series (oldest first), then the other hubs it's in. */
@Composable
private fun SeriesTab(vm: AppViewModel, st: GameState, screen: Screen.Game) {
    when (val e = st.extras) {
        null, Load.Loading -> LoadingView()
        is Load.Err -> ErrorView(e.message, vm) { vm.refresh() }
        is Load.Ok -> CardGrid(st.listStates.getValue(GameTab.Series)) {
            val series = e.data.series
            if (series == null) {
                fullWidthItem("noseries") { SectionHeading("Not part of a series", "RetroAchievements doesn't list a series for this game.") }
            } else {
                fullWidthItem("h:series") { SectionHeading(hubName(series.title), "${num(series.games)} games in the series") }
                when (val list = st.series) {
                    null, Load.Loading -> fullWidthItem("loading") { Text("Loading the series…", color = Palette.Dim, fontSize = 12.sp) }
                    is Load.Err -> fullWidthItem("err") { Text(list.message, color = Palette.Error, fontSize = 12.sp) }
                    is Load.Ok -> items(list.data, key = { "s${it.id}" }) { r ->
                        RelatedGameRow(vm, "ser:${r.id}", r, current = r.id == screen.id)
                    }
                }
            }
            val hubs = e.data.hubs.filter { it.id != series?.id }
            if (hubs.isNotEmpty()) {
                fullWidthItem("h:hubs") { SectionHeading("Also in", "Other RetroAchievements hubs with this game") }
                items(hubs, key = { "h${it.id}" }) { h ->
                    FocusRow("hub:${h.id}", vm) {
                        Text(hubName(h.title), color = Palette.Text, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        hubKind(h.title)?.let { Tag(it, Palette.Soft) }
                        if (h.games > 0) Text("${num(h.games)} games", color = Palette.Dim, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SimilarTab(vm: AppViewModel, st: GameState) {
    when (val e = st.extras) {
        null, Load.Loading -> LoadingView()
        is Load.Err -> ErrorView(e.message, vm) { vm.refresh() }
        is Load.Ok -> if (e.data.similar.isEmpty()) {
            EmptyView("RetroAchievements doesn't list similar games for this one yet.")
        } else CardGrid(st.listStates.getValue(GameTab.Similar)) {
            items(e.data.similar, key = { it.id }) { r -> RelatedGameRow(vm, "sim:${r.id}", r, current = false) }
        }
    }
}

/** You and the people you follow, most progress first. */
@Composable
private fun CompareTab(vm: AppViewModel, st: GameState) {
    when (val c = st.compare) {
        null, Load.Loading -> LoadingView()
        is Load.Err -> ErrorView(c.message, vm) { vm.refresh() }
        is Load.Ok -> CardGrid(st.listStates.getValue(GameTab.Compare)) {
            if (c.data.none { !it.isMe }) {
                fullWidthItem("nobody") { SectionHeading("Nobody you follow has played this yet", "Only you so far.") }
            }
            items(c.data, key = { it.user }) { p ->
                FocusRow("cmp:${p.user}", vm, onClick = if (p.isMe) null else ({ vm.openUser(p.user) })) {
                    Thumb(p.avatarUrl, 36.dp, shape = CircleShape)
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (p.isMe) "${p.user} (you)" else p.user,
                            color = if (p.isMe) Palette.Accent else Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProgressLine(p.fraction, p.earnedHc >= p.earned && p.earnedHc > 0, Modifier.weight(1f))
                            Text("${p.shown}/${p.total}", color = Palette.Dim, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp))
                        }
                        if (p.earnedHc > 0) Text("${p.earnedHc} in hardcore", color = Palette.Faint, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, sub: String) {
    Column(Modifier.padding(start = 2.dp, top = 6.dp, bottom = 2.dp)) {
        Text(title, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(sub, color = Palette.Dim, fontSize = 11.sp)
    }
}

/** "[Subseries - Paper Mario]" -> "Paper Mario". */
private fun hubName(title: String): String =
    if (title.startsWith("[") && " - " in title) title.substringAfter(" - ").removeSuffix("]").trim() else title

/** "[Subseries - Paper Mario]" -> "Subseries". */
private fun hubKind(title: String): String? =
    if (title.startsWith("[") && " - " in title) title.removePrefix("[").substringBefore(" - ").trim() else null

/** A game in a series or similar-games list; opens it. */
@Composable
fun RelatedGameRow(vm: AppViewModel, key: String, r: RelatedGame, current: Boolean) {
    FocusRow(key, vm, onClick = if (current) null else ({ vm.openGame(r.id, r.title) })) {
        Thumb(r.iconUrl, 44.dp)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    r.title, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (current) Tag("This game", Palette.Accent)
            }
            val sub = listOfNotNull(r.console, r.released?.take(4)).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, color = Palette.Dim, fontSize = 11.sp)
            Text(
                (if (r.achievements > 0) "${num(r.achievements)} achievements · ${num(r.points)} pts" else "No achievements yet") +
                    if (r.players > 0) " · ${num(r.players)} players" else "",
                color = Palette.Faint, fontSize = 11.sp,
            )
        }
        if (!current) WantToggle(vm, r.id)
    }
}

@Composable
private fun AchievementRow(vm: AppViewModel, a: Achievement, players: Int, gameTitle: String) {
    val textAlpha = if (a.unlocked) 1f else 0.75f
    FocusRow("a:${a.id}", vm, onClick = { vm.openAchievement(a, gameTitle, players) }) {
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

/**
 * Title screen, in-game screenshot and box art, scrolling sideways. Each is
 * reachable with the D-pad; A (or a tap) opens it full screen.
 */
@Composable
private fun GameMedia(vm: AppViewModel, g: GameDetail) {
    val images = g.images
    if (images.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        images.forEachIndexed { i, (url, label) ->
            MediaImage(vm, "img:$i", url, label, width = if (label == "Box art") 84.dp else 112.dp) {
                vm.openImages(images, i)
            }
        }
    }
}

@Composable
private fun MediaImage(vm: AppViewModel, key: String, url: String, label: String, width: Dp, onOpen: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    if (vm.focusTarget == key) {
        LaunchedEffect(key) {
            requestFocusWhenReady(requester) { focused }
            vm.consumeFocus(key)
        }
    }
    val shape = RoundedCornerShape(8.dp)
    AsyncImage(
        model = url,
        contentDescription = label,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .width(width)
            .height(84.dp)
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) vm.rememberFocus(key)
            }
            .clip(shape)
            .background(Palette.Surface)
            .border(2.dp, if (focused) Palette.Accent else Color.Transparent, shape)
            .clickable(onClick = onOpen),
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

/** Developer, publisher, genre, release, players, your playtime and authors, as (label, value). */
fun gameFacts(g: GameDetail, extras: GameExtras? = null): List<Pair<String, String>> = buildList {
    g.developer?.let { add("Developer" to it) }
    g.publisher?.let { add("Publisher" to it) }
    g.genre?.let { add("Genre" to it) }
    releaseText(g.released, g.releasedPrecision)?.let { add("Released" to it) }
    if (g.players > 0) {
        add("Players" to "${num(g.players)}" + if (g.playersHardcore > 0) " · ${num(g.playersHardcore)} hardcore" else "")
    }
    if (g.playtimeSeconds > 0) add("Playtime" to playtime(g.playtimeSeconds))
    // Median time to beat, from the website's game page (loaded with the Info tab).
    if (extras != null && (extras.timeToBeat != null || extras.timeToBeatHardcore != null)) {
        add(
            "Time to beat" to listOfNotNull(
                extras.timeToBeat?.let { playtime(it.toLong()) },
                extras.timeToBeatHardcore?.let { playtime(it.toLong()) + " hardcore" },
            ).joinToString(" · ")
        )
    }
    if (g.authors.isNotEmpty()) {
        val a = g.authors
        add("Made by" to if (a.size <= 3) a.joinToString(", ") else "${a.take(2).joinToString(", ")} and ${a.size - 2} others")
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

/** The game's leaderboards: each leader and, where you have one, your rank. */
@Composable
private fun LeaderboardsTab(vm: AppViewModel, st: GameState) {
    when (val l = st.leaderboards) {
        null, Load.Loading -> LoadingView()
        is Load.Err -> ErrorView(l.message, vm) { vm.refresh() }
        is Load.Ok -> if (l.data.isEmpty()) {
            EmptyView("This game has no leaderboards.")
        } else CardGrid(st.listStates.getValue(GameTab.Leaderboards)) {
            items(l.data, key = { it.id }) { b ->
                FocusRow("lb:${b.id}", vm) {
                    Column(Modifier.weight(1f)) {
                        Text(b.title, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (b.description.isNotEmpty()) Text(b.description, color = Palette.Dim, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        b.leader?.let { Text("1st: $it · ${b.leaderScore.orEmpty()}", color = Palette.Faint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    if (b.myRank != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("#${num(b.myRank)}", color = Palette.Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            b.myScore?.let { Text(it, color = Palette.Dim, fontSize = 11.sp) }
                        }
                    }
                }
            }
        }
    }
}
