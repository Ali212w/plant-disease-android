package com.zm.plantdisease.data.model

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.Tensor
import org.pytorch.torchvision.TensorImageUtils
import java.io.File
import kotlin.math.abs

/**
 * On-device inference engine built with PyTorch Lite.
 * The default model keeps the original release file name: model.ptl.
 */
class PlantDiseaseClassifier(private val context: Context) {

    companion object {
        private const val IMG_SIZE = 224
        private const val EXPECTED_CLASS_COUNT = 7

        val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

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

        val CLASS_EMOJIS_LIST = listOf("🌽", "🌽", "🌽", "✅", "🥔", "🥔", "✅")
    }

    private var module: Module? = null

    fun load() {
        if (module != null) return
        val modelPath = ModelDownloadManager.getModelPath(context)
        module = loadValidatedModule(modelPath)
    }

    fun classify(imageUri: Uri): InferenceResult {
        load()
        return runInference(module!!, imageUri, "Model")
    }

    fun classifyFromPath(imageUri: Uri, modelPath: String): InferenceResult {
        val externalModule = loadValidatedModule(modelPath)
        return try {
            runInference(
                mod = externalModule,
                imageUri = imageUri,
                modelName = modelPath.substringAfterLast("/").removeSuffix(".ptl")
            )
        } finally {
            externalModule.destroy()
        }
    }

    fun validateModelFile(modelPath: String): String? {
        return try {
            loadValidatedModule(modelPath).destroy()
            null
        } catch (e: Exception) {
            e.message ?: "النموذج غير صالح أو غير متوافق."
        }
    }

    private fun runInference(mod: Module, imageUri: Uri, modelName: String): InferenceResult {
        val startTotal = System.currentTimeMillis()
        val bitmap = loadAndPreprocess(imageUri)
        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(bitmap, MEAN, STD)

        val startPredict = System.currentTimeMillis()
        val outputTensor = mod.forward(IValue.from(inputTensor)).toTensor()
        val predictMs = System.currentTimeMillis() - startPredict

        val rawOutput = outputTensor.dataAsFloatArray
        ensureExpectedOutputSize(rawOutput.size, modelName)
        val scores = normalizeScores(rawOutput)

        val topIdx = scores.indices.maxByOrNull { scores[it] } ?: 0
        val allClasses = scores.mapIndexed { i, prob ->
            ClassResult(
                classIndex = i,
                nameEn = CLASS_NAMES_EN.getOrElse(i) { "Class $i" },
                nameAr = CLASS_NAMES_AR.getOrElse(i) { "فئة $i" },
                probability = prob * 100f
            )
        }.sortedByDescending { it.probability }

        return InferenceResult(
            predictedClass = topIdx,
            predictedNameEn = CLASS_NAMES_EN.getOrElse(topIdx) { "Unknown" },
            predictedNameAr = CLASS_NAMES_AR.getOrElse(topIdx) { "غير معروف" },
            confidence = scores[topIdx] * 100f,
            allClasses = allClasses,
            predictMs = predictMs.toFloat(),
            totalMs = (System.currentTimeMillis() - startTotal).toFloat()
        )
    }

    private fun normalizeScores(values: FloatArray): FloatArray {
        return if (looksLikeProbabilities(values)) {
            values.copyOf()
        } else {
            softmax(values)
        }
    }

    private fun looksLikeProbabilities(values: FloatArray): Boolean {
        if (values.isEmpty()) return false
        if (values.any { it < 0f || it > 1f }) return false
        val sum = values.sum()
        return abs(sum - 1f) < 1e-2f
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val maxVal = logits.max()
        val exps = FloatArray(logits.size) {
            Math.exp((logits[it] - maxVal).toDouble()).toFloat()
        }
        val sumExp = exps.sum()
        return FloatArray(logits.size) { exps[it] / sumExp }
    }

    private fun loadValidatedModule(modelPath: String): Module {
        check(File(modelPath).exists()) {
            "النموذج غير موجود في المسار المحدد."
        }

        val loadedModule = LiteModuleLoader.load(modelPath)
        return try {
            validateModuleOutput(loadedModule, modelPath)
            loadedModule
        } catch (e: Exception) {
            loadedModule.destroy()
            throw e
        }
    }

    private fun validateModuleOutput(mod: Module, modelPath: String) {
        val probe = Tensor.fromBlob(
            FloatArray(3 * IMG_SIZE * IMG_SIZE),
            longArrayOf(1, 3, IMG_SIZE.toLong(), IMG_SIZE.toLong())
        )
        val probeOutput = mod.forward(IValue.from(probe)).toTensor().dataAsFloatArray
        ensureExpectedOutputSize(probeOutput.size, File(modelPath).name)
    }

    private fun ensureExpectedOutputSize(outputSize: Int, modelName: String) {
        if (outputSize != EXPECTED_CLASS_COUNT) {
            throw IllegalStateException(
                "النموذج \"$modelName\" غير متوافق مع التطبيق: يجب أن ينتج " +
                    "$EXPECTED_CLASS_COUNT قيم، لكنه أعاد $outputSize."
            )
        }
    }

    private fun loadAndPreprocess(uri: Uri): Bitmap {
        val original = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        return Bitmap.createScaledBitmap(original, IMG_SIZE, IMG_SIZE, true)
    }

    fun release() {
        module?.destroy()
        module = null
    }
}

data class InferenceResult(
    val predictedClass: Int,
    val predictedNameEn: String,
    val predictedNameAr: String,
    val confidence: Float,
    val allClasses: List<ClassResult>,
    val predictMs: Float,
    val totalMs: Float
)
