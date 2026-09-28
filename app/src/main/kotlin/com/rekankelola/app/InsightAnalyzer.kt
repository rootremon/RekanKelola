package com.rekankelola.app

import android.content.Context

/**
 * Berisi aturan sederhana untuk menentukan kapan notif "insight"
 * otomatis perlu dikirim: pemasukan besar, pengeluaran terlalu banyak,
 * atau saldo hampir habis. Dipanggil setiap kali ada transaksi baru
 * ditambahkan (bukan dijadwalkan seperti pengingat harian), supaya
 * reaksinya langsung terasa ketika user mencatat transaksi.
 *
 * Ambang batas (threshold) di bawah ini sengaja dibuat sebagai
 * angka tetap yang gampang diubah kalau nanti mau dibuat bisa
 * dikonfigurasi user lewat halaman Settings.
 */
object InsightAnalyzer {

    // Pemasukan tunggal >= ini dianggap "pemasukan besar".
    private const val LARGE_INCOME_THRESHOLD = 1_000_000L

    // Total pengeluaran HARI INI >= ini dianggap "terlalu banyak".
    private const val HIGH_DAILY_SPENDING_THRESHOLD = 500_000L

    // Saldo (starting balance + semua transaksi) di bawah ini
    // dianggap "hampir habis".
    private const val LOW_BALANCE_THRESHOLD = 100_000L

    // Supaya tidak spam: satu jenis notif insight yang sama hanya
    // boleh dikirim ulang setelah jeda ini (dalam milidetik).
    private const val MIN_GAP_BETWEEN_SAME_INSIGHT_MS = 6L * 60 * 60 * 1000 // 6 jam

    private fun prefs(context: Context) =
        context.getSharedPreferences("rekan_kelola_insight_gate", Context.MODE_PRIVATE)

    private fun canSendAgain(context: Context, key: String): Boolean {
        val lastSent = prefs(context).getLong(key, 0L)
        return System.currentTimeMillis() - lastSent >= MIN_GAP_BETWEEN_SAME_INSIGHT_MS
    }

    private fun markSent(context: Context, key: String) {
        prefs(context).edit().putLong(key, System.currentTimeMillis()).apply()
    }

    /**
     * Panggil ini tepat setelah transaksi baru disimpan. Fungsi ini
     * yang memutuskan notif insight mana (jika ada) yang perlu tampil.
     *
     * @param newTransaction transaksi yang baru saja ditambahkan
     * @param allTransactions daftar transaksi lengkap terkini
     * @param startingBalance saldo awal yang di-set user
     */
    fun evaluateAfterNewTransaction(
        context: Context,
        newTransaction: MainActivity.Transaction,
        allTransactions: List<MainActivity.Transaction>,
        startingBalance: Long
    ) {
        NotificationHelper.ensureChannels(context)

        // ---------- 1. Pemasukan besar ----------
        if (newTransaction.type == "income" &&
            newTransaction.amount >= LARGE_INCOME_THRESHOLD
        ) {
            val key = "last_income_insight"
            if (canSendAgain(context, key)) {
                NotificationHelper.sendNotification(
                    context = context,
                    channelId = NotificationHelper.CHANNEL_ID_INSIGHT,
                    notifId = NotificationHelper.NOTIF_ID_INCOME_INSIGHT,
                    title = "RekanKelola",
                    message = "Kamu memiliki banyak pemasukan, jangan lupa untuk menabung 💰"
                )
                markSent(context, key)
            }
        }

        // ---------- 2. Pengeluaran hari ini terlalu banyak ----------
        val today = allTransactions
            .filter { it.type == "expense" && it.date == newTransaction.date }
            .sumOf { it.amount }

        if (newTransaction.type == "expense" &&
            today >= HIGH_DAILY_SPENDING_THRESHOLD
        ) {
            val key = "last_spending_insight"
            if (canSendAgain(context, key)) {
                NotificationHelper.sendNotification(
                    context = context,
                    channelId = NotificationHelper.CHANNEL_ID_INSIGHT,
                    notifId = NotificationHelper.NOTIF_ID_SPENDING_INSIGHT,
                    title = "RekanKelola",
                    message = "Pengeluaran kamu hari ini sudah cukup banyak, yuk cek lagi anggarannya 👀"
                )
                markSent(context, key)
            }
        }

        // ---------- 3. Saldo hampir habis ----------
        val balance = startingBalance + allTransactions.sumOf {
            if (it.type == "income") it.amount else -it.amount
        }

        if (balance in 0..LOW_BALANCE_THRESHOLD) {
            val key = "last_low_balance_insight"
            if (canSendAgain(context, key)) {
                NotificationHelper.sendNotification(
                    context = context,
                    channelId = NotificationHelper.CHANNEL_ID_INSIGHT,
                    notifId = NotificationHelper.NOTIF_ID_LOW_BALANCE,
                    title = "RekanKelola",
                    message = "Saldo kamu sudah menipis, atur pengeluaran biar tetap aman ⚠️"
                )
                markSent(context, key)
            }
        }
    }
}
