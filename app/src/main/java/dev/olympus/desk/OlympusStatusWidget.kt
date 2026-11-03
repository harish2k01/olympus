package dev.olympus.desk

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import dev.olympus.desk.data.LiveRepository
import dev.olympus.desk.data.incidentTitle
import kotlin.math.roundToInt

open class OlympusStatusWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (LiveRepository.time.isNotBlank()) publish(context)
        ids.forEach { render(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        render(context, manager, id)
    }

    companion object {
        private fun data(context: Context) = context.getSharedPreferences("status-widget", Context.MODE_PRIVATE)

        internal fun publish(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, OlympusStatusWidget::class.java)) +
                manager.getAppWidgetIds(ComponentName(context, OlympusExpandedStatusWidget::class.java)) +
                manager.getAppWidgetIds(ComponentName(context, OlympusLockScreenWidget::class.java))
            if (ids.isEmpty()) return
            val state = currentVerdict()
            val prefs = data(context)
            val label = state.verdict.label
            val cause = state.incidents.firstOrNull()?.let(::incidentTitle).orEmpty()
            val cpu = LiveRepository.cpuPercent?.roundToInt()?.let { "CPU $it%" }.orEmpty()
            val memory = LiveRepository.memoryPercent?.roundToInt()?.let { "Memory $it%" }.orEmpty()
            val time = localTime(LiveRepository.time).substringBeforeLast(':')
            if (prefs.getString("label", null) == label && prefs.getString("cause", null) == cause &&
                prefs.getString("cpu", null) == cpu && prefs.getString("memory", null) == memory &&
                prefs.getString("time", null) == time) return
            prefs.edit().putString("label", label).putString("cause", cause).putString("cpu", cpu)
                .putString("memory", memory).putString("time", time).apply()
            if (LiveRepository.time.isNotBlank()) publish(context)
        ids.forEach { render(context, manager, it) }
        }

        private fun render(context: Context, manager: AppWidgetManager, id: Int) {
            val prefs = data(context)
            val options = manager.getAppWidgetOptions(id)
            val lockScreen = manager.getAppWidgetInfo(id)?.provider?.className == OlympusLockScreenWidget::class.java.name
            val expanded = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) >= 100
            val cpu = prefs.getString("cpu", "").orEmpty()
            val details = if (expanded) {
                val cause = prefs.getString("cause", "").orEmpty()
                val metrics = listOf(cpu, prefs.getString("memory", "").orEmpty())
                    .filter(String::isNotBlank).joinToString(" · ")
                listOf(cause, metrics).filter(String::isNotBlank).joinToString("\n")
            } else cpu
            val views = RemoteViews(context.packageName, R.layout.olympus_widget)
            views.setTextViewText(R.id.widget_verdict, prefs.getString("label", "Open Olympus"))
            views.setTextViewText(R.id.widget_details, if (lockScreen) "" else details)
            if (lockScreen) {
                views.setViewVisibility(R.id.widget_details, android.view.View.GONE)
            }
            val stamp = prefs.getString("time", "").orEmpty()
            views.setTextViewText(R.id.widget_time, if (stamp.isBlank()) "Awaiting snapshot" else "Snapshot · $stamp")
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.widget_body, open)
            manager.updateAppWidget(id, views)
        }
    }
}

class OlympusExpandedStatusWidget : OlympusStatusWidget()

class OlympusLockScreenWidget : OlympusStatusWidget()
