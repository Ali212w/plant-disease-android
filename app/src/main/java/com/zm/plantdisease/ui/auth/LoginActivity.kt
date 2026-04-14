package com.zm.plantdisease.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.zm.plantdisease.R
import com.zm.plantdisease.data.firebase.FirebaseManager
import com.zm.plantdisease.databinding.ActivityLoginBinding
import com.zm.plantdisease.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val auth = FirebaseAuth.getInstance()

    // Google Sign-In Launcher
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            showError("فشل تسجيل الدخول بـ Google: ${e.message}")
            setLoading(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // إذا كان مسجّل دخول من قبل ← انتقل مباشرة
        if (FirebaseManager.isLoggedIn) {
            goToMain(); return
        }

        setupListeners()
    }

    private fun setupListeners() {

        // ── تسجيل دخول بـ Google ──────────────────────────────────────────
        binding.btnGoogleSignIn.setOnClickListener {
            setLoading(true)
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
            val signInIntent = GoogleSignIn.getClient(this, gso).signInIntent
            googleSignInLauncher.launch(signInIntent)
        }

        // ── تسجيل دخول بالبريد ───────────────────────────────────────────
        binding.btnEmailSignIn.setOnClickListener {
            val email    = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()
            if (email.isEmpty() || password.isEmpty()) {
                showError("أدخل البريد الإلكتروني وكلمة المرور")
                return@setOnClickListener
            }
            setLoading(true)
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { goToMain() }
                .addOnFailureListener { showError(it.message ?: "خطأ في تسجيل الدخول"); setLoading(false) }
        }

        // ── إنشاء حساب ───────────────────────────────────────────────────
        binding.btnRegister.setOnClickListener {
            val email    = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()
            if (email.isEmpty() || password.length < 6) {
                showError("البريد صحيح + كلمة مرور 6 أحرف على الأقل")
                return@setOnClickListener
            }
            setLoading(true)
            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    it.user?.let { u ->
                        CoroutineScope(Dispatchers.IO).launch {
                            FirebaseManager.ensureUserProfile(u)
                        }
                    }
                    goToMain()
                }
                .addOnFailureListener { showError(it.message ?: "خطأ في الإنشاء"); setLoading(false) }
        }

        // ── الدخول بدون حساب (ضيف) - بدون Firebase ──────────────────────────
        binding.btnGuest.setOnClickListener {
            // الدخول المحلي مباشرة بدون Firebase Auth
            goToMain()
        }

    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                result.user?.let { u ->
                    CoroutineScope(Dispatchers.IO).launch {
                        FirebaseManager.ensureUserProfile(u)
                    }
                }
                goToMain()
            }
            .addOnFailureListener {
                showError("فشل: ${it.message}")
                setLoading(false)
            }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnGoogleSignIn.isEnabled  = !loading
        binding.btnEmailSignIn.isEnabled   = !loading
        binding.btnRegister.isEnabled      = !loading
        binding.btnGuest.isEnabled         = !loading
    }

    private fun showError(msg: String) {
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
    }
}
