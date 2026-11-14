package app.wasabi.data

const val MEDIA = "https://media.retroachievements.org"

data class Profile(
    val user: String,
    val avatarUrl: String?,
    val motto: String?,
    val richPresence: String?,
    val points: Int,
    val softPoints: Int,
    val truePoints: Int,
    val memberSince: String?,
)

/** Progress numbers shared by every "game with progress" row. */
interface GameProgress {
    val total: Int
    val earned: Int
    val earnedHc: Int
    val shown get() = maxOf(earned, earnedHc)
    val fraction get() = if (total > 0) shown.toFloat() / total else 0f
    val isHardcore get() = earnedHc > 0 && earnedHc >= earned
}

data class RecentGame(
    val id: Int,
    val title: String,
    val console: String,
    val iconUrl: String?,
    val lastPlayed: String?,
    override val total: Int,
    override val earned: Int,
    override val earnedHc: Int,
) : GameProgress

data class LibraryGame(
    val id: Int,
    val title: String,
    val console: String,
    val iconUrl: String?,
    override val total: Int,
    override val earned: Int,
    override val earnedHc: Int,
    val award: String?,
    val lastUnlock: String?,
) : GameProgress

/** A game on the user's RetroAchievements "Want to Play" list. */
data class WantGame(
    val id: Int,
    val title: String,
    val console: String,
    val iconUrl: String?,
    val achievements: Int,
    val points: Int,
)

data class Unlock(
    val achievementId: Int,
    val title: String,
    val description: String,
    val points: Int,
    val badgeUrl: String?,
    val date: String?,
    val hardcore: Boolean,
    val gameId: Int,
    val gameTitle: String,
    val console: String,
)

data class Achievement(
    val id: Int,
    val title: String,
    val description: String,
    val points: Int,
    val badge: String,
    val order: Int,
    val type: String?,
    val earnedAt: String?,
    val earnedHcAt: String?,
    val numAwarded: Int,
) {
    val unlocked get() = earnedAt != null || earnedHcAt != null
    val hardcore get() = earnedHcAt != null
    val unlockedAt get() = earnedHcAt ?: earnedAt
    val badgeUrl get() = if (unlocked) "$MEDIA/Badge/$badge.png" else "$MEDIA/Badge/${badge}_lock.png"
}

data class GameDetail(
    val id: Int,
    val title: String,
    val console: String,
    val iconUrl: String?,
    val players: Int,
    val award: String?,
    val achievements: List<Achievement>,
) : GameProgress {
    override val total get() = achievements.size
    override val earned get() = achievements.count { it.unlocked }
    override val earnedHc get() = achievements.count { it.hardcore }
    val points get() = achievements.sumOf { it.points }
    val pointsEarned get() = achievements.filter { it.unlocked }.sumOf { it.points }
}
