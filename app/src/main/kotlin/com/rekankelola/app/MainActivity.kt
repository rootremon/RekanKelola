package com.rekankelola.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.*
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min

class MainActivity : Activity() {

    // ============================================================
    // BAHASA / LOCALIZATION
    // ============================================================

    // Diterapkan sebelum Activity dibuat, supaya locale sudah aktif
    // sejak awal render (bukan cuma diset belakangan di onCreate).
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(
            LocaleManager.applyLocale(newBase)
        )
    }

    // Shortcut supaya di seluruh MainActivity cukup panggil tr("key")
    // tanpa perlu tulis LocaleManager.tr(this, "key") berulang-ulang.
    private fun tr(key: String): String =
        LocaleManager.tr(this, key)

    // ============================================================
    // COLORS
    // ============================================================

    private val bg = Color.rgb(234, 241, 252)
    private val surface = Color.rgb(255, 255, 255)
    private val surface2 = Color.rgb(241, 245, 253)
    private val surface3 = Color.rgb(225, 234, 251)

    // Catatan: "white" sekarang berarti warna TEKS UTAMA (navy gelap).
    // Namanya dipertahankan supaya seluruh kode lain tetap valid.
    private val white = Color.rgb(20, 27, 61)
    private val muted = Color.rgb(112, 124, 152)

    private val green = Color.rgb(34, 190, 138)
    private val purple = Color.rgb(124, 92, 255)
    private val blue = Color.rgb(47, 91, 255)
    private val red = Color.rgb(240, 62, 94)
    private val orange = Color.rgb(255, 152, 31)

    // Warna garis tepi (stroke) untuk kolom form di dalam dialog/pop-up,
    // supaya setiap kolom isian punya batas yang jelas dan tidak
    // menyatu dengan background surface di sekitarnya.
    private val strokeColor = Color.rgb(210, 219, 238)

    private val categoryPalette = intArrayOf(
        Color.rgb(245, 196, 32),
        Color.rgb(240, 62, 94),
        Color.rgb(255, 122, 168),
        Color.rgb(140, 100, 255),
        Color.rgb(45, 170, 255),
        Color.rgb(255, 138, 42),
        Color.rgb(38, 190, 140),
        Color.rgb(132, 214, 64)
    )

    // ============================================================
    // API
    // ============================================================

    private val MODELS_URL =
        "https://generativelanguage.googleapis.com/v1beta/models"

    private val INTERACTIONS_URL =
        "https://generativelanguage.googleapis.com/v1/interactions"

    private val GENERATE_URL =
        "https://generativelanguage.googleapis.com/v1beta"

    // ============================================================
    // STORAGE
    // ============================================================

    private val prefs by lazy {
        getSharedPreferences("rekan_kelola_data", Context.MODE_PRIVATE)
    }

    private val handler = Handler(Looper.getMainLooper())

    // ============================================================
    // DATA
    // ============================================================

    data class Transaction(
        val id: Long,
        val type: String,
        val amount: Long,
        val category: String,
        val note: String,
        val date: String,
        val timestamp: Long
    )

    data class Target(
        val id: Long,
        val name: String,
        val targetAmount: Long,
        val savedAmount: Long,
        val deadline: String,
        val priority: String
    )

    // ============================================================
    // STATE
    // ============================================================

    private val transactions = mutableListOf<Transaction>()
    private val targets = mutableListOf<Target>()

    private var startingBalance = 0L

    private var savingPercent = 20
    private var transportPercent = 10
    private var entertainmentPercent = 10

    private var apiKey = ""
    private var selectedModel = "AUTO"

    private var interactionId: String? = null
    private var currentScreen = "home"

    // ============================================================
    // ROOT UI
    // ============================================================

    private lateinit var root: FrameLayout
    private lateinit var content: FrameLayout
    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView
    private lateinit var bottomBar: LinearLayout

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private val navIcons = mutableMapOf<String, NavIconView>()
    private val navDots = mutableMapOf<String, View>()

    private val categoryColors = mutableMapOf<String, Int>()

    private val dialogTheme =
        android.R.style.Theme_DeviceDefault_Light_Dialog_Alert

    // ============================================================
    // LIFECYCLE
    // ============================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg

        loadData()
        buildShell()
        showHome()

        // ---------- NOTIFIKASI ----------
        NotificationHelper.ensureChannels(this)
        if (!NotificationHelper.hasNotificationPermission(this)) {
            NotificationHelper.requestNotificationPermission(this)
        }
        // Menjadwalkan (atau menjadwalkan ulang) pengingat harian jam
        // 23:40 setiap kali aplikasi dibuka, supaya selalu up-to-date.
        ReminderScheduler.scheduleDailyReminder(this)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Tidak perlu logic khusus: kalau user menolak, notifikasi
        // otomatis tidak akan muncul (dicegah oleh hasNotificationPermission
        // di NotificationHelper), tanpa membuat aplikasi crash.
    }

    // ============================================================
    // SCAN STRUK (kamera + OCR offline)
    // ============================================================

    private val REQUEST_CODE_SCAN_RECEIPT = 801

    private fun launchReceiptScan() {
        val intent = Intent(this, ReceiptScanActivity::class.java)
        startActivityForResult(intent, REQUEST_CODE_SCAN_RECEIPT)
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != REQUEST_CODE_SCAN_RECEIPT) return
        if (resultCode != Activity.RESULT_OK || data == null) return

        val amount = if (data.hasExtra(ReceiptScanActivity.EXTRA_AMOUNT)) {
            data.getLongExtra(ReceiptScanActivity.EXTRA_AMOUNT, 0L)
        } else {
            null
        }
        val merchant = data.getStringExtra(ReceiptScanActivity.EXTRA_MERCHANT)

        if (amount == null && merchant == null) {
            Toast.makeText(
                this,
                "Tidak berhasil membaca nominal dari struk, silakan isi manual",
                Toast.LENGTH_LONG
            ).show()
            showTransactionDialog()
            return
        }

        // Bungkus hasil scan sebagai Transaction "draft" lalu lempar ke
        // dialog Tambah Transaksi yang sudah ada (reuse, mode pre-fill),
        // supaya user tetap bisa cek/koreksi sebelum benar-benar disimpan.
        val draft = Transaction(
            id = 0L,
            type = "expense",
            amount = amount ?: 0L,
            category = categoryOptionsFor("expense").firstOrNull { it == "Belanja" }
                ?: categoryOptionsFor("expense").firstOrNull()
                ?: "",
            note = merchant ?: "",
            date = today(),
            timestamp = System.currentTimeMillis()
        )

        Toast.makeText(
            this,
            "Struk berhasil dipindai, silakan cek hasilnya",
            Toast.LENGTH_SHORT
        ).show()

        showTransactionDialog(draft, isFromScan = true)
    }

    // ============================================================
    // SHELL
    // ============================================================

    private fun buildShell() {
        root = FrameLayout(this)
        root.setBackgroundColor(bg)

        val main = LinearLayout(this)
        main.orientation = LinearLayout.VERTICAL

        root.addView(
            main,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // ---------- HEADER ----------
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(22), dp(18), dp(18), dp(10))

        val titleBox = LinearLayout(this)
        titleBox.orientation = LinearLayout.VERTICAL

        titleView = text(
            "RekanKelola",
            26f,
            white,
            true
        )

        subtitleView = text(
            "Kelola keuangan dengan lebih tenang.",
            13f,
            muted,
            false
        )

        titleBox.addView(titleView)
        titleBox.addView(subtitleView)

        header.addView(
            titleBox,
            LinearLayout.LayoutParams(
                0,
                WRAP,
                1f
            )
        )

        val avatar = TextView(this)
        avatar.text = "⚙"
        avatar.textSize = 20f
        avatar.gravity = Gravity.CENTER
        avatar.setTextColor(Color.WHITE)
        avatar.contentDescription = "Pengaturan"

        val avatarBg = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.rgb(255, 150, 122),
                Color.rgb(240, 62, 94)
            )
        )
        avatarBg.cornerRadius = dp(16).toFloat()
        avatar.background = avatarBg
        avatar.elevation = dp(4).toFloat()

        avatar.setOnClickListener {
            showSettings()
        }

        addPressAnim(avatar)
        popIn(avatar, 200L)

        header.addView(
            avatar,
            LinearLayout.LayoutParams(
                dp(46),
                dp(46)
            )
        )

        main.addView(
            header,
            LinearLayout.LayoutParams(
                MATCH,
                WRAP
            )
        )

        // ---------- CONTENT ----------
        content = FrameLayout(this)

        main.addView(
            content,
            LinearLayout.LayoutParams(
                MATCH,
                0,
                1f
            )
        )

        // ---------- BOTTOM BAR ----------
        bottomBar = LinearLayout(this)
        bottomBar.orientation = LinearLayout.HORIZONTAL
        bottomBar.gravity = Gravity.CENTER
        bottomBar.setPadding(
            dp(8),
            dp(6),
            dp(8),
            dp(6)
        )
        bottomBar.background = rounded(surface, 28)
        bottomBar.elevation = dp(10).toFloat()

        bottomBar.addView(
            navItem("home", "Beranda"),
            LinearLayout.LayoutParams(0, dp(58), 1f)
        )

        bottomBar.addView(
            navItem("tx", "Transaksi"),
            LinearLayout.LayoutParams(0, dp(58), 1f)
        )

        // ruang kosong untuk tombol + di tengah
        bottomBar.addView(
            View(this),
            LinearLayout.LayoutParams(0, dp(58), 1f)
        )

        bottomBar.addView(
            navItem("target", "Target"),
            LinearLayout.LayoutParams(0, dp(58), 1f)
        )

        bottomBar.addView(
            navItem("ai", "RekanAI"),
            LinearLayout.LayoutParams(0, dp(58), 1f)
        )

        val bottomContainer = FrameLayout(this)
        bottomContainer.clipChildren = false
        bottomContainer.clipToPadding = false
        bottomContainer.setPadding(
            dp(14),
            dp(0),
            dp(14),
            dp(12)
        )

        bottomContainer.addView(
            bottomBar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(20)
                gravity = Gravity.BOTTOM
            }
        )

        val fab = TextView(this)
        fab.text = "+"
        fab.textSize = 30f
        fab.setIncludeFontPadding(false)
        fab.gravity = Gravity.CENTER
        fab.setTextColor(Color.WHITE)
        fab.contentDescription = "Tambah transaksi"

        val fabBg = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.rgb(90, 134, 255),
                blue
            )
        )
        fabBg.shape = GradientDrawable.OVAL
        fab.background = fabBg
        fab.elevation = dp(14).toFloat()

        fab.setOnClickListener {
            fab.animate()
                .setStartDelay(0L)
                .rotationBy(90f)
                .setDuration(240L)
                .start()
            showTransactionDialog()
        }

        fab.setOnLongClickListener {
            fab.animate()
                .setStartDelay(0L)
                .scaleX(1.15f)
                .scaleY(1.15f)
                .setDuration(120L)
                .withEndAction {
                    fab.scaleX = 1f
                    fab.scaleY = 1f
                }
                .start()
            launchReceiptScan()
            true
        }

        addPressAnim(fab)
        popIn(fab, 350L)

        bottomContainer.addView(
            fab,
            FrameLayout.LayoutParams(
                dp(58),
                dp(58)
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            }
        )

        bottomContainer.translationY = dp(100).toFloat()
        bottomContainer.post {
            bottomContainer.animate()
                .translationY(0f)
                .setStartDelay(150L)
                .setDuration(500L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        main.addView(
            bottomContainer,
            LinearLayout.LayoutParams(
                MATCH,
                WRAP
            )
        )

        setContentView(root)
        applyLightSystemBars()
    }

    @Suppress("DEPRECATION")
    private fun applyLightSystemBars() {
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            var flags = window.decorView.systemUiVisibility
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

            if (android.os.Build.VERSION.SDK_INT >= 26) {
                flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }

            window.decorView.systemUiVisibility = flags
        }
    }

    private fun navItem(kind: String, label: String): LinearLayout {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER
        box.contentDescription = label

        val icon = NavIconView(this, kind)
        icon.tint = muted

        box.addView(
            icon,
            LinearLayout.LayoutParams(
                dp(26),
                dp(26)
            )
        )

        val dot = View(this)
        dot.background = rounded(blue, 3)
        dot.alpha = 0f

        box.addView(
            dot,
            LinearLayout.LayoutParams(
                dp(5),
                dp(5)
            ).apply {
                topMargin = dp(6)
            }
        )

        navIcons[label] = icon
        navDots[label] = dot

        box.setOnClickListener {
            when (label) {
                "Beranda" -> showHome()
                "Transaksi" -> showTransactions()
                "Target" -> showTargets()
                "RekanAI" -> showAI()
                "Pengaturan" -> showSettings()
            }
        }

        addPressAnim(box)

        return box
    }

    private fun updateNav() {
        val active = when (currentScreen) {
            "home" -> "Beranda"
            "transactions" -> "Transaksi"
            "targets" -> "Target"
            "ai" -> "RekanAI"
            else -> ""
        }

        for ((label, icon) in navIcons) {
            val isActive = label == active
            val scale = if (isActive) 1.18f else 1f

            icon.tint = if (isActive) blue else muted
            icon.animate()
                .setStartDelay(0L)
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(220L)
                .start()

            val dot = navDots[label]

            if (dot != null) {
                dot.animate()
                    .setStartDelay(0L)
                    .alpha(if (isActive) 1f else 0f)
                    .setDuration(220L)
                    .start()
            }
        }
    }

    // ============================================================
    // ANIMATION HELPERS
    // ============================================================

    @Suppress("ClickableViewAccessibility")
    private fun addPressAnim(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .setStartDelay(0L)
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(100L)
                        .start()
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .setStartDelay(0L)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(180L)
                        .setInterpolator(OvershootInterpolator(2f))
                        .start()
                }
            }

            false
        }
    }

    private fun popIn(view: View, delay: Long) {
        view.scaleX = 0f
        view.scaleY = 0f

        view.post {
            view.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(delay)
                .setDuration(420L)
                .setInterpolator(OvershootInterpolator(1.6f))
                .start()
        }
    }

    private fun pulse(view: View) {
        val animator = ValueAnimator.ofFloat(0.45f, 1f)
        animator.duration = 750L
        animator.repeatMode = ValueAnimator.REVERSE
        animator.repeatCount = ValueAnimator.INFINITE
        animator.addUpdateListener {
            view.alpha = it.animatedValue as Float
        }

        view.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}

                override fun onViewDetachedFromWindow(v: View) {
                    animator.cancel()
                }
            }
        )

        animator.start()
    }

    private fun countUp(view: TextView, target: Long, delay: Long = 150L) {
        if (target == 0L) return

        view.text = formatRupiah(0L)

        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 900L
        animator.startDelay = delay
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener {
            val fraction = it.animatedValue as Float
            view.text = formatRupiah(
                (target.toDouble() * fraction.toDouble()).toLong()
            )
        }
        animator.start()
    }

    private fun showContent(view: View) {
        content.addView(view)

        val target: View =
            if (view is ScrollView && view.childCount > 0) {
                view.getChildAt(0)
            } else {
                view
            }

        if (target is ViewGroup) {
            for (i in 0 until target.childCount) {
                val child = target.getChildAt(i)

                child.alpha = 0f
                child.translationY = dp(22).toFloat()

                child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(minOf(i * 55L, 500L))
                    .setDuration(380L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }
    }

    // ============================================================
    // HOME
    // ============================================================

    private fun showHome() {
        currentScreen = "home"
        setHeader(
            tr("dashboard_title"),
            tr("dashboard_subtitle")
        )

        content.removeAllViews()

        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.isVerticalScrollBarEnabled = false

        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(
            dp(18),
            dp(6),
            dp(18),
            dp(24)
        )

        // ---------- DATA BULAN INI ----------
        val monthKey = today().substring(0, 7)

        val monthIncome = transactions
            .filter { it.type == "income" && it.date.startsWith(monthKey) }
            .sumOf { it.amount }

        val monthExpense = transactions
            .filter { it.type == "expense" && it.date.startsWith(monthKey) }
            .sumOf { it.amount }

        val dayOfMonth = max(
            1,
            Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        )

        val perDay = monthExpense / dayOfMonth.toLong()

        val spentPercent: Int =
            if (monthIncome > 0L) {
                min(100L, monthExpense * 100L / monthIncome).toInt()
            } else if (monthExpense > 0L) {
                100
            } else {
                0
            }

        // ---------- HERO ----------
        val hero = LinearLayout(this)
        hero.orientation = LinearLayout.VERTICAL
        hero.setPadding(
            dp(22),
            dp(20),
            dp(22),
            dp(20)
        )

        val heroBg = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.rgb(78, 126, 255),
                Color.rgb(47, 91, 255),
                Color.rgb(34, 68, 226)
            )
        )
        heroBg.cornerRadius = dp(26).toFloat()
        hero.background = heroBg
        hero.elevation = dp(10).toFloat()

        if (android.os.Build.VERSION.SDK_INT >= 28) {
            hero.outlineSpotShadowColor = Color.rgb(47, 91, 255)
            hero.outlineAmbientShadowColor = Color.rgb(47, 91, 255)
        }

        val month = SimpleDateFormat(
            "MMMM yyyy",
            Locale("id", "ID")
        ).format(Date())

        val monthLabel = month.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale("id", "ID"))
            else it.toString()
        }

        val heroTop = LinearLayout(this)
        heroTop.orientation = LinearLayout.HORIZONTAL
        heroTop.gravity = Gravity.CENTER_VERTICAL

        heroTop.addView(
            text(
                tr("dashboard_current_balance"),
                13f,
                Color.argb(215, 255, 255, 255),
                false
            ),
            LinearLayout.LayoutParams(
                0,
                WRAP,
                1f
            )
        )

        val monthChip = text(
            monthLabel,
            11f,
            Color.WHITE,
            true
        )
        monthChip.setPadding(dp(10), dp(4), dp(10), dp(4))
        monthChip.background = rounded(
            Color.argb(56, 255, 255, 255),
            12
        )

        heroTop.addView(monthChip)
        hero.addView(heroTop)

        val balanceText = text(
            formatRupiah(currentBalance()),
            34f,
            Color.WHITE,
            true
        )
        balanceText.setPadding(0, dp(10), 0, dp(14))
        hero.addView(balanceText)
        countUp(balanceText, currentBalance())

        val progressRow = LinearLayout(this)
        progressRow.orientation = LinearLayout.HORIZONTAL
        progressRow.gravity = Gravity.CENTER_VERTICAL

        val heroPill = PillProgress(this)
        heroPill.trackColor = Color.argb(70, 255, 255, 255)
        heroPill.fillColor = Color.WHITE
        heroPill.fraction = spentPercent / 100f

        progressRow.addView(
            heroPill,
            LinearLayout.LayoutParams(
                0,
                dp(7),
                1f
            )
        )

        progressRow.addView(
            text(
                "$spentPercent%",
                12f,
                Color.WHITE,
                true
            ).apply {
                setPadding(dp(12), 0, 0, 0)
            }
        )

        hero.addView(progressRow)

        hero.addView(
            text(
                tr("dashboard_month_expense_avg").format(formatRupiah(perDay)),
                12f,
                Color.argb(220, 255, 255, 255),
                false
            ).apply {
                setPadding(0, dp(10), 0, 0)
            }
        )

        column.addView(
            hero,
            LinearLayout.LayoutParams(
                MATCH,
                WRAP
            ).apply {
                bottomMargin = dp(16)
            }
        )

        // ---------- INCOME / EXPENSE ----------
        val stats = LinearLayout(this)
        stats.orientation = LinearLayout.HORIZONTAL

        stats.addView(
            statCard(
                tr("dashboard_income"),
                formatRupiah(totalIncome()),
                green,
                totalIncome()
            ),
            LinearLayout.LayoutParams(
                0,
                WRAP,
                1f
            ).apply {
                rightMargin = dp(7)
            }
        )

        stats.addView(
            statCard(
                tr("dashboard_expense"),
                formatRupiah(totalExpense()),
                red,
                totalExpense()
            ),
            LinearLayout.LayoutParams(
                0,
                WRAP,
                1f
            ).apply {
                leftMargin = dp(7)
            }
        )

        column.addView(stats)

        // Catatan: section budget per kategori (progress bar terpakai vs
        // alokasi) sudah ditampilkan di section "Alokasi bulanan" di
        // bawah -- tidak diduplikasi di sini supaya tidak tampil 2 kali
        // dalam satu layar yang sama.
        val budgetItems = computeBudgetItems()

        // ---------- TRANSAKSI TERBARU ----------
        column.addView(
            sectionTitle(
                tr("dashboard_recent_transactions"),
                tr("dashboard_recent_subtitle"),
                tr("dashboard_see_all"),
                { showTransactions() }
            ),
            sectionParams()
        )

        val recent = transactions
            .sortedByDescending { it.timestamp }
            .take(5)

        if (recent.isEmpty()) {
            column.addView(emptyState(tr("dashboard_no_transactions")))
        } else {
            val recentBox = LinearLayout(this)
            recentBox.orientation = LinearLayout.VERTICAL

            for (transaction in recent) {
                recentBox.addView(transactionRow(transaction))
            }

            column.addView(recentBox)
        }

        // ---------- TARGET (WISHLIST) ----------
        if (targets.isNotEmpty()) {
            column.addView(
                sectionTitle(
                    tr("dashboard_target_section"),
                    tr("dashboard_target_subtitle"),
                    tr("dashboard_see_all"),
                    { showTargets() }
                ),
                sectionParams()
            )

            val targetScroll = HorizontalScrollView(this)
            targetScroll.isHorizontalScrollBarEnabled = false
            targetScroll.overScrollMode = View.OVER_SCROLL_NEVER

            val targetRow = LinearLayout(this)
            targetRow.orientation = LinearLayout.HORIZONTAL

            val sortedTargets = targets.sortedBy { it.deadline }

            for (i in sortedTargets.indices) {
                targetRow.addView(
                    targetMiniCard(sortedTargets[i], i),
                    LinearLayout.LayoutParams(
                        dp(150),
                        WRAP
                    ).apply {
                        rightMargin = dp(12)
                    }
                )
            }

            targetScroll.addView(targetRow)
            column.addView(targetScroll)
        }

        // ---------- ANALITIK ----------
        column.addView(
            sectionTitle(
                "Analitik",
                "Pemasukan dan pengeluaran"
            ),
            sectionParams()
        )

        val analytics = LinearLayout(this)
        analytics.orientation = LinearLayout.VERTICAL
        analytics.setPadding(dp(16), dp(16), dp(16), dp(14))
        analytics.background = rounded(surface, 24)
        analytics.elevation = dp(3).toFloat()

        val chart = WeeklyChartView(this)
        chart.setData(transactions)

        val totalLabel = text("", 26f, white, true)
        val totalCaption = text("", 12f, muted, false)

        fun refreshTotals(mode: Int) {
            val caption = when (mode) {
                0 -> "7 hari terakhir"
                1 -> "6 bulan terakhir"
                else -> "4 tahun terakhir"
            }

            totalLabel.text = formatRupiah(chart.expenseTotal)
            totalCaption.text = "Total pengeluaran • $caption"
        }

        refreshTotals(0)

        val tabs = segmented(
            listOf("Minggu", "Bulan", "Tahun"),
            0
        ) { index ->
            chart.showMode(index)
            refreshTotals(index)
        }

        analytics.addView(
            tabs,
            LinearLayout.LayoutParams(
                MATCH,
                WRAP
            )
        )

        analytics.addView(
            totalLabel.apply {
                setPadding(0, dp(16), 0, 0)
            }
        )

        analytics.addView(totalCaption)

        analytics.addView(
            chart,
            LinearLayout.LayoutParams(
                MATCH,
                dp(190)
            ).apply {
                topMargin = dp(8)
            }
        )

        val legend = LinearLayout(this)
        legend.orientation = LinearLayout.HORIZONTAL
        legend.gravity = Gravity.CENTER

        legend.addView(legendItem("Pemasukan", green))
        legend.addView(
            legendItem("Pengeluaran", blue),
            LinearLayout.LayoutParams(
                WRAP,
                WRAP
            ).apply {
                leftMargin = dp(18)
            }
        )

        analytics.addView(
            legend,
            LinearLayout.LayoutParams(
                MATCH,
                WRAP
            ).apply {
                topMargin = dp(8)
            }
        )

        column.addView(analytics)

        // ---------- KATEGORI PENGELUARAN ----------
        column.addView(
            sectionTitle(
                "Kategori pengeluaran",
                "Ke mana uangmu pergi"
            ),
            sectionParams()
        )

        val expenseByCategory = transactions
            .filter { it.type == "expense" }
            .groupBy { it.category.trim().lowercase().ifBlank { "lainnya" } }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }

        val slices = ArrayList<Pair<String, Long>>()

        for (entry in expenseByCategory.take(5)) {
            slices.add(
                Pair(
                    entry.first.replaceFirstChar { it.uppercase() },
                    entry.second
                )
            )
        }

        val otherTotal = expenseByCategory
            .drop(5)
            .sumOf { it.second }

        if (otherTotal > 0L) {
            slices.add(Pair("Lainnya", otherTotal))
        }

        if (slices.isEmpty()) {
            column.addView(emptyState("Belum ada pengeluaran"))
        } else {
            val sliceTotal = slices.sumOf { it.second }

            val donutCard = LinearLayout(this)
            donutCard.orientation = LinearLayout.HORIZONTAL
            donutCard.gravity = Gravity.CENTER_VERTICAL
            donutCard.setPadding(dp(14), dp(16), dp(16), dp(16))
            donutCard.background = rounded(surface, 24)
            donutCard.elevation = dp(3).toFloat()

            val donut = DonutChartView(this)
            donut.setData(
                slices.map { it.second },
                IntArray(slices.size) { categoryColor(slices[it].first) },
                shortMoney(sliceTotal),
                "Pengeluaran"
            )

            donutCard.addView(
                donut,
                LinearLayout.LayoutParams(
                    dp(150),
                    dp(150)
                )
            )

            val donutLegend = LinearLayout(this)
            donutLegend.orientation = LinearLayout.VERTICAL

            for (slice in slices) {
                val pct = slice.second.toDouble() * 100.0 /
                    sliceTotal.toDouble()

                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL

                row.addView(
                    dot(categoryColor(slice.first)),
                    LinearLayout.LayoutParams(
                        dp(8),
                        dp(8)
                    ).apply {
                        rightMargin = dp(8)
                    }
                )

                val nameView = text(slice.first, 12f, white, false)
                nameView.setSingleLine(true)
                nameView.ellipsize = android.text.TextUtils.TruncateAt.END

                row.addView(
                    nameView,
                    LinearLayout.LayoutParams(
                        0,
                        WRAP,
                        1f
                    )
                )

                row.addView(
                    text(
                        String.format(Locale.US, "%.1f%%", pct),
                        11f,
                        muted,
                        true
                    ).apply {
                        setPadding(dp(6), 0, 0, 0)
                    }
                )

                donutLegend.addView(
                    row,
                    LinearLayout.LayoutParams(
                        MATCH,
                        dp(28)
                    )
                )
            }

            donutCard.addView(
                donutLegend,
                LinearLayout.LayoutParams(
                    0,
                    WRAP,
                    1f
                ).apply {
                    leftMargin = dp(14)
                }
            )

            column.addView(donutCard)
        }

        // ---------- ALOKASI ----------
        column.addView(
            sectionTitle(
                "Alokasi bulanan",
                "Atur pembagian uang sesuai kebiasaan kamu"
            ),
            sectionParams()
        )

        val allocation = LinearLayout(this)
        allocation.orientation = LinearLayout.VERTICAL
        allocation.background = rounded(surface, 24)
        allocation.elevation = dp(3).toFloat()
        allocation.setPadding(
            dp(18),
            dp(12),
            dp(18),
            dp(12)
        )

        for (item in budgetItems) {
            addBudgetProgressRow(allocation, item)
        }

        column.addView(allocation)

        scroll.addView(column)
        showContent(scroll)
    }

    private fun statCard(
        title: String,
        value: String,
        accent: Int,
        amount: Long? = null
    ): LinearLayout {

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(
            dp(16),
            dp(15),
            dp(16),
            dp(16)
        )
        card.background = rounded(surface, 22)
        card.elevation = dp(3).toFloat()

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL

        val badge = TextView(this)
        badge.text = if (accent == green) "↗" else "↘"
        badge.textSize = 15f
        badge.gravity = Gravity.CENTER
        badge.setTextColor(accent)
        badge.setTypeface(null, android.graphics.Typeface.BOLD)
        badge.background = rounded(
            blend(accent, surface, 0.14f),
            11
        )

        head.addView(
            badge,
            LinearLayout.LayoutParams(
                dp(32),
                dp(32)
            )
        )

        head.addView(
            text(
                title,
                12f,
                muted,
                false
            ).apply {
                setPadding(dp(10), 0, 0, 0)
            }
        )

        card.addView(head)

        val valueView = text(
            value,
            16f,
            white,
            true
        )
        valueView.setPadding(0, dp(12), 0, 0)

        card.addView(valueView)

        if (amount != null) {
            countUp(valueView, amount, 250L)
        }

        return card
    }

    /**
     * Dipanggil setelah transaksi baru tersimpan. Kalau transaksi itu
     * bikin salah satu kategori budget (transport/hiburan) baru saja
     * MELEWATI batasnya, kirim notif sekali. Pakai gate 6 jam yang sama
     * seperti InsightAnalyzer (lewat NotificationHelper) supaya tidak
     * spam kalau user nambah beberapa transaksi kecil beruntun di
     * kategori yang sudah habis.
     */
    private fun checkBudgetLimitNotification() {
        val prefs = getSharedPreferences(
            "rekan_kelola_insight_gate",
            Context.MODE_PRIVATE
        )

        val items = computeBudgetItems()

        for (item in items) {
            if (item.isSavingType || !item.isExhausted) continue

            val gateKey = "last_budget_exhausted_${item.key}"
            val lastSent = prefs.getLong(gateKey, 0L)
            val sixHoursMs = 6L * 60 * 60 * 1000

            if (System.currentTimeMillis() - lastSent < sixHoursMs) continue

            NotificationHelper.ensureChannels(this)
            NotificationHelper.sendNotification(
                context = this,
                channelId = NotificationHelper.CHANNEL_ID_INSIGHT,
                notifId = NotificationHelper.NOTIF_ID_SPENDING_INSIGHT,
                title = "RekanKelola",
                message = "Batas budget ${item.displayName.lowercase()} kamu sudah habis bulan ini"
            )

            prefs.edit().putLong(gateKey, System.currentTimeMillis()).apply()
        }
    }

    private fun currentYearMonth(): String {
        return today().substring(0, 7)
    }

    private fun computeBudgetItems(): List<BudgetTracker.BudgetItem> {
        return BudgetTracker.compute(
            currentBalance = currentBalance(),
            transactions = transactions,
            yearMonth = currentYearMonth(),
            savingPercent = savingPercent,
            transportPercent = transportPercent,
            entertainmentPercent = entertainmentPercent,
            colorSaving = green,
            colorTransport = blue,
            colorEntertainment = purple
        )
    }

    /**
     * Menampilkan progress bar berdasarkan berapa yang SUDAH TERPAKAI
     * dari budget bulan ini, bukan cuma persentase alokasinya. Berubah merah dan
     * menampilkan keterangan begitu limit tercapai.
     */
    private fun addBudgetProgressRow(
        parent: LinearLayout,
        item: BudgetTracker.BudgetItem
    ) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(0, dp(9), 0, dp(9))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL

        row.addView(
            text(
                item.displayName,
                13f,
                white,
                false
            ),
            LinearLayout.LayoutParams(
                0,
                WRAP,
                1f
            )
        )

        val rightLabel = if (item.isSavingType) {
            formatRupiah(item.allocated)
        } else {
            "${formatRupiah(item.used)} / ${formatRupiah(item.allocated)}"
        }

        row.addView(
            text(
                rightLabel,
                12f,
                if (item.isExhausted) red else item.color,
                true
            )
        )

        box.addView(row)

        val barColor = when {
            item.isSavingType -> item.color
            item.isExhausted -> red
            item.isNearLimit -> orange
            else -> item.color
        }

        val bar = PillProgress(this)
        bar.trackColor = surface3
        bar.fillColor = barColor
        bar.fraction = if (item.isSavingType) 1f else item.fraction

        box.addView(
            bar,
            LinearLayout.LayoutParams(
                MATCH,
                dp(6)
            ).apply {
                topMargin = dp(8)
            }
        )

        if (!item.isSavingType && item.isExhausted) {
            box.addView(
                text(
                    "Batas ${item.displayName.lowercase()} sudah habis",
                    11f,
                    red,
                    true
                ).apply {
                    setPadding(0, dp(6), 0, 0)
                }
            )
        } else if (!item.isSavingType && item.isNearLimit) {
            val remainingPercent = (100 - (item.fraction * 100).toInt())
            box.addView(
                text(
                    "Sisa $remainingPercent% dari budget ${item.displayName.lowercase()}",
                    11f,
                    orange,
                    false
                ).apply {
                    setPadding(0, dp(6), 0, 0)
                }
            )
        }

        parent.addView(box)
    }

    private fun targetPercent(target: Target): Int {
        if (target.targetAmount <= 0L) return 0

        return min(
            100,
            ((target.savedAmount.toDouble() /
                target.targetAmount.toDouble()) * 100).toInt()
        )
    }

    private fun targetMiniCard(
        target: Target,
        index: Int
    ): LinearLayout {

        val gradients = arrayOf(
            intArrayOf(Color.rgb(56, 214, 160), Color.rgb(24, 174, 124)),
            intArrayOf(Color.rgb(64, 178, 255), Color.rgb(36, 112, 255)),
            intArrayOf(Color.rgb(255, 126, 118), Color.rgb(238, 66, 92)),
            intArrayOf(Color.rgb(160, 122, 255), Color.rgb(112, 80, 240)),
            intArrayOf(Color.rgb(255, 184, 64), Color.rgb(255, 130, 30))
        )

        val percent = targetPercent(target)

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(
            dp(14),
            dp(14),
            dp(14),
            dp(14)
        )

        val cardBg = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            gradients[index % gradients.size]
        )
        cardBg.cornerRadius = dp(22).toFloat()
        card.background = cardBg

        val iconBadge = TextView(this)
        iconBadge.text = "🎯"
        iconBadge.textSize = 16f
        iconBadge.gravity = Gravity.CENTER
        iconBadge.setIncludeFontPadding(false)

        val iconBg = GradientDrawable()
        iconBg.shape = GradientDrawable.OVAL
        iconBg.setColor(Color.argb(60, 255, 255, 255))
        iconBadge.background = iconBg

        card.addView(
            iconBadge,
            LinearLayout.LayoutParams(
                dp(36),
                dp(36)
            )
        )

        val nameView = text(
            target.name,
            13f,
            Color.WHITE,
            true
        )
        nameView.setSingleLine(true)
        nameView.ellipsize = android.text.TextUtils.TruncateAt.END
        nameView.setPadding(0, dp(14), 0, dp(10))

        card.addView(nameView)

        val bottom = LinearLayout(this)
        bottom.orientation = LinearLayout.HORIZONTAL
        bottom.gravity = Gravity.CENTER_VERTICAL

        val pill = PillProgress(this)
        pill.trackColor = Color.argb(80, 255, 255, 255)
        pill.fillColor = Color.WHITE
        pill.fraction = percent / 100f

        bottom.addView(
            pill,
            LinearLayout.LayoutParams(
                0,
                dp(5),
                1f
            )
        )

        bottom.addView(
            text(
                "$percent%",
                11f,
                Color.WHITE,
                true
            ).apply {
                setPadding(dp(8), 0, 0, 0)
            }
        )

        card.addView(bottom)

        card.setOnClickListener {
            showTargets()
        }

        addPressAnim(card)

        return card
    }

    private fun legendItem(label: String, color: Int): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL

        row.addView(
            dot(color),
            LinearLayout.LayoutParams(
                dp(8),
                dp(8)
            ).apply {
                rightMargin = dp(6)
            }
        )

        row.addView(
            text(
                label,
                11f,
                muted,
                false
            )
        )

        return row
    }

    private fun dot(color: Int): View {
        val view = View(this)

        val shape = GradientDrawable()
        shape.shape = GradientDrawable.OVAL
        shape.setColor(color)

        view.background = shape

        return view
    }

    private fun segmented(
        options: List<String>,
        selected: Int,
        onSelect: (Int) -> Unit
    ): LinearLayout {

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.background = rounded(surface2, 16)
        bar.setPadding(dp(4), dp(4), dp(4), dp(4))

        val items = ArrayList<TextView>()

        fun paintSelection(index: Int) {
            for (i in items.indices) {
                val active = i == index

                items[i].setTextColor(
                    if (active) Color.WHITE else muted
                )

                items[i].background =
                    if (active) rounded(blue, 12) else null
            }
        }

        for ((index, label) in options.withIndex()) {
            val item = text(label, 13f, muted, true)
            item.gravity = Gravity.CENTER

            item.setOnClickListener {
                paintSelection(index)
                onSelect(index)
            }

            items.add(item)

            bar.addView(
                item,
                LinearLayout.LayoutParams(
                    0,
                    dp(34),
                    1f
                )
            )
        }

        paintSelection(selected)

        return bar
    }

    private fun sectionParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            MATCH,
            WRAP
        ).apply {
            topMargin = dp(22)
            bottomMargin = dp(10)
        }
    }

    private fun categoryColor(name: String): Int {
        val key = name.trim().lowercase()

        return categoryColors.getOrPut(key) {
            categoryPalette[categoryColors.size % categoryPalette.size]
        }
    }

    private fun categoryEmoji(category: String, type: String): String {
        val c = category.lowercase()

        return when {
            c.contains("makan") ||
                c.contains("minum") ||
                c.contains("food") ||
                c.contains("kopi") ||
                c.contains("kuliner") -> "🍔"

            c.contains("transport") ||
                c.contains("bensin") ||
                c.contains("ojek") ||
                c.contains("grab") ||
                c.contains("parkir") -> "🚗"

            c.contains("belanja") ||
                c.contains("shop") -> "🛍"

            c.contains("listrik") ||
                c.contains("tagihan") ||
                c.contains("internet") ||
                c.contains("wifi") ||
                c.contains("pulsa") -> "💡"

            c.contains("hibur") ||
                c.contains("game") ||
                c.contains("film") ||
                c.contains("nonton") -> "🎮"

            c.contains("sehat") ||
                c.contains("obat") ||
                c.contains("dokter") -> "💊"

            c.contains("gaji") ||
                c.contains("bonus") ||
                c.contains("income") -> "💰"

            c.contains("tabung") ||
                c.contains("invest") -> "🏦"

            c.contains("pendidikan") ||
                c.contains("sekolah") ||
                c.contains("kuliah") -> "🎓"

            type == "income" -> "↗"

            else -> "↘"
        }
    }

    private fun friendlyDate(date: String): String {
        if (date == today()) return "Hari ini"

        if (date.length >= 10) {
            val day = date.substring(8, 10).toIntOrNull()
            val month = date.substring(5, 7).toIntOrNull()

            if (day != null && month != null && month in 1..12) {
                val names = arrayOf(
                    "Jan", "Feb", "Mar", "Apr", "Mei", "Jun",
                    "Jul", "Agu", "Sep", "Okt", "Nov", "Des"
                )

                return "$day ${names[month - 1]}"
            }
        }

        return date
    }

    private fun shortMoney(value: Long): String {
        val negative = value < 0L
        val v = if (negative) -value else value

        val body = when {
            v >= 1_000_000_000L -> trimZero(v / 1_000_000_000.0) + "M"
            v >= 1_000_000L -> trimZero(v / 1_000_000.0) + "jt"
            v >= 1_000L -> trimZero(v / 1_000.0) + "rb"
            else -> v.toString()
        }

        return if (negative) "-$body" else body
    }

    private fun trimZero(value: Double): String {
        val s = String.format(Locale.US, "%.1f", value)

        return if (s.endsWith(".0")) s.dropLast(2) else s
    }

    // ============================================================
    // CUSTOM VIEWS
    // ============================================================

    private inner class NavIconView(
        context: Context,
        private val kind: String
    ) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        var tint: Int = muted
            set(value) {
                field = value
                invalidate()
            }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val w = width.toFloat()
            val h = height.toFloat()
            val s = min(w, h) / 24f

            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeWidth = 2f
            paint.color = tint

            canvas.save()
            canvas.translate((w - 24f * s) / 2f, (h - 24f * s) / 2f)
            canvas.scale(s, s)

            when (kind) {
                "home" -> {
                    val roof = Path()
                    roof.moveTo(3f, 11f)
                    roof.lineTo(12f, 3.5f)
                    roof.lineTo(21f, 11f)
                    canvas.drawPath(roof, paint)

                    val body = Path()
                    body.moveTo(5.5f, 9.5f)
                    body.lineTo(5.5f, 20f)
                    body.lineTo(18.5f, 20f)
                    body.lineTo(18.5f, 9.5f)
                    canvas.drawPath(body, paint)

                    val door = Path()
                    door.moveTo(10f, 20f)
                    door.lineTo(10f, 14.5f)
                    door.lineTo(14f, 14.5f)
                    door.lineTo(14f, 20f)
                    canvas.drawPath(door, paint)
                }

                "tx" -> {
                    val up = Path()
                    up.moveTo(8f, 19f)
                    up.lineTo(8f, 5f)
                    up.moveTo(4.5f, 8.5f)
                    up.lineTo(8f, 5f)
                    up.lineTo(11.5f, 8.5f)
                    canvas.drawPath(up, paint)

                    val down = Path()
                    down.moveTo(16f, 5f)
                    down.lineTo(16f, 19f)
                    down.moveTo(12.5f, 15.5f)
                    down.lineTo(16f, 19f)
                    down.lineTo(19.5f, 15.5f)
                    canvas.drawPath(down, paint)
                }

                "target" -> {
                    canvas.drawCircle(12f, 12f, 9f, paint)
                    canvas.drawCircle(12f, 12f, 5f, paint)

                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(12f, 12f, 1.6f, paint)
                }

                "ai" -> {
                    val spark = Path()
                    spark.moveTo(12f, 3f)
                    spark.quadTo(12.8f, 10.2f, 21f, 12f)
                    spark.quadTo(12.8f, 13.8f, 12f, 21f)
                    spark.quadTo(11.2f, 13.8f, 3f, 12f)
                    spark.quadTo(11.2f, 10.2f, 12f, 3f)
                    spark.close()
                    canvas.drawPath(spark, paint)
                }

                else -> {}
            }

            canvas.restore()
        }
    }

    private inner class PillProgress(
        context: Context
    ) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var shown = 0f

        var trackColor: Int = Color.LTGRAY
        var fillColor: Int = Color.BLUE
        var fraction: Float = 0f

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()

            val target = max(0f, min(1f, fraction))

            val animator = ValueAnimator.ofFloat(0f, target)
            animator.duration = 900L
            animator.startDelay = 250L
            animator.interpolator = DecelerateInterpolator()
            animator.addUpdateListener {
                shown = it.animatedValue as Float
                invalidate()
            }
            animator.start()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val w = width.toFloat()
            val h = height.toFloat()
            val r = h / 2f

            paint.style = Paint.Style.FILL
            paint.color = trackColor
            canvas.drawRoundRect(0f, 0f, w, h, r, r, paint)

            if (shown > 0.001f) {
                paint.color = fillColor
                val fillWidth = max(h, w * shown)
                canvas.drawRoundRect(0f, 0f, fillWidth, h, r, r, paint)
            }
        }
    }

    private inner class DonutChartView(
        context: Context
    ) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()

        private var values = emptyList<Long>()
        private var colors = IntArray(0)
        private var centerTitle = ""
        private var centerSub = ""
        private var progress = 0f
        private var animator: ValueAnimator? = null

        fun setData(
            newValues: List<Long>,
            newColors: IntArray,
            title: String,
            sub: String
        ) {
            values = newValues
            colors = newColors
            centerTitle = title
            centerSub = sub
            invalidate()
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()

            animator?.cancel()

            val a = ValueAnimator.ofFloat(0f, 1f)
            a.duration = 1000L
            a.startDelay = 200L
            a.interpolator = DecelerateInterpolator()
            a.addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }

            animator = a
            a.start()
        }

        override fun onDetachedFromWindow() {
            animator?.cancel()
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val w = width.toFloat()
            val h = height.toFloat()
            val stroke = dp(22).toFloat()
            val size = min(w, h) - stroke
            val cx = w / 2f
            val cy = h / 2f

            rect.set(
                cx - size / 2f,
                cy - size / 2f,
                cx + size / 2f,
                cy + size / 2f
            )

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            paint.strokeCap = Paint.Cap.BUTT
            paint.color = Color.rgb(236, 241, 250)
            canvas.drawArc(rect, 0f, 360f, false, paint)

            val total = values.sum()

            if (total > 0L && colors.isNotEmpty()) {
                val gap = if (values.size > 1) 2f else 0f
                val sweepLimit = 360f * progress

                var start = -90f
                var drawn = 0f

                for (i in values.indices) {
                    val full = 360f * values[i].toFloat() / total.toFloat()
                    val remaining = sweepLimit - drawn

                    if (remaining <= 0f) break

                    val sweep = min(full, remaining)

                    paint.color = colors[i % colors.size]
                    canvas.drawArc(
                        rect,
                        start + gap / 2f,
                        max(0f, sweep - gap),
                        false,
                        paint
                    )

                    start += full
                    drawn += full
                }
            }

            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER

            paint.color = white
            paint.textSize = dp(15).toFloat()
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            canvas.drawText(centerTitle, cx, cy + dp(2), paint)

            paint.color = muted
            paint.textSize = dp(10).toFloat()
            paint.typeface = android.graphics.Typeface.DEFAULT
            canvas.drawText(centerSub, cx, cy + dp(16), paint)
        }
    }

    // ============================================================
    // TRANSACTIONS
    // ============================================================

    private fun showTransactions() {
        currentScreen = "transactions"
        setHeader(
            "Transaksi",
            "Semua pemasukan dan pengeluaran"
        )

        content.removeAllViews()

        val scroll = ScrollView(this)

        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(
            dp(18),
            dp(6),
            dp(18),
            dp(25)
        )

        val addButton = button(
            "+ Tambah transaksi",
            blue
        )

        addButton.setOnClickListener {
            showTransactionDialog()
        }

        column.addView(
            addButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(54)
            ).apply {
                bottomMargin = dp(12)
            }
        )

        val search = EditText(this)
        search.hint = "Cari transaksi..."
        search.setHintTextColor(muted)
        search.setTextColor(white)
        search.textSize = 14f
        search.setSingleLine(true)
        search.setPadding(
            dp(16),
            0,
            dp(16),
            0
        )
        search.background = rounded(surface, 22)
        search.elevation = dp(2).toFloat()

        column.addView(
            search,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply {
                bottomMargin = dp(18)
            }
        )

        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL

        fun render(filter: String) {
            list.removeAllViews()

            val items = transactions
                .sortedByDescending { it.timestamp }
                .filter {
                    filter.isBlank() ||
                        it.note.contains(filter, true) ||
                        it.category.contains(filter, true)
                }

            if (items.isEmpty()) {
                list.addView(
                    emptyState(
                        if (filter.isBlank()) {
                            "Belum ada transaksi"
                        } else {
                            "Transaksi tidak ditemukan"
                        }
                    )
                )
            } else {
                for (transaction in items) {
                    list.addView(transactionRow(transaction))
                }
            }
        }

        render("")

        search.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {}

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    render(s?.toString() ?: "")
                }

                override fun afterTextChanged(
                    s: Editable?
                ) {}
            }
        )

        column.addView(list)

        scroll.addView(column)
        showContent(scroll)
    }

    private fun transactionRow(
        transaction: Transaction
    ): LinearLayout {

        val isIncome = transaction.type == "income"

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(
            dp(14),
            dp(13),
            dp(14),
            dp(13)
        )
        row.background = rounded(surface, 20)
        row.elevation = dp(2).toFloat()

        val accent =
            if (isIncome) green
            else red

        val tone = categoryColor(transaction.category)

        val icon = TextView(this)
        icon.text = categoryEmoji(
            transaction.category,
            transaction.type
        )
        icon.textSize = 20f
        icon.gravity = Gravity.CENTER
        icon.setIncludeFontPadding(false)
        icon.setTextColor(accent)
        icon.background = rounded(
            blend(tone, surface, 0.18f),
            15
        )

        row.addView(
            icon,
            LinearLayout.LayoutParams(
                dp(48),
                dp(48)
            )
        )

        val info = LinearLayout(this)
        info.orientation = LinearLayout.VERTICAL

        val nameView = text(
            transaction.note.ifBlank {
                transaction.category
            },
            14f,
            white,
            true
        )
        nameView.setSingleLine(true)
        nameView.ellipsize = android.text.TextUtils.TruncateAt.END

        info.addView(nameView)

        info.addView(
            text(
                if (transaction.note.isBlank()) {
                    if (isIncome) "Pemasukan" else "Pengeluaran"
                } else {
                    transaction.category
                },
                11f,
                muted,
                false
            ).apply {
                setPadding(0, dp(4), 0, 0)
            }
        )

        row.addView(
            info,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                leftMargin = dp(12)
                rightMargin = dp(8)
            }
        )

        val right = LinearLayout(this)
        right.orientation = LinearLayout.VERTICAL
        right.gravity = Gravity.END

        val amountView = text(
            (if (isIncome) "+ " else "- ") +
                formatRupiah(transaction.amount),
            13f,
            accent,
            true
        )
        // Nominal tidak boleh terpotong: satu baris dan tidak di-ellipsize.
        amountView.setSingleLine(true)
        amountView.ellipsize = null
        amountView.includeFontPadding = false

        right.addView(
            amountView,
            LinearLayout.LayoutParams(
                WRAP,
                WRAP
            )
        )

        val dateChip = text(
            friendlyDate(transaction.date),
            10f,
            muted,
            true
        )
        dateChip.setPadding(dp(8), dp(3), dp(8), dp(3))
        dateChip.background = rounded(surface3, 9)

        right.addView(
            dateChip,
            LinearLayout.LayoutParams(
                WRAP,
                WRAP
            ).apply {
                topMargin = dp(6)
                gravity = Gravity.END
            }
        )

        row.addView(
            right,
            LinearLayout.LayoutParams(
                WRAP,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        row.setOnLongClickListener {
            confirmDeleteTransaction(transaction)
            true
        }

        row.setOnClickListener {
            showTransactionDetail(transaction)
        }

        addPressAnim(row)

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        params.bottomMargin = dp(10)
        row.layoutParams = params

        return row
    }

    // Kategori bawaan per jenis transaksi. Kategori yang pernah dipakai
    // user akan otomatis ditambahkan juga supaya dropdown makin lengkap.
    private val defaultExpenseCategories = listOf(
        "Makan", "Transport", "Belanja", "Tagihan",
        "Kesehatan", "Hiburan", "Pendidikan", "Lainnya"
    )

    private val defaultIncomeCategories = listOf(
        "Gaji", "Bonus", "Hadiah", "Investasi", "Lainnya"
    )

    private val CUSTOM_CATEGORY_OPTION = "+ Kategori baru"

    private fun categoryOptionsFor(type: String): List<String> {
        val defaults =
            if (type == "income")
                defaultIncomeCategories
            else
                defaultExpenseCategories

        val used = transactions
            .filter { it.type == type }
            .map { it.category }
            .filter { it.isNotBlank() }

        val merged = (used + defaults).distinct().toMutableList()
        merged.remove(CUSTOM_CATEGORY_OPTION)
        merged.add(CUSTOM_CATEGORY_OPTION)

        return merged
    }

    private fun fieldLabel(label: String): TextView {
        val v = text(label.uppercase(), 11f, blend(muted, white, 0.35f), true)
        v.letterSpacing = 0.03f
        v.setPadding(dp(2), 0, 0, 0)
        return v
    }

    private fun segmentButton(label: String): TextView {
        val b = TextView(this)
        b.text = label
        b.textSize = 13f
        b.gravity = Gravity.CENTER
        b.setTypeface(null, android.graphics.Typeface.BOLD)
        addPressAnim(b)
        return b
    }

    private fun styledSpinnerAdapter(
        items: List<String>
    ): ArrayAdapter<String> {

        return object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            items
        ) {
            override fun getView(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {
                val v = (convertView as? TextView) ?: TextView(this@MainActivity)
                v.text = getItem(position)
                v.setTextColor(white)
                v.textSize = 14f
                v.setSingleLine(true)
                v.ellipsize = android.text.TextUtils.TruncateAt.END
                v.setPadding(dp(14), dp(13), dp(14), dp(13))
                v.gravity = Gravity.CENTER_VERTICAL
                v.minHeight = dp(48)
                val bg = GradientDrawable()
                bg.setColor(surface2)
                bg.cornerRadius = dp(14).toFloat()
                bg.setStroke(dp(1), strokeColor)
                v.background = bg
                return v
            }

            override fun getDropDownView(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {
                val v = (convertView as? TextView) ?: TextView(this@MainActivity)
                v.text = getItem(position)
                v.setTextColor(
                    if (getItem(position) == CUSTOM_CATEGORY_OPTION) blue else white
                )
                v.setBackgroundColor(surface)
                v.textSize = 14f
                v.setPadding(dp(16), dp(14), dp(16), dp(14))
                return v
            }
        }
    }

    private fun showTransactionDialog(
        editing: Transaction? = null,
        isFromScan: Boolean = false
    ) {

        val isEditing = editing != null && !isFromScan

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(
            dp(22),
            dp(8),
            dp(22),
            dp(6)
        )

        // ---------- JENIS TRANSAKSI (segmented toggle) ----------
        layout.addView(fieldLabel("Jenis transaksi"))
        layout.addView(space(6))

        var selectedType = editing?.type ?: "expense"

        val typeRow = LinearLayout(this)
        typeRow.orientation = LinearLayout.HORIZONTAL
        typeRow.background = GradientDrawable().apply {
            setColor(surface2)
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), strokeColor)
        }
        typeRow.setPadding(dp(4), dp(4), dp(4), dp(4))

        val incomeBtn = segmentButton("Pemasukan")
        val expenseBtn = segmentButton("Pengeluaran")

        typeRow.addView(
            incomeBtn,
            LinearLayout.LayoutParams(0, dp(42), 1f)
        )
        typeRow.addView(
            expenseBtn,
            LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                leftMargin = dp(4)
            }
        )

        layout.addView(typeRow)
        layout.addView(space(16))

        // ---------- NOMINAL ----------
        layout.addView(fieldLabel("Nominal"))
        layout.addView(space(6))

        val amount = input(
            "Nominal",
            "Contoh: 150000"
        )
        amount.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        editing?.let { amount.setText(it.amount.toString()) }

        layout.addView(amount)
        layout.addView(space(16))

        // ---------- KATEGORI (dropdown) ----------
        layout.addView(fieldLabel("Kategori"))
        layout.addView(space(6))

        val categorySpinner = Spinner(this)
        val customCategoryInput = input(
            "Nama kategori baru",
            "Contoh: Obat"
        )
        customCategoryInput.visibility = View.GONE

        fun applyCategoryAdapter(preSelect: String?) {
            val options = categoryOptionsFor(selectedType)
            categorySpinner.adapter = styledSpinnerAdapter(options)

            val idx = if (preSelect != null) options.indexOf(preSelect) else -1

            when {
                idx >= 0 -> {
                    categorySpinner.setSelection(idx)
                    customCategoryInput.visibility = View.GONE
                }
                preSelect != null && preSelect.isNotBlank() -> {
                    val customIdx = options.indexOf(CUSTOM_CATEGORY_OPTION)
                    categorySpinner.setSelection(max(0, customIdx))
                    customCategoryInput.setText(preSelect)
                    customCategoryInput.visibility = View.VISIBLE
                }
                else -> {
                    categorySpinner.setSelection(0)
                    customCategoryInput.visibility = View.GONE
                }
            }
        }

        categorySpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val chosen = categorySpinner.adapter.getItem(position) as String
                    customCategoryInput.visibility =
                        if (chosen == CUSTOM_CATEGORY_OPTION) View.VISIBLE else View.GONE
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

        fun refreshTypeButtons() {
            if (selectedType == "income") {
                incomeBtn.background = rounded(green, 12)
                incomeBtn.setTextColor(Color.WHITE)
                expenseBtn.background = rounded(surface2, 12)
                expenseBtn.setTextColor(muted)
            } else {
                expenseBtn.background = rounded(red, 12)
                expenseBtn.setTextColor(Color.WHITE)
                incomeBtn.background = rounded(surface2, 12)
                incomeBtn.setTextColor(muted)
            }
        }

        incomeBtn.setOnClickListener {
            if (selectedType != "income") {
                selectedType = "income"
                refreshTypeButtons()
                applyCategoryAdapter(null)
            }
        }

        expenseBtn.setOnClickListener {
            if (selectedType != "expense") {
                selectedType = "expense"
                refreshTypeButtons()
                applyCategoryAdapter(null)
            }
        }

        refreshTypeButtons()
        applyCategoryAdapter(editing?.category)

        layout.addView(categorySpinner)
        layout.addView(space(8))
        layout.addView(customCategoryInput)
        layout.addView(space(16))

        // ---------- CATATAN ----------
        layout.addView(fieldLabel("Catatan (opsional)"))
        layout.addView(space(6))

        val note = input(
            "Catatan",
            "Contoh: Makan siang"
        )
        editing?.let { note.setText(it.note) }

        layout.addView(note)

        val scrollWrap = ScrollView(this)
        scrollWrap.isVerticalScrollBarEnabled = false
        scrollWrap.addView(layout)

        val dialog = AlertDialog.Builder(this, dialogTheme)
            .setTitle(if (isEditing) "Edit transaksi" else "Tambah transaksi")
            .setView(scrollWrap)
            .setNegativeButton("Batal", null)
            .setPositiveButton(
                if (isEditing) "Simpan perubahan" else "Simpan",
                null
            )
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {

                    val value = parseMoney(
                        amount.text.toString()
                    )

                    if (value <= 0) {
                        amount.error = "Nominal tidak valid"
                        return@setOnClickListener
                    }

                    val chosenOption =
                        categorySpinner.selectedItem as? String ?: ""

                    val cat =
                        if (chosenOption == CUSTOM_CATEGORY_OPTION) {
                            customCategoryInput.text.toString().trim()
                        } else {
                            chosenOption
                        }

                    if (cat.isBlank()) {
                        customCategoryInput.error = "Kategori wajib diisi"
                        return@setOnClickListener
                    }

                    if (isEditing && editing != null) {
                        val idx = transactions.indexOfFirst {
                            it.id == editing.id
                        }

                        if (idx >= 0) {
                            transactions[idx] = editing.copy(
                                type = selectedType,
                                amount = value,
                                category = cat,
                                note = note.text.toString().trim()
                            )
                        }
                    } else {
                        val newTx = Transaction(
                            id = System.currentTimeMillis(),
                            type = selectedType,
                            amount = value,
                            category = cat,
                            note = note.text.toString().trim(),
                            date = today(),
                            timestamp = System.currentTimeMillis()
                        )
                        transactions.add(newTx)

                        InsightAnalyzer.evaluateAfterNewTransaction(
                            context = this,
                            newTransaction = newTx,
                            allTransactions = transactions,
                            startingBalance = startingBalance
                        )

                        checkBudgetLimitNotification()
                    }

                    saveData()
                    dialog.dismiss()

                    when (currentScreen) {
                        "home" -> showHome()
                        else -> showTransactions()
                    }
                }
        }

        dialog.show()
    }

    private fun showTransactionDetail(
        transaction: Transaction
    ) {
        val type =
            if (transaction.type == "income")
                "Pemasukan"
            else
                "Pengeluaran"

        AlertDialog.Builder(this, dialogTheme)
            .setTitle(transaction.note.ifBlank {
                transaction.category
            })
            .setMessage(
                "Jenis: $type\n" +
                    "Nominal: ${formatRupiah(transaction.amount)}\n" +
                    "Kategori: ${transaction.category}\n" +
                    "Tanggal: ${transaction.date}"
            )
            .setNegativeButton("Tutup", null)
            .setNeutralButton("Edit") { _, _ ->
                showTransactionDialog(transaction)
            }
            .setPositiveButton("Hapus") { _, _ ->
                confirmDeleteTransaction(transaction)
            }
            .show()
    }

    private fun confirmDeleteTransaction(
        transaction: Transaction
    ) {
        AlertDialog.Builder(this, dialogTheme)
            .setTitle("Hapus transaksi?")
            .setMessage(
                "Transaksi ${formatRupiah(transaction.amount)} akan dihapus."
            )
            .setNegativeButton("Batal", null)
            .setPositiveButton("Hapus") { _, _ ->
                transactions.removeAll {
                    it.id == transaction.id
                }
                saveData()

                when (currentScreen) {
                    "home" -> showHome()
                    "transactions" -> showTransactions()
                }
            }
            .show()
    }

    // ============================================================
    // TARGET
    // ============================================================

    private fun showTargets() {
        currentScreen = "targets"
        setHeader(
            "Target",
            "Bangun tujuan keuangan kamu"
        )

        content.removeAllViews()

        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false

        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(
            dp(18),
            dp(6),
            dp(18),
            dp(25)
        )

        val totalTarget = targets.sumOf {
            it.targetAmount
        }

        val totalSaved = targets.sumOf {
            it.savedAmount
        }

        val totalPercent =
            if (totalTarget > 0L) {
                min(100L, totalSaved * 100L / totalTarget).toInt()
            } else {
                0
            }

        val summary = LinearLayout(this)
        summary.orientation = LinearLayout.VERTICAL
        summary.setPadding(
            dp(22),
            dp(20),
            dp(22),
            dp(20)
        )

        val summaryBg = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.rgb(140, 104, 255),
                Color.rgb(96, 84, 250),
                Color.rgb(47, 91, 255)
            )
        )
        summaryBg.cornerRadius = dp(26).toFloat()
        summary.background = summaryBg
        summary.elevation = dp(10).toFloat()

        if (android.os.Build.VERSION.SDK_INT >= 28) {
            summary.outlineSpotShadowColor = Color.rgb(96, 84, 250)
            summary.outlineAmbientShadowColor = Color.rgb(96, 84, 250)
        }

        summary.addView(
            text(
                "Total tabungan target",
                13f,
                Color.argb(215, 255, 255, 255),
                false
            )
        )

        val savedText = text(
            formatRupiah(totalSaved),
            30f,
            Color.WHITE,
            true
        )
        savedText.setPadding(0, dp(8), 0, dp(12))

        summary.addView(savedText)
        countUp(savedText, totalSaved)

        val summaryRow = LinearLayout(this)
        summaryRow.orientation = LinearLayout.HORIZONTAL
        summaryRow.gravity = Gravity.CENTER_VERTICAL

        val summaryPill = PillProgress(this)
        summaryPill.trackColor = Color.argb(70, 255, 255, 255)
        summaryPill.fillColor = Color.WHITE
        summaryPill.fraction = totalPercent / 100f

        summaryRow.addView(
            summaryPill,
            LinearLayout.LayoutParams(
                0,
                dp(7),
                1f
            )
        )

        summaryRow.addView(
            text(
                "$totalPercent%",
                12f,
                Color.WHITE,
                true
            ).apply {
                setPadding(dp(12), 0, 0, 0)
            }
        )

        summary.addView(summaryRow)

        summary.addView(
            text(
                "dari ${formatRupiah(totalTarget)} target",
                12f,
                Color.argb(220, 255, 255, 255),
                false
            ).apply {
                setPadding(0, dp(10), 0, 0)
            }
        )

        column.addView(
            summary,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(14)
            }
        )

        val add = button(
            "+ Tambah target",
            purple
        )

        add.setOnClickListener {
            showTargetDialog()
        }

        column.addView(
            add,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(54)
            ).apply {
                bottomMargin = dp(18)
            }
        )

        if (targets.isEmpty()) {
            column.addView(
                emptyState(
                    "Belum ada target tabungan"
                )
            )
        } else {
            for (target in targets.sortedBy {
                it.deadline
            }) {
                column.addView(
                    targetCard(target)
                )
            }
        }

        scroll.addView(column)
        showContent(scroll)
    }

    private fun targetCard(
        target: Target
    ): LinearLayout {

        val percent = targetPercent(target)

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(
            dp(18),
            dp(17),
            dp(18),
            dp(17)
        )
        card.background = rounded(surface, 24)
        card.elevation = dp(3).toFloat()

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL

        val badge = TextView(this)
        badge.text = "🎯"
        badge.textSize = 18f
        badge.gravity = Gravity.CENTER
        badge.setIncludeFontPadding(false)
        badge.background = rounded(
            blend(purple, surface, 0.14f),
            14
        )

        top.addView(
            badge,
            LinearLayout.LayoutParams(
                dp(44),
                dp(44)
            )
        )

        val titleBox = LinearLayout(this)
        titleBox.orientation = LinearLayout.VERTICAL

        val title = text(
            target.name,
            16f,
            white,
            true
        )
        title.setSingleLine(true)
        title.ellipsize = android.text.TextUtils.TruncateAt.END

        titleBox.addView(title)

        titleBox.addView(
            text(
                target.priority,
                11f,
                purple,
                true
            ).apply {
                setPadding(0, dp(3), 0, 0)
            }
        )

        top.addView(
            titleBox,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                leftMargin = dp(12)
                rightMargin = dp(8)
            }
        )

        top.addView(
            text(
                "$percent%",
                18f,
                if (percent >= 100) green else blue,
                true
            )
        )

        card.addView(top)

        card.addView(
            text(
                "${formatRupiah(target.savedAmount)} / ${
                    formatRupiah(target.targetAmount)
                }",
                13f,
                muted,
                false
            ).apply {
                setPadding(0, dp(14), 0, dp(10))
            }
        )

        val progress = PillProgress(this)
        progress.trackColor = surface3
        progress.fillColor = if (percent >= 100) green else blue
        progress.fraction = percent / 100f

        card.addView(
            progress,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(8)
            )
        )

        card.addView(
            text(
                "Deadline ${target.deadline}",
                11f,
                muted,
                false
            ).apply {
                setPadding(0, dp(10), 0, dp(12))
            }
        )

        val actions = LinearLayout(this)
        actions.orientation = LinearLayout.HORIZONTAL

        val save = button(
            "+ Tabung",
            green
        )

        save.setOnClickListener {
            showAddSavingDialog(target)
        }

        actions.addView(
            save,
            LinearLayout.LayoutParams(
                0,
                dp(45),
                1f
            ).apply {
                rightMargin = dp(5)
            }
        )

        val delete = button(
            "Hapus",
            red
        )

        delete.setOnClickListener {
            AlertDialog.Builder(this, dialogTheme)
                .setTitle("Hapus target?")
                .setMessage(target.name)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Hapus") { _, _ ->
                    targets.removeAll {
                        it.id == target.id
                    }
                    saveData()
                    showTargets()
                }
                .show()
        }

        actions.addView(
            delete,
            LinearLayout.LayoutParams(
                0,
                dp(45),
                1f
            ).apply {
                leftMargin = dp(5)
            }
        )

        card.addView(actions)

        card.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(12)
        }

        return card
    }

    private fun showTargetDialog() {

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(
            dp(22),
            dp(5),
            dp(22),
            dp(5)
        )

        val name = input(
            "Nama target",
            "Contoh: Laptop"
        )

        val targetAmount = input(
            "Nominal target",
            "Contoh: 10000000"
        )
        targetAmount.inputType = android.text.InputType.TYPE_CLASS_NUMBER

        val savedAmount = input(
            "Sudah ditabung",
            "Contoh: 1500000"
        )
        savedAmount.inputType = android.text.InputType.TYPE_CLASS_NUMBER

        val deadline = input(
            "Deadline",
            "Contoh: 2027-01-01"
        )

        val priority = Spinner(this)

        priority.adapter = styledSpinnerAdapter(
            listOf(
                "Prioritas tinggi",
                "Prioritas sedang",
                "Prioritas rendah"
            )
        )

        layout.addView(fieldLabel("Nama target"))
        layout.addView(space(6))
        layout.addView(name)
        layout.addView(space(16))

        layout.addView(fieldLabel("Nominal target"))
        layout.addView(space(6))
        layout.addView(targetAmount)
        layout.addView(space(16))

        layout.addView(fieldLabel("Sudah ditabung (opsional)"))
        layout.addView(space(6))
        layout.addView(savedAmount)
        layout.addView(space(16))

        layout.addView(fieldLabel("Deadline (opsional)"))
        layout.addView(space(6))
        layout.addView(deadline)
        layout.addView(space(16))

        layout.addView(fieldLabel("Prioritas"))
        layout.addView(space(6))
        layout.addView(priority)

        val dialog = AlertDialog.Builder(this, dialogTheme)
            .setTitle("Tambah target")
            .setView(layout)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Simpan", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {

                    val target = parseMoney(
                        targetAmount.text.toString()
                    )

                    val saved = parseMoney(
                        savedAmount.text.toString()
                    )

                    if (name.text.toString().trim().isBlank()) {
                        name.error = "Nama wajib diisi"
                        return@setOnClickListener
                    }

                    if (target <= 0) {
                        targetAmount.error = "Nominal tidak valid"
                        return@setOnClickListener
                    }

                    if (saved < 0 || saved > target) {
                        savedAmount.error =
                            "Nominal tabungan tidak valid"
                        return@setOnClickListener
                    }

                    targets.add(
                        Target(
                            id = System.currentTimeMillis(),
                            name = name.text.toString().trim(),
                            targetAmount = target,
                            savedAmount = saved,
                            deadline = deadline.text.toString().trim()
                                .ifBlank { "-" },
                            priority = priority.selectedItem.toString()
                        )
                    )

                    saveData()
                    dialog.dismiss()
                    showTargets()
                }
        }

        dialog.show()
    }

    private fun showAddSavingDialog(
        target: Target
    ) {

        val amount = input(
            "Nominal tabungan",
            "Contoh: 500000"
        )
        amount.inputType = android.text.InputType.TYPE_CLASS_NUMBER

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(dp(22), dp(5), dp(22), dp(5))
        layout.addView(fieldLabel("Nominal tabungan"))
        layout.addView(space(6))
        layout.addView(amount)

        AlertDialog.Builder(this, dialogTheme)
            .setTitle("Tambah tabungan")
            .setView(layout)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Simpan") { _, _ ->

                val value = parseMoney(
                    amount.text.toString()
                )

                if (value <= 0) {
                    Toast.makeText(
                        this,
                        "Nominal tidak valid",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                val index = targets.indexOfFirst {
                    it.id == target.id
                }

                if (index >= 0) {
                    val newSaved = min(
                        target.targetAmount,
                        target.savedAmount + value
                    )

                    targets[index] =
                        target.copy(
                            savedAmount = newSaved
                        )

                    saveData()
                    showTargets()
                }
            }
            .show()
    }

    // ============================================================
    // AI
    // ============================================================

    private fun showAI() {
        currentScreen = "ai"
        setHeader(
            "RekanAI",
            "Asisten keuangan pribadi"
        )

        content.removeAllViews()

        val main = LinearLayout(this)
        main.orientation = LinearLayout.VERTICAL
        main.setPadding(
            dp(14),
            dp(4),
            dp(14),
            dp(10)
        )

        val messages = LinearLayout(this)
        messages.orientation = LinearLayout.VERTICAL

        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.addView(messages)

        main.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        if (apiKey.isBlank()) {
            messages.addView(
                aiBubble(
                    "Halo 👋\n\n" +
                        "Saya RekanAI. Untuk mulai menggunakan AI, " +
                        "masukkan Gemini API Key di Pengaturan.",
                    false
                )
            )
        } else {
            messages.addView(
                aiBubble(
                    "Halo 👋\n\n" +
                        "Saya bisa membantu membaca kondisi keuangan, " +
                        "menganalisis pengeluaran, membuat rencana " +
                        "tabungan, atau mencatat transaksi dari percakapan.",
                    false
                )
            )
        }

        val suggestions = LinearLayout(this)
        suggestions.orientation = LinearLayout.HORIZONTAL

        val suggestionTexts = arrayOf(
            "Analisis keuangan",
            "Buat rencana",
            "Catat transaksi"
        )

        for (suggestion in suggestionTexts) {
            val b = button(
                suggestion,
                surface3
            )

            b.setOnClickListener {
                inputField.setText(suggestion)
                inputField.setSelection(
                    inputField.text.length
                )
            }

            suggestions.addView(
                b,
                LinearLayout.LayoutParams(
                    0,
                    dp(44),
                    1f
                ).apply {
                    leftMargin = dp(3)
                    rightMargin = dp(3)
                }
            )
        }

        main.addView(
            suggestions,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
            ).apply {
                bottomMargin = dp(7)
            }
        )

        val composer = LinearLayout(this)
        composer.orientation = LinearLayout.HORIZONTAL
        composer.gravity = Gravity.CENTER_VERTICAL
        composer.background = rounded(surface, 28)
        composer.elevation = dp(6).toFloat()
        composer.setPadding(
            dp(8),
            dp(5),
            dp(8),
            dp(5)
        )

        inputField = EditText(this)
        inputField.hint = "Tanya RekanAI..."
        inputField.setHintTextColor(muted)
        inputField.setTextColor(white)
        inputField.textSize = 14f
        inputField.setSingleLine(false)
        inputField.maxLines = 4
        inputField.setPadding(
            dp(10),
            dp(5),
            dp(8),
            dp(5)
        )
        inputField.background = null

        composer.addView(
            inputField,
            LinearLayout.LayoutParams(
                0,
                dp(50),
                1f
            )
        )

        val send = button(
            "↑",
            blue
        )

        val sendBg = GradientDrawable()
        sendBg.shape = GradientDrawable.OVAL
        sendBg.setColor(blue)
        send.background = sendBg
        send.textSize = 20f

        send.setOnClickListener {
            val message =
                inputField.text.toString().trim()

            if (message.isBlank()) return@setOnClickListener

            if (apiKey.isBlank()) {
                Toast.makeText(
                    this,
                    "Masukkan Gemini API Key di Pengaturan.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            inputField.setText("")

            messages.addView(
                aiBubble(message, true)
            )

            val loading = aiBubble(
                "RekanAI sedang berpikir...",
                false,
                false
            )

            messages.addView(loading)
            pulse(loading)

            scroll.post {
                scroll.fullScroll(View.FOCUS_DOWN)
            }

            send.isEnabled = false

            thread {
                val result = askAI(message)

                handler.post {
                    messages.removeView(loading)

                    if (result.success) {
                        messages.addView(
                            aiBubble(
                                result.reply,
                                false
                            )
                        )

                        for (tx in result.transactions) {
                            transactions.add(tx)

                            InsightAnalyzer.evaluateAfterNewTransaction(
                                context = this@MainActivity,
                                newTransaction = tx,
                                allTransactions = transactions,
                                startingBalance = startingBalance
                            )
                        }

                        if (result.transactions.isNotEmpty()) {
                            checkBudgetLimitNotification()
                            saveData()

                            messages.addView(
                                aiBubble(
                                    "✓ ${result.transactions.size} transaksi berhasil dicatat.",
                                    false
                                )
                            )
                        }
                    } else {
                        messages.addView(
                            aiBubble(
                                "Maaf, terjadi masalah:\n\n${result.reply}",
                                false
                            )
                        )
                    }

                    send.isEnabled = true

                    scroll.post {
                        scroll.fullScroll(
                            View.FOCUS_DOWN
                        )
                    }
                }
            }
        }

        composer.addView(
            send,
            LinearLayout.LayoutParams(
                dp(50),
                dp(50)
            )
        )

        main.addView(
            composer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(62)
            )
        )

        showContent(main)
    }

    private lateinit var inputField: EditText

    data class AIResult(
        val success: Boolean,
        val reply: String,
        val transactions: List<Transaction>
    )

    private fun aiBubble(
        message: String,
        user: Boolean,
        animate: Boolean = true
    ): TextView {

        val view = text(
            message,
            14f,
            if (user) Color.WHITE else white,
            false
        )

        view.setPadding(
            dp(15),
            dp(12),
            dp(15),
            dp(12)
        )

        val bubble = GradientDrawable()
        bubble.setColor(if (user) blue else surface)

        val big = dp(18).toFloat()
        val small = dp(5).toFloat()

        bubble.setCornerRadii(
            if (user) {
                floatArrayOf(big, big, big, big, small, small, big, big)
            } else {
                floatArrayOf(big, big, big, big, big, big, small, small)
            }
        )

        view.background = bubble

        if (!user) {
            view.elevation = dp(2).toFloat()
        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        params.topMargin = dp(5)
        params.bottomMargin = dp(5)

        if (user) {
            params.gravity = Gravity.END
            params.leftMargin = dp(55)
        } else {
            params.gravity = Gravity.START
            params.rightMargin = dp(35)
        }

        view.layoutParams = params

        if (animate) {
            view.alpha = 0f
            view.translationY = dp(14).toFloat()

            view.post {
                view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(320L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }

        return view
    }

    private fun askAI(
        userMessage: String
    ): AIResult {

        return try {

            val modelCandidates = resolveModelCandidates()

            if (modelCandidates.isEmpty()) {
                return AIResult(
                    false,
                    "Tidak ada model AI yang tersedia untuk API key ini. Coba periksa API key kamu di pengaturan.",
                    emptyList()
                )
            }

            val prompt = buildAIPrompt(
                userMessage
            )

            var lastError = "Gagal menghubungi AI."

            // Coba tiap kandidat model satu per satu. Begitu ada yang
            // berhasil, langsung dipakai dan berhenti. Kalau satu model
            // ternyata sudah tidak tersedia/deprecated di sisi Google
            // (seperti error "no longer available to new users"),
            // otomatis lanjut coba model berikutnya alih-alih gagal total.
            for (model in modelCandidates) {

                var interactionResult =
                    callInteractions(
                        model,
                        prompt
                    )

                if (!interactionResult.first) {
                    interactionId = null

                    interactionResult =
                        callGenerateContent(
                            model,
                            prompt
                        )
                }

                if (interactionResult.first) {
                    return parseAIResponse(
                        interactionResult.second
                    )
                }

                lastError = interactionResult.second

                // Kalau errornya jelas soal model tidak tersedia/deprecated,
                // lanjut ke kandidat berikutnya. Kalau errornya soal hal
                // lain (misal API key salah, tidak ada koneksi), tidak ada
                // gunanya coba model lain -- error yang sama akan terjadi
                // lagi, jadi langsung berhenti dan laporkan.
                val looksLikeModelIssue =
                    lastError.contains("not found", ignoreCase = true) ||
                    lastError.contains("no longer available", ignoreCase = true) ||
                    lastError.contains("not supported", ignoreCase = true) ||
                    lastError.contains("deprecated", ignoreCase = true) ||
                    lastError.contains("404")

                if (!looksLikeModelIssue) {
                    break
                }
            }

            AIResult(
                false,
                lastError,
                emptyList()
            )

        } catch (e: Exception) {
            AIResult(
                false,
                e.message ?: "Gagal menghubungi AI.",
                emptyList()
            )
        }
    }

    private fun buildAIPrompt(
        userMessage: String
    ): String {

        val recent = transactions
            .sortedByDescending {
                it.timestamp
            }
            .take(20)

        val transactionText = if (recent.isEmpty()) {
            "Belum ada transaksi."
        } else {
            recent.joinToString("\n") {
                "- ${it.date} | ${it.type} | " +
                    "${it.category} | " +
                    formatRupiah(it.amount) +
                    " | ${it.note}"
            }
        }

        val targetText = if (targets.isEmpty()) {
            "Belum ada target."
        } else {
            targets.joinToString("\n") {
                "- ${it.name}: " +
                    "${formatRupiah(it.savedAmount)} / " +
                    "${formatRupiah(it.targetAmount)} | " +
                    "deadline ${it.deadline}"
            }
        }

        return """
Kamu adalah RekanAI, asisten keuangan pribadi di aplikasi RekanKelola.

Gaya jawaban:
- Bahasa Indonesia.
- Ramah, jelas, tidak menggurui.
- Gunakan angka rupiah yang mudah dibaca.
- Fokus pada data pengguna.
- Jangan mengarang transaksi yang tidak diberikan.
- Jika pengguna meminta pencatatan transaksi, buat transaksi yang jelas dari pesannya.
- Jika informasi transaksi tidak cukup jelas, jangan membuat transaksi.

KONDISI KEUANGAN:
Saldo awal: ${formatRupiah(startingBalance)}
Saldo saat ini: ${formatRupiah(currentBalance())}
Total pemasukan: ${formatRupiah(totalIncome())}
Total pengeluaran: ${formatRupiah(totalExpense())}

ALOKASI:
Tabungan: $savingPercent%
Transport: $transportPercent%
Hiburan: $entertainmentPercent%

TRANSAKSI TERBARU:
$transactionText

TARGET:
$targetText

BALAS HANYA DENGAN JSON VALID BERBENTUK:

{
  "reply": "jawaban untuk pengguna",
  "transactions": [
    {
      "type": "income atau expense",
      "amount": 0,
      "category": "kategori",
      "note": "catatan"
    }
  ]
}

Jika tidak ada transaksi baru, gunakan:
"transactions": []

Jangan gunakan markdown.
Jangan gunakan ```json.
Nominal harus berupa angka integer tanpa titik atau koma.

PERTANYAAN PENGGUNA:
$userMessage
""".trimIndent()
    }

    /**
     * Mengembalikan daftar model yang akan DICOBA berurutan, bukan cuma
     * satu nama model tetap. Kalau user sudah pilih model tertentu di
     * pengaturan, itu dicoba duluan -- tapi tetap ada fallback ke hasil
     * discovery kalau ternyata model pilihan itu gagal (misal sudah
     * di-deprecate Google). Tidak ada lagi nama model yang di-hardcode
     * sebagai fallback terakhir, karena nama model apa pun bisa berubah
     * status ketersediaannya sewaktu-waktu di sisi Google.
     */
    private fun resolveModelCandidates(): List<String> {

        val ordered = mutableListOf<String>()

        if (
            selectedModel.isNotBlank() &&
            selectedModel != "AUTO"
        ) {
            ordered.add(
                selectedModel.removePrefix("models/")
            )
        }

        ordered.addAll(discoverModelCandidates())

        return ordered.distinct()
    }

    private fun discoverModelCandidates(): List<String> {

        val result = httpGet(
            MODELS_URL,
            apiKey
        )

        if (!result.first) {
            return emptyList()
        }

        return try {

            val root = JSONObject(
                result.second
            )

            val array =
                root.optJSONArray("models")
                    ?: return emptyList()

            val candidates =
                mutableListOf<String>()

            for (i in 0 until array.length()) {

                val obj =
                    array.optJSONObject(i)
                        ?: continue

                val name =
                    obj.optString("name")
                        .removePrefix("models/")

                val actions =
                    obj.optJSONArray(
                        "supportedActions"
                    )

                val oldActions =
                    obj.optJSONArray(
                        "supportedGenerationMethods"
                    )

                var supports = false

                if (actions != null) {
                    for (j in 0 until actions.length()) {
                        if (
                            actions.optString(j) ==
                            "generateContent"
                        ) {
                            supports = true
                        }
                    }
                }

                if (oldActions != null) {
                    for (j in 0 until oldActions.length()) {
                        if (
                            oldActions.optString(j) ==
                            "generateContent"
                        ) {
                            supports = true
                        }
                    }
                }

                if (
                    supports &&
                    name.startsWith("gemini-") &&
                    !name.contains("image") &&
                    !name.contains("tts") &&
                    !name.contains("audio")
                ) {
                    candidates.add(name)
                }
            }

            // Semua kandidat dikembalikan (bukan cuma satu), terurut dari
            // yang paling mungkin ringan/cepat/murah duluan. Kalau
            // kandidat pertama ternyata sudah deprecated di sisi Google
            // (walau masih muncul di endpoint listing /models), caller
            // akan mencoba kandidat berikutnya secara otomatis, bukan
            // langsung gagal total seperti sebelumnya.
            candidates.distinct().sortedWith(
                compareBy<String> {
                    when {
                        it.contains("flash") &&
                            it.contains("lite") -> 0

                        it.contains("flash") -> 1

                        it.contains("pro") -> 2

                        else -> 3
                    }
                }.thenByDescending {
                    // Nomor versi lebih tinggi (mis. 3.5 > 2.5) diprioritaskan
                    // duluan, supaya model versi lama yang sudah dihentikan
                    // Google (seperti kasus 2.5-flash-lite) otomatis dilewati
                    // lebih dulu daripada versi terbarunya.
                    Regex("(\\d+)\\.(\\d+)")
                        .find(it)
                        ?.value
                        ?.toDoubleOrNull()
                        ?: 0.0
                }
            )

        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun callInteractions(
        model: String,
        prompt: String
    ): Pair<Boolean, String> {

        return try {

            val body = JSONObject()

            body.put(
                "model",
                model
            )

            body.put(
                "input",
                prompt
            )

            body.put(
                "system_instruction",
                "You are RekanAI, a personal finance assistant."
            )

            body.put(
                "store",
                true
            )

            if (!interactionId.isNullOrBlank()) {
                body.put(
                    "previous_interaction_id",
                    interactionId
                )
            }

            val result = httpPost(
                INTERACTIONS_URL,
                apiKey,
                body.toString()
            )

            if (!result.first) {
                return result
            }

            val json = JSONObject(
                result.second
            )

            val id =
                json.optString("id")

            if (id.isNotBlank()) {
                interactionId = id
            }

            val output =
                extractInteractionText(json)

            if (output.isBlank()) {
                Pair(
                    false,
                    "AI mengembalikan respons kosong."
                )
            } else {
                Pair(true, output)
            }

        } catch (e: Exception) {
            Pair(
                false,
                e.message ?: "Interactions API gagal."
            )
        }
    }

    private fun extractInteractionText(
        json: JSONObject
    ): String {

        val direct =
            json.optString("output_text")

        if (direct.isNotBlank()) {
            return direct
        }

        val steps =
            json.optJSONArray("steps")
                ?: return ""

        val output = StringBuilder()

        for (i in 0 until steps.length()) {

            val step =
                steps.optJSONObject(i)
                    ?: continue

            val type =
                step.optString("type")

            if (
                type != "model_output" &&
                type != "output"
            ) {
                continue
            }

            val content =
                step.optJSONArray("content")
                    ?: continue

            for (j in 0 until content.length()) {

                val item =
                    content.optJSONObject(j)
                        ?: continue

                if (
                    item.optString("type") ==
                    "text"
                ) {
                    output.append(
                        item.optString("text")
                    )
                }
            }
        }

        return output.toString().trim()
    }

    private fun callGenerateContent(
        model: String,
        prompt: String
    ): Pair<Boolean, String> {

        return try {

            val url =
                "$GENERATE_URL/models/$model:generateContent"

            val body = JSONObject()

            val contents = JSONArray()

            val content = JSONObject()
            content.put("role", "user")

            val requestParts = JSONArray()

            val part = JSONObject()
            part.put(
                "text",
                prompt
            )

            requestParts.put(part)
            content.put("parts", requestParts)
            contents.put(content)

            body.put(
                "contents",
                contents
            )

            val systemInstruction =
                JSONObject()

            val systemParts =
                JSONArray()

            val systemPart =
                JSONObject()

            systemPart.put(
                "text",
                "You are RekanAI, a personal finance assistant."
            )

            systemParts.put(systemPart)

            systemInstruction.put(
                "parts",
                systemParts
            )

            body.put(
                "systemInstruction",
                systemInstruction
            )

            val result = httpPost(
                url,
                apiKey,
                body.toString()
            )

            if (!result.first) {
                return result
            }

            val json =
                JSONObject(result.second)

            val candidates =
                json.optJSONArray("candidates")
                    ?: return Pair(
                        false,
                        "Respons AI tidak memiliki candidates."
                    )

            if (candidates.length() == 0) {
                return Pair(
                    false,
                    "AI tidak memberikan jawaban."
                )
            }

            val first =
                candidates.optJSONObject(0)
                    ?: return Pair(
                        false,
                        "Respons AI tidak valid."
                    )

            val contentObj =
                first.optJSONObject("content")
                    ?: return Pair(
                        false,
                        "Konten AI kosong."
                    )

            val parts =
                contentObj.optJSONArray("parts")
                    ?: return Pair(
                        false,
                        "Bagian respons AI kosong."
                    )

            val output =
                StringBuilder()

            for (i in 0 until parts.length()) {
                val p =
                    parts.optJSONObject(i)

                if (p != null) {
                    output.append(
                        p.optString("text")
                    )
                }
            }

            val text =
                output.toString().trim()

            if (text.isBlank()) {
                Pair(
                    false,
                    "AI mengembalikan jawaban kosong."
                )
            } else {
                Pair(true, text)
            }

        } catch (e: Exception) {
            Pair(
                false,
                e.message ?: "GenerateContent gagal."
            )
        }
    }

    private fun parseAIResponse(
        raw: String
    ): AIResult {

        return try {

            var clean = raw.trim()

            if (clean.startsWith("```")) {
                clean = clean
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()
            }

            val start = clean.indexOf("{")
            val end = clean.lastIndexOf("}")

            if (start < 0 || end <= start) {
                return AIResult(
                    true,
                    clean,
                    emptyList()
                )
            }

            val json = JSONObject(
                clean.substring(
                    start,
                    end + 1
                )
            )

            val reply =
                json.optString(
                    "reply",
                    "Selesai."
                )

            val array =
                json.optJSONArray(
                    "transactions"
                )

            val result =
                mutableListOf<Transaction>()

            if (array != null) {

                for (i in 0 until array.length()) {

                    val item =
                        array.optJSONObject(i)
                            ?: continue

                    val type =
                        item.optString("type")
                            .lowercase(Locale.US)

                    val amount =
                        item.optLong(
                            "amount",
                            0L
                        )

                    if (
                        type != "income" &&
                        type != "expense"
                    ) {
                        continue
                    }

                    if (amount <= 0) {
                        continue
                    }

                    val category =
                        item.optString(
                            "category",
                            "Lainnya"
                        ).trim()

                    val note =
                        item.optString(
                            "note",
                            ""
                        ).trim()

                    result.add(
                        Transaction(
                            id = System.nanoTime() +
                                i.toLong(),
                            type = type,
                            amount = amount,
                            category =
                                category.ifBlank {
                                    "Lainnya"
                                },
                            note = note,
                            date = today(),
                            timestamp =
                                System.currentTimeMillis() +
                                    i
                        )
                    )
                }
            }

            AIResult(
                true,
                reply.ifBlank {
                    "Selesai."
                },
                result
            )

        } catch (_: Exception) {

            AIResult(
                true,
                raw,
                emptyList()
            )
        }
    }

    // ============================================================
    // SETTINGS
    // ============================================================

    private fun showSettings() {
        currentScreen = "settings"
        setHeader(
            "Pengaturan",
            "Atur RekanKelola sesuai kebutuhan"
        )

        content.removeAllViews()

        val scroll = ScrollView(this)

        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(
            dp(18),
            dp(6),
            dp(18),
            dp(30)
        )

        column.addView(
            sectionTitle(
                tr("settings_language"),
                tr("settings_language_subtitle")
            )
        )

        column.addView(languageSettingCard())

        column.addView(
            sectionTitle(
                "Keuangan",
                "Nilai dasar aplikasi"
            )
        )

        val starting =
            input(
                "Saldo awal",
                "Contoh: 5000000"
            )

        starting.setText(
            startingBalance.toString()
        )

        column.addView(
            settingCard(
                "Saldo awal",
                starting
            )
        )

        val saving =
            input(
                "Persentase tabungan",
                "Contoh: 20"
            )

        saving.setText(
            savingPercent.toString()
        )

        column.addView(
            settingCard(
                "Tabungan (%)",
                saving
            )
        )

        val transport =
            input(
                "Persentase transport",
                "Contoh: 10"
            )

        transport.setText(
            transportPercent.toString()
        )

        column.addView(
            settingCard(
                "Transport (%)",
                transport
            )
        )

        val entertainment =
            input(
                "Persentase hiburan",
                "Contoh: 10"
            )

        entertainment.setText(
            entertainmentPercent.toString()
        )

        column.addView(
            settingCard(
                "Hiburan (%)",
                entertainment
            )
        )

        val saveFinance = button(
            "Simpan pengaturan keuangan",
            green
        )

        saveFinance.setOnClickListener {

            startingBalance =
                parseMoney(
                    starting.text.toString()
                )

            savingPercent =
                parsePercent(
                    saving.text.toString()
                )

            transportPercent =
                parsePercent(
                    transport.text.toString()
                )

            entertainmentPercent =
                parsePercent(
                    entertainment.text.toString()
                )

            saveData()

            Toast.makeText(
                this,
                "Pengaturan disimpan.",
                Toast.LENGTH_SHORT
            ).show()
        }

        column.addView(
            saveFinance,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply {
                topMargin = dp(8)
                bottomMargin = dp(25)
            }
        )

        column.addView(
            sectionTitle(
                "RekanAI",
                "Hubungkan Gemini API"
            )
        )

        val keyInput =
            input(
                "Gemini API Key",
                "Masukkan API key"
            )

        keyInput.inputType =
            android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD

        keyInput.setText(apiKey)

        column.addView(
            settingCard(
                "API Key",
                keyInput
            )
        )

        val model =
            input(
                "Model",
                "AUTO atau nama model"
            )

        model.setText(
            selectedModel
        )

        column.addView(
            settingCard(
                "Model Gemini",
                model
            )
        )

        val saveAI = button(
            "Simpan konfigurasi AI",
            purple
        )

        saveAI.setOnClickListener {

            apiKey =
                keyInput.text.toString().trim()

            selectedModel =
                model.text.toString()
                    .trim()
                    .ifBlank {
                        "AUTO"
                    }

            interactionId = null

            saveData()

            Toast.makeText(
                this,
                "Konfigurasi AI disimpan.",
                Toast.LENGTH_SHORT
            ).show()
        }

        column.addView(
            saveAI,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply {
                topMargin = dp(8)
                bottomMargin = dp(25)
            }
        )

        column.addView(
            sectionTitle(
                "Data",
                "Backup dan pemulihan"
            )
        )

        val backup = button(
            "Salin backup JSON",
            blue
        )

        backup.setOnClickListener {
            copyBackup()
        }

        column.addView(
            backup,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
            ).apply {
                bottomMargin = dp(8)
            }
        )

        val restore = button(
            "Pulihkan dari clipboard",
            orange
        )

        restore.setOnClickListener {
            restoreBackup()
        }

        column.addView(
            restore,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
            ).apply {
                bottomMargin = dp(8)
            }
        )

        val reset = button(
            "Reset semua data",
            red
        )

        reset.setOnClickListener {
            confirmReset()
        }

        column.addView(
            reset,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
            )
        )

        column.addView(
            text(
                "\nRekanKelola V1\nData tersimpan secara lokal di perangkat.",
                11f,
                muted,
                false
            ).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(22), 0, 0)
            }
        )

        scroll.addView(column)
        showContent(scroll)
    }

    // ============================================================
    // BAHASA (Settings card + dialog pemilihan)
    // ============================================================

    private fun languageSettingCard(): LinearLayout {

        val card = LinearLayout(this)
        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.CENTER_VERTICAL
        card.setPadding(
            dp(15),
            dp(14),
            dp(15),
            dp(14)
        )
        card.background = rounded(surface, 22)
        card.elevation = dp(2).toFloat()
        card.isClickable = true
        card.isFocusable = true

        val currentLangCode = LocaleManager.getCurrentLanguage(this)

        val label = text(
            Strings.displayNameFor(currentLangCode),
            14f,
            white,
            true
        )
        label.tag = "language_label"

        card.addView(
            label,
            LinearLayout.LayoutParams(
                0,
                WRAP,
                1f
            )
        )

        card.addView(
            text(
                "›",
                18f,
                muted,
                true
            )
        )

        card.layoutParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
                bottomMargin = dp(6)
            }

        card.setOnClickListener {
            showLanguagePickerDialog(label)
        }

        return card
    }

    private fun showLanguagePickerDialog(labelToUpdate: TextView) {

        val currentCode = LocaleManager.getCurrentLanguage(this)
        val codes = Strings.SUPPORTED_LANGUAGES
        val names = codes.map { Strings.displayNameFor(it) }.toTypedArray()
        val currentIndex = codes.indexOf(currentCode).coerceAtLeast(0)

        AlertDialog.Builder(this, dialogTheme)
            .setTitle(tr("settings_language"))
            .setSingleChoiceItems(names, currentIndex) { dialog, which ->
                val chosen = codes[which]

                if (chosen != currentCode) {
                    LocaleManager.setLanguage(this, chosen)

                    // Restart activity supaya seluruh layar (termasuk
                    // attachBaseContext dan semua teks yang sudah
                    // dimigrasi ke tr()) langsung memakai bahasa baru.
                    val intent = intent
                    finish()
                    startActivity(intent)
                    overridePendingTransition(0, 0)
                } else {
                    labelToUpdate.text = Strings.displayNameFor(chosen)
                }

                dialog.dismiss()
            }
            .setNegativeButton(tr("cancel"), null)
            .show()
    }

    private fun settingCard(
        title: String,
        field: EditText
    ): LinearLayout {

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(
            dp(15),
            dp(12),
            dp(15),
            dp(12)
        )
        card.background = rounded(surface, 22)
        card.elevation = dp(2).toFloat()

        card.addView(
            text(
                title,
                11f,
                muted,
                false
            )
        )

        card.addView(
            field,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            )
        )

        card.layoutParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
                bottomMargin = dp(6)
            }

        return card
    }

    private fun copyBackup() {

        val json = createBackup()

        val clipboard =
            getSystemService(
                Context.CLIPBOARD_SERVICE
            ) as android.content.ClipboardManager

        clipboard.setPrimaryClip(
            android.content.ClipData.newPlainText(
                "RekanKelola Backup",
                json
            )
        )

        Toast.makeText(
            this,
            "Backup JSON disalin.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun restoreBackup() {

        val clipboard =
            getSystemService(
                Context.CLIPBOARD_SERVICE
            ) as android.content.ClipboardManager

        if (!clipboard.hasPrimaryClip()) {
            Toast.makeText(
                this,
                "Clipboard kosong.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val text =
            clipboard.primaryClip
                ?.getItemAt(0)
                ?.coerceToText(this)
                ?.toString()
                ?: ""

        if (text.isBlank()) {
            Toast.makeText(
                this,
                "Backup tidak ditemukan.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        try {
            restoreFromJson(text)

            Toast.makeText(
                this,
                "Data berhasil dipulihkan.",
                Toast.LENGTH_SHORT
            ).show()

            showHome()

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Backup tidak valid.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun confirmReset() {

        AlertDialog.Builder(this, dialogTheme)
            .setTitle("Reset semua data?")
            .setMessage(
                "Semua transaksi, target, dan pengaturan lokal akan dihapus."
            )
            .setNegativeButton("Batal", null)
            .setPositiveButton("Reset") { _, _ ->

                prefs.edit().clear().apply()

                transactions.clear()
                targets.clear()

                startingBalance = 0L
                savingPercent = 20
                transportPercent = 10
                entertainmentPercent = 10
                apiKey = ""
                selectedModel = "AUTO"
                interactionId = null

                showHome()

                Toast.makeText(
                    this,
                    "Data telah direset.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .show()
    }

    // ============================================================
    // DATA STORAGE
    // ============================================================

    private fun loadData() {

        startingBalance =
            prefs.getLong(
                "starting_balance",
                0L
            )

        savingPercent =
            prefs.getInt(
                "saving_percent",
                20
            )

        transportPercent =
            prefs.getInt(
                "transport_percent",
                10
            )

        entertainmentPercent =
            prefs.getInt(
                "entertainment_percent",
                10
            )

        apiKey =
            prefs.getString(
                "api_key",
                ""
            ) ?: ""

        selectedModel =
            prefs.getString(
                "model",
                "AUTO"
            ) ?: "AUTO"

        transactions.clear()
        targets.clear()

        try {

            val transactionJson =
                prefs.getString(
                    "transactions",
                    "[]"
                ) ?: "[]"

            val arr =
                JSONArray(transactionJson)

            for (i in 0 until arr.length()) {

                val o =
                    arr.optJSONObject(i)
                        ?: continue

                transactions.add(
                    Transaction(
                        id = o.optLong("id"),
                        type = o.optString("type"),
                        amount = o.optLong("amount"),
                        category =
                            o.optString("category"),
                        note =
                            o.optString("note"),
                        date =
                            o.optString("date"),
                        timestamp =
                            o.optLong("timestamp")
                    )
                )
            }

            val targetJson =
                prefs.getString(
                    "targets",
                    "[]"
                ) ?: "[]"

            val targetArr =
                JSONArray(targetJson)

            for (i in 0 until targetArr.length()) {

                val o =
                    targetArr.optJSONObject(i)
                        ?: continue

                targets.add(
                    Target(
                        id = o.optLong("id"),
                        name =
                            o.optString("name"),
                        targetAmount =
                            o.optLong("targetAmount"),
                        savedAmount =
                            o.optLong("savedAmount"),
                        deadline =
                            o.optString("deadline"),
                        priority =
                            o.optString("priority")
                    )
                )
            }

        } catch (_: Exception) {
            transactions.clear()
            targets.clear()
        }
    }

    private fun saveData() {

        val transactionArr =
            JSONArray()

        for (tx in transactions) {

            val o = JSONObject()

            o.put("id", tx.id)
            o.put("type", tx.type)
            o.put("amount", tx.amount)
            o.put("category", tx.category)
            o.put("note", tx.note)
            o.put("date", tx.date)
            o.put("timestamp", tx.timestamp)

            transactionArr.put(o)
        }

        val targetArr =
            JSONArray()

        for (target in targets) {

            val o = JSONObject()

            o.put("id", target.id)
            o.put("name", target.name)
            o.put(
                "targetAmount",
                target.targetAmount
            )
            o.put(
                "savedAmount",
                target.savedAmount
            )
            o.put(
                "deadline",
                target.deadline
            )
            o.put(
                "priority",
                target.priority
            )

            targetArr.put(o)
        }

        prefs.edit()
            .putLong(
                "starting_balance",
                startingBalance
            )
            .putInt(
                "saving_percent",
                savingPercent
            )
            .putInt(
                "transport_percent",
                transportPercent
            )
            .putInt(
                "entertainment_percent",
                entertainmentPercent
            )
            .putString(
                "api_key",
                apiKey
            )
            .putString(
                "model",
                selectedModel
            )
            .putString(
                "transactions",
                transactionArr.toString()
            )
            .putString(
                "targets",
                targetArr.toString()
            )
            .apply()
    }

    private fun createBackup(): String {

        val root =
            JSONObject()

        root.put(
            "version",
            1
        )

        root.put(
            "startingBalance",
            startingBalance
        )

        root.put(
            "savingPercent",
            savingPercent
        )

        root.put(
            "transportPercent",
            transportPercent
        )

        root.put(
            "entertainmentPercent",
            entertainmentPercent
        )

        root.put(
            "apiKey",
            apiKey
        )

        root.put(
            "model",
            selectedModel
        )

        val txArray =
            JSONArray()

        for (tx in transactions) {

            val o = JSONObject()

            o.put("id", tx.id)
            o.put("type", tx.type)
            o.put("amount", tx.amount)
            o.put("category", tx.category)
            o.put("note", tx.note)
            o.put("date", tx.date)
            o.put("timestamp", tx.timestamp)

            txArray.put(o)
        }

        root.put(
            "transactions",
            txArray
        )

        val targetArray =
            JSONArray()

        for (target in targets) {

            val o = JSONObject()

            o.put("id", target.id)
            o.put("name", target.name)
            o.put(
                "targetAmount",
                target.targetAmount
            )
            o.put(
                "savedAmount",
                target.savedAmount
            )
            o.put(
                "deadline",
                target.deadline
            )
            o.put(
                "priority",
                target.priority
            )

            targetArray.put(o)
        }

        root.put(
            "targets",
            targetArray
        )

        return root.toString(2)
    }

    private fun restoreFromJson(
        jsonText: String
    ) {

        val root =
            JSONObject(jsonText)

        startingBalance =
            root.optLong(
                "startingBalance",
                0L
            )

        savingPercent =
            parsePercent(
                root.optString(
                    "savingPercent",
                    "20"
                )
            )

        transportPercent =
            parsePercent(
                root.optString(
                    "transportPercent",
                    "10"
                )
            )

        entertainmentPercent =
            parsePercent(
                root.optString(
                    "entertainmentPercent",
                    "10"
                )
            )

        apiKey =
            root.optString(
                "apiKey",
                ""
            )

        selectedModel =
            root.optString(
                "model",
                "AUTO"
            )

        transactions.clear()

        val txArray =
            root.optJSONArray(
                "transactions"
            )

        if (txArray != null) {

            for (i in 0 until txArray.length()) {

                val o =
                    txArray.optJSONObject(i)
                        ?: continue

                transactions.add(
                    Transaction(
                        id = o.optLong(
                            "id",
                            System.currentTimeMillis()
                        ),
                        type =
                            o.optString("type"),
                        amount =
                            o.optLong("amount"),
                        category =
                            o.optString("category"),
                        note =
                            o.optString("note"),
                        date =
                            o.optString("date"),
                        timestamp =
                            o.optLong(
                                "timestamp",
                                System.currentTimeMillis()
                            )
                    )
                )
            }
        }

        targets.clear()

        val targetArray =
            root.optJSONArray(
                "targets"
            )

        if (targetArray != null) {

            for (i in 0 until targetArray.length()) {

                val o =
                    targetArray.optJSONObject(i)
                        ?: continue

                targets.add(
                    Target(
                        id = o.optLong(
                            "id",
                            System.currentTimeMillis()
                        ),
                        name =
                            o.optString("name"),
                        targetAmount =
                            o.optLong("targetAmount"),
                        savedAmount =
                            o.optLong("savedAmount"),
                        deadline =
                            o.optString("deadline"),
                        priority =
                            o.optString("priority")
                    )
                )
            }
        }

        interactionId = null

        saveData()
    }

    // ============================================================
    // CALCULATIONS
    // ============================================================

    private fun totalIncome(): Long {
        return transactions
            .filter {
                it.type == "income"
            }
            .sumOf {
                it.amount
            }
    }

    private fun totalExpense(): Long {
        return transactions
            .filter {
                it.type == "expense"
            }
            .sumOf {
                it.amount
            }
    }

    private fun currentBalance(): Long {
        return startingBalance +
            totalIncome() -
            totalExpense()
    }

    // ============================================================
    // NETWORK
    // ============================================================

    private fun httpGet(
        urlString: String,
        key: String
    ): Pair<Boolean, String> {

        var connection: HttpURLConnection? = null

        return try {

            connection =
                URL(urlString)
                    .openConnection()
                    as HttpURLConnection

            connection.requestMethod = "GET"
            connection.connectTimeout = 20000
            connection.readTimeout = 30000

            connection.setRequestProperty(
                "x-goog-api-key",
                key
            )

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            val code =
                connection.responseCode

            val stream =
                if (code in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val text =
                readStream(stream)

            if (code in 200..299) {
                Pair(true, text)
            } else {
                Pair(
                    false,
                    parseHttpError(
                        code,
                        text
                    )
                )
            }

        } catch (e: Exception) {

            Pair(
                false,
                e.message ?: "Network error"
            )

        } finally {
            connection?.disconnect()
        }
    }

    private fun httpPost(
        urlString: String,
        key: String,
        body: String
    ): Pair<Boolean, String> {

        var connection: HttpURLConnection? = null

        return try {

            connection =
                URL(urlString)
                    .openConnection()
                    as HttpURLConnection

            connection.requestMethod = "POST"
            connection.connectTimeout = 20000
            connection.readTimeout = 60000
            connection.doOutput = true

            connection.setRequestProperty(
                "x-goog-api-key",
                key
            )

            connection.setRequestProperty(
                "Content-Type",
                "application/json"
            )

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            OutputStreamWriter(
                connection.outputStream,
                Charsets.UTF_8
            ).use {
                it.write(body)
            }

            val code =
                connection.responseCode

            val stream =
                if (code in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val text =
                readStream(stream)

            if (code in 200..299) {
                Pair(true, text)
            } else {
                Pair(
                    false,
                    parseHttpError(
                        code,
                        text
                    )
                )
            }

        } catch (e: Exception) {

            Pair(
                false,
                e.message ?: "Network error"
            )

        } finally {
            connection?.disconnect()
        }
    }

    private fun readStream(
        stream: java.io.InputStream?
    ): String {

        if (stream == null) {
            return ""
        }

        return BufferedReader(
            InputStreamReader(
                stream,
                Charsets.UTF_8
            )
        ).use {
            it.readText()
        }
    }

    private fun parseHttpError(
        code: Int,
        body: String
    ): String {

        if (body.isBlank()) {
            return "HTTP $code"
        }

        return try {

            val json =
                JSONObject(body)

            val error =
                json.optJSONObject("error")

            if (error != null) {
                error.optString(
                    "message",
                    "HTTP $code"
                )
            } else {
                "HTTP $code"
            }

        } catch (_: Exception) {
            "HTTP $code"
        }
    }

    // ============================================================
    // WEEKLY CHART
    // ============================================================

    private inner class WeeklyChartView(
        context: Context
    ) : View(context) {

        private var data =
            emptyList<Transaction>()

        private var mode = 0

        private val labels = ArrayList<String>()
        private val incomes = ArrayList<Long>()
        private val expenses = ArrayList<Long>()

        private var grow = 0f
        private var animator: ValueAnimator? = null

        var expenseTotal = 0L
            private set

        var incomeTotal = 0L
            private set

        private val paint =
            Paint(Paint.ANTI_ALIAS_FLAG)

        private val rect = RectF()

        private val monthNames = arrayOf(
            "Jan", "Feb", "Mar", "Apr", "Mei", "Jun",
            "Jul", "Agu", "Sep", "Okt", "Nov", "Des"
        )

        fun setData(
            transactions: List<Transaction>
        ) {
            data = transactions
            rebuild()
            invalidate()
        }

        fun showMode(newMode: Int) {
            mode = newMode
            rebuild()
            playAnimation()
        }

        private fun rebuild() {
            labels.clear()
            incomes.clear()
            expenses.clear()

            val now = Calendar.getInstance()

            val dayFormat =
                SimpleDateFormat("yyyy-MM-dd", Locale.US)

            val dayLabelFormat =
                SimpleDateFormat("dd", Locale.US)

            val monthFormat =
                SimpleDateFormat("yyyy-MM", Locale.US)

            val yearFormat =
                SimpleDateFormat("yyyy", Locale.US)

            if (mode == 0) {

                for (i in 6 downTo 0) {
                    val c = now.clone() as Calendar
                    c.add(Calendar.DAY_OF_YEAR, -i)

                    val key = dayFormat.format(c.time)

                    labels.add(dayLabelFormat.format(c.time))

                    incomes.add(
                        data.filter {
                            it.type == "income" &&
                                it.date == key
                        }.sumOf { it.amount }
                    )

                    expenses.add(
                        data.filter {
                            it.type == "expense" &&
                                it.date == key
                        }.sumOf { it.amount }
                    )
                }

            } else if (mode == 1) {

                for (i in 5 downTo 0) {
                    val c = now.clone() as Calendar
                    c.set(Calendar.DAY_OF_MONTH, 1)
                    c.add(Calendar.MONTH, -i)

                    val key = monthFormat.format(c.time)

                    labels.add(monthNames[c.get(Calendar.MONTH)])

                    incomes.add(
                        data.filter {
                            it.type == "income" &&
                                it.date.startsWith(key)
                        }.sumOf { it.amount }
                    )

                    expenses.add(
                        data.filter {
                            it.type == "expense" &&
                                it.date.startsWith(key)
                        }.sumOf { it.amount }
                    )
                }

            } else {

                for (i in 3 downTo 0) {
                    val c = now.clone() as Calendar
                    c.set(Calendar.DAY_OF_MONTH, 1)
                    c.add(Calendar.YEAR, -i)

                    val key = yearFormat.format(c.time)

                    labels.add(key)

                    incomes.add(
                        data.filter {
                            it.type == "income" &&
                                it.date.startsWith(key)
                        }.sumOf { it.amount }
                    )

                    expenses.add(
                        data.filter {
                            it.type == "expense" &&
                                it.date.startsWith(key)
                        }.sumOf { it.amount }
                    )
                }
            }

            incomeTotal = incomes.sum()
            expenseTotal = expenses.sum()
        }

        private fun playAnimation() {
            animator?.cancel()

            grow = 0f

            val a = ValueAnimator.ofFloat(0f, 1f)
            a.duration = 850L
            a.startDelay = 120L
            a.interpolator = DecelerateInterpolator()
            a.addUpdateListener {
                grow = it.animatedValue as Float
                invalidate()
            }

            animator = a
            a.start()
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            playAnimation()
        }

        override fun onDetachedFromWindow() {
            animator?.cancel()
            super.onDetachedFromWindow()
        }

        override fun onDraw(
            canvas: Canvas
        ) {
            super.onDraw(canvas)

            val count = labels.size

            if (count == 0) return

            val w = width.toFloat()
            val h = height.toFloat()

            var maxValue = 1L

            for (i in 0 until count) {
                maxValue = max(
                    maxValue,
                    max(incomes[i], expenses[i])
                )
            }

            val left = dp(6).toFloat()
            val right = w - dp(6)
            val top = dp(22).toFloat()
            val bottom = h - dp(24)
            val slot = (right - left) / count.toFloat()

            // garis bantu
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1).toFloat()
            paint.color = Color.rgb(233, 238, 248)

            for (i in 0..3) {
                val y = top + (bottom - top) * i / 3f

                canvas.drawLine(
                    left,
                    y,
                    right,
                    y,
                    paint
                )
            }

            val barWidth = min(dp(14).toFloat(), slot * 0.28f)
            val gap = dp(3).toFloat()
            val radius = barWidth / 2f
            val stub = dp(4).toFloat()

            for (i in 0 until count) {

                val cx = left + slot * i + slot / 2f

                val incomeHeight =
                    (incomes[i].toDouble() / maxValue.toDouble())
                        .toFloat() * (bottom - top) * grow

                val expenseHeight =
                    (expenses[i].toDouble() / maxValue.toDouble())
                        .toFloat() * (bottom - top) * grow

                paint.style = Paint.Style.FILL

                // batang pemasukan
                paint.color =
                    if (incomes[i] > 0L) green
                    else Color.rgb(233, 238, 248)

                rect.set(
                    cx - gap / 2f - barWidth,
                    bottom - max(incomeHeight, stub),
                    cx - gap / 2f,
                    bottom
                )

                canvas.drawRoundRect(
                    rect,
                    radius,
                    radius,
                    paint
                )

                // batang pengeluaran
                paint.color =
                    if (expenses[i] > 0L) blue
                    else Color.rgb(233, 238, 248)

                rect.set(
                    cx + gap / 2f,
                    bottom - max(expenseHeight, stub),
                    cx + gap / 2f + barWidth,
                    bottom
                )

                canvas.drawRoundRect(
                    rect,
                    radius,
                    radius,
                    paint
                )

                // label nilai di atas batang tertinggi
                val peak = max(incomes[i], expenses[i])

                if (peak > 0L) {
                    val peakHeight = max(incomeHeight, expenseHeight)

                    paint.color = muted
                    paint.alpha = (255f * grow).toInt()
                    paint.textSize = dp(9).toFloat()
                    paint.textAlign = Paint.Align.CENTER

                    canvas.drawText(
                        shortMoney(peak),
                        cx,
                        bottom - max(peakHeight, stub) - dp(5),
                        paint
                    )

                    paint.alpha = 255
                }

                // label sumbu X
                paint.color = muted
                paint.textSize = dp(10).toFloat()
                paint.textAlign = Paint.Align.CENTER

                canvas.drawText(
                    labels[i],
                    cx,
                    h - dp(6),
                    paint
                )
            }
        }
    }

    // ============================================================
    // UI HELPERS
    // ============================================================

    private fun setHeader(
        title: String,
        subtitle: String
    ) {
        titleView.text = title
        subtitleView.text = subtitle

        titleView.alpha = 0f
        titleView.translationX = -dp(14).toFloat()

        titleView.animate()
            .setStartDelay(0L)
            .alpha(1f)
            .translationX(0f)
            .setDuration(320L)
            .setInterpolator(DecelerateInterpolator())
            .start()

        subtitleView.alpha = 0f

        subtitleView.animate()
            .alpha(1f)
            .setStartDelay(90L)
            .setDuration(320L)
            .start()

        updateNav()
    }

    private fun sectionTitle(
        title: String,
        subtitle: String,
        action: String? = null,
        onAction: (() -> Unit)? = null
    ): LinearLayout {

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL

        top.addView(
            text(
                title,
                18f,
                white,
                true
            ),
            LinearLayout.LayoutParams(
                0,
                WRAP,
                1f
            )
        )

        if (action != null) {
            val actionView = text(
                action,
                12f,
                blue,
                true
            )

            actionView.setPadding(dp(10), dp(4), 0, dp(4))

            actionView.setOnClickListener {
                if (onAction != null) {
                    onAction()
                }
            }

            top.addView(actionView)
        }

        box.addView(top)

        box.addView(
            text(
                subtitle,
                11f,
                muted,
                false
            ).apply {
                setPadding(0, dp(3), 0, 0)
            }
        )

        return box
    }

    private fun emptyState(
        message: String
    ): LinearLayout {

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER
        box.setPadding(
            dp(20),
            dp(32),
            dp(20),
            dp(32)
        )
        box.background = rounded(surface, 22)
        box.elevation = dp(2).toFloat()

        val iconView = text(
            "○",
            26f,
            blue,
            false
        )
        iconView.gravity = Gravity.CENTER
        iconView.background = rounded(
            blend(blue, surface, 0.10f),
            22
        )

        box.addView(
            iconView,
            LinearLayout.LayoutParams(
                dp(56),
                dp(56)
            )
        )

        box.addView(
            text(
                message,
                13f,
                muted,
                false
            ).apply {
                gravity = Gravity.CENTER
                setPadding(
                    0,
                    dp(12),
                    0,
                    0
                )
            }
        )

        return box
    }

    private fun button(
        label: String,
        color: Int
    ): TextView {

        val b = TextView(this)

        b.text = label
        b.textSize = 13f
        b.setTextColor(
            if (color == surface3)
                white
            else
                Color.WHITE
        )
        b.gravity = Gravity.CENTER
        b.setTypeface(
            null,
            android.graphics.Typeface.BOLD
        )

        b.background =
            rounded(
                color,
                18
            )

        b.setPadding(
            dp(10),
            0,
            dp(10),
            0
        )

        addPressAnim(b)

        return b
    }

    private fun input(
        hint: String,
        description: String
    ): EditText {

        val field = EditText(this)

        field.hint = hint
        field.contentDescription = description
        field.setHintTextColor(blend(muted, surface, 0.55f))
        field.setTextColor(white)
        field.textSize = 14f
        field.setSingleLine(true)
        field.setPadding(
            dp(14),
            dp(13),
            dp(14),
            dp(13)
        )

        // Stroke tipis + sedikit lebih terang dari surface sekitarnya,
        // supaya kolom isian jelas kelihatan batasnya (tidak menyatu
        // dengan background dialog).
        val bg = GradientDrawable()
        bg.setColor(surface2)
        bg.cornerRadius = dp(14).toFloat()
        bg.setStroke(dp(1), strokeColor)
        field.background = bg

        field.layoutParams = LinearLayout.LayoutParams(
            MATCH,
            dp(48)
        )

        // Saat fokus, stroke jadi lebih tegas (warna biru) supaya user
        // tahu persis kolom mana yang sedang aktif diisi.
        field.setOnFocusChangeListener { _, hasFocus ->
            val focusedBg = GradientDrawable()
            focusedBg.setColor(surface2)
            focusedBg.cornerRadius = dp(14).toFloat()
            focusedBg.setStroke(
                dp(if (hasFocus) 2 else 1),
                if (hasFocus) blue else strokeColor
            )
            field.background = focusedBg
        }

        return field
    }

    private fun text(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean
    ): TextView {

        val view = TextView(this)

        view.text = value
        view.textSize = size
        view.setTextColor(color)

        if (bold) {
            view.setTypeface(
                null,
                android.graphics.Typeface.BOLD
            )
        }

        return view
    }

    private fun rounded(
        color: Int,
        radiusDp: Int
    ): GradientDrawable {

        val drawable =
            GradientDrawable()

        drawable.setColor(color)

        drawable.cornerRadius =
            dp(radiusDp).toFloat()

        return drawable
    }

    private fun blend(
        foreground: Int,
        background: Int,
        amount: Float
    ): Int {

        val r =
            (Color.red(background) *
                (1f - amount) +
                Color.red(foreground) *
                amount).toInt()

        val g =
            (Color.green(background) *
                (1f - amount) +
                Color.green(foreground) *
                amount).toInt()

        val b =
            (Color.blue(background) *
                (1f - amount) +
                Color.blue(foreground) *
                amount).toInt()

        return Color.rgb(
            r,
            g,
            b
        )
    }

    private fun space(
        height: Int
    ): View {

        return Space(this).apply {
            layoutParams =
                LinearLayout.LayoutParams(
                    1,
                    dp(height)
                )
        }
    }

    // ============================================================
    // FORMATTING
    // ============================================================

    private fun formatRupiah(
        value: Long
    ): String {

        // Catatan: sengaja TIDAK memakai NumberFormat.getCurrencyInstance
        // dengan Locale("id","ID"). Di sejumlah perangkat/versi Android,
        // kombinasi itu punya bug bawaan ICU yang membuat simbol "Rp"
        // muncul tapi angkanya kosong. Format manual di bawah ini pakai
        // digit ASCII biasa sehingga selalu tampil dengan benar di semua
        // perangkat.

        val negative = value < 0
        val digits = kotlin.math.abs(value).toString()

        val grouped = StringBuilder()
        for (i in digits.indices) {
            val remaining = digits.length - i
            grouped.append(digits[i])
            if (remaining > 1 && remaining % 3 == 1) {
                grouped.append(".")
            }
        }

        return (if (negative) "-Rp " else "Rp ") + grouped.toString()
    }

    private fun parseMoney(
        value: String
    ): Long {

        val clean =
            value
                .replace(
                    "Rp",
                    "",
                    ignoreCase = true
                )
                .replace(".", "")
                .replace(",", "")
                .replace(" ", "")
                .trim()

        return clean.toLongOrNull() ?: 0L
    }

    private fun parsePercent(
        value: String
    ): Int {

        val parsed =
            value
                .replace(
                    "%",
                    ""
                )
                .trim()
                .toIntOrNull()
                ?: 0

        return min(
            100,
            max(
                0,
                parsed
            )
        )
    }

    private fun today(): String {

        return SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.US
        ).format(Date())
    }

    private fun dp(
        value: Int
    ): Int {

        return (
            value *
                resources.displayMetrics.density
            ).toInt()
    }

    // ============================================================
    // CLEANUP
    // ============================================================

    override fun onDestroy() {
        super.onDestroy()
    }
}