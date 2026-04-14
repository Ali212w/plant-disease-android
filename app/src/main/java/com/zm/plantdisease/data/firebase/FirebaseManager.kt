package com.zm.plantdisease.data.firebase

import android.net.Uri
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.zm.plantdisease.data.model.HistoryItem
import com.zm.plantdisease.data.model.PredictionResponse
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

// ── Firebase Manager — إدارة جميع عمليات Firebase ───────────────────────────

object FirebaseManager {

    private val auth      = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage   = FirebaseStorage.getInstance()

    // ── معرف الضيف المحلي الدائم ─────────────────────────────────────────────
    // يُستخدم عندما لا يكون المستخدم مسجلاً في Firebase
    private val GUEST_ID = "guest_${UUID.nameUUIDFromBytes("local_guest".toByteArray())}"

    // ── المستخدم الحالي ──────────────────────────────────────────────────────
    val currentUser: FirebaseUser? get() = auth.currentUser

    /** true دائماً - الضيف يُعامَل كمسجّل محلياً **/
    val isLoggedIn: Boolean get() = true

    /** معرف المستخدم الفعلي أو الضيف - لا يُرجع null أبداً **/
    val userId: String get() = auth.currentUser?.uid ?: GUEST_ID

    // ── مراقبة حالة الدخول ──────────────────────────────────────────────────
    fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    // ── تسجيل الخروج ────────────────────────────────────────────────────────
    fun signOut() = auth.signOut()

    // ══════════════════════════════════════════════════════════════════════════
    //  STORAGE — رفع الصور
    // ══════════════════════════════════════════════════════════════════════════

    suspend fun uploadImage(imageUri: Uri, userId: String): String {
        val imageId  = UUID.randomUUID().toString()
        val ref      = storage.reference.child("users/$userId/images/$imageId.jpg")
        ref.putFile(imageUri).await()
        return ref.downloadUrl.await().toString()
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  FIRESTORE — حفظ واسترجاع سجل التشخيصات
    // ══════════════════════════════════════════════════════════════════════════

    private fun historyCollection(userId: String) =
        firestore.collection("users").document(userId).collection("predictions")

    /** حفظ نتيجة تشخيص جديدة */
    suspend fun savePrediction(
        userId: String,
        imageUrl: String,
        result: PredictionResponse
    ): String {
        val item = hashMapOf(
            "userId"           to userId,
            "imageUrl"         to imageUrl,
            "modelName"        to result.modelName,
            "predictedClass"   to result.predictedClass,
            "predictedNameAr"  to result.predictedNameAr,
            "predictedNameEn"  to result.predictedNameEn,
            "confidence"       to result.confidence,
            "timestamp"        to Timestamp.now(),
            "probabilities"    to result.allClasses.map { it.probability }
        )
        val doc = historyCollection(userId).add(item).await()
        return doc.id
    }

    /** جلب سجل التشخيصات (آخر 50) */
    fun getPredictionsFlow(userId: String): Flow<List<HistoryItem>> = callbackFlow {
        val listener = historyCollection(userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, _ ->
                val items = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(HistoryItem::class.java)
                } ?: emptyList()
                trySend(items)
            }
        awaitClose { listener.remove() }
    }

    /** حذف تشخيص من السجل */
    suspend fun deletePrediction(userId: String, docId: String) {
        historyCollection(userId).document(docId).delete().await()
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  USER PROFILE
    // ══════════════════════════════════════════════════════════════════════════

    suspend fun ensureUserProfile(user: FirebaseUser) {
        val docRef = firestore.collection("users").document(user.uid)
        val doc    = docRef.get().await()
        if (!doc.exists()) {
            docRef.set(
                hashMapOf(
                    "uid"         to user.uid,
                    "email"       to (user.email ?: ""),
                    "displayName" to (user.displayName ?: ""),
                    "photoUrl"    to (user.photoUrl?.toString() ?: ""),
                    "createdAt"   to Timestamp.now()
                )
            ).await()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  STATS — إحصائيات المستخدم
    // ══════════════════════════════════════════════════════════════════════════

    suspend fun getUserStats(userId: String): Map<String, Any> {
        val snap = historyCollection(userId).get().await()
        val total    = snap.size()
        val byModel  = snap.documents.groupBy { it.getString("modelName") ?: "Unknown" }
            .mapValues { it.value.size }
        val avgConf  = snap.documents.mapNotNull { it.getDouble("confidence") }
            .takeIf { it.isNotEmpty() }?.average() ?: 0.0
        return mapOf(
            "total"         to total,
            "byModel"       to byModel,
            "avgConfidence" to avgConf
        )
    }
}
