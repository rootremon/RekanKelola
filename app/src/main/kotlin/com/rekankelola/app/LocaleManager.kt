package com.rekankelola.app

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Mengatur bahasa aktif aplikasi: menyimpan pilihan user, menerapkan
 * locale ke seluruh Activity, dan menyediakan fungsi tr() untuk
 * mengambil teks sesuai bahasa aktif dari Strings.kt.
 *
 * Default: English (LANG_EN) untuk instalasi baru, sesuai permintaan
 * supaya aplikasi terasa "global" saat pertama dibuka. User bisa ganti
 * kapan saja lewat halaman Settings, dan pilihannya tersimpan permanen.
 */
object LocaleManager {

    private const val PREFS_NAME = "rekan_kelola_locale"
    private const val KEY_LANGUAGE = "selected_language"

    /**
     * Mengambil kode bahasa yang sedang aktif. Kalau user belum pernah
     * memilih (instalasi baru), kembalikan English sebagai default --
     * BUKAN bahasa sistem HP, sesuai keputusan produk yang sudah dipilih.
     */
    fun getCurrentLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, Strings.LANG_EN) ?: Strings.LANG_EN
    }

    /**
     * Menyimpan pilihan bahasa baru. Panggil applyLocale() dan restart
     * activity setelah ini supaya perubahan langsung terlihat.
     */
    fun setLanguage(context: Context, languageCode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply()
    }

    /**
     * Menerapkan locale terpilih ke Configuration Android. Ini memengaruhi
     * hal-hal bawaan sistem seperti format tanggal lewat java.text jika
     * dipakai dengan Locale.getDefault(), meskipun teks utama aplikasi
     * (lewat tr()) tidak bergantung pada ini karena disimpan manual di
     * Strings.kt -- pendekatan ini dipilih karena MainActivity.kt seluruhnya
     * programatik tanpa resource strings.xml/values-xx bawaan Android.
     */
    fun applyLocale(context: Context): Context {
        val languageCode = getCurrentLanguage(context)
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return context.createConfigurationContext(config)
    }

    /**
     * Fungsi utama yang dipanggil dari MainActivity untuk ambil teks.
     * Contoh: tr(context, "dashboard_title")
     *
     * Kalau key tidak ditemukan sama sekali, atau bahasa aktif belum
     * punya terjemahan untuk key itu, otomatis fallback ke English
     * supaya tidak pernah menampilkan teks kosong/crash selama migrasi
     * bertahap masih berjalan.
     */
    fun tr(context: Context, key: String): String {
        val lang = getCurrentLanguage(context)
        val entry = Strings.STRINGS[key]
            ?: return "[$key]" // key belum didaftarkan sama sekali di Strings.kt

        return entry[lang]
            ?: entry[Strings.LANG_EN]
            ?: "[$key]"
    }
}
