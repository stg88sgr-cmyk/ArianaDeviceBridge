package de.snowworks.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import de.snowworks.app.R
import de.snowworks.app.ui.X88HomeActivity

class ArianaPresenceWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { render(context, manager, it) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, ArianaPresenceWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { render(context, manager, it) }
        }

        private fun render(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val state = PresenceWidgetStateStore.read(context)
            val views = RemoteViews(context.packageName, R.layout.ariana_presence_widget)
            views.setTextViewText(R.id.widget_core_status, state.core)
            views.setTextViewText(R.id.widget_dialog_status, state.dialog)
            views.setTextViewText(R.id.widget_wakeword_status, state.wakeword)
            val launchIntent = Intent(context, X88HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val openPendingIntent = PendingIntent.getActivity(
                context,
                8800 + appWidgetId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val speakIntent = Intent(context, X88HomeActivity::class.java).apply {
                action = "de.snowworks.app.widget.START_CONVERSATION"
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(X88HomeActivity.EXTRA_START_CONVERSATION, true)
            }
            val speakPendingIntent = PendingIntent.getActivity(
                context,
                9800 + appWidgetId,
                speakIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, openPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_speak, speakPendingIntent)
            manager.updateAppWidget(appWidgetId, views)
        }
    }
}
