package app.wasabi.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wasabi.data.GameProgress
import app.wasabi.data.Profile
import app.wasabi.data.WantGame

/** Below this width the layout is the single-column handheld/phone layout. */
val WideWidth = 600.dp

@Composable
fun HomeScreen(vm: AppViewModel) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WideWidth
        Column(Modifier.fillMaxSize()) {
            if (wide) {
                // 16:9 / 4:3 landscape: profile and tabs share one row to save height.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileBar(vm.profile, Modifier.widthIn(max = 300.dp).weight(1f, fill = false))
                    Tabs(vm, Modifier.weight(1f).padding(end = 10.dp))
                }
            } else {
                ProfileBar(vm.profile)
                Tabs(vm, Modifier.fillMaxWidth().padding(horizontal = 10.dp))
            }
            Box(Modifier.weight(1f).padding(top = 4.dp)) {
                when (vm.tab) {
                    HomeTab.Playing -> PlayingTab(vm)
                    HomeTab.ForYou -> ForYouTab(vm)
                    HomeTab.WantToPlay -> WantToPlayTab(vm)
                    HomeTab.Following -> FollowingTab(vm)
                    HomeTab.Browse -> BrowseTab(vm)
                    HomeTab.Search -> SearchTab(vm)
                    HomeTab.Library -> LibraryTab(vm)
                    HomeTab.Unlocks -> UnlocksTab(vm)
                    HomeTab.Profile -> ProfileTab(vm, wide)
                }
            }
            when (vm.tab) {
                HomeTab.Library -> HintBar("A" to "Open", "Y" to "Sort", "X" to "Refresh", "L1/R1" to "Tabs")
                HomeTab.Profile -> HintBar("X" to "Refresh", "L1/R1" to "Tabs", "Start" to "Settings")
                HomeTab.Search -> HintBar("A" to "Open", "X" to "Reload games", "L1/R1" to "Tabs")
                else -> HintBar("A" to "Open", "X" to "Refresh", "L1/R1" to "Tabs")
            }
        }
    }
}

/**
 * Tab strip: compact pills that scroll sideways. Fades with arrows show
 * when more tabs are off-screen on either side, L1/R1 keycaps flank it,
 * and the selected tab is kept in view when L1/R1 change it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tabs(vm: AppViewModel, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    Row(modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        KeyCap("L1")
        Box(Modifier.weight(1f).padding(horizontal = 6.dp)) {
            Row(
                Modifier.horizontalScroll(scroll),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                vm.visibleTabs.forEach { t ->
                    val selected = vm.tab == t
                    val requester = remember { BringIntoViewRequester() }
                    LaunchedEffect(selected) { if (selected) requester.bringIntoView() }
                    Pill(
                        t.label,
                        onClick = { vm.selectTab(t) },
                        selected = selected,
                        compact = true,
                        modifier = Modifier.bringIntoViewRequester(requester),
                    )
                }
                Pill("Settings", onClick = { vm.openSettings() }, compact = true)
            }
            Row(Modifier.matchParentSize()) {
                if (scroll.canScrollBackward) EdgeFade(left = true)
                Spacer(Modifier.weight(1f))
                if (scroll.canScrollForward) EdgeFade(left = false)
            }
        }
        KeyCap("R1")
    }
}

/** Fades the strip's edge into the background and points at hidden tabs. */
@Composable
private fun EdgeFade(left: Boolean) {
    val colors = listOf(Palette.Bg, Palette.Bg.copy(alpha = 0.85f), Color.Transparent)
    Box(
        Modifier.width(30.dp).fillMaxHeight()
            .background(Brush.horizontalGradient(if (left) colors else colors.reversed())),
        contentAlignment = if (left) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Text(if (left) "‹" else "›", color = Palette.Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun KeyCap(label: String) {
    Text(
        label,
        color = Palette.Dim,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(Palette.Surface)
            .padding(horizontal = 5.dp, vertical = 3.dp),
    )
}

@Composable
private fun ProfileBar(state: Load<Profile>, modifier: Modifier = Modifier) {
    val p = (state as? Load.Ok)?.data
    Row(
        modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Thumb(p?.avatarUrl, 36.dp, shape = CircleShape)
        Column(Modifier.weight(1f)) {
            Text(
                p?.user ?: "…",
                color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            p?.richPresence?.let {
                Text(it, color = Palette.Dim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (p != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text("${num(p.points)} pts", color = Palette.Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (p.softPoints > 0) {
                    Text("${num(p.softPoints)} soft", color = Palette.Soft, fontSize = 11.sp)
                }
            }
        }
    }
}

/**
 * A list that becomes a grid on wide screens: one column on the Pocket
 * Classic or a phone, two or more on 16:9 and 4:3 landscape.
 */
@Composable
fun CardGrid(state: LazyGridState, modifier: Modifier = Modifier, content: LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(300.dp),
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

fun LazyGridScope.fullWidthItem(key: Any, content: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

@Composable
private fun PlayingTab(vm: AppViewModel) {
    when (val s = vm.recent) {
        null, Load.Loading -> LoadingView()
        is Load.Err -> ErrorView(s.message, vm) { vm.refresh() }
        is Load.Ok -> if (s.data.isEmpty()) EmptyView("No games played yet.") else CardGrid(vm.listState(HomeTab.Playing)) {
            items(s.data, key = { it.id }) { g ->
                GameRow(
                    vm, "g:${g.id}", g.title, g.iconUrl, g, award = null,
                    sub = if (g.lastPlayed != null) "${g.console} · ${ago(g.lastPlayed)}" else g.console,
                ) { vm.openGame(g.id, g.title) }
            }
        }
    }
}

@Composable
private fun WantToPlayTab(vm: AppViewModel) {
    when (val s = vm.wantToPlay) {
        null, Load.Loading -> LoadingView()
        is Load.Err -> ErrorView(s.message, vm) { vm.refresh() }
        is Load.Ok -> if (s.data.isEmpty()) {
            EmptyView("Your Want to Play list is empty. Add games to it on retroachievements.org and they show up here.")
        } else CardGrid(vm.listState(HomeTab.WantToPlay)) {
            fullWidthItem("count") {
                Text(
                    "${num(s.data.size)} games on your list",
                    color = Palette.Dim, fontSize = 12.sp,
                    modifier = Modifier.padding(start = 2.dp, top = 2.dp, bottom = 2.dp),
                )
            }
            items(s.data, key = { it.id }) { g -> WantRow(vm, "w:${g.id}", g) }
        }
    }
}

@Composable
fun WantRow(vm: AppViewModel, key: String, g: WantGame) {
    FocusRow(key, vm, onClick = { vm.openGame(g.id, g.title) }) {
        Thumb(g.iconUrl, 44.dp)
        Column(Modifier.weight(1f)) {
            Text(
                g.title,
                color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(g.console, color = Palette.Dim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (g.achievements > 0) "${num(g.achievements)} achievements · ${num(g.points)} pts" else "No achievements yet",
                color = if (g.achievements > 0) Palette.Accent else Palette.Faint,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun LibraryTab(vm: AppViewModel) {
    when (val s = vm.library) {
        null, Load.Loading -> LoadingView()
        is Load.Err -> ErrorView(s.message, vm) { vm.refresh() }
        is Load.Ok -> {
            val games = vm.sortedLibrary()
            if (games.isEmpty()) EmptyView("Unlock an achievement and your games show up here.") else CardGrid(vm.listState(HomeTab.Library)) {
                fullWidthItem("sort") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pill("Sort: ${vm.librarySort.label}", onClick = { vm.option() })
                        Spacer(Modifier.weight(1f))
                        val mastered = games.count { it.award == "mastered" }
                        Text(
                            "${num(games.size)} games" + if (mastered > 0) " · $mastered mastered" else "",
                            color = Palette.Dim, fontSize = 12.sp,
                        )
                    }
                }
                items(games, key = { it.id }) { g ->
                    GameRow(vm, "g:${g.id}", g.title, g.iconUrl, g, g.award, sub = g.console) {
                        vm.openGame(g.id, g.title)
                    }
                }
            }
        }
    }
}

@Composable
fun GameRow(
    vm: AppViewModel,
    key: String,
    title: String,
    icon: String?,
    progress: GameProgress,
    award: String?,
    sub: String,
    onClick: () -> Unit,
) {
    FocusRow(key, vm, onClick = onClick) {
        Thumb(icon, 44.dp)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    title,
                    color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                AwardTag(award)
            }
            Text(sub, color = Palette.Dim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            if (progress.total > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressLine(progress.fraction, progress.isHardcore, Modifier.weight(1f))
                    Text(
                        "${progress.shown}/${progress.total}",
                        color = Palette.Dim, fontSize = 11.sp,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            } else {
                Text("No achievements yet", color = Palette.Faint, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun UnlocksTab(vm: AppViewModel) {
    when (val s = vm.unlocks) {
        null, Load.Loading -> LoadingView()
        is Load.Err -> ErrorView(s.message, vm) { vm.refresh() }
        is Load.Ok -> if (s.data.isEmpty()) EmptyView("Nothing unlocked in the last 30 days.") else CardGrid(vm.listState(HomeTab.Unlocks)) {
            items(s.data, key = { "${it.achievementId}:${it.date}" }) { u ->
                FocusRow("u:${u.achievementId}:${u.date}", vm, onClick = { vm.openGame(u.gameId, u.gameTitle) }) {
                    Thumb(u.badgeUrl, 44.dp)
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                u.title,
                                color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${u.points}",
                                color = if (u.hardcore) Palette.Accent else Palette.Soft,
                                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                        Text(u.description, color = Palette.Dim, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${u.gameTitle} · ${ago(u.date)}" + if (u.hardcore) "" else " · softcore",
                            color = Palette.Faint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileTab(vm: AppViewModel, wide: Boolean) {
    when (val s = vm.profile) {
        Load.Loading -> LoadingView()
        is Load.Err -> ErrorView(s.message, vm) { vm.refresh() }
        is Load.Ok -> {
            val p = s.data
            if (wide) {
                // Landscape: identity on the left, numbers on the right.
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Column(
                        Modifier.weight(0.8f).fillMaxSize().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    ) { Identity(vm, p) }
                    Column(
                        Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    ) { Stats(p) }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Identity(vm, p, showSignOut = false)
                    Spacer(Modifier.height(4.dp))
                    Stats(p)
                    Spacer(Modifier.height(8.dp))
                    Pill("Sign out", onClick = { vm.signOut() }, focusKey = "signout", vm = vm)
                }
            }
        }
    }
}

@Composable
private fun Identity(vm: AppViewModel, p: Profile, showSignOut: Boolean = true) {
    Thumb(p.avatarUrl, 72.dp, shape = CircleShape)
    Text(p.user, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    p.motto?.let { Text("“$it”", color = Palette.Dim, fontSize = 13.sp, textAlign = TextAlign.Center) }
    if (showSignOut) {
        Spacer(Modifier.height(4.dp))
        Pill("Sign out", onClick = { vm.signOut() }, focusKey = "signout", vm = vm)
    }
}

@Composable
private fun Stats(p: Profile) {
    Column(Modifier.widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Stat("Hardcore points", num(p.points), Palette.Accent)
        Stat("Softcore points", num(p.softPoints), Palette.Soft)
        Stat("RetroPoints", num(p.truePoints), Palette.Text)
        p.memberSince?.let { Stat("Member since", fullDate(it), Palette.Text) }
        p.richPresence?.let {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Palette.Surface).padding(10.dp),
            ) {
                Text("Last seen", color = Palette.Dim, fontSize = 11.sp)
                Text(it, color = Palette.Text, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Palette.Surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Palette.Dim, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
