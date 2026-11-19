package com.routinely.app

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

internal object ReminderScheduler {
    const val CHANNEL_ID = "routine_reminders"
    const val ACTION_FIRE = "com.routinely.app.ACTION_FIRE"
    const val ACTION_SNOOZE = "com.routinely.app.ACTION_SNOOZE"
    const val EXTRA_ID = "id"
    const val EXTRA_TITLE = "title"
    const val EXTRA_REPEAT = "repeat"

    private val TIME_REGEX = Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$")

    private fun alarmManager(context: Context) = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager(context).canScheduleExactAlarms()

    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun pending(context: Context, key: String, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(context, key.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun fireIntent(context: Context, id: String, title: String, repeat: Boolean): Intent =
        Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_REPEAT, repeat)

    private fun setAt(context: Context, triggerAtMillis: Long, pi: PendingIntent) {
        val am = alarmManager(context)
        try {
            if (canScheduleExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    private fun nextTriggerMillis(time: LocalTime): Long {
        val now = LocalDateTime.now()
        var next = LocalDateTime.of(now.toLocalDate(), time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /** Schedules the next daily reminder for a routine (replaces any existing one). */
    fun schedule(context: Context, task: RoutineTask) {
        if (!TIME_REGEX.matches(task.time)) { cancel(context, task.id); return }
        val pi = pending(context, task.id, fireIntent(context, task.id, task.title, true))
        setAt(context, nextTriggerMillis(LocalTime.parse(task.time)), pi)
    }

    fun cancel(context: Context, id: String) {
        val am = alarmManager(context)
        am.cancel(pending(context, id, fireIntent(context, id, "", true)))
        am.cancel(pending(context, id + "_s", fireIntent(context, id, "", false)))
    }

    fun sync(context: Context, tasks: List<RoutineTask>, enabled: Boolean) {
        tasks.forEach { if (enabled && it.remind) schedule(context, it) else cancel(context, it.id) }
    }

    fun snooze(context: Context, id: String, title: String, minutes: Int) {
        val pi = pending(context, id + "_s", fireIntent(context, id, title, false))
        setAt(context, System.currentTimeMillis() + minutes * 60_000L, pi)
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Routine reminders", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Reminders for your daily routines" }
            )
        }
    }

    fun showNotification(context: Context, id: String, title: String) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val snoozeIntent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_SNOOZE).putExtra(EXTRA_ID, id).putExtra(EXTRA_TITLE, title)
        val snoozePi = pending(context, id + "_a", snoozeIntent)
        val icon = Icon.createWithResource(context, R.mipmap.ic_launcher_foreground)
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText("Time for your routine")
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(icon, "Snooze", snoozePi).build())
            .build()
        nm.notify(id.hashCode(), notification)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ReminderScheduler.EXTRA_ID) ?: return
        val settings = RoutineStorage.loadSettings(context)
        val task = RoutineStorage.loadTasks(context).firstOrNull { it.id == id }
        when (intent.action) {
            ReminderScheduler.ACTION_FIRE -> {
                // Routine deleted or reminder turned off: let the alarm chain end here.
                if (task == null || !task.remind || !settings.remindersEnabled) return
                if (intent.getBooleanExtra(ReminderScheduler.EXTRA_REPEAT, true)) ReminderScheduler.schedule(context, task)
                if (task.done) return
                ReminderScheduler.showNotification(context, task.id, task.title)
            }
            ReminderScheduler.ACTION_SNOOZE -> {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(id.hashCode())
                if (task != null && settings.remindersEnabled) {
                    ReminderScheduler.snooze(context, task.id, task.title, settings.snoozeMinutes)
                }
            }
        }
    }
}

/** Re-creates alarms after reboot, app update, or a clock/time-zone change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val settings = RoutineStorage.loadSettings(context)
        ReminderScheduler.sync(context, RoutineStorage.loadTasks(context), settings.remindersEnabled)
    }
}
