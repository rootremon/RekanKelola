package com.rekankelola.app

/**
 * Semua teks yang tampil di aplikasi, dalam 4 bahasa: Inggris (default),
 * Indonesia, China (Simplified), dan Rusia.
 *
 * CARA KERJA:
 * Setiap teks punya "key" unik (bahasa Inggris singkat, mis. "dashboard_title").
 * Fungsi tr(key) di LocaleManager akan mengambil teks yang sesuai dengan
 * bahasa aktif user saat ini.
 *
 * CARA MENAMBAH TEKS BARU:
 * 1. Tambahkan key baru di STRINGS di bawah, isi keempat bahasanya.
 * 2. Di MainActivity, ganti string hardcode "..." menjadi tr("key_kamu").
 *
 * INI FONDASI -- migrasi seluruh 1800+ baris teks di MainActivity.kt akan
 * dilakukan bertahap per layar di sesi-sesi berikutnya. File ini sudah
 * mencakup layar Dashboard sebagai contoh pola yang benar untuk migrasi
 * layar-layar lainnya (Transaksi, Target, RekanAI, Settings, dialog-dialog).
 */
object Strings {

    // Kode bahasa yang didukung. Dipakai juga sebagai value yang disimpan
    // di SharedPreferences oleh LocaleManager.
    const val LANG_EN = "en"
    const val LANG_ID = "id"
    const val LANG_ZH = "zh"
    const val LANG_RU = "ru"

    val SUPPORTED_LANGUAGES = listOf(
        LANG_EN,
        LANG_ID,
        LANG_ZH,
        LANG_RU
    )

    fun displayNameFor(code: String): String = when (code) {
        LANG_EN -> "English"
        LANG_ID -> "Bahasa Indonesia"
        LANG_ZH -> "中文"
        LANG_RU -> "Русский"
        else -> code
    }

    /**
     * Setiap entri: key -> map(kode bahasa -> teks).
     * Kalau suatu bahasa belum diisi untuk key tertentu, sistem otomatis
     * fallback ke bahasa Inggris (lihat LocaleManager.tr()), jadi aplikasi
     * tidak akan pernah menampilkan teks kosong walau migrasi belum
     * lengkap 100%.
     */
    val STRINGS: Map<String, Map<String, String>> = mapOf(

        // ============================================================
        // UMUM / SHARED
        // ============================================================
        "app_name" to mapOf(
            LANG_EN to "RekanKelola",
            LANG_ID to "RekanKelola",
            LANG_ZH to "RekanKelola",
            LANG_RU to "RekanKelola"
        ),
        "save" to mapOf(
            LANG_EN to "Save",
            LANG_ID to "Simpan",
            LANG_ZH to "保存",
            LANG_RU to "Сохранить"
        ),
        "save_changes" to mapOf(
            LANG_EN to "Save changes",
            LANG_ID to "Simpan perubahan",
            LANG_ZH to "保存更改",
            LANG_RU to "Сохранить изменения"
        ),
        "cancel" to mapOf(
            LANG_EN to "Cancel",
            LANG_ID to "Batal",
            LANG_ZH to "取消",
            LANG_RU to "Отмена"
        ),
        "delete" to mapOf(
            LANG_EN to "Delete",
            LANG_ID to "Hapus",
            LANG_ZH to "删除",
            LANG_RU to "Удалить"
        ),
        "edit" to mapOf(
            LANG_EN to "Edit",
            LANG_ID to "Edit",
            LANG_ZH to "编辑",
            LANG_RU to "Изменить"
        ),
        "today" to mapOf(
            LANG_EN to "Today",
            LANG_ID to "Hari ini",
            LANG_ZH to "今天",
            LANG_RU to "Сегодня"
        ),
        "nav_home" to mapOf(
            LANG_EN to "Home",
            LANG_ID to "Beranda",
            LANG_ZH to "首页",
            LANG_RU to "Главная"
        ),
        "nav_transaction" to mapOf(
            LANG_EN to "Transaction",
            LANG_ID to "Transaksi",
            LANG_ZH to "交易",
            LANG_RU to "Транзакция"
        ),
        "nav_target" to mapOf(
            LANG_EN to "Target",
            LANG_ID to "Target",
            LANG_ZH to "目标",
            LANG_RU to "Цель"
        ),
        "nav_ai" to mapOf(
            LANG_EN to "RekanAI",
            LANG_ID to "RekanAI",
            LANG_ZH to "RekanAI",
            LANG_RU to "RekanAI"
        ),
        "nav_settings" to mapOf(
            LANG_EN to "Settings",
            LANG_ID to "Setting",
            LANG_ZH to "设置",
            LANG_RU to "Настройки"
        ),

        // ============================================================
        // DASHBOARD (contoh migrasi layar pertama)
        // ============================================================
        "dashboard_title" to mapOf(
            LANG_EN to "Dashboard",
            LANG_ID to "Dashboard",
            LANG_ZH to "仪表盘",
            LANG_RU to "Панель"
        ),
        "dashboard_subtitle" to mapOf(
            LANG_EN to "RekanKelola • Your financial summary",
            LANG_ID to "RekanKelola • Ringkasan keuangan kamu",
            LANG_ZH to "RekanKelola • 您的财务摘要",
            LANG_RU to "RekanKelola • Обзор ваших финансов"
        ),
        "dashboard_balance" to mapOf(
            LANG_EN to "Balance",
            LANG_ID to "Saldo",
            LANG_ZH to "余额",
            LANG_RU to "Баланс"
        ),
        "dashboard_income" to mapOf(
            LANG_EN to "Income",
            LANG_ID to "Pemasukan",
            LANG_ZH to "收入",
            LANG_RU to "Доход"
        ),
        "dashboard_expense" to mapOf(
            LANG_EN to "Expense",
            LANG_ID to "Pengeluaran",
            LANG_ZH to "支出",
            LANG_RU to "Расход"
        ),
        "dashboard_recent_transactions" to mapOf(
            LANG_EN to "Recent transactions",
            LANG_ID to "Transaksi terbaru",
            LANG_ZH to "最近交易",
            LANG_RU to "Последние транзакции"
        ),
        "dashboard_recent_subtitle" to mapOf(
            LANG_EN to "Your latest financial activity",
            LANG_ID to "Aktivitas keuangan terakhir",
            LANG_ZH to "您最近的财务活动",
            LANG_RU to "Ваша последняя финансовая активность"
        ),
        "dashboard_see_all" to mapOf(
            LANG_EN to "See all",
            LANG_ID to "Lihat semua",
            LANG_ZH to "查看全部",
            LANG_RU to "Смотреть все"
        ),
        "dashboard_no_transactions" to mapOf(
            LANG_EN to "No transactions yet",
            LANG_ID to "Belum ada transaksi",
            LANG_ZH to "暂无交易记录",
            LANG_RU to "Пока нет транзакций"
        ),
        "dashboard_current_balance" to mapOf(
            LANG_EN to "Current balance",
            LANG_ID to "Saldo saat ini",
            LANG_ZH to "当前余额",
            LANG_RU to "Текущий баланс"
        ),
        "dashboard_month_expense_avg" to mapOf(
            LANG_EN to "This month's expense • avg %s/day",
            LANG_ID to "Pengeluaran bulan ini • rata-rata %s/hari",
            LANG_ZH to "本月支出 • 日均 %s",
            LANG_RU to "Расходы за месяц • в среднем %s/день"
        ),
        "dashboard_target_section" to mapOf(
            LANG_EN to "Target",
            LANG_ID to "Target",
            LANG_ZH to "目标",
            LANG_RU to "Цель"
        ),
        "dashboard_target_subtitle" to mapOf(
            LANG_EN to "Your savings progress",
            LANG_ID to "Progres tabungan kamu",
            LANG_ZH to "您的储蓄进度",
            LANG_RU to "Ваш прогресс накоплений"
        ),

        // ============================================================
        // TAMBAH TRANSAKSI (dialog)
        // ============================================================
        "tx_dialog_add_title" to mapOf(
            LANG_EN to "Add transaction",
            LANG_ID to "Tambah transaksi",
            LANG_ZH to "添加交易",
            LANG_RU to "Добавить транзакцию"
        ),
        "tx_dialog_edit_title" to mapOf(
            LANG_EN to "Edit transaction",
            LANG_ID to "Edit transaksi",
            LANG_ZH to "编辑交易",
            LANG_RU to "Изменить транзакцию"
        ),
        "tx_type_label" to mapOf(
            LANG_EN to "Transaction type",
            LANG_ID to "Jenis transaksi",
            LANG_ZH to "交易类型",
            LANG_RU to "Тип транзакции"
        ),
        "tx_type_income" to mapOf(
            LANG_EN to "Income",
            LANG_ID to "Pemasukan",
            LANG_ZH to "收入",
            LANG_RU to "Доход"
        ),
        "tx_type_expense" to mapOf(
            LANG_EN to "Expense",
            LANG_ID to "Pengeluaran",
            LANG_ZH to "支出",
            LANG_RU to "Расход"
        ),
        "tx_amount_label" to mapOf(
            LANG_EN to "Amount",
            LANG_ID to "Nominal",
            LANG_ZH to "金额",
            LANG_RU to "Сумма"
        ),
        "tx_amount_hint" to mapOf(
            LANG_EN to "Amount",
            LANG_ID to "Nominal",
            LANG_ZH to "金额",
            LANG_RU to "Сумма"
        ),
        "tx_category_label" to mapOf(
            LANG_EN to "Category",
            LANG_ID to "Kategori",
            LANG_ZH to "分类",
            LANG_RU to "Категория"
        ),
        "tx_note_label" to mapOf(
            LANG_EN to "Note (optional)",
            LANG_ID to "Catatan (opsional)",
            LANG_ZH to "备注（可选）",
            LANG_RU to "Заметка (необязательно)"
        ),
        "tx_note_hint" to mapOf(
            LANG_EN to "Note",
            LANG_ID to "Catatan",
            LANG_ZH to "备注",
            LANG_RU to "Заметка"
        ),
        "tx_invalid_amount" to mapOf(
            LANG_EN to "Invalid amount",
            LANG_ID to "Nominal tidak valid",
            LANG_ZH to "金额无效",
            LANG_RU to "Неверная сумма"
        ),

        // ============================================================
        // SETTINGS -- BAHASA (halaman baru)
        // ============================================================
        "settings_language" to mapOf(
            LANG_EN to "Language",
            LANG_ID to "Bahasa",
            LANG_ZH to "语言",
            LANG_RU to "Язык"
        ),
        "settings_language_subtitle" to mapOf(
            LANG_EN to "Choose your preferred app language",
            LANG_ID to "Pilih bahasa aplikasi yang kamu inginkan",
            LANG_ZH to "选择您偏好的应用语言",
            LANG_RU to "Выберите предпочитаемый язык приложения"
        )
    )
}
