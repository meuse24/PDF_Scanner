package info.meuse24.pdf_scanner.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.ui.entry.AppEntryActionCodec

class ScanWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(appWidgetId, createRemoteViews(context))
        }
    }

    private fun createRemoteViews(context: Context): RemoteViews {
        val launchIntent = AppEntryActionCodec.buildLaunchIntent(
            context = context,
            actionValue = AppEntryActionCodec.VALUE_SCAN_NEW
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            2001,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return RemoteViews(context.packageName, R.layout.widget_scan).apply {
            setOnClickPendingIntent(R.id.widget_scan_root, pendingIntent)
        }
    }
}
