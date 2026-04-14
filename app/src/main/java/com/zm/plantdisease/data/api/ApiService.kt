package com.zm.plantdisease.data.api

import com.zm.plantdisease.data.model.*
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

// ── Retrofit Interface ───────────────────────────────────────────────────────

interface ApiService {

    @GET("api/models")
    suspend fun getModels(): Response<ModelsResponse>

    @Multipart
    @POST("api/predict/{modelName}")
    suspend fun predict(
        @Path("modelName", encoded = true) modelName: String,
        @Part file: MultipartBody.Part
    ): Response<PredictionResponse>

    @Multipart
    @POST("api/compare")
    suspend fun compare(
        @Part file: MultipartBody.Part
    ): Response<CompareResponse>

    @GET("api/status")
    suspend fun getStatus(): Response<StatusResponse>

    @GET("api/preload")
    suspend fun preloadModels(): Response<Map<String, Any>>
}

// ── Retrofit Client (غير مستخدم، الاستدلال محلي الآن) ───────────────────────

object RetrofitClient {

    // لم يعد التطبيق يتصل بأي سيرفر — الاستدلال يتم محلياً
    private var baseUrl: String = "http://localhost:8000/"

    fun setBaseUrl(url: String) {
        baseUrl = if (url.endsWith("/")) url else "$url/"
        _instance = null   // إعادة بناء العميل
    }

    fun getBaseUrl(): String = baseUrl

    private var _instance: ApiService? = null

    val instance: ApiService
        get() = _instance ?: buildService().also { _instance = it }

    private fun buildService(): ApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)      // الاستدلال قد يأخذ وقتاً
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
