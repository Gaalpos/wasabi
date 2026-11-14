package app.cheevos.ui

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cheevos.data.Achievement
import app.cheevos.data.GameDetail
import java.util.Locale

@Composable
fun GameScreen(vm: AppViewModel, screen: Screen.Game) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (val s = vm.game) {
                null, Load.Loading -> LoadingView()
                is Load.Err -> ErrorView(s.message, vm) { vm.refresh() }
                is Load.Ok -> GameContent(vm, s.data)
            }
        }
        HintBar("B" to "Back", "Y" to "Filter", "X" to "Refresh")
    }
}

@Composable
private fun GameContent(vm: AppViewModel, g: GameDetail) {
    val list = vm.filteredAchievements()
    LazyColumn(
        state = vm.gameListState,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "header") { GameHeader(vm, g) }
        if (g.achievements.isEmpty()) {
            item(key = "none") { EmptyView("This game has no achievements yet.") }
        } else if (list.isEmpty()) {
            item(key = "none") {
                EmptyView(if (vm.achFilter == AchFilter.Locked) "All unlocked. Nice!" else "Nothing unlocked yet.")
            }
        }
        items(list, key = { it.id }) { a -> AchievementRow(vm, a, g.players) }
    }
}

@Composable
private fun GameHeader(vm: AppViewModel, g: GameDetail) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
        if (g.total > 0) {
            ProgressLine(g.fraction, g.isHardcore)
            Text(
                "${g.shown} of ${g.total} unlocked · ${num(g.pointsEarned)}/${num(g.points)} pts" +
                    if (g.earnedHc in 1 until g.earned) " · ${g.earnedHc} hardcore" else "",
                color = Palette.Dim, fontSize = 12.sp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("‹ Back", onClick = { vm.back() })
            if (g.total > 0) Pill("Show: ${vm.achFilter.label}", onClick = { vm.option() })
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
            }
            Text(a.description, color = Palette.Dim.copy(alpha = textAlpha), fontSize = 12.sp)
            Spacer(Modifier.height(2.dp))
            val details = buildList {
                if (a.unlocked) {
                    add("Unlocked ${ago(a.unlockedAt)}" + if (a.hardcore) "" else " (softcore)")
                } else if (players > 0) {
                    add(String.format(Locale.getDefault(), "%.1f%% of players", 100f * a.numAwarded / players))
                }
                when (a.type) {
                    "missable" -> add("Missable")
                    "progression" -> add("Progression")
                    "win_condition" -> add("Win condition")
                }
            }
            if (details.isNotEmpty()) {
                Text(
                    details.joinToString(" · "),
                    color = if (a.hardcore) Palette.Accent else Palette.Faint,
                    fontSize = 11.sp,
                )
            }
        }
    }
}
