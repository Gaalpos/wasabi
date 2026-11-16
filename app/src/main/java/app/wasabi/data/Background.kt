package app.wasabi.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.wasabi.MainActivity
import app.wasabi.R
import app.wasabi.UnlocksWidget
import app.wasabi.WasabiWidget
import java.util.concurrent.TimeUnit

/**
 * The background check: every 15 minutes (Android's minimum) while online,
 * it refreshes the home screen widget and, if enabled, notifies you of new
 * unlocks and of masteries by people you follow. It only runs when
 * notifications are on or a widget is placed.
 */
object Background {
    private const val WORK = "wasabi-check"
    const val CHANNEL = "unlocks"

    fun schedule(context: Context) {
        val prefs = Prefs(context)
        val wanted = prefs.key != null && (prefs.notifications || prefs.widgetPlaced)
        val wm = WorkManager.getInstance(context)
        if (!wanted) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<CheckWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Runs one check now (e.g. right after a widget is placed, so it fills in). */
    fun runNow(context: Context) {
        if (Prefs(context).key == null) return
        val request = androidx.work.OneTimeWorkRequestBuilder<CheckWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("$WORK-now", androidx.work.ExistingWorkPolicy.REPLACE, request)
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Unlocks and masteries", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Your new achievements, and games mastered by people you follow"
            }
        )
    }

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    @android.annotation.SuppressLint("MissingPermission")   // checked in canNotify
    fun notify(context: Context, id: Int, title: String, text: String, lines: List<String> = emptyList()) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF9DC93B.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
        if (lines.isNotEmpty()) {
            b.setStyle(NotificationCompat.InboxStyle().also { s -> lines.take(6).forEach { s.addLine(it) } })
        }
        NotificationManagerCompat.from(context).notify(id, b.build())
    }
}

class CheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        val user = prefs.user ?: return Result.success()
        val key = prefs.key ?: return Result.success()
        val api = RaApi(user, key)

        // Widget: points and what you're playing.
        runCatching { api.profile() }.getOrNull()?.let { p ->
            prefs.saveWidget(p.user, p.points, p.richPresence)
            WasabiWidget.refresh(applicationContext)
        }
        // Recent achievements widget.
        if (UnlocksWidget.placed(applicationContext)) {
            runCatching { api.recentUnlocks() }.getOrNull()?.let { UnlocksWidget.update(applicationContext, it) }
        }
        if (!prefs.notifications) return Result.success()

        // Your new unlocks (last day). The first run only sets the starting point.
        runCatching { api.recentUnlocks(minutes = 60 * 24) }.getOrNull()?.let { unlocks ->
            val newest = unlocks.maxOfOrNull { it.date.orEmpty() }.orEmpty()
            val last = prefs.lastUnlockSeen
            if (last.isEmpty()) {
                prefs.lastUnlockSeen = newest.ifEmpty { "0" }
            } else {
                val fresh = unlocks.filter { it.date.orEmpty() > last }.sortedBy { it.date.orEmpty() }
                if (fresh.isNotEmpty()) {
                    prefs.lastUnlockSeen = newest
                    if (fresh.size == 1) {
                        val u = fresh[0]
                        Background.notify(applicationContext, 1, "Unlocked: ${u.title}", "${u.gameTitle} · ${u.points} pts")
                    } else {
                        Background.notify(
                            applicationContext, 1, "${fresh.size} new achievements",
                            fresh.joinToString(", ") { it.title },
                            fresh.map { "${it.title} · ${it.gameTitle}" },
                        )
                    }
                }
            }
        }

        // Masteries and hardcore beats by people you follow.
        val names = prefs.followingNames
        if (names.isNotEmpty()) {
            runCatching { api.recentGameAwards("mastered,beaten-hardcore", 100) }.getOrNull()?.let { awards ->
                val newest = awards.maxOfOrNull { it.date.orEmpty() }.orEmpty()
                val last = prefs.lastAwardSeen
                if (last.isEmpty()) {
                    prefs.lastAwardSeen = newest.ifEmpty { "0" }
                } else {
                    val fresh = awards.filter { it.date.orEmpty() > last && it.user?.lowercase() in names }
                    if (newest > last) prefs.lastAwardSeen = newest
                    fresh.take(3).forEachIndexed { i, a ->
                        Background.notify(
                            applicationContext, 100 + i,
                            "${a.user} ${if (a.detail == "Mastered") "mastered" else "beat"} ${a.title}",
                            a.console,
                        )
                    }
                }
            }
        }
        return Result.success()
    }
}
