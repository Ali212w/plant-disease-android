package com.zm.plantdisease.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.zm.plantdisease.data.firebase.FirebaseManager
import com.zm.plantdisease.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.InputStream

// ── Main ViewModel (Auth state) ───────────────────────────────────────────────

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val authState: LiveData<FirebaseUser?> = FirebaseManager.authStateFlow().asLiveData()
}

// ── Detect ViewModel ──────────────────────────────────────────────────────────

class DetectViewModel(app: Application) : AndroidViewModel(app) {

    private val classifier = PlantDiseaseClassifier(app.applicationContext)

    // النموذج الافتراضي المدمج
    private val localModel = ModelInfo(
        id          = "ensemble",
        name        = "Dual-Backbone Ensemble",
        description = "EfficientNet-B0 + ConvNeXt-Tiny — α=0.3",
        type        = "mine",
        available   = true,
        color       = "#4CAF50"
    )

    // قائمة النماذج (قابلة للتوسع بنماذج خارجية)
    private val modelList = mutableListOf(localModel)

    private val _models = MutableLiveData<Resource<List<ModelInfo>>>(
        Resource.Success(modelList.toList())
    )
    val models: LiveData<Resource<List<ModelInfo>>> = _models

    private val _prediction = MutableLiveData<Resource<PredictionResponse>>()
    val prediction: LiveData<Resource<PredictionResponse>> = _prediction

    private val _selectedModel = MutableLiveData<ModelInfo?>(localModel)
    val selectedModel: LiveData<ModelInfo?> = _selectedModel

    fun loadModels() {
        _models.postValue(Resource.Success(modelList.toList()))
        if (_selectedModel.value == null) _selectedModel.postValue(localModel)
    }

    fun selectModel(model: ModelInfo) = _selectedModel.postValue(model)

    /** إضافة نموذج خارجي (.ptl) من مسار داخلي في الجوال */
    fun addExternalModel(modelPath: String, modelName: String, description: String) {
        val ext = ModelInfo(
            id          = modelPath, // المسار هو المعرف الفريد
            name        = modelName,
            description = description,
            type        = "external",
            available   = true,
            color       = "#00BCD4"
        )
        // تجنب التكرار
        modelList.removeAll { it.type == "external" && it.id == modelPath }
        modelList.add(ext)
        _models.postValue(Resource.Success(modelList.toList()))
        _selectedModel.postValue(ext)
    }

    fun predict(modelName: String, inputStream: InputStream, userId: String, imageUri: Uri?) {
        viewModelScope.launch(Dispatchers.IO) {
            _prediction.postValue(Resource.Loading())
            try {
                // تحقق من النموذج المختار: هل هو النموذج الافتراضي أم خارجي؟
                val selectedModel = _selectedModel.value
                val result = if (selectedModel?.type == "external" && selectedModel.id.isNotEmpty()) {
                    classifier.classifyFromPath(imageUri!!, selectedModel.id)
                } else {
                    classifier.classify(imageUri!!)
                }

                val usedModelName = selectedModel?.name ?: localModel.name
                val response = PredictionResponse(
                    predictedClass   = result.predictedClass,
                    predictedNameEn  = result.predictedNameEn,
                    predictedNameAr  = result.predictedNameAr,
                    confidence       = result.confidence,
                    modelName        = usedModelName,
                    allClasses       = result.allClasses,
                    inferenceMs      = null,
                    predictMs        = result.predictMs,
                    totalMs          = result.totalMs
                )

                // حفظ بيانات التشخيص في Firestore (بدون رفع صورة - Storage يحتاج خطة مدفوعة)
                launch {
                    try {
                        FirebaseManager.savePrediction(userId, "", response)
                    } catch (_: Exception) {}
                }

                _prediction.postValue(Resource.Success(response))

            } catch (e: Exception) {
                _prediction.postValue(Resource.Error(e.message ?: "خطأ في التشخيص المحلي"))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        classifier.release()
    }
}

// ── Compare ViewModel ─────────────────────────────────────────────────────────

class CompareViewModel(app: Application) : AndroidViewModel(app) {

    private val classifier = PlantDiseaseClassifier(app.applicationContext)

    private val _compareResult = MutableLiveData<Resource<Map<String, PredictionResponse>>>()
    val compareResult: LiveData<Resource<Map<String, PredictionResponse>>> = _compareResult

    fun compare(inputStream: InputStream, imageUri: Uri?) {
        viewModelScope.launch(Dispatchers.IO) {
            _compareResult.postValue(Resource.Loading())
            try {
                val result = classifier.classify(imageUri!!)
                val response = PredictionResponse(
                    predictedClass  = result.predictedClass,
                    predictedNameEn = result.predictedNameEn,
                    predictedNameAr = result.predictedNameAr,
                    confidence      = result.confidence,
                    modelName       = "Dual-Backbone Ensemble",
                    allClasses      = result.allClasses,
                    inferenceMs     = null,
                    predictMs       = result.predictMs,
                    totalMs         = result.totalMs
                )
                _compareResult.postValue(Resource.Success(mapOf("ensemble" to response)))
            } catch (e: Exception) {
                _compareResult.postValue(Resource.Error(e.message ?: "خطأ"))
            }
        }
    }
}

// ── History ViewModel ─────────────────────────────────────────────────────────

class HistoryViewModel : androidx.lifecycle.ViewModel() {

    private val _history = MutableLiveData<Resource<List<HistoryItem>>>()
    val history: LiveData<Resource<List<HistoryItem>>> = _history

    private val _stats = MutableLiveData<Map<String, Any>>()
    val stats: LiveData<Map<String, Any>> = _stats

    fun loadHistory(userId: String) {
        viewModelScope.launch {
            FirebaseManager.getPredictionsFlow(userId).collect { items ->
                _history.postValue(Resource.Success(items))
            }
        }
    }

    fun loadStats(userId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _stats.postValue(FirebaseManager.getUserStats(userId))
            } catch (_: Exception) {}
        }
    }

    fun delete(userId: String, docId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            FirebaseManager.deletePrediction(userId, docId)
        }
    }
}
