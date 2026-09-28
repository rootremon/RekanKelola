package com.rekankelola.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Menjadwalkan alarm harian jam 23:40 yang memicu ReminderReceiver.
 * Dipanggil dari MainActivity.onCreate (supaya selalu ter-reschedule
 * saat app dibuka) dan dari BootReceiver (supaya tetap jalan setelah
 * HP di-restart, karena semua alarm hilang saat reboot).
 */
object ReminderScheduler {

    private const val REQUEST_CODE_DAILY_REMINDER = 9001

    fun scheduleDailyReminder(context: Context) {
        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_DAILY_REMINDER
        }

        val pendingFlags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_DAILY_REMINDER,
            intent,
            pendingFlags
        )

        val triggerTime = nextOccurrenceOf(23, 40)

        // setExactAndAllowWhileIdle supaya tetap tepat waktu walau HP
        // sedang di Doze mode (layar mati lama). Ini yang dipakai
        // aplikasi-aplikasi seperti alarm/reminder profesional.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }

    /**
     * Dipanggil oleh ReminderReceiver setelah notif jam 23:40 terkirim,
     * supaya alarm berikutnya (besok jam 23:40) langsung terjadwal lagi.
     * AlarmManager di Android tidak punya "repeat exact" yang presisi,
     * jadi pola re-schedule manual seperti ini yang paling akurat.
     */
    fun rescheduleForTomorrow(context: Context) {
        scheduleDailyReminder(context)
    }

    private fun nextOccurrenceOf(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()

        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        return target.timeInMillis
    }
}
