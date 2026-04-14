package com.zm.plantdisease.data.model

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * مدير تحميل النماذج من GitHub Releases عبر OkHttp
 */
object ModelDownloadManager {

    // النموذج الافتراضي (EfficientNet-B0 — 15.6 MB)
    const val MODEL_FILE_NAME = "model_A.ptl"
    private const val DEFAULT_DOWNLOAD_URL =
        "https://github.com/Ali212w/plant-disease-model/releases/download/v1.0/model_A.ptl"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.MINUTES)
        .writeTimeout(15, TimeUnit.MINUTES)
        .build()

    // ── حالة النموذج ─────────────────────────────────────────────────────────

    /** هل النموذج الافتراضي جاهز؟ */
    fun isModelReady(context: Context): Boolean =
        File(context.filesDir, MODEL_FILE_NAME).exists()

    /** المسار الكامل للنموذج الافتراضي */
    fun getModelPath(context: Context): String =
        File(context.filesDir, MODEL_FILE_NAME).absolutePath

    // ── تحميل النموذج الافتراضي (من SplashActivity) ──────────────────────────

    fun downloadModel(
        context: Context,
        onProgress: (Int) -> Unit,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) = downloadFile(context, DEFAULT_DOWNLOAD_URL, MODEL_FILE_NAME, onProgress, onSuccess, onError)

    // ── تحميل أي نموذج بمساره واسمه ─────────────────────────────────────────

    /**
     * تحميل نموذج محدد من رابط معطى وحفظه بـ fileName في filesDir
     * @param url       رابط التحميل المباشر من GitHub Releases
     * @param fileName  اسم الملف المحلي (مثل: model_B.ptl)
     */
    fun downloadFile(
        context: Context,
        url: String,
        fileName: String,
        onProgress: (Int) -> Unit,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val localFile = File(context.filesDir, fileName)
        val tempFile  = File(context.filesDir, "$fileName.tmp")

        Thread {
            try {
                if (tempFile.exists()) tempFile.delete()

                val request  = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    onError("خطأ في الاتصال: ${response.code}")
                    return@Thread
                }

                val body       = response.body ?: run { onError("استجابة فارغة"); return@Thread }
                val totalBytes = body.contentLength()

                FileOutputStream(tempFile).use { out ->
                    body.byteStream().use { inp ->
                        val buffer    = ByteArray(8 * 1024)
                        var downloaded = 0L
                        var read: Int
                        while (inp.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                            downloaded += read
                            if (totalBytes > 0)
                                onProgress((100.0 * downloaded / totalBytes).toInt())
                        }
                    }
                }

                if (tempFile.renameTo(localFile)) onSuccess()
                else onError("فشل في حفظ النموذج")

            } catch (e: Exception) {
                tempFile.delete()
                onError(e.localizedMessage ?: "خطأ غير معروف")
            }
        }.start()
    }

    // ── حذف نموذج ────────────────────────────────────────────────────────────

    fun deleteModel(context: Context, fileName: String): Boolean {
        File(context.filesDir, "$fileName.tmp").delete()
        return File(context.filesDir, fileName).delete()
    }
}
