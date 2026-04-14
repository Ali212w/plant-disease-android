package com.zm.plantdisease.data.model

import android.os.Parcelable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

// ── API Response Models ──────────────────────────────────────────────────────

data class ClassResult(
    val classIndex: Int = 0,
    @SerializedName("name_en") val nameEn: String = "",
    @SerializedName("name_ar") val nameAr: String = "",
    val probability: Float = 0f
)

data class PredictionResponse(
    @SerializedName("predicted_class")    val predictedClass: Int,
    @SerializedName("predicted_name_en") val predictedNameEn: String,
    @SerializedName("predicted_name_ar") val predictedNameAr: String,
    val confidence: Float,
    @SerializedName("all_classes")       val allClasses: List<ClassResult>,
    @SerializedName("model_name")        val modelName: String,
    @SerializedName("inference_ms")      val inferenceMs: Float?,
    @SerializedName("predict_ms")        val predictMs: Float?,
    @SerializedName("total_ms")          val totalMs: Float?
)

data class ModelInfo(
    val id: String = "",
    val name: String = "",
    val type: String = "mine",      // mine | baseline | external
    val description: String = "",
    val color: String = "#4CAF50",
    val available: Boolean = true,
    val loaded: Boolean = true,
    val downloadUrl: String = "",   // رابط التحميل من GitHub
    val fileName: String = "",      // اسم الملف في filesDir
    val accuracy: String = "",      // نسبة الدقة للعرض
    val isProtected: Boolean = false // نماذج مدمجة لا يمكن حذفها
)

data class ModelsResponse(val models: List<ModelInfo>)

data class CompareResponse(val comparisons: Map<String, PredictionResponse>)

data class StatusResponse(
    val status: String,
    val device: String,
    @SerializedName("has_gpu")      val hasGpu: Boolean,
    @SerializedName("has_timm")     val hasTimm: Boolean,
    @SerializedName("models_loaded") val modelsLoaded: List<String>,
    @SerializedName("num_classes")  val numClasses: Int,
    @SerializedName("classes_en")   val classesEn: List<String>,
    @SerializedName("classes_ar")   val classesAr: List<String>
)

// ── Firestore Model ──────────────────────────────────────────────────────────

@Parcelize
data class HistoryItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val imageUrl: String = "",
    val modelName: String = "",
    val predictedClass: Int = 0,
    val predictedNameAr: String = "",
    val predictedNameEn: String = "",
    val confidence: Float = 0f,
    val timestamp: Timestamp? = null,
    val probabilities: List<Float> = emptyList()
) : Parcelable

// ── Domain Sealed Classes ────────────────────────────────────────────────────

sealed class Resource<T> {
    data class Success<T>(val data: T) : Resource<T>()
    data class Error<T>(val message: String, val data: T? = null) : Resource<T>()
    class Loading<T> : Resource<T>()
}

// ── Model Categories ─────────────────────────────────────────────────────────

enum class ModelType(val label: String, val labelAr: String) {
    MINE("My Models", "نموذجك"),
    BASELINE("Baseline", "مقارنة"),
    ABLATION("Ablation", "Ablation")
}

val CLASS_EMOJIS = listOf("🌽", "🌽", "🌽", "✅", "🥔", "🥔", "✅")
