package app.wasabi.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Games from people you follow, checked for suggestions (keeps API calls modest). */
private const val FRIENDS_CHECKED = 10

/**
 * RetroAchievements has no suggestions endpoint, so Wasabi builds its own
 * from three sources:
 * - games you're at least halfway through but haven't mastered,
 * - games people you follow played recently that you haven't started,
 * - a few random picks from your Want to Play list.
 */
suspend fun RaApi.suggestions(): Suggestions = coroutineScope {
    val libraryJob = async { library() }
    val wantJob = async { runCatching { wantToPlay() }.getOrDefault(emptyList()) }
    val followingJob = async { runCatching { following() }.getOrDefault(emptyList()) }

    val library = libraryJob.await()
    val mine = library.map { it.id }.toSet()

    val almostDone = library
        .filter { it.total > 0 && it.award != "mastered" && it.fraction >= 0.5f && it.shown < it.total }
        .sortedWith(compareByDescending<LibraryGame> { it.fraction }.thenBy { it.total - it.shown })
        .take(10)

    // Who you follow, most points first: active players tend to have fresh games.
    val friends = followingJob.await().sortedByDescending { it.points + it.softPoints }.take(FRIENDS_CHECKED)
    val friendGames = friends.map { f ->
        async { runCatching { f.user to recentGames(f.user, 15) }.getOrNull() }
    }.awaitAll().filterNotNull()

    val byGame = linkedMapOf<Int, Pair<RecentGame, MutableList<String>>>()
    friendGames.forEach { (friend, games) ->
        games.filter { it.id !in mine && it.total > 0 }.forEach { g ->
            byGame.getOrPut(g.id) { g.copy(earned = 0, earnedHc = 0) to mutableListOf() }.second.add(friend)
        }
    }
    val friendsPlaying = byGame.values
        .map { (g, who) -> FriendGame(g, who.distinct()) }
        .sortedWith(
            compareByDescending<FriendGame> { it.friends.size }
                .thenByDescending { it.game.lastPlayed.orEmpty() }
        )
        .take(15)

    val fromWant = wantJob.await().filter { it.achievements > 0 && it.id !in mine }.shuffled().take(5)

    Suggestions(almostDone, friendsPlaying, fromWant)
}

/** Profile, recently played games and recent unlocks for another user. */
suspend fun RaApi.userView(name: String): UserView = coroutineScope {
    val profile = async { profile(name) }
    val games = async { runCatching { recentGames(name, 20) }.getOrDefault(emptyList()) }
    val unlocks = async { runCatching { recentUnlocks(name) }.getOrDefault(emptyList()) }
    UserView(profile.await(), games.await(), unlocks.await())
}

/**
 * Your progress in a game next to everyone you follow (up to 60 people),
 * most progress first. Followed players who haven't started it are left out.
 */
suspend fun RaApi.compare(gameId: Int, total: Int): List<CompareEntry> = coroutineScope {
    val gate = kotlinx.coroutines.sync.Semaphore(4)
    val friends = runCatching { following() }.getOrDefault(emptyList()).take(60)
    val me = async { runCatching { progressIn(user, gameId) }.getOrDefault(0 to 0) }
    val others = friends.map { f ->
        async {
            gate.acquire()
            try {
                runCatching { progressIn(f.user, gameId) }.getOrNull()
                    ?.takeIf { (soft, hard) -> soft > 0 || hard > 0 }
                    ?.let { (soft, hard) -> CompareEntry(f.user, f.avatarUrl, soft, hard, total) }
            } finally {
                gate.release()
            }
        }
    }.awaitAll().filterNotNull()
    val (soft, hard) = me.await()
    val mine = CompareEntry(user, "$MEDIA/UserPic/$user.png", soft, hard, total, isMe = true)
    (others + mine).sortedWith(compareByDescending<CompareEntry> { it.shown }.thenByDescending { it.earnedHc })
}
