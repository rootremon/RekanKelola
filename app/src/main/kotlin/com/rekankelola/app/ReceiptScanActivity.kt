package com.rekankelola.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

/**
 * Activity ringan yang tugasnya:
 * 1. Minta izin kamera kalau belum ada
 * 2. Buka aplikasi kamera bawaan HP untuk ambil foto struk
 * 3. Jalankan OCR OFFLINE (Google ML Kit -- gratis, tanpa internet,
 *    tanpa API key) untuk membaca semua teks di foto
 * 4. Serahkan teks mentahnya ke ReceiptParser untuk diekstrak jadi
 *    nominal + nama toko
 * 5. Kembali ke MainActivity membawa hasilnya lewat Intent extras,
 *    supaya MainActivity yang menampilkan dialog konfirmasi
 *    (reuse showTransactionDialog yang sudah ada)
 *
 * Tidak ada data yang dikirim ke server manapun -- semua proses OCR
 * terjadi di dalam HP.
 */
class ReceiptScanActivity : android.app.Activity() {

    companion object {
        const val EXTRA_AMOUNT = "extra_amount"
        const val EXTRA_MERCHANT = "extra_merchant"
        private const val REQUEST_CODE_CAMERA_PERMISSION = 701
        private const val REQUEST_CODE_TAKE_PHOTO = 702
    }

    private var photoUri: Uri? = null
    private lateinit var loadingOverlay: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Overlay loading sederhana, tampil sambil nunggu kamera/OCR.
        // Dibuat programatik (tanpa layout XML) supaya konsisten dengan
        // gaya MainActivity.kt yang juga full-programatik.
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        loadingOverlay = FrameLayout(this)
        loadingOverlay.setBackgroundColor(Color.parseColor("#080B12"))

        val label = TextView(this)
        label.text = "Memindai struk..."
        label.setTextColor(Color.WHITE)
        label.textSize = 15f
        label.gravity = Gravity.CENTER

        loadingOverlay.addView(
            label,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
        )

        root.addView(
            loadingOverlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        setContentView(root)

        if (hasCameraPermission()) {
            launchCamera()
        } else {
            requestCameraPermission()
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            REQUEST_CODE_CAMERA_PERMISSION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
            val granted = grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED

            if (granted) {
                launchCamera()
            } else {
                Toast.makeText(
                    this,
                    "Izin kamera diperlukan untuk memindai struk",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private fun launchCamera() {
        val photoFile = File(
            cacheDir,
            "receipt_${System.currentTimeMillis()}.jpg"
        )

        photoUri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            photoFile
        )

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }

        if (intent.resolveActivity(packageManager) != null) {
            startActivityForResult(intent, REQUEST_CODE_TAKE_PHOTO)
        } else {
            Toast.makeText(
                this,
                "Tidak ada aplikasi kamera yang ditemukan",
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != REQUEST_CODE_TAKE_PHOTO) return

        if (resultCode != android.app.Activity.RESULT_OK) {
            // User batal foto -- tutup activity, kembali seperti semula.
            finish()
            return
        }

        val uri = photoUri
        if (uri == null) {
            finish()
            return
        }

        runOcr(uri)
    }

    private fun runOcr(uri: Uri) {
        val image = try {
            InputImage.fromFilePath(this, uri)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Gagal membaca foto, coba lagi",
                Toast.LENGTH_SHORT
            ).show()
            finish()
            return
        }

        // TextRecognizerOptions.DEFAULT_OPTIONS mendukung Latin script,
        // cocok untuk struk berbahasa Indonesia (huruf latin + angka).
        val recognizer = TextRecognition.getClient(
            TextRecognizerOptions.DEFAULT_OPTIONS
        )

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val parsed = ReceiptParser.parse(visionText.text)
                finishWithResult(parsed)
            }
            .addOnFailureListener {
                Toast.makeText(
                    this,
                    "Gagal memindai teks di struk, coba foto ulang dengan pencahayaan lebih terang",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
    }

    private fun finishWithResult(parsed: ReceiptParser.ParsedReceipt) {
        val resultIntent = Intent().apply {
            parsed.amount?.let { putExtra(EXTRA_AMOUNT, it) }
            parsed.merchantName?.let { putExtra(EXTRA_MERCHANT, it) }
        }
        setResult(android.app.Activity.RESULT_OK, resultIntent)
        finish()
    }
}
