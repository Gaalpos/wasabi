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
    val numAwardedHc: Int = 0,
    /** RetroPoints: points weighted by how rare the achievement is. */
    val trueRatio: Int = 0,
    val author: String? = null,
) {
    val missable get() = type == "missable"
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
    val titleImageUrl: String? = null,
    val ingameImageUrl: String? = null,
    val boxArtUrl: String? = null,
    val developer: String? = null,
    val publisher: String? = null,
    val genre: String? = null,
    /** YYYY-MM-DD, precise to [releasedPrecision] ("day", "month", "year"). */
    val released: String? = null,
    val releasedPrecision: String? = null,
    val playersHardcore: Int = 0,
    val playersSoftcore: Int = 0,
    /** Your total time in the game, in seconds (0 when unknown). */
    val playtimeSeconds: Long = 0,
) : GameProgress {
    val retroPoints get() = achievements.sumOf { it.trueRatio }
    val missableCount get() = achievements.count { it.missable }
    val authors get() = achievements.mapNotNull { it.author }.distinct()

    /** Available images as (url, label), in display order. */
    val images: List<Pair<String, String>>
        get() = listOfNotNull(
            titleImageUrl?.let { it to "Title screen" },
            ingameImageUrl?.let { it to "In game" },
            boxArtUrl?.let { it to "Box art" },
        )
    override val total get() = achievements.size
    override val earned get() = achievements.count { it.unlocked }
    override val earnedHc get() = achievements.count { it.hardcore }
    val points get() = achievements.sumOf { it.points }
    val pointsEarned get() = achievements.filter { it.unlocked }.sumOf { it.points }
}

data class FollowedUser(
    val user: String,
    val avatarUrl: String?,
    val points: Int,
    val softPoints: Int,
    val followsBack: Boolean,
)

/** Everything shown on another user's page. */
data class UserView(
    val profile: Profile,
    val recentGames: List<RecentGame>,
    val unlocks: List<Unlock>,
)

data class Console(val id: Int, val name: String, val iconUrl: String?)

data class ConsoleGame(
    val id: Int,
    val title: String,
    val iconUrl: String?,
    val achievements: Int,
    val points: Int,
    val console: String = "",
)

/** A game people you follow have played recently and you haven't started. */
data class FriendGame(val game: RecentGame, val friends: List<String>)

data class Suggestions(
    val almostDone: List<LibraryGame>,
    val friendsPlaying: List<FriendGame>,
    val fromWantToPlay: List<WantGame>,
) {
    val isEmpty get() = almostDone.isEmpty() && friendsPlaying.isEmpty() && fromWantToPlay.isEmpty()
}

/** A hub (series, genre, etc.) a game belongs to, from the website's game page. */
data class HubRef(val id: Int, val title: String, val games: Int)

/** A game in a hub or similar-games list. */
data class RelatedGame(
    val id: Int,
    val title: String,
    val iconUrl: String?,
    val console: String?,
    val achievements: Int,
    val points: Int,
    val released: String? = null,
)

/** Series, hubs and similar games for one game (website game page + API v2). */
data class GameExtras(
    val series: HubRef?,
    val hubs: List<HubRef>,
    val similar: List<RelatedGame>,
)

/** Someone's progress in one game, for Compare. */
data class CompareEntry(
    val user: String,
    val avatarUrl: String?,
    val earned: Int,
    val earnedHc: Int,
    val total: Int,
    val isMe: Boolean = false,
) {
    val shown get() = maxOf(earned, earnedHc)
    val fraction get() = if (total > 0) shown.toFloat() / total else 0f
}
