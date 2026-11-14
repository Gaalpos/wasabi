# Cheevos

A small Android app for checking your RetroAchievements progress on a
handheld. It's built for the Retroid Pocket Classic: a dark AMOLED theme,
a compact layout for the small, nearly square screen, and controls that work
with the built-in buttons as well as touch.

## What it shows

| Tab | Contents |
| --- | --- |
| **Playing** | Your 50 most recently played games, with progress bars |
| **Library** | Every game you've unlocked something in; sort by recent, progress or name |
| **Unlocks** | Your unlocks from the last 30 days |
| **Profile** | Points, RetroPoints, member since, last seen (rich presence), sign out |

Open any game to see its full achievement list: unlocked badges in color,
locked ones grayed out, unlock dates, how many players have each one, and
missable/progression tags. You can filter the list to show all, locked or
unlocked achievements.

Gold means hardcore and silver means softcore.

## Controls

| Button | Action |
| --- | --- |
| D-pad | Move |
| A | Open |
| B | Back |
| L1 / R1 | Previous / next tab |
| Y | Sort the library, or filter a game's achievements |
| X | Refresh |

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

The APK ends up in `app/build/outputs/apk/release/`. It's signed with the
debug key, so it installs without setting up a keystore.

## Project layout

```
app/src/main/java/app/cheevos/
  MainActivity.kt        entry point, shoulder/face button handling
  data/RaApi.kt          RetroAchievements Web API client, saved login
  data/Models.kt         data classes
  ui/AppViewModel.kt     screen stack, loading, focus restore
  ui/Components.kt       theme, focusable rows, pills, formatting
  ui/LoginScreen.kt
  ui/HomeScreen.kt       the four tabs
  ui/GameScreen.kt       achievement list
```

It uses Kotlin and Jetpack Compose. JSON is parsed with the built-in
`org.json` and images load with Coil. API reference:
https://api-docs.retroachievements.org
