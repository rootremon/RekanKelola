package com.rekankelola.app

import java.util.regex.Pattern

/**
 * Mengubah teks mentah hasil OCR (bacaan ML Kit dari foto struk) menjadi
 * data terstruktur: nominal total belanja + nama toko (kalau kebaca).
 *
 * OCR offline sifatnya hanya membaca teks apa adanya -- dia tidak "paham"
 * mana yang total, mana yang harga per-item. Jadi di sinilah logic
 * berbasis pola/kata kunci dipakai untuk menebak baris mana yang paling
 * mungkin adalah total belanja. Karena format struk sangat beragam
 * (minimarket, restoran, SPBU, dll berbeda-beda), akurasinya tidak akan
 * 100% -- makanya hasil parsing selalu ditampilkan dulu ke user untuk
 * dikoreksi sebelum disimpan sebagai transaksi (bukan auto-save).
 */
object ReceiptParser {

    data class ParsedReceipt(
        val amount: Long?,
        val merchantName: String?,
        val rawText: String
    )

    // Kata kunci baris yang mengindikasikan "ini adalah baris total",
    // diurutkan dari yang paling spesifik/dapat dipercaya duluan.
    private val totalKeywords = listOf(
        "grand total",
        "total belanja",
        "total bayar",
        "total harga",
        "jumlah bayar",
        "total tagihan",
        "total",
        "jumlah"
    )

    // Baris yang sebenarnya BUKAN total meski mengandung kata "total",
    // supaya tidak salah tangkap (misal "Total Item: 5" bukan nominal uang).
    private val excludeKeywords = listOf(
        "total item",
        "total qty",
        "total kuantitas",
        "jumlah item",
        "jumlah barang",
        "subtotal" // subtotal biasanya sebelum diskon/pajak, bukan total akhir
    )

    private val amountPattern = Pattern.compile(
        "(?:rp\\.?\\s*)?([0-9]{1,3}(?:[.,][0-9]{3})+|[0-9]{4,})",
        Pattern.CASE_INSENSITIVE
    )

    fun parse(rawText: String): ParsedReceipt {
        val lines = rawText
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val amount = findTotalAmount(lines)
        val merchant = guessMerchantName(lines)

        return ParsedReceipt(
            amount = amount,
            merchantName = merchant,
            rawText = rawText
        )
    }

    private fun findTotalAmount(lines: List<String>): Long? {
        // Tahap 1: cari baris yang cocok kata kunci total, dari yang
        // paling spesifik. Kalau baris itu sendiri tidak ada angkanya
        // (kadang label & nominal terpisah baris karena layout struk),
        // cek juga baris berikutnya.
        for (keyword in totalKeywords) {
            for (i in lines.indices) {
                val lower = lines[i].lowercase()
                if (!lower.contains(keyword)) continue
                if (excludeKeywords.any { lower.contains(it) }) continue

                extractAmount(lines[i])?.let { return it }

                if (i + 1 < lines.size) {
                    extractAmount(lines[i + 1])?.let { return it }
                }
            }
        }

        // Tahap 2 (fallback): kalau tidak ada baris berlabel "total" yang
        // kebaca OCR sama sekali, ambil nominal TERBESAR di seluruh struk.
        // Asumsinya: total belanja biasanya angka terbesar dibanding
        // harga per-item satuan.
        val allAmounts = lines.mapNotNull { extractAmount(it) }
        return allAmounts.maxOrNull()
    }

    private fun extractAmount(line: String): Long? {
        val matcher = amountPattern.matcher(line)
        var best: Long? = null

        while (matcher.find()) {
            val raw = matcher.group(1) ?: continue
            val normalized = raw.replace(".", "").replace(",", "")
            val value = normalized.toLongOrNull() ?: continue

            // Saring angka yang jelas bukan nominal rupiah wajar
            // (misal nomor telepon, tanggal, nomor struk yang kebaca ikut).
            if (value < 100 || value > 999_999_999L) continue

            if (best == null || value > best!!) {
                best = value
            }
        }

        return best
    }

    /**
     * Nama toko biasanya ada di baris paling atas struk, dicetak besar.
     * Heuristik sederhana: ambil baris pertama yang cukup panjang,
     * bukan cuma simbol/angka, dan bukan alamat (tidak mengandung
     * kata seperti "jl", "no.", dsb di posisi awal).
     */
    private fun guessMerchantName(lines: List<String>): String? {
        for (line in lines.take(5)) {
            val letters = line.count { it.isLetter() }
            if (letters < 3) continue

            val lower = line.lowercase()
            if (lower.startsWith("jl") || lower.startsWith("jalan")) continue
            if (lower.contains("telp") || lower.contains("no.")) continue

            return line
        }
        return null
    }
}
