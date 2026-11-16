package app.wasabi

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import app.wasabi.data.Background
import app.wasabi.data.FollowedUser
import app.wasabi.data.Prefs
import app.wasabi.data.RaApi
import app.wasabi.data.RecentGame
import app.wasabi.data.Unlock
import app.wasabi.data.WantGame
import app.wasabi.data.WeeklyAchievement
import app.wasabi.ui.ago
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.NumberFormat

/**
 * One row of a list widget. [date], if set, is shown as "2h ago" after
 * [sub] when the widget draws, so it stays current between refreshes.
 */
data class WidgetRow(
    val title: String,
    val sub: String,
    val right: String = "",
    val rightAccent: Boolean = false,
    val tag: String? = null,
    val image: String? = null,
    val date: String? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("title", title).put("sub", sub).put("right", right).put("accent", rightAccent)
        .put("tag", tag).put("image", image).put("date", date)

    companion object {
        fun from(j: JSONObject) = WidgetRow(
            title = j.optString("title"),
            sub = j.optString("sub"),
            right = j.optString("right"),
            rightAccent = j.optBoolean("accent"),
            tag = j.optString("tag").takeIf { !j.isNull("tag") && it.isNotEmpty() },
            image = j.optString("image").takeIf { !j.isNull("image") && it.isNotEmpty() },
            date = j.optString("date").takeIf { !j.isNull("date") && it.isNotEmpty() },
        )
    }
}

/** The list widgets: each shows one part of the app on the home screen. */
enum class WidgetKind(
    val provider: Class<out AppWidgetProvider>,
    val titleRes: Int,
    val empty: String,
    /** Round images (avatars) instead of square badges. */
    val round: Boolean = false,
) {
    Unlocks(UnlocksWidget::class.java, R.string.widget_unlocks_title, "Nothing unlocked in the last 30 days"),
    Playing(PlayingWidget::class.java, R.string.widget_playing_title, "No games played yet"),
    WantToPlay(WantWidget::class.java, R.string.widget_want_title, "Your Want to Play list is empty"),
    Friends(FriendsWidget::class.java, R.string.widget_friends_title, "You don't follow anyone yet", round = true),
    Weekly(WeeklyWidget::class.java, R.string.widget_weekly_title, "No Achievement of the Week right now"),
}

/** Turns app data into widget rows (shared by the app and the background check). */
object WidgetRows {
    private const val MAX = 30
    private val accentTag = "HARDCORE"
    private fun n(v: Int) = NumberFormat.getIntegerInstance().format(v)

    fun unlocks(list: List<Unlock>) = list.sortedByDescending { it.date.orEmpty() }.take(MAX).map { u ->
        WidgetRow(
            title = u.title, sub = u.gameTitle, right = "${u.points}", rightAccent = u.hardcore,
            tag = if (u.hardcore) accentTag else null, image = u.badgeUrl, date = u.date,
        )
    }

    /** Recently played games, minus the ones hidden from Playing (unless played since). */
    fun playing(list: List<RecentGame>, hidden: Map<Int, String>) = list
        .filter { g -> hidden[g.id]?.let { at -> at.isNotEmpty() && g.lastPlayed.orEmpty() > at } ?: true }
        .take(MAX).map { g ->
            val pct = if (g.total > 0) "${(100 * g.shown / g.total)}%" else ""
            WidgetRow(
                title = g.title,
                sub = listOf(g.console, if (g.total > 0) "${g.shown}/${g.total}" else "").filter { it.isNotEmpty() }.joinToString(" · "),
                right = pct, rightAccent = g.isHardcore,
                tag = if (g.isHardcore && g.shown > 0) accentTag else null,
                image = g.iconUrl, date = g.lastPlayed,
            )
        }

    fun want(list: List<WantGame>) = list.take(MAX * 2).map { g ->
        WidgetRow(
            title = g.title,
            sub = listOf(g.console, if (g.achievements > 0) "${g.achievements} achievements" else "").filter { it.isNotEmpty() }.joinToString(" · "),
            right = if (g.points > 0) "${n(g.points)} pts" else "",
            image = g.iconUrl,
        )
    }

    /** You and the people you follow, ranked by hardcore points. */
    fun friends(list: List<FollowedUser>, me: String?, myPoints: Int): List<WidgetRow> {
        data class P(val name: String, val avatar: String?, val points: Int, val isMe: Boolean, val back: Boolean)
        val people = list.map { P(it.user, it.avatarUrl, it.points, false, it.followsBack) } +
            listOfNotNull(me?.takeIf { myPoints >= 0 }?.let { P(it, "${app.wasabi.data.MEDIA}/UserPic/$it.png", myPoints, true, false) })
        return people.distinctBy { it.name.lowercase() }.sortedByDescending { it.points }.take(MAX * 2)
            .mapIndexed { i, p ->
                WidgetRow(
                    title = "${i + 1}. ${p.name}",
                    sub = when { p.isMe -> "You"; p.back -> "Follows you"; else -> "" },
                    right = "${n(p.points)} pts", rightAccent = p.isMe,
                    image = p.avatar,
                )
            }
    }

    fun weekly(w: WeeklyAchievement) = listOf(
        WidgetRow(
            title = w.title,
            sub = "${w.gameTitle} · ${w.console}",
            right = "${w.points}", rightAccent = true,
            image = w.badgeUrl,
        ),
    )
}

object Widgets {
    private const val IMAGE_PX = 64

    private fun ids(context: Context, cls: Class<*>) =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, cls))

    fun placed(context: Context, kind: WidgetKind) = ids(context, kind.provider).isNotEmpty()

    /** Any Wasabi widget on the home screen (keeps the background check running). */
    fun anyPlaced(context: Context) =
        placed(context, WasabiWidget::class.java) || WidgetKind.entries.any { placed(context, it) }

    private fun placed(context: Context, cls: Class<*>) = ids(context, cls).isNotEmpty()

    private fun prefKey(kind: WidgetKind) = "widget_${kind.name}"
    private fun imageDir(context: Context) = File(context.filesDir, "widget")

    /** Images are named after their URL, so unchanged ones aren't downloaded again. */
    fun imageFile(context: Context, url: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(imageDir(context), "$hash.png")
    }

    fun rows(context: Context, kind: WidgetKind): List<WidgetRow>? = runCatching {
        val raw = Prefs(context).widgetData(prefKey(kind)) ?: return null
        val arr = JSONArray(raw)
        (0 until arr.length()).map { WidgetRow.from(arr.getJSONObject(it)) }
    }.getOrNull()

    /** Saves [rows] for [kind] and their images, then redraws its widgets. */
    suspend fun save(context: Context, kind: WidgetKind, rows: List<WidgetRow>) {
        if (!placed(context, kind)) return
        withContext(Dispatchers.IO) {
            rows.mapNotNull { it.image }.distinct().forEach { saveImage(context, it) }
            Prefs(context).setWidgetData(prefKey(kind), JSONArray().apply { rows.forEach { put(it.toJson()) } }.toString())
            prune(context)
        }
        refresh(context, kind)
    }

    /** Fetches and saves the data for every placed list widget (background check). */
    suspend fun updateAll(context: Context, api: RaApi) {
        val prefs = Prefs(context)
        for (kind in WidgetKind.entries) {
            if (!placed(context, kind)) continue
            val rows = runCatching {
                when (kind) {
                    WidgetKind.Unlocks -> WidgetRows.unlocks(api.recentUnlocks())
                    WidgetKind.Playing -> WidgetRows.playing(api.recentGames(), prefs.hiddenPlaying)
                    WidgetKind.WantToPlay -> WidgetRows.want(api.wantToPlay())
                    WidgetKind.Friends -> WidgetRows.friends(api.following(), prefs.widgetUser, prefs.widgetPoints)
                    WidgetKind.Weekly -> WidgetRows.weekly(api.achievementOfTheWeek())
                }
            }.getOrNull() ?: continue
            save(context, kind, rows)
        }
    }

    /** Forgets every widget's data (on sign-out). */
    fun clear(context: Context) {
        val prefs = Prefs(context)
        WidgetKind.entries.forEach { prefs.setWidgetData(prefKey(it), null) }
        imageDir(context).listFiles()?.forEach { it.delete() }
        WidgetKind.entries.forEach { refresh(context, it) }
    }

    fun refresh(context: Context, kind: WidgetKind) {
        val manager = AppWidgetManager.getInstance(context)
        val widgetIds = ids(context, kind.provider)
        if (widgetIds.isEmpty()) return
        widgetIds.forEach { manager.updateAppWidget(it, views(context, kind, it)) }
        @Suppress("DEPRECATION")
        manager.notifyAppWidgetViewDataChanged(widgetIds, R.id.u_list)
    }

    /** Deletes images no widget uses any more. */
    private fun prune(context: Context) {
        val keep = WidgetKind.entries.flatMap { rows(context, it).orEmpty() }
            .mapNotNull { it.image?.let { url -> imageFile(context, url) } }.toSet()
        imageDir(context).listFiles()?.filter { it !in keep }?.forEach { it.delete() }
    }

    private fun saveImage(context: Context, url: String) {
        val file = imageFile(context, url)
        if (file.exists()) return
        runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            val raw = conn.inputStream.use { BitmapFactory.decodeStream(it) } ?: return
            val small = Bitmap.createScaledBitmap(raw, IMAGE_PX, IMAGE_PX, true)
            file.parentFile?.mkdirs()
            file.outputStream().use { small.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }.onFailure { file.delete() }
    }

    fun views(context: Context, kind: WidgetKind, widgetId: Int): RemoteViews {
        val p = Prefs(context)
        val open = PendingIntent.getActivity(
            context, 2, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Tapping a row opens Wasabi too (rows fill in this template).
        val rowTap = PendingIntent.getActivity(
            context, 3, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val adapter = Intent(context, WidgetListService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            putExtra(WidgetListService.KIND, kind.name)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        return RemoteViews(context.packageName, R.layout.widget_list).apply {
            setOnClickPendingIntent(R.id.u_root, open)
            setTextViewText(R.id.u_head, context.getString(kind.titleRes))
            setTextViewText(
                R.id.u_total,
                if (p.key != null && p.widgetPoints >= 0 && kind != WidgetKind.Friends)
                    NumberFormat.getIntegerInstance().format(p.widgetPoints) + " pts" else "",
            )
            setTextViewText(
                R.id.u_empty,
                when {
                    p.key == null -> "Open Wasabi to sign in"
                    rows(context, kind) == null -> "Loading… (or open Wasabi)"
                    else -> kind.empty
                },
            )
            @Suppress("DEPRECATION")
            setRemoteAdapter(R.id.u_list, adapter)
            setEmptyView(R.id.u_list, R.id.u_empty)
            setPendingIntentTemplate(R.id.u_list, rowTap)
        }
    }
}

/** Shared behavior of the list widgets; each subclass is one [WidgetKind]. */
abstract class ListWidget(private val kind: WidgetKind) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, Widgets.views(context, kind, it)) }
    }

    override fun onEnabled(context: Context) {
        Prefs(context).widgetPlaced = true
        Background.schedule(context)
        Background.runNow(context)   // fill it in right away
    }

    override fun onDisabled(context: Context) {
        Prefs(context).widgetPlaced = Widgets.anyPlaced(context)
        Background.schedule(context)
    }
}

class UnlocksWidget : ListWidget(WidgetKind.Unlocks)
class PlayingWidget : ListWidget(WidgetKind.Playing)
class WantWidget : ListWidget(WidgetKind.WantToPlay)
class FriendsWidget : ListWidget(WidgetKind.Friends)
class WeeklyWidget : ListWidget(WidgetKind.Weekly)

/** Feeds each list widget's rows from what Wasabi saved. */
class WidgetListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val kind = WidgetKind.entries.find { it.name == intent.getStringExtra(KIND) } ?: WidgetKind.Unlocks
        return Factory(applicationContext, kind)
    }

    private class Factory(private val context: Context, private val kind: WidgetKind) : RemoteViewsFactory {
        private var items: List<WidgetRow> = emptyList()

        override fun onCreate() = onDataSetChanged()
        override fun onDataSetChanged() {
            items = if (Prefs(context).key == null) emptyList() else Widgets.rows(context, kind).orEmpty()
        }
        override fun onDestroy() {}
        override fun getCount() = items.size
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = false
        override fun getLoadingView(): RemoteViews? = null

        override fun getViewAt(position: Int): RemoteViews {
            val r = items.getOrNull(position)
            return RemoteViews(context.packageName, R.layout.widget_list_item).apply {
                if (r == null) return@apply
                setTextViewText(R.id.u_title, r.title)
                setTextViewText(R.id.u_sub, listOf(r.sub, ago(r.date)).filter { it.isNotBlank() }.joinToString(" · "))
                setViewVisibility(R.id.u_sub, if (r.sub.isBlank() && r.date == null) View.GONE else View.VISIBLE)
                setTextViewText(R.id.u_points, r.right)
                setTextColor(R.id.u_points, if (r.rightAccent) 0xFF9DC93B.toInt() else 0xFFAFC0D8.toInt())
                setViewVisibility(R.id.u_points, if (r.right.isEmpty()) View.GONE else View.VISIBLE)
                setTextViewText(R.id.u_hc, r.tag.orEmpty())
                setViewVisibility(R.id.u_hc, if (r.tag != null) View.VISIBLE else View.GONE)
                val bmp = r.image?.let { Widgets.imageFile(context, it) }?.takeIf { it.exists() }
                    ?.let { BitmapFactory.decodeFile(it.path) }
                    ?.let { if (kind.round) circle(it) else it }
                if (bmp != null) setImageViewBitmap(R.id.u_badge, bmp)
                else setImageViewResource(R.id.u_badge, R.mipmap.ic_launcher)
                setOnClickFillInIntent(R.id.u_row, Intent())
            }
        }

        private fun circle(src: Bitmap): Bitmap {
            val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(out)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            c.drawCircle(src.width / 2f, src.height / 2f, src.width / 2f, paint)
            paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
            c.drawBitmap(src, 0f, 0f, paint)
            return out
        }
    }

    companion object {
        const val KIND = "kind"
    }
}
