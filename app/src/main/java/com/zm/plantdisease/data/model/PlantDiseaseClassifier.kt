package com.zm.plantdisease.data.model

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.torchvision.TensorImageUtils
import java.io.File

/**
 * محرك الاستدلال المحلي باستخدام PyTorch Lite
 * يحمل نموذج model.ptl من مجلد assets ويُشغّله على الصورة
 */
class PlantDiseaseClassifier(private val context: Context) {

    companion object {
        private const val MODEL_FILE = "model.ptl"
        private const val IMG_SIZE   = 224

        // قيم التطبيع لـ ImageNet (نفس ما استُخدم في التدريب)
        val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        val STD  = floatArrayOf(0.229f, 0.224f, 0.225f)

        // أسماء الفئات بالعربي والإنجليزي
        val CLASS_NAMES_EN = listOf(
            "Corn Gray Leaf Spot",
            "Corn Common Rust",
            "Corn Northern Leaf Blight",
            "Corn Healthy",
            "Potato Early Blight",
            "Potato Late Blight",
            "Potato Healthy"
        )

        val CLASS_NAMES_AR = listOf(
            "تبقع أوراق الذرة الرمادي",
            "صدأ الذرة الشائع",
            "تفحّم أوراق الذرة الشمالي",
            "ذرة سليمة",
            "اللفحة المبكرة للبطاطا",
            "اللفحة المتأخرة للبطاطا",
            "بطاطا سليمة"
        )

        // إيموجي لكل فئة
        val CLASS_EMOJIS_LIST = listOf("🌽","🌽","🌽","✅","🥔","🥔","✅")
    }

    private var module: Module? = null

    /** تحميل النموذج (مرة واحدة فقط) */
    fun load() {
        if (module != null) return
        val modelPath = ModelDownloadManager.getModelPath(context)
        check(File(modelPath).exists()) {
            "النموذج غير موجود. يرجى إعادة تشغيل التطبيق لتحميله."
        }
        module = LiteModuleLoader.load(modelPath)
    }

    /** تشغيل الاستدلال على صورة من Uri باستخدام النموذج الافتراضي (assets) */
    fun classify(imageUri: Uri): InferenceResult {
        load()
        return runInference(module!!, imageUri, "Dual-Backbone Ensemble")
    }

    /** تشغيل الاستدلال باستخدام نموذج خارجي من مسار مطلق */
    fun classifyFromPath(imageUri: Uri, modelPath: String): InferenceResult {
        val externalModule = LiteModuleLoader.load(modelPath)
        return try {
            runInference(externalModule, imageUri, modelPath.substringAfterLast("/").removeSuffix(".ptl"))
        } finally {
            externalModule.destroy()
        }
    }

    private fun runInference(mod: Module, imageUri: Uri, modelName: String): InferenceResult {
        val startTotal = System.currentTimeMillis()
        val bitmap = loadAndPreprocess(imageUri)
        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(bitmap, MEAN, STD)

        val startPredict = System.currentTimeMillis()
        val outputTensor = mod.forward(IValue.from(inputTensor)).toTensor()
        val predictMs    = System.currentTimeMillis() - startPredict

        // تطبيق softmax على الـ logits الخام (النماذج المنفردة لا تحتوي على softmax)
        val logits = outputTensor.dataAsFloatArray
        val scores = softmax(logits)

        val topIdx = scores.indices.maxByOrNull { scores[it] } ?: 0

        val allClasses = scores.mapIndexed { i, prob ->
            ClassResult(
                classIndex  = i,
                nameEn      = CLASS_NAMES_EN.getOrElse(i) { "Class $i" },
                nameAr      = CLASS_NAMES_AR.getOrElse(i) { "فئة $i" },
                probability = prob * 100f
            )
        }.sortedByDescending { it.probability }

        return InferenceResult(
            predictedClass   = topIdx,
            predictedNameEn  = CLASS_NAMES_EN.getOrElse(topIdx) { "Unknown" },
            predictedNameAr  = CLASS_NAMES_AR.getOrElse(topIdx) { "غير معروف" },
            confidence       = scores[topIdx] * 100f,
            allClasses       = allClasses,
            predictMs        = predictMs.toFloat(),
            totalMs          = (System.currentTimeMillis() - startTotal).toFloat()
        )
    }

    /** تحويل logits إلى احتمالات باستخدام Softmax */
    private fun softmax(logits: FloatArray): FloatArray {
        val maxVal = logits.max() ?: 0f
        val expVals = logits.map { Math.exp((it - maxVal).toDouble()).toFloat() }
        val sum = expVals.sum()
        return expVals.map { it / sum }.toFloatArray()
    }

    private fun loadAndPreprocess(uri: Uri): Bitmap {
        val original = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        return Bitmap.createScaledBitmap(original, IMG_SIZE, IMG_SIZE, true)
    }

    // assetFilePath مُزالة - النموذج يُحمَّل من Firebase Storage إلى filesDir
    // عبر ModelDownloadManager عند أول تشغيل للتطبيق

    fun release() {
        module?.destroy()
        module = null
    }
}

/** نتيجة الاستدلال */
data class InferenceResult(
    val predictedClass  : Int,
    val predictedNameEn : String,
    val predictedNameAr : String,
    val confidence      : Float,
    val allClasses      : List<ClassResult>,
    val predictMs       : Float,
    val totalMs         : Float
)
