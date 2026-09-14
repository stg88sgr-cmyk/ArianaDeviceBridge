package de.snowworks.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import android.widget.Toast
import de.snowworks.app.R
import de.snowworks.app.ui.X88EventJournal
import de.snowworks.app.ui.X88HomeActivity
import de.snowworks.ariana.ArianaDeviceApi
import de.snowworks.ariana.Feature

class X88WidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { appWidgetId ->
            render(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_MASTER_OFF -> {
                ArianaDeviceApi(context.applicationContext).setMasterEnabled(false)
                X88EventJournal.add("master_off", "widget")
                Toast.makeText(context, "X-88 Gerätezugriff ausgeschaltet.", Toast.LENGTH_SHORT).show()
                updateAll(context)
                return
            }

            ACTION_STOP_ALL -> {
                ArianaDeviceApi(context.applicationContext).stopAll()
                X88EventJournal.add("stop_all", "widget")
                Toast.makeText(context, "STOP ALL ist aktiv.", Toast.LENGTH_SHORT).show()
                updateAll(context)
                return
            }
        }
        super.onReceive(context, intent)
    }

    companion object {
        private const val ACTION_MASTER_OFF = "de.snowworks.app.widget.MASTER_OFF"
        private const val ACTION_STOP_ALL = "de.snowworks.app.widget.STOP_ALL"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, X88WidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { appWidgetId ->
                render(context, manager, appWidgetId)
            }
        }

        private fun render(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int,
        ) {
            val api = ArianaDeviceApi(context.applicationContext)
            val state = X88WidgetState(
                masterEnabled = api.isMasterEnabled(),
                blocked = api.isBlocked(),
                cameraActive = api.getStatus(Feature.CAMERA).sessionActive,
                microphoneActive = api.getStatus(Feature.MICROPHONE).sessionActive,
                screenActive = api.getStatus(Feature.SCREEN).sessionActive,
            )
            val views = RemoteViews(context.packageName, R.layout.widget_x88)
            val openApp = openAppIntent(context, appWidgetId)

            views.setTextViewText(R.id.widget_status, state.headline)
            views.setTextViewText(R.id.widget_detail, state.detail)
            views.setTextColor(
                R.id.widget_status,
                when (state.mode) {
                    X88WidgetState.Mode.ACTIVE -> Color.parseColor("#25D9FF")
                    X88WidgetState.Mode.OFF -> Color.parseColor("#93A5B5")
                    X88WidgetState.Mode.BLOCKED -> Color.parseColor("#FF5B87")
                },
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)
            views.setOnClickPendingIntent(R.id.widget_open, openApp)
            views.setOnClickPendingIntent(
                R.id.widget_master_action,
                if (state.mode == X88WidgetState.Mode.ACTIVE) {
                    views.setTextViewText(R.id.widget_master_action, "MASTER AUS")
                    broadcastIntent(context, appWidgetId + 20_000, ACTION_MASTER_OFF)
                } else {
                    views.setTextViewText(R.id.widget_master_action, "IN APP AKTIVIEREN")
                    openApp
                },
            )
            views.setOnClickPendingIntent(
                R.id.widget_stop_all,
                broadcastIntent(context, appWidgetId + 30_000, ACTION_STOP_ALL),
            )
            manager.updateAppWidget(appWidgetId, views)
        }

        private fun openAppIntent(context: Context, requestCode: Int): PendingIntent {
            val intent = Intent(context, X88HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun broadcastIntent(
            context: Context,
            requestCode: Int,
            action: String,
        ): PendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, X88WidgetProvider::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
