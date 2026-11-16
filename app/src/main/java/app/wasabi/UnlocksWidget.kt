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
import app.wasabi.data.Prefs
import app.wasabi.data.Unlock
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
 * Home screen widget: your recent achievements in a scrolling list, with
 * badge, game, points and a HARDCORE tag. Wasabi updates it when the Unlocks
 * tab loads and on each background check (about every 15 minutes).
 */
class UnlocksWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context, it)) }
    }

    override fun onEnabled(context: Context) {
        Prefs(context).widgetPlaced = true
        Background.schedule(context)
        Background.runNow(context)
    }

    override fun onDisabled(context: Context) {
        Prefs(context).widgetPlaced = ids(context, WasabiWidget::class.java).isNotEmpty()
        Background.schedule(context)
    }

    companion object {
        /** How many unlocks the list keeps (it scrolls). */
        private const val MAX = 30
        private const val BADGE_PX = 64

        private fun ids(context: Context, cls: Class<*>) =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, cls))

        fun placed(context: Context) = ids(context, UnlocksWidget::class.java).isNotEmpty()

        private fun badgeDir(context: Context) = File(context.filesDir, "widget")

        /** Badge files are named after their URL, so unchanged badges aren't downloaded again. */
        fun badgeFile(context: Context, url: String): File {
            val hash = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
            return File(badgeDir(context), "$hash.png")
        }

        /** Saves the newest [unlocks] and their badges, then redraws the widget. */
        suspend fun update(context: Context, unlocks: List<Unlock>) {
            if (!placed(context)) return
            val top = unlocks.sortedByDescending { it.date.orEmpty() }.take(MAX)
            withContext(Dispatchers.IO) {
                val keep = top.mapNotNull { u -> u.badgeUrl?.let { badgeFile(context, it) } }.toSet()
                badgeDir(context).listFiles()?.filter { it !in keep }?.forEach { it.delete() }
                top.forEach { u -> u.badgeUrl?.let { saveBadge(context, it) } }
                val arr = JSONArray()
                top.forEach { u ->
                    arr.put(
                        JSONObject()
                            .put("title", u.title).put("game", u.gameTitle).put("points", u.points)
                            .put("date", u.date).put("hardcore", u.hardcore).put("badge", u.badgeUrl),
                    )
                }
                Prefs(context).widgetUnlocks = arr.toString()
            }
            refresh(context)
        }

        /** Forgets the saved unlocks (on sign-out). */
        fun clear(context: Context) {
            Prefs(context).widgetUnlocks = null
            badgeDir(context).listFiles()?.forEach { it.delete() }
            refresh(context)
        }

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val widgetIds = ids(context, UnlocksWidget::class.java)
            widgetIds.forEach { manager.updateAppWidget(it, views(context, it)) }
            @Suppress("DEPRECATION")
            manager.notifyAppWidgetViewDataChanged(widgetIds, R.id.u_list)
        }

        private fun saveBadge(context: Context, url: String) {
            val file = badgeFile(context, url)
            if (file.exists()) return
            runCatching {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                val raw = conn.inputStream.use { BitmapFactory.decodeStream(it) } ?: return
                val small = Bitmap.createScaledBitmap(raw, BADGE_PX, BADGE_PX, true)
                file.parentFile?.mkdirs()
                file.outputStream().use { small.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }.onFailure { file.delete() }
        }

        /** The saved unlocks, newest first. */
        fun items(context: Context): List<JSONObject> = runCatching {
            val arr = JSONArray(Prefs(context).widgetUnlocks ?: "[]")
            (0 until arr.length()).map { arr.getJSONObject(it) }
        }.getOrDefault(emptyList())

        private fun views(context: Context, widgetId: Int): RemoteViews {
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
            val adapter = Intent(context, UnlocksWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            return RemoteViews(context.packageName, R.layout.widget_unlocks).apply {
                setOnClickPendingIntent(R.id.u_root, open)
                setTextViewText(
                    R.id.u_total,
                    if (p.key != null && p.widgetPoints >= 0) NumberFormat.getIntegerInstance().format(p.widgetPoints) + " pts" else "",
                )
                setTextViewText(
                    R.id.u_empty,
                    when {
                        p.key == null -> "Open Wasabi to sign in"
                        p.widgetUnlocks == null -> "Open Wasabi to load your achievements"
                        else -> "Nothing unlocked in the last 30 days"
                    },
                )
                @Suppress("DEPRECATION")
                setRemoteAdapter(R.id.u_list, adapter)
                setEmptyView(R.id.u_list, R.id.u_empty)
                setPendingIntentTemplate(R.id.u_list, rowTap)
            }
        }
    }
}

/** Feeds the widget's list from the unlocks Wasabi saved. */
class UnlocksWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    private class Factory(private val context: Context) : RemoteViewsFactory {
        private var items: List<JSONObject> = emptyList()

        override fun onCreate() = onDataSetChanged()
        override fun onDataSetChanged() {
            items = if (Prefs(context).key == null) emptyList() else UnlocksWidget.items(context)
        }
        override fun onDestroy() {}
        override fun getCount() = items.size
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = false
        override fun getLoadingView(): RemoteViews? = null

        override fun getViewAt(position: Int): RemoteViews {
            val j = items.getOrNull(position)
            return RemoteViews(context.packageName, R.layout.widget_unlock_item).apply {
                if (j == null) return@apply
                val hardcore = j.optBoolean("hardcore")
                setTextViewText(R.id.u_title, j.optString("title"))
                val date = if (j.isNull("date")) null else j.optString("date")
                setTextViewText(R.id.u_sub, listOf(j.optString("game"), ago(date)).filter { it.isNotBlank() }.joinToString(" · "))
                setTextViewText(R.id.u_points, j.optInt("points").toString())
                setTextColor(R.id.u_points, if (hardcore) 0xFF9DC93B.toInt() else 0xFFAFC0D8.toInt())
                setViewVisibility(R.id.u_hc, if (hardcore) View.VISIBLE else View.GONE)
                val badge = if (j.isNull("badge")) null
                    else UnlocksWidget.badgeFile(context, j.optString("badge")).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
                if (badge != null) setImageViewBitmap(R.id.u_badge, badge)
                else setImageViewResource(R.id.u_badge, R.mipmap.ic_launcher)
                setOnClickFillInIntent(R.id.u_row, Intent())
            }
        }
    }
}
