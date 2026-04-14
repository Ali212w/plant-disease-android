package com.zm.plantdisease.data.model

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * مدير تحميل النموذج من GitHub Releases عبر OkHttp
 * يتحقق من وجود النموذج محلياً، وإن لم يوجد يحمّله مرة واحدة ويخزّنه للأبد
 */
object ModelDownloadManager {

    const val MODEL_FILE_NAME = "model.ptl"

    // ── رابط تحميل النموذج من GitHub Releases ────────────────────────────────
    private const val MODEL_DOWNLOAD_URL =
        "https://github.com/Ali212w/plant-disease-model/releases/download/v1.0/model.ptl"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)   // 127MB يحتاج وقتاً كافياً
        .writeTimeout(10, TimeUnit.MINUTES)
        .build()

    // ── حالة النموذج ─────────────────────────────────────────────────────────

    /** هل النموذج محمّل وجاهز للاستخدام؟ */
    fun isModelReady(context: Context): Boolean =
        getModelFile(context).exists()

    /** المسار المطلق لملف النموذج في التخزين الداخلي */
    fun getModelPath(context: Context): String =
        getModelFile(context).absolutePath

    private fun getModelFile(context: Context) =
        File(context.filesDir, MODEL_FILE_NAME)

    // ── تحميل النموذج ─────────────────────────────────────────────────────────

    /**
     * تحميل النموذج من GitHub Releases (يعمل في Thread منفصل)
     * @param onProgress  نسبة التقدم من 0 إلى 100
     * @param onSuccess   يُستدعى عند اكتمال التحميل
     * @param onError     يُستدعى مع رسالة الخطأ عند الفشل
     */
    fun downloadModel(
        context: Context,
        onProgress: (Int) -> Unit,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val localFile = getModelFile(context)
        val tempFile  = File(context.filesDir, "$MODEL_FILE_NAME.tmp")

        // تشغيل التحميل في خيط خلفي
        Thread {
            try {
                if (tempFile.exists()) tempFile.delete()

                val request  = Request.Builder().url(MODEL_DOWNLOAD_URL).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    onError("خطأ في الاتصال: ${response.code}")
                    return@Thread
                }

                val body        = response.body ?: run { onError("استجابة فارغة من الخادم"); return@Thread }
                val totalBytes  = body.contentLength()

                FileOutputStream(tempFile).use { out ->
                    body.byteStream().use { input ->
                        val buffer    = ByteArray(8 * 1024)
                        var downloaded = 0L
                        var read: Int

                        while (input.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                            downloaded += read
                            if (totalBytes > 0) {
                                val pct = (100.0 * downloaded / totalBytes).toInt()
                                onProgress(pct)
                            }
                        }
                    }
                }

                if (tempFile.renameTo(localFile)) {
                    onSuccess()
                } else {
                    onError("فشل في حفظ النموذج، المرجو إعادة المحاولة")
                }

            } catch (e: Exception) {
                tempFile.delete()
                onError(e.localizedMessage ?: "خطأ غير معروف أثناء التحميل")
            }
        }.start()
    }

    // ── حذف النموذج ──────────────────────────────────────────────────────────

    /** حذف النموذج المخزّن (يُجبر على إعادة التحميل في المرة القادمة) */
    fun clearModel(context: Context) {
        getModelFile(context).delete()
        File(context.filesDir, "$MODEL_FILE_NAME.tmp").delete()
    }
}
