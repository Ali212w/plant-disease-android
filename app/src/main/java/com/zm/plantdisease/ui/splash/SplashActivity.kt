package com.zm.plantdisease.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.zm.plantdisease.R
import com.zm.plantdisease.data.model.ModelDownloadManager
import com.zm.plantdisease.ui.main.MainActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    // ── عناصر واجهة التحميل ───────────────────────────────────────────────────
    private lateinit var downloadCard : View
    private lateinit var progressBar  : ProgressBar
    private lateinit var tvPercent    : TextView
    private lateinit var tvSubtitle   : TextView
    private lateinit var tvError      : TextView
    private lateinit var btnRetry     : MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        bindViews()

        if (ModelDownloadManager.isModelReady(this)) {
            // ✅ النموذج موجود → انتقل للتطبيق بعد ثانيتين
            goToMainDelayed()
        } else {
            // ⬇️ النموذج غير موجود → ابدأ التحميل
            showDownloadUI()
            startDownload()
        }
    }

    // ── ربط العناصر ───────────────────────────────────────────────────────────

    private fun bindViews() {
        downloadCard = findViewById(R.id.downloadCard)
        progressBar  = findViewById(R.id.progressBar)
        tvPercent    = findViewById(R.id.tvPercent)
        tvSubtitle   = findViewById(R.id.tvDownloadSubtitle)
        tvError      = findViewById(R.id.tvError)
        btnRetry     = findViewById(R.id.btnRetry)

        btnRetry.setOnClickListener {
            hideError()
            startDownload()
        }
    }

    // ── منطق التحميل ──────────────────────────────────────────────────────────

    private fun startDownload() {
        tvSubtitle.text = "يُحمَّل مرة واحدة فقط، ثم يعمل بدون إنترنت"
        progressBar.progress = 0
        tvPercent.text = "0%"

        ModelDownloadManager.downloadModel(
            context    = this,
            onProgress = { pct ->
                runOnUiThread {
                    progressBar.progress = pct
                    tvPercent.text       = "$pct%"
                }
            },
            onSuccess  = {
                runOnUiThread {
                    progressBar.progress = 100
                    tvPercent.text       = "100%"
                    tvSubtitle.text      = "✅ اكتمل التحميل! جاري تشغيل التطبيق..."
                    goToMainDelayed(delayMs = 800)
                }
            },
            onError    = { msg ->
                runOnUiThread { showError(msg) }
            }
        )
    }

    // ── التنقل ────────────────────────────────────────────────────────────────

    private fun goToMainDelayed(delayMs: Long = 2000) {
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, delayMs)
    }

    // ── مساعدات الواجهة ───────────────────────────────────────────────────────

    private fun showDownloadUI() {
        downloadCard.visibility = View.VISIBLE
    }

    private fun showError(message: String) {
        tvError.text       = "⚠️ $message"
        tvError.visibility = View.VISIBLE
        btnRetry.visibility = View.VISIBLE
        tvSubtitle.text    = "تأكد من اتصال الإنترنت وأعد المحاولة"
    }

    private fun hideError() {
        tvError.visibility  = View.GONE
        btnRetry.visibility = View.GONE
    }
}
