package app.cheevos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cheevos.data.GameProgress
import app.cheevos.data.Profile

@Composable
fun HomeScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        ProfileBar(vm.profile)
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            HomeTab.entries.forEach { t ->
                Pill(t.label, onClick = { vm.selectTab(t) }, selected = vm.tab == t)
            }
        }
        Box(Modifier.weight(1f)) {
            when (vm.tab) {
                HomeTab.Playing -> PlayingTab(vm)
                HomeTab.Library -> LibraryTab(vm)
                HomeTab.Unlocks -> UnlocksTab(vm)
                HomeTab.Profile -> ProfileTab(vm)
            }
        }
        if (vm.tab == HomeTab.Library) {
            HintBar("A" to "Open", "Y" to "Sort", "X" to "Refresh", "L1/R1" to "Tabs")
        } else if (vm.tab == HomeTab.Profile) {
            HintBar("X" to "Refresh", "L1/R1" to "Tabs")
        } else {
            HintBar("A" to "Open", "X" to "Refresh", "L1/R1" to "Tabs")
        }
    }
}

@Composable
private fun ProfileBar(state: Load<Profile>) {
    val p = (state as? Load.Ok)?.data
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 2.dp),
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

private val listPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)

@Composable
private fun PlayingTab(vm: AppViewModel) {
    when (val s = vm.recent) {
        null, Load.Loading -> LoadingView()
        is Load.Err -> ErrorView(s.message, vm) { vm.refresh() }
        is Load.Ok -> if (s.data.isEmpty()) EmptyView("No games played yet.") else LazyColumn(
            state = vm.listState(HomeTab.Playing),
            contentPadding = listPadding,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(s.data, key = { it.id }) { g ->
                GameRow(
                    vm, "g:${g.id}", g.title, g.console, g.iconUrl, g, award = null,
                    sub = if (g.lastPlayed != null) "${g.console} · ${ago(g.lastPlayed)}" else g.console,
                ) { vm.openGame(g.id, g.title) }
            }
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
            if (games.isEmpty()) EmptyView("Unlock an achievement and your games show up here.") else LazyColumn(
                state = vm.listState(HomeTab.Library),
                contentPadding = listPadding,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "sort") {
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
                    GameRow(
                        vm, "g:${g.id}", g.title, g.console, g.iconUrl, g, g.award,
                        sub = g.console,
                    ) { vm.openGame(g.id, g.title) }
                }
            }
        }
    }
}

@Composable
private fun GameRow(
    vm: AppViewModel,
    key: String,
    title: String,
    console: String,
    icon: String?,
    progress: GameProgress,
    award: String?,
    sub: String = console,
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
        is Load.Ok -> if (s.data.isEmpty()) EmptyView("Nothing unlocked in the last 30 days.") else LazyColumn(
            state = vm.listState(HomeTab.Unlocks),
            contentPadding = listPadding,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
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
private fun ProfileTab(vm: AppViewModel) {
    when (val s = vm.profile) {
        Load.Loading -> LoadingView()
        is Load.Err -> ErrorView(s.message, vm) { vm.refresh() }
        is Load.Ok -> {
            val p = s.data
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Thumb(p.avatarUrl, 72.dp, shape = CircleShape)
                Text(p.user, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                p.motto?.let { Text("“$it”", color = Palette.Dim, fontSize = 13.sp) }
                Spacer(Modifier.height(4.dp))
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
                Spacer(Modifier.height(8.dp))
                Pill("Sign out", onClick = { vm.signOut() }, focusKey = "signout", vm = vm)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Palette.Surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Palette.Dim, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
