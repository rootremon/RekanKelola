package com.rekankelola.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Semua alarm yang dijadwalkan lewat AlarmManager HILANG setiap kali
 * HP di-restart. Receiver ini dengar sinyal BOOT_COMPLETED dari sistem
 * dan langsung menjadwalkan ulang pengingat 23:40, supaya user tidak
 * perlu buka aplikasi dulu setelah restart HP agar notif jalan lagi.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.scheduleDailyReminder(context)
        }
    }
}
