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

    // النماذج الثلاثة
    private val modelA = ModelInfo(
        id          = "model_A",
        name        = "EfficientNet-B0 (Model A)",
        description = "نموذجريع وخفيف، دقة أداء ممتازة (15.6 MB)",
        type        = "mine",
        available   = false,
        downloadUrl = "https://github.com/Ali212w/plant-disease-model/releases/download/v1.0/model_A.ptl",
        fileName    = "model_A.ptl",
        accuracy    = "98.63%",
        color       = "#4CAF50"
    )

    private val baselineModel = ModelInfo(
        id          = "baseline",
        name        = "EfficientNet-B0 (Baseline)",
        description = "نموذج Ablation للمقارنة (15.6 MB)",
        type        = "mine",
        available   = false,
        downloadUrl = "https://github.com/Ali212w/plant-disease-model/releases/download/v1.0/baseline.ptl",
        fileName    = "baseline.ptl",
        accuracy    = "98.63%",
        color       = "#9C27B0"
    )

    private val modelB = ModelInfo(
        id          = "model_B",
        name        = "ConvNeXt-Tiny (Model B)",
        description = "دقة أعلى، لكن حجمه أكبر (106 MB)",
        type        = "mine",
        available   = false,
        downloadUrl = "https://github.com/Ali212w/plant-disease-model/releases/download/v1.0/model_B.ptl",
        fileName    = "model_B.ptl",
        accuracy    = "99.26%",
        color       = "#FF9800"
    )

    // القائمة المدمجة
    private val predefinedModels = listOf(modelA, baselineModel, modelB)

    // القائمة النشطة (قابلة للتوسع بنماذج خارجية)
    private val modelList = predefinedModels.toMutableList()

    private val _models = MutableLiveData<Resource<List<ModelInfo>>>(
        Resource.Success(modelList.toList())
    )
    val models: LiveData<Resource<List<ModelInfo>>> = _models

    private val _prediction = MutableLiveData<Resource<PredictionResponse>>()
    val prediction: LiveData<Resource<PredictionResponse>> = _prediction

    private val _selectedModel = MutableLiveData<ModelInfo?>(modelA)
    val selectedModel: LiveData<ModelInfo?> = _selectedModel

    // تتبع حالة التحميل الجاري: fileName -> progress(0-100)
    private val _downloadProgress = MutableLiveData<Map<String, Int>>(emptyMap())
    val downloadProgress: LiveData<Map<String, Int>> = _downloadProgress

    fun loadModels() {
        val ctx = getApplication<Application>().applicationContext
        
        // تحديث حالة النماذج المسبقة من حيث توفرها محلياً
        for (i in modelList.indices) {
            val m = modelList[i]
            if (m.fileName.isNotEmpty()) {
                val exists = java.io.File(ctx.filesDir, m.fileName).exists()
                modelList[i] = m.copy(
                    available = exists,
                    // إذا كان موجوداً، نجعل id الخاص به هو المسار الكامل ليتم تحميله بـ classifyFromPath
                    id = if (exists) java.io.File(ctx.filesDir, m.fileName).absolutePath else m.id
                )
            }
        }
        
        _models.postValue(Resource.Success(modelList.toList()))
        
        // إذا كان النموذج الحالي المختار متوفراً بعد التحديث
        val currentSelect = _selectedModel.value
        val updatedSelect = modelList.find { it.name == currentSelect?.name }
        if (updatedSelect != null && updatedSelect.available) {
            _selectedModel.postValue(updatedSelect)
        } else {
            // اختيار أول نموذج متاح، وإلا لا شيء
            _selectedModel.postValue(modelList.firstOrNull { it.available })
        }
    }

    fun selectModel(model: ModelInfo) = _selectedModel.postValue(model)

    /**
     * تحميل نموذج مدار من الـ ViewModel للاستمرار عند التنقل بين التبويبات
     */
    fun downloadModelFile(context: android.content.Context, url: String, fileName: String) {
        // لا تحمل مجدداً إذا جاري تحميله بالفعل
        if ((_downloadProgress.value ?: emptyMap()).containsKey(fileName)) return
        // ضبط التقدم إلى 0
        val current = (_downloadProgress.value ?: emptyMap()).toMutableMap()
        current[fileName] = 0
        _downloadProgress.postValue(current)

        com.zm.plantdisease.data.model.ModelDownloadManager.downloadFile(
            context = context,
            url = url,
            fileName = fileName,
            onProgress = { pct ->
                val map = (_downloadProgress.value ?: emptyMap()).toMutableMap()
                map[fileName] = pct
                _downloadProgress.postValue(map)
            },
            onSuccess = {
                val map = (_downloadProgress.value ?: emptyMap()).toMutableMap()
                map.remove(fileName)
                _downloadProgress.postValue(map)
                loadModels()
            },
            onError = { _ ->
                val map = (_downloadProgress.value ?: emptyMap()).toMutableMap()
                map.remove(fileName)
                _downloadProgress.postValue(map)
            }
        )
    }

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

    /** حذف نموذج خارجي من القائمة وحذف ملفه من التخزين */
    fun removeExternalModel(model: ModelInfo): Boolean {
        val deleted = try {
            java.io.File(model.id).delete()
        } catch (_: Exception) { false }
        modelList.removeAll { it.id == model.id }
        // إذا كان النموذج المحذوف هو المختار، ارجع للنموذج الافتراضي
        if (_selectedModel.value?.id == model.id) {
            _selectedModel.postValue(modelList.firstOrNull())
        }
        _models.postValue(Resource.Success(modelList.toList()))
        return deleted
    }

    fun predict(modelName: String, inputStream: InputStream, userId: String, imageUri: Uri?) {
        viewModelScope.launch(Dispatchers.IO) {
            _prediction.postValue(Resource.Loading())
            try {
                // تحقق من النموذج المختار
                val selectedModel = _selectedModel.value
                val result = if (selectedModel != null && selectedModel.available && selectedModel.fileName.isNotEmpty()) {
                    classifier.classifyFromPath(imageUri!!, selectedModel.id) // id أصبح المسار الكامل بفضل loadModels
                } else if (selectedModel?.type == "external" && selectedModel.id.isNotEmpty()) {
                    classifier.classifyFromPath(imageUri!!, selectedModel.id)
                } else {
                    // في الحالات الطارئة جداً
                    classifier.classify(imageUri!!)
                }

                val usedModelName = selectedModel?.name ?: modelA.name
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
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            _compareResult.postValue(Resource.Loading())
            try {
                val resultsMap = mutableMapOf<String, PredictionResponse>()
                
                // البحث عن كل النماذج المحملة
                val modelFiles = app.filesDir.listFiles { _, name -> name.endsWith(".ptl") }
                
                if (modelFiles.isNullOrEmpty()) {
                    _compareResult.postValue(Resource.Error("لا توجد نماذج محملة للمقارنة. يرجى تحميلها من قائمة النماذج أولاً."))
                    return@launch
                }
                
                val knownNames = mapOf(
                    "model_A.ptl"  to "Model A (15MB)",
                    "baseline.ptl" to "Baseline (15MB)",
                    "model_B.ptl"  to "Model B (106MB)"
                )
                
                for (file in modelFiles) {
                    try {
                        val result = classifier.classifyFromPath(imageUri!!, file.absolutePath)
                        val displayName = knownNames[file.name] ?: file.nameWithoutExtension
                        
                        val response = PredictionResponse(
                            predictedClass  = result.predictedClass,
                            predictedNameEn = result.predictedNameEn,
                            predictedNameAr = result.predictedNameAr,
                            confidence      = result.confidence,
                            modelName       = displayName,
                            allClasses      = result.allClasses,
                            inferenceMs     = null,
                            predictMs       = result.predictMs,
                            totalMs         = result.totalMs
                        )
                        resultsMap[displayName] = response
                    } catch (e: Exception) {
                        // تخطي النموذج اللي يفشل
                    }
                }
                
                if (resultsMap.isEmpty()) {
                    _compareResult.postValue(Resource.Error("جميع النماذج فشلت في استخراج النتيجة."))
                } else {
                    _compareResult.postValue(Resource.Success(resultsMap))
                }
            } catch (e: Exception) {
                _compareResult.postValue(Resource.Error(e.message ?: "حدث خطأ عام"))
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
