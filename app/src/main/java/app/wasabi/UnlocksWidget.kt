package app.wasabi

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import android.widget.RemoteViews
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
import java.text.NumberFormat

/**
 * Home screen widget: your most recent achievements, with badge, game,
 * points and a HARDCORE tag. Wasabi updates it when the Unlocks tab loads
 * and on each background check (about every 15 minutes).
 */
class UnlocksWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    override fun onEnabled(context: Context) {
        Prefs(context).widgetPlaced = true
        Background.schedule(context)
        Background.runNow(context)
    }

    override fun onDisabled(context: Context) {
        Prefs(context).widgetPlaced = wasabiWidgetPlaced(context)
        Background.schedule(context)
    }

    companion object {
        private const val ROWS = 5
        private const val BADGE_PX = 64

        private val rowIds = intArrayOf(R.id.u_row0, R.id.u_row1, R.id.u_row2, R.id.u_row3, R.id.u_row4)
        private val badgeIds = intArrayOf(R.id.u_badge0, R.id.u_badge1, R.id.u_badge2, R.id.u_badge3, R.id.u_badge4)
        private val titleIds = intArrayOf(R.id.u_title0, R.id.u_title1, R.id.u_title2, R.id.u_title3, R.id.u_title4)
        private val subIds = intArrayOf(R.id.u_sub0, R.id.u_sub1, R.id.u_sub2, R.id.u_sub3, R.id.u_sub4)
        private val hcIds = intArrayOf(R.id.u_hc0, R.id.u_hc1, R.id.u_hc2, R.id.u_hc3, R.id.u_hc4)
        private val pointIds = intArrayOf(R.id.u_points0, R.id.u_points1, R.id.u_points2, R.id.u_points3, R.id.u_points4)

        private fun ids(context: Context, cls: Class<*>) =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, cls))

        fun placed(context: Context) = ids(context, UnlocksWidget::class.java).isNotEmpty()
        private fun wasabiWidgetPlaced(context: Context) = ids(context, WasabiWidget::class.java).isNotEmpty()

        private fun badgeFile(context: Context, i: Int) = File(context.filesDir, "widget/badge$i.png")

        /** Saves the newest [unlocks] and their badges, then redraws the widget. */
        suspend fun update(context: Context, unlocks: List<Unlock>) {
            if (!placed(context)) return
            val top = unlocks.sortedByDescending { it.date.orEmpty() }.take(ROWS)
            withContext(Dispatchers.IO) {
                top.forEachIndexed { i, u -> saveBadge(context, i, u.badgeUrl) }
                val arr = JSONArray()
                top.forEach { u ->
                    arr.put(
                        JSONObject()
                            .put("title", u.title).put("game", u.gameTitle).put("points", u.points)
                            .put("date", u.date).put("hardcore", u.hardcore),
                    )
                }
                Prefs(context).widgetUnlocks = arr.toString()
            }
            refresh(context)
        }

        /** Forgets the saved unlocks (on sign-out). */
        fun clear(context: Context) {
            Prefs(context).widgetUnlocks = null
            (0 until ROWS).forEach { badgeFile(context, it).delete() }
            refresh(context)
        }

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            ids(context, UnlocksWidget::class.java).forEach { manager.updateAppWidget(it, views(context)) }
        }

        private fun saveBadge(context: Context, i: Int, url: String?) {
            val file = badgeFile(context, i)
            if (url == null) { file.delete(); return }
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

        private fun views(context: Context): RemoteViews {
            val p = Prefs(context)
            val open = PendingIntent.getActivity(
                context, 2, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val items = runCatching {
                val arr = JSONArray(p.widgetUnlocks ?: "[]")
                (0 until arr.length()).map { arr.getJSONObject(it) }
            }.getOrDefault(emptyList())

            return RemoteViews(context.packageName, R.layout.widget_unlocks).apply {
                setOnClickPendingIntent(R.id.u_root, open)
                setTextViewText(
                    R.id.u_total,
                    if (p.key != null && p.widgetPoints >= 0) NumberFormat.getIntegerInstance().format(p.widgetPoints) + " pts" else "",
                )
                val empty = when {
                    p.key == null -> "Open Wasabi to sign in"
                    p.widgetUnlocks == null -> "Open Wasabi to load your achievements"
                    items.isEmpty() -> "Nothing unlocked in the last 30 days"
                    else -> null
                }
                setTextViewText(R.id.u_empty, empty.orEmpty())
                setViewVisibility(R.id.u_empty, if (empty != null) View.VISIBLE else View.GONE)

                for (i in 0 until ROWS) {
                    val j = items.getOrNull(i)
                    if (j == null || p.key == null) {
                        setViewVisibility(rowIds[i], View.GONE)
                        continue
                    }
                    val hardcore = j.optBoolean("hardcore")
                    setViewVisibility(rowIds[i], View.VISIBLE)
                    setTextViewText(titleIds[i], j.optString("title"))
                    val date = if (j.isNull("date")) null else j.optString("date")
                    setTextViewText(subIds[i], listOf(j.optString("game"), ago(date)).filter { it.isNotBlank() }.joinToString(" · "))
                    setTextViewText(pointIds[i], j.optInt("points").toString())
                    setTextColor(pointIds[i], if (hardcore) 0xFF9DC93B.toInt() else 0xFFAFC0D8.toInt())
                    setViewVisibility(hcIds[i], if (hardcore) View.VISIBLE else View.GONE)
                    val badge = badgeFile(context, i).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
                    if (badge != null) setImageViewBitmap(badgeIds[i], badge)
                    else setImageViewResource(badgeIds[i], R.mipmap.ic_launcher)
                }
            }
        }
    }
}
