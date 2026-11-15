package app.wasabi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wasabi.data.ConsoleGame
import app.wasabi.data.FollowedUser
import app.wasabi.data.FriendGame
import app.wasabi.data.Unlock
import app.wasabi.data.UserView

@Composable
private fun SectionTitle(text: String, sub: String? = null) {
    Column(Modifier.padding(start = 2.dp, top = 8.dp, bottom = 2.dp)) {
        Text(text, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (sub != null) Text(sub, color = Palette.Dim, fontSize = 11.sp)
    }
}

// ---------------------------------------------------------------- For You

@Composable
fun ForYouTab(vm: AppViewModel) {
    when (val s = vm.suggested) {
        null, Load.Loading -> LoadingView()
        is Load.Err -> ErrorView(s.message, vm) { vm.refresh() }
        is Load.Ok -> {
            val data = s.data
            if (data.isEmpty) {
                EmptyView("No suggestions yet. Play a few games, follow some players or fill your Want to Play list.")
            } else CardGrid(vm.listState(HomeTab.ForYou)) {
                if (data.almostDone.isNotEmpty()) {
                    fullWidthItem("h:a") { SectionTitle("Close to mastering", "You're at least halfway through these") }
                    items(data.almostDone, key = { "a${it.id}" }) { g ->
                        GameRow(vm, "s:a:${g.id}", g.title, g.iconUrl, g, g.award, sub = "${g.console} · ${g.total - g.shown} to go") {
                            vm.openGame(g.id, g.title)
                        }
                    }
                }
                if (data.friendsPlaying.isNotEmpty()) {
                    fullWidthItem("h:f") { SectionTitle("Your friends are playing", "Games people you follow played recently") }
                    items(data.friendsPlaying, key = { "f${it.game.id}" }) { f -> FriendGameRow(vm, f) }
                }
                if (data.fromWantToPlay.isNotEmpty()) {
                    fullWidthItem("h:w") { SectionTitle("From your Want to Play", "Random picks; refresh with X for others") }
                    items(data.fromWantToPlay, key = { "w${it.id}" }) { g -> WantRow(vm, "s:w:${g.id}", g) }
                }
            }
        }
    }
}

@Composable
private fun FriendGameRow(vm: AppViewModel, f: FriendGame) {
    val g = f.game
    val who = when (f.friends.size) {
        1 -> f.friends[0]
        2 -> "${f.friends[0]} and ${f.friends[1]}"
        else -> "${f.friends[0]} and ${f.friends.size - 1} others"
    }
    FocusRow("s:f:${g.id}", vm, onClick = { vm.openGame(g.id, g.title) }) {
        Thumb(g.iconUrl, 44.dp)
        Column(Modifier.weight(1f)) {
            Text(g.title, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${g.console} · ${num(g.total)} achievements", color = Palette.Dim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Played by $who", color = Palette.Accent, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------------------------------------------------------- Following

@Composable
fun FollowingTab(vm: AppViewModel) {
    when (val s = vm.following) {
        null, Load.Loading -> LoadingView()
        is Load.Err -> ErrorView(s.message, vm) { vm.refresh() }
        is Load.Ok -> if (s.data.isEmpty()) {
            EmptyView("You aren't following anyone yet. Follow players on retroachievements.org to see them here.")
        } else CardGrid(vm.listState(HomeTab.Following)) {
            fullWidthItem("count") {
                Text(
                    "Following ${num(s.data.size)}",
                    color = Palette.Dim, fontSize = 12.sp,
                    modifier = Modifier.padding(start = 2.dp, top = 2.dp, bottom = 2.dp),
                )
            }
            items(s.data, key = { it.user }) { u -> FollowedRow(vm, u) }
        }
    }
}

@Composable
private fun FollowedRow(vm: AppViewModel, u: FollowedUser) {
    FocusRow("f:${u.user}", vm, onClick = { vm.openUser(u.user) }) {
        Thumb(u.avatarUrl, 40.dp, shape = CircleShape)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    u.user, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (u.followsBack) Tag("Follows you", Palette.Soft)
            }
            Text(
                "${num(u.points)} pts" + if (u.softPoints > 0) " · ${num(u.softPoints)} soft" else "",
                color = Palette.Dim, fontSize = 11.sp,
            )
        }
    }
}

/** Another player's page: who they are, what they played, what they unlocked. */
@Composable
fun UserScreen(vm: AppViewModel, screen: Screen.User) {
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f)) {
            val sidePanel = maxWidth >= WideWidth && maxWidth > maxHeight
            when (val s = vm.user) {
                null, Load.Loading -> LoadingView()
                is Load.Err -> ErrorView(s.message, vm) { vm.refresh() }
                is Load.Ok -> if (sidePanel) {
                    Row(Modifier.fillMaxSize()) {
                        Column(
                            Modifier.width(260.dp).fillMaxHeight().verticalScroll(rememberScrollState())
                                .padding(start = 14.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
                        ) { UserHeader(vm, s.data) }
                        UserLists(vm, screen, s.data, withHeader = false, modifier = Modifier.weight(1f))
                    }
                } else {
                    UserLists(vm, screen, s.data, withHeader = true)
                }
            }
        }
        HintBar("A" to "Open", "B" to "Back", "X" to "Refresh")
    }
}

@Composable
private fun UserHeader(vm: AppViewModel, v: UserView) {
    val p = v.profile
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Thumb(p.avatarUrl, 56.dp, shape = CircleShape)
            Column(Modifier.weight(1f)) {
                Text(p.user, color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${num(p.points)} pts", color = Palette.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                if (p.softPoints > 0) Text("${num(p.softPoints)} softcore", color = Palette.Soft, fontSize = 11.sp)
            }
        }
        p.motto?.let { Text("“$it”", color = Palette.Dim, fontSize = 12.sp) }
        p.richPresence?.let { Text("Last seen: $it", color = Palette.Faint, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        Spacer(Modifier.height(2.dp))
        Pill("‹ Back", onClick = { vm.back() })
    }
}

@Composable
private fun UserLists(vm: AppViewModel, screen: Screen.User, v: UserView, withHeader: Boolean, modifier: Modifier = Modifier) {
    CardGrid(vm.userListState, modifier) {
        if (withHeader) fullWidthItem("header") { UserHeader(vm, v) }
        fullWidthItem("h:games") { SectionTitle("Recently played", "Open a game to see ${screen.name}'s progress") }
        if (v.recentGames.isEmpty()) {
            fullWidthItem("none:games") { Text("Nothing played recently.", color = Palette.Faint, fontSize = 12.sp) }
        }
        items(v.recentGames, key = { "g${it.id}" }) { g ->
            GameRow(
                vm, "ug:${g.id}", g.title, g.iconUrl, g, award = null,
                sub = if (g.lastPlayed != null) "${g.console} · ${ago(g.lastPlayed)}" else g.console,
            ) { vm.openGame(g.id, g.title, of = screen.name) }
        }
        fullWidthItem("h:unlocks") { SectionTitle("Recent unlocks", "Last 30 days") }
        if (v.unlocks.isEmpty()) {
            fullWidthItem("none:unlocks") { Text("No unlocks in the last 30 days.", color = Palette.Faint, fontSize = 12.sp) }
        }
        items(v.unlocks, key = { "u${it.achievementId}:${it.date}" }) { u ->
            UnlockRow(vm, "uu:${u.achievementId}:${u.date}", u) { vm.openGame(u.gameId, u.gameTitle, of = screen.name) }
        }
    }
}

@Composable
fun UnlockRow(vm: AppViewModel, key: String, u: Unlock, onClick: () -> Unit) {
    FocusRow(key, vm, onClick = onClick) {
        Thumb(u.badgeUrl, 44.dp)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    u.title, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
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

// ---------------------------------------------------------------- Browse

@Composable
fun BrowseTab(vm: AppViewModel) {
    when (val s = vm.consoles) {
        null, Load.Loading -> LoadingView()
        is Load.Err -> ErrorView(s.message, vm) { vm.refresh() }
        is Load.Ok -> CardGrid(vm.listState(HomeTab.Browse)) {
            fullWidthItem("hint") {
                Text(
                    "Pick a console to search its games",
                    color = Palette.Dim, fontSize = 12.sp,
                    modifier = Modifier.padding(start = 2.dp, top = 2.dp, bottom = 2.dp),
                )
            }
            items(s.data, key = { it.id }) { c ->
                FocusRow("c:${c.id}", vm, onClick = { vm.openConsole(c) }) {
                    Thumb(c.iconUrl, 32.dp)
                    Text(
                        c.name, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Every game with achievements on one console, with a search box. */
@Composable
fun ConsoleScreen(vm: AppViewModel, screen: Screen.ConsoleGames) {
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Pill("‹ Back", onClick = { vm.back() })
            Column(Modifier.weight(1f)) {
                Text(screen.name, color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                (vm.consoleGames as? Load.Ok)?.let {
                    val shown = vm.filteredConsoleGames().size
                    Text(
                        if (shown == it.data.size) "${num(shown)} games" else "${num(shown)} of ${num(it.data.size)} games",
                        color = Palette.Dim, fontSize = 11.sp,
                    )
                }
            }
        }
        OutlinedTextField(
            value = vm.consoleQuery,
            onValueChange = { vm.consoleQuery = it },
            placeholder = { Text("Search games") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        )
        Column(Modifier.weight(1f)) {
            when (val s = vm.consoleGames) {
                null, Load.Loading -> LoadingView()
                is Load.Err -> ErrorView(s.message, vm) { vm.refresh() }
                is Load.Ok -> {
                    val games = vm.filteredConsoleGames()
                    if (games.isEmpty()) {
                        EmptyView(if (s.data.isEmpty()) "No games with achievements here yet." else "No games match \"${vm.consoleQuery.trim()}\".")
                    } else CardGrid(vm.consoleListState) {
                        items(games, key = { it.id }) { g -> ConsoleGameRow(vm, g) }
                    }
                }
            }
        }
        HintBar("A" to "Open", "B" to "Back", "X" to "Refresh")
    }
}

@Composable
private fun ConsoleGameRow(vm: AppViewModel, g: ConsoleGame) {
    FocusRow("cg:${g.id}", vm, onClick = { vm.openGame(g.id, g.title) }) {
        Thumb(g.iconUrl, 44.dp)
        Column(Modifier.weight(1f)) {
            Text(g.title, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${num(g.achievements)} achievements · ${num(g.points)} pts", color = Palette.Dim, fontSize = 11.sp)
        }
    }
}
