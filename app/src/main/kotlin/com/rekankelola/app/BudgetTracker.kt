package com.rekankelola.app

/**
 * Menghitung alokasi budget per kategori berdasarkan persentase yang
 * di-set user di Settings (savingPercent, transportPercent,
 * entertainmentPercent -- variabel-variabel ini sudah ada sebelumnya
 * di MainActivity.kt, dipakai juga untuk kartu "Alokasi bulanan" di
 * layar Analisis).
 *
 * KONSEP:
 * - Basis perhitungan adalah SALDO SAAT INI (bukan total pemasukan),
 *   supaya budget selalu mencerminkan uang yang benar-benar tersisa.
 * - "Terpakai" dihitung dari total pengeluaran BULAN INI pada kategori
 *   yang cocok (transport -> kategori "Transport", dst).
 * - Tabungan diperlakukan sedikit berbeda: dia bukan "pengeluaran" yang
 *   mengurangi limit, tapi porsi saldo yang DISARANKAN untuk disisihkan
 *   ke Target. Progress bar tabungan menunjukkan seberapa besar alokasi
 *   itu sudah "siap dipindah" (selalu 0% terpakai sampai user pindahkan
 *   manual ke Target -- lihat catatan di BudgetItem.isSavingType).
 */
object BudgetTracker {

    data class BudgetItem(
        val key: String, // "saving" | "transport" | "entertainment"
        val displayName: String,
        val percent: Int,
        val allocated: Long, // nominal budget = saldo * persen%
        val used: Long, // total pengeluaran kategori terkait bulan ini
        val color: Int,
        val isSavingType: Boolean = false
    ) {
        val remaining: Long get() = (allocated - used).coerceAtLeast(0L)

        val fraction: Float get() {
            if (allocated <= 0L) return 0f
            return (used.toDouble() / allocated.toDouble())
                .toFloat()
                .coerceIn(0f, 1f)
        }

        val isExhausted: Boolean get() = allocated > 0L && used >= allocated
        val isNearLimit: Boolean get() = allocated > 0L && !isExhausted && fraction >= 0.8f
    }

    // Kategori transaksi yang dianggap masuk ke masing-masing pos budget.
    // Dicocokkan case-insensitive supaya "transport" dan "Transport" sama.
    private val transportCategories = setOf("transport")
    private val entertainmentCategories = setOf("hiburan", "entertainment")

    /**
     * @param currentBalance saldo saat ini (startingBalance + income - expense)
     * @param transactions seluruh transaksi (akan difilter ke bulan berjalan)
     * @param yearMonth format "yyyy-MM", dipakai untuk filter "bulan ini"
     */
    fun compute(
        currentBalance: Long,
        transactions: List<MainActivity.Transaction>,
        yearMonth: String,
        savingPercent: Int,
        transportPercent: Int,
        entertainmentPercent: Int,
        colorSaving: Int,
        colorTransport: Int,
        colorEntertainment: Int
    ): List<BudgetItem> {

        val baseAmount = currentBalance.coerceAtLeast(0L)

        val thisMonthExpenses = transactions.filter {
            it.type == "expense" && it.date.startsWith(yearMonth)
        }

        val transportUsed = thisMonthExpenses
            .filter { it.category.lowercase() in transportCategories }
            .sumOf { it.amount }

        val entertainmentUsed = thisMonthExpenses
            .filter { it.category.lowercase() in entertainmentCategories }
            .sumOf { it.amount }

        val savingAllocated = percentOf(baseAmount, savingPercent)
        val transportAllocated = percentOf(baseAmount, transportPercent)
        val entertainmentAllocated = percentOf(baseAmount, entertainmentPercent)

        return listOf(
            BudgetItem(
                key = "saving",
                displayName = "Tabungan",
                percent = savingPercent,
                allocated = savingAllocated,
                used = 0L, // tabungan tidak "terpakai", lihat catatan di atas
                color = colorSaving,
                isSavingType = true
            ),
            BudgetItem(
                key = "transport",
                displayName = "Transport",
                percent = transportPercent,
                allocated = transportAllocated,
                used = transportUsed,
                color = colorTransport
            ),
            BudgetItem(
                key = "entertainment",
                displayName = "Hiburan",
                percent = entertainmentPercent,
                allocated = entertainmentAllocated,
                used = entertainmentUsed,
                color = colorEntertainment
            )
        )
    }

    private fun percentOf(base: Long, percent: Int): Long {
        return (base * percent.toLong()) / 100L
    }
}
