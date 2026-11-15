# Wasabi

A small Android app for checking your RetroAchievements progress on a
handheld. It's built for the Retroid Pocket Classic but fits other screen
shapes too: one column on near-square screens, and a grid with a side panel
on 4:3 and 16:9 landscape. It uses a true-black AMOLED theme and works with
the built-in buttons as well as touch.

## What it shows

| Tab | Contents |
| --- | --- |
| **Playing** | Your 50 most recently played games, with progress bars. Press **Y** (or tap ✕) to remove a game from this list, with Undo; bring them back in Settings. It comes back if you play it again. This only hides it in Wasabi: RetroAchievements can't remove a recently played game without resetting its progress |
| **For You** | The Achievement of the Week, then Suggestions: games you're close to mastering, games people you follow are playing, picks from Want to Play |
| **Want to Play** | Your RetroAchievements Want to Play list, with achievement counts and points. Add or remove a game with the **Want to Play** button on its screen |
| **Library** | Every game you've unlocked something in; sort by recent, progress or name |
| **Unlocks** | Your unlocks from the last 30 days |
| **Following** | Players you follow, or (Y) your followers; open one to see their recent games, unlocks and per-game progress |
| **Browse** | A search box for every game with achievements, across all systems (the game lists download on your first search and are cached for a week); below it **Most played games** (top 200 by players) and **Hacks** (ROM hacks, most played first), both searchable and sortable (**Y**: most players, A–Z, most achievements, most points, system), from the website's game list; then every system: pick one to search its games and sort them (**Y**: A–Z, most players, most achievements, most points, recently updated; player counts come from the website's per-system list, top 500). Press **Y** (or tap the star) to favorite a system; favorites stay at the top |
| **Profile** | Site rank, points, RetroPoints, member since, last seen; **Awards** (your badge wall: mastered, completed, beaten, events) and **Stats** (points per week, streaks and an unlock calendar for the last 90 days) |

Open any game for its title, Want to Play button and your progress, plus
five tabs (switch with **LB/RB**):

| Tab | Contents |
| --- | --- |
| **Achievements** | Badges (grayed out while locked), points and RetroPoints, an unlock-rate bar with the hardcore share, author, missable/progression tags. **Y** shows all, locked, unlocked or only missable |
| **Info** | Title screen, in-game shot and box art (A opens them full screen); totals; developer, publisher, genre, release date, players, your playtime, median time to beat and the achievement authors. Every row is selectable, so the D-pad scrolls through it all |
| **Series** | The game's series, oldest first (API v2 hub games), and the other hubs it belongs to |
| **Similar** | Games RetroAchievements lists as similar |
| **Compare** | Your progress next to everyone you follow who has played it |
| **Leaderboards** | Each leaderboard's leader and, where you have an entry, your rank and score |

Press **A** on an achievement to see who unlocked it recently and its comments.

Series, Similar and time to beat come from the game's public page on retroachievements.org
(the Web API doesn't link games to hubs or similar games), read in a hidden
browser, so they need no sign-in.

Wasabi green means hardcore and silver means softcore.

## Controls

| Button | Action |
| --- | --- |
| D-pad | Move between items |
| A | Open, select or change |
| B | Go back. With the on-screen keyboard open, B just closes it |
| X | Add the game to Want to Play, or remove it (on a game, or the selected game in any list; game rows also have a +/✓ button) |
| Y | Sort the library or a system's games, filter achievements, favorite a system (in Browse), or remove a game from Playing |
| L1 / R1 | Previous / next tab (on a game: its tabs) |
| Swipe left / right | Next / previous tab, on the touchscreen |
| R2 | Refresh. Pick L2 or Select instead in **Settings → Refresh button** |
| Start | Settings |

The same list is in **Settings → Controls**. X, Y, Start, L1/R1 and the
refresh button are handled before anything on screen, and only the first
press counts (held buttons don't repeat).

A and B work through Android's standard gamepad mapping. If they feel
swapped, change the A/B layout in the Retroid settings.

## Signing in

You need your username and your **Web API key**, not your password. You can
find the key at retroachievements.org → Settings → Authentication → Web API Key.
The app stores it privately on the device, leaves it out of backups, and
deletes it when you sign out.

## Build and install

You'll need Android Studio, or JDK 17+ and the Android SDK (`local.properties`
already points to `~/Android/Sdk`).

1. Turn on USB debugging on the Retroid: Settings → About → tap *Build number*
   seven times, then Settings → System → Developer options → USB debugging.
2. Connect it over USB and run:

```bash
./gradlew installDebug
```

Or build an APK you can copy over and sideload:

```bash
./gradlew assembleRelease
```

The APK ends up in `app/build/outputs/wasabi/`, named with its version (for example `wasabi-1.3.apk`). It's signed with the
debug key, so it installs without setting up a keystore.

## Notifications, widget and offline

- **Notifications** (Settings → Notifications): your new achievements, and
  games mastered or beaten in hardcore by people you follow. Wasabi checks
  about every 15 minutes while you're online (Android's minimum).
- **Home screen widget**: your name, points and what you're playing. It
  updates when Wasabi loads your profile and on each background check.
- **Offline**: every answer from RetroAchievements is saved on the device.
  Without a connection Wasabi shows the saved data, with an "Offline" bar.

Browse also lists **Coming soon** (new sets being made right now) and
**Recent masteries** across RetroAchievements.

## Adding to Want to Play

The Web API (v1 and v2) is read-only, so Wasabi changes the list the same way
the website does, with `POST`/`DELETE /internal-api/user-game-list/{gameId}`.
The first time, Wasabi shows retroachievements.org's own login page
("Remember me" is ticked for you). Your password goes to the website, never
to Wasabi. After that, every add or remove runs in a hidden browser in the
background. If the website session ends, Wasabi asks you to sign in again.
These are internal website requests, so a site update could break them.
Sign out under **Settings → RetroAchievements website**.

## Updates

Wasabi checks GitHub for a newer release when it starts and shows a banner
when one is out. You can also check from **Settings → Updates**. Choosing
**Update** downloads the APK and opens Android's installer. The first time,
Android asks you to let Wasabi install apps, and it confirms every install.

Updates only install over an APK signed with the same key. Release builds
are signed with this machine's debug key, so build releases on the same
machine, or set up a release keystore and reinstall once.

## Project layout

```
app/src/main/java/app/wasabi/
  MainActivity.kt        entry point, shoulder/face button handling
  data/RaApi.kt          RetroAchievements Web API client, saved login
  data/Models.kt         data classes
  ui/AppViewModel.kt     screen stack, loading, focus restore
  ui/Components.kt       theme, focusable rows, pills, formatting
  ui/LoginScreen.kt
  ui/HomeScreen.kt       tab strip, Playing, Want to Play, Library, Unlocks, Profile
  ui/ExploreScreens.kt   For You, Following + user page, Browse (search, lists, systems) + console screen
  data/Suggest.kt        how suggestions are picked
  data/Catalog.kt        all-games list for Browse's search box, cached on the device
  ui/SettingsScreen.kt   updates, accent color and tab choices
  data/Updater.kt        in-app updates from GitHub releases
  ui/UpdateUi.kt         update banner and Settings row
  data/RaWeb.kt          website requests for Want to Play
  ui/WebScreens.kt       website sign-in and the hidden browser
  ui/GameScreen.kt       achievement list
```

Website: https://wasabi.gaalpos.dev (GitHub Pages, served from `docs/`).

It uses Kotlin and Jetpack Compose. JSON is parsed with the built-in
`org.json` and images load with Coil. API reference:
https://api-docs.retroachievements.org
