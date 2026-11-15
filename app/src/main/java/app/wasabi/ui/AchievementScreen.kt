package app.wasabi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wasabi.data.MEDIA

/** One achievement: its details, who unlocked it recently, and comments. */
@Composable
fun AchievementScreen(vm: AppViewModel, screen: Screen.AchievementDetail) {
    val a = screen.achievement
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Thumb(a.badgeUrl, 64.dp, alpha = if (a.unlocked) 1f else 0.85f)
            Column(Modifier.weight(1f)) {
                Text(a.title, color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(a.description, color = Palette.Dim, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(
                    "${a.points} pts" + (if (a.trueRatio > 0) " · ${num(a.trueRatio)} RetroPoints" else "") + " · ${screen.gameTitle}",
                    color = Palette.Faint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (a.unlocked) {
                    Text("You unlocked it ${ago(a.unlockedAt)}" + if (a.hardcore) " in hardcore" else "", color = Palette.Accent, fontSize = 11.sp)
                }
            }
        }
        Box(Modifier.weight(1f)) {
            when (val s = vm.achievementInfo) {
                null, Load.Loading -> LoadingView()
                is Load.Err -> ErrorView(s.message, vm) { vm.refresh() }
                is Load.Ok -> {
                    val info = s.data
                    CardGrid(vm.achievementListState) {
                        fullWidthItem("rate") {
                            Text(
                                if (info.players > 0) {
                                    "Unlocked by ${num(info.unlocks)} of ${num(info.players)} players · ${num(info.unlocksHardcore)} in hardcore"
                                } else "Unlocked ${num(info.unlocks)} times",
                                color = Palette.Dim, fontSize = 12.sp,
                                modifier = Modifier.padding(start = 2.dp, top = 2.dp),
                            )
                        }
                        fullWidthItem("h:recent") { Heading2("Recent unlocks") }
                        if (info.recent.isEmpty()) fullWidthItem("none:recent") { Text("Nobody yet.", color = Palette.Faint, fontSize = 12.sp) }
                        items(info.recent.size, key = { "u$it" }) { i ->
                            val u = info.recent[i]
                            FocusRow("au:$i", vm, onClick = { vm.openUser(u.user) }) {
                                Thumb("$MEDIA/UserPic/${u.user}.png", 32.dp, shape = CircleShape)
                                Text(u.user, color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(ago(u.date) + if (u.hardcore) "" else " · softcore", color = if (u.hardcore) Palette.Accent else Palette.Dim, fontSize = 11.sp)
                            }
                        }
                        fullWidthItem("h:comments") { Heading2("Comments") }
                        if (info.comments.isEmpty()) fullWidthItem("none:comments") { Text("No comments yet.", color = Palette.Faint, fontSize = 12.sp) }
                        items(info.comments.size, key = { "c$it" }) { i ->
                            val c = info.comments[i]
                            FocusRow("cm:$i", vm) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(c.user, color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                        Text(ago(c.date), color = Palette.Faint, fontSize = 11.sp)
                                    }
                                    Text(c.text, color = Palette.Dim, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        HintBar("A" to "Profile", "B" to "Back", REFRESH to "Refresh")
    }
}

@Composable
private fun Heading2(text: String) {
    Text(text, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 2.dp, top = 8.dp))
}
