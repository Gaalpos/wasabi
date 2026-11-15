package app.wasabi

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.wasabi.data.Background
import app.wasabi.data.Prefs
import java.text.NumberFormat

/**
 * Home screen widget: your name, hardcore points and what you're playing.
 * Wasabi updates it when your profile loads and on each background check.
 */
class WasabiWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    override fun onEnabled(context: Context) {
        Prefs(context).widgetPlaced = true
        Background.schedule(context)
    }

    override fun onDisabled(context: Context) {
        Prefs(context).widgetPlaced = false
        Background.schedule(context)
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WasabiWidget::class.java))
            ids.forEach { manager.updateAppWidget(it, views(context)) }
        }

        private fun views(context: Context): RemoteViews {
            val p = Prefs(context)
            val open = PendingIntent.getActivity(
                context, 1, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return RemoteViews(context.packageName, R.layout.widget_wasabi).apply {
                setTextViewText(R.id.widget_user, p.widgetUser ?: "Wasabi")
                setTextViewText(
                    R.id.widget_points,
                    if (p.widgetPoints >= 0) NumberFormat.getIntegerInstance().format(p.widgetPoints) + " pts" else "",
                )
                setTextViewText(
                    R.id.widget_line,
                    when {
                        p.key == null -> "Open Wasabi to sign in"
                        p.widgetLine.isNullOrBlank() -> "Open Wasabi to update"
                        else -> p.widgetLine
                    },
                )
                setOnClickPendingIntent(R.id.widget_root, open)
            }
        }
    }
}
