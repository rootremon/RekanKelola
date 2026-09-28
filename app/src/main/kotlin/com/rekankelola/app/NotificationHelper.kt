package com.rekankelola.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Semua hal terkait notifikasi dikumpulkan di sini: pembuatan channel,
 * pengecekan izin, dan fungsi kirim notif. Dipisah dari MainActivity.kt
 * supaya rapi dan gampang dipakai dari Activity maupun dari
 * BroadcastReceiver (yang tidak punya akses ke Activity).
 *
 * Support Android 7 (API 24) ke atas:
 * - Notification channel hanya dibuat kalau API >= 26 (Android 8+),
 *   di bawah itu channel tidak dikenal dan diabaikan otomatis.
 * - Izin POST_NOTIFICATIONS hanya perlu diminta run-time kalau
 *   API >= 33 (Android 13+); di versi lebih lama izin ini otomatis
 *   dianggap diberikan lewat AndroidManifest.
 */
object NotificationHelper {

    const val CHANNEL_ID_REMINDER = "rekan_kelola_reminder"
    const val CHANNEL_ID_INSIGHT = "rekan_kelola_insight"

    const val NOTIF_ID_DAILY_REMINDER = 1001
    const val NOTIF_ID_INCOME_INSIGHT = 1002
    const val NOTIF_ID_SPENDING_INSIGHT = 1003
    const val NOTIF_ID_LOW_BALANCE = 1004

    const val REQUEST_CODE_NOTIF_PERMISSION = 501

    /**
     * Dipanggil sekali saat aplikasi start (di onCreate MainActivity).
     * Membuat dua channel: satu untuk pengingat harian rutin, satu untuk
     * insight otomatis (pemasukan besar, pengeluaran boros, saldo tipis).
     * Dipisah channel supaya user bisa atur/matikan masing-masing lewat
     * pengaturan sistem Android tanpa mematikan semuanya sekaligus.
     */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        val reminderChannel = NotificationChannel(
            CHANNEL_ID_REMINDER,
            "Pengingat Harian",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Pengingat mengisi laporan keuangan harian"
            enableLights(true)
            enableVibration(true)
        }

        val insightChannel = NotificationChannel(
            CHANNEL_ID_INSIGHT,
            "Insight Keuangan",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description =
                "Notifikasi otomatis soal pemasukan, pengeluaran, dan saldo"
            enableLights(true)
            enableVibration(true)
        }

        manager.createNotificationChannel(reminderChannel)
        manager.createNotificationChannel(insightChannel)
    }

    /**
     * Android 13+ (API 33) mewajibkan izin run-time POST_NOTIFICATIONS.
     * Di bawah itu, fungsi ini selalu true (izin dianggap otomatis ada).
     */
    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true

        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Minta izin notifikasi ke user (hanya relevan di Android 13+).
     * Panggil ini dari MainActivity.onCreate; hasilnya ditangani di
     * onRequestPermissionsResult.
     */
    fun requestNotificationPermission(activity: android.app.Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_CODE_NOTIF_PERMISSION
        )
    }

    /**
     * Kirim notifikasi yang muncul di status bar DAN sebagai heads-up
     * (banner di atas layar), persis seperti aplikasi profesional.
     * Syarat heads-up: importance channel HIGH (sudah diset di atas)
     * + priority HIGH pada notifikasi itu sendiri.
     */
    fun sendNotification(
        context: Context,
        channelId: String,
        notifId: Int,
        title: String,
        message: String
    ) {
        if (!hasNotificationPermission(context)) return

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingFlags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

        val contentIntent = PendingIntent.getActivity(
            context,
            notifId,
            openAppIntent,
            pendingFlags
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)

        NotificationManagerCompat.from(context).notify(notifId, builder.build())
    }
}
