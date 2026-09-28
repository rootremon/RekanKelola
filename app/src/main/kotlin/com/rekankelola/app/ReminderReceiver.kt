package com.rekankelola.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Dipicu oleh AlarmManager setiap jam 23:40. Tugasnya cuma dua:
 * 1. Tampilkan notif pengingat isi laporan harian
 * 2. Jadwalkan ulang alarm untuk besok (karena alarm exact di Android
 *    tidak otomatis berulang tanpa di-reschedule manual)
 */
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DAILY_REMINDER =
            "com.rekankelola.app.ACTION_DAILY_REMINDER"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DAILY_REMINDER) return

        NotificationHelper.ensureChannels(context)

        NotificationHelper.sendNotification(
            context = context,
            channelId = NotificationHelper.CHANNEL_ID_REMINDER,
            notifId = NotificationHelper.NOTIF_ID_DAILY_REMINDER,
            title = "RekanKelola",
            message = "Jangan sampai lupa mengisi laporan harian keuangan kamu :)"
        )

        ReminderScheduler.rescheduleForTomorrow(context)
    }
}
