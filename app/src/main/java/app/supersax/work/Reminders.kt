package app.supersax.work

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
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.supersax.MainActivity
import app.supersax.R
import app.supersax.SaxApp
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Opt-in practice reminders: at most one per planned day, neutral wording, and only if nothing was
 * played yet that day. Off by default.
 */
object Reminders {
    private const val WORK = "practice-reminder"
    const val CHANNEL = "reminders"

    fun sync(context: Context, enabled: Boolean, hour: Int, minute: Int) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) { wm.cancelUniqueWork(WORK); return }
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(hour, minute)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMinutes()
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS).setInitialDelay(delay, TimeUnit.MINUTES).build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private const val DAILY_WORK = "daily-exercise-reminder"

    /** Schedules (or cancels) the daily-exercise reminder at [hour]:[minute]. */
    fun syncDaily(context: Context, enabled: Boolean, hour: Int, minute: Int) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) { wm.cancelUniqueWork(DAILY_WORK); return }
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(hour, minute)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMinutes()
        val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(1, TimeUnit.DAYS).setInitialDelay(delay, TimeUnit.MINUTES).build()
        wm.enqueueUniquePeriodicWork(DAILY_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.reminders_channel), NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    fun canNotify(context: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as SaxApp).container
        val settings = c.settings.current()
        if (!settings.remindersEnabled || !Reminders.canNotify(applicationContext)) return Result.success()
        val program = c.practice.activeProgram.first() ?: return Result.success()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        if (program.dayFor(today.dayOfWeek.value) == null) return Result.success()
        val startOfDay = today.atStartOfDay(zone).toInstant().toEpochMilli()
        if (c.practice.sessions.first().any { !it.phone && it.startedAtMs >= startOfDay }) return Result.success()
        Reminders.ensureChannel(applicationContext)
        val open = PendingIntent.getActivity(applicationContext, 0, Intent(applicationContext, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(applicationContext, Reminders.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_note)
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setContentText(applicationContext.getString(R.string.reminder_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(applicationContext).notify(1, n)
        return Result.success()
    }
}

/** Reminds about the daily exercise only if it is not done yet today; mentions the streak at stake. */
class DailyReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as SaxApp).container
        if (!c.settings.current().dailyReminder || !Reminders.canNotify(applicationContext)) return Result.success()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone).toEpochDay()
        val days = app.supersax.ui.practice.dailyDays(c.progress.drills.first(), zone)
        if (today in days) return Result.success()
        val streak = app.supersax.domain.drills.DailyMix.streak(days, today)
        Reminders.ensureChannel(applicationContext)
        val intent = Intent(applicationContext, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_DAILY, true)
        val open = PendingIntent.getActivity(applicationContext, 2, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = if (streak > 0) applicationContext.resources.getQuantityString(R.plurals.daily_reminder_streak, streak, streak)
            else applicationContext.getString(R.string.daily_reminder_text)
        val n = NotificationCompat.Builder(applicationContext, Reminders.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_note)
            .setContentTitle(applicationContext.getString(R.string.daily_title))
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(applicationContext).notify(2, n)
        return Result.success()
    }
}
